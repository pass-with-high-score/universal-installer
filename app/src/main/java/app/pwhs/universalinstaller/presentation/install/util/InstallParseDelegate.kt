package app.pwhs.universalinstaller.presentation.install.util

import android.app.Application
import android.content.Context
import android.net.Uri
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.universalinstaller.data.remote.VirusTotalService
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.InstallerProfile
import app.pwhs.universalinstaller.domain.model.SplitType
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.domain.scanner.DexTrackerScanner
import app.pwhs.universalinstaller.presentation.install.PendingInstallStore
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.splits.SplitPackage

class InstallParseDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val virusTotalService: VirusTotalService,
    private val virusTotalNotifier: VirusTotalNotifier,
    private val obbDelegate: InstallObbDelegate,
    private val onProfileMatched: (String) -> Unit,
) {
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _isApk = MutableStateFlow(false)
    val isApk: StateFlow<Boolean> = _isApk.asStateFlow()

    private val _pendingApkInfo = MutableStateFlow<ApkInfo?>(null)
    val pendingApkInfo: StateFlow<ApkInfo?> = _pendingApkInfo.asStateFlow()

    var pendingApkUris: List<Uri>? = null
        private set

    var pendingFileName: String? = null
        private set

    var pendingOriginalUri: Uri? = null
        private set

    private var scanJob: Job? = null
    private var parseJob: Job? = null
    private var trackerScanJob: Job? = null

    fun parseApkInfo(
        context: Context,
        uri: Uri,
        splitPackage: SplitPackage.Provider,
        fileName: String,
        isAndroidAuto: Boolean? = null,
        blacklist: Set<String>,
        currentProfiles: List<InstallerProfile>,
        appProfileMapping: Map<String, String>,
    ) {
        scanJob?.cancel()
        obbDelegate.setPendingEntries(emptyList())
        parseJob?.cancel()
        parseJob = scope.launch {
            _isLoading.value = true
            _isApk.value = fileName.substringAfterLast('.', "").lowercase() == "apk"
            pendingFileName = fileName
            pendingOriginalUri = uri
            val result = InstallParseCoordinator.parseApkInfo(
                context = context,
                uri = uri,
                splitPackage = splitPackage,
                fileName = fileName,
                isAndroidAuto = isAndroidAuto,
                blacklist = blacklist,
                currentProfiles = currentProfiles,
                appProfileMapping = appProfileMapping,
            )
            val status = if (result.info.packageName.isNotEmpty()) {
                app.pwhs.core.telemetry.TelemetryEvents.PARSE_SUCCESS
            } else {
                app.pwhs.core.telemetry.TelemetryEvents.PARSE_CORRUPTED
            }
            app.pwhs.core.telemetry.AnalyticsHelper.logPackageParseResult(
                fileType = fileName.substringAfterLast('.', ""),
                status = status,
                hasObb = result.obbEntries.isNotEmpty(),
                targetSdk = result.info.targetSdkVersion
            )
            pendingApkUris = result.splitUris
            obbDelegate.setPendingEntries(result.obbEntries)
            _pendingApkInfo.value = result.info
            _isLoading.value = false
            if (result.matchingProfileId != null) {
                onProfileMatched(result.matchingProfileId)
            }
            launchHashLookupOnly(context, uri)
            launchTrackerScan(context, uri, result.splitUris)
        }
    }

    fun toggleSplit(index: Int) {
        val info = _pendingApkInfo.value ?: return
        val entries = info.splitEntries.toMutableList()
        if (index !in entries.indices) return
        val entry = entries[index]
        if (entry.type == SplitType.Base) return
        entries[index] = entry.copy(selected = !entry.selected)
        _pendingApkInfo.value = info.copy(splitEntries = entries)
        pendingApkUris = entries.filter { it.selected }.map { it.uri }
    }

    fun dismissPendingInstall() {
        trackerScanJob?.cancel()
        _pendingApkInfo.value = null
        pendingApkUris = null
        pendingFileName = null
        pendingOriginalUri = null
        obbDelegate.clear()
    }

    suspend fun stashPendingInstall(): PendingInstallStore.Entry? {
        val entry = PendingInstallStager.stashPendingInstall(
            context = application,
            apkInfo = _pendingApkInfo.value,
            pendingFileName = pendingFileName,
            pendingOriginalUri = pendingOriginalUri,
            pendingApkUris = pendingApkUris,
            pendingObbEntries = obbDelegate.pendingObbEntries,
            attachedObbFiles = obbDelegate.attachedObbFiles.value,
            cachedIconPath = InstallSessionManager.cacheIcon(application, _pendingApkInfo.value),
        )
        if (entry != null) {
            dismissPendingInstall()
        }
        return entry
    }

    fun restorePendingInstall(entry: PendingInstallStore.Entry) {
        _pendingApkInfo.value = entry.apkInfo
        pendingApkUris = entry.apkUris
        pendingFileName = entry.fileName
        pendingOriginalUri = entry.originalUri
        obbDelegate.restore(entry.obbEntries, entry.attachedObbs)
    }

    fun updatePendingApkInfo(transform: (ApkInfo) -> ApkInfo) {
        val current = _pendingApkInfo.value ?: return
        _pendingApkInfo.value = transform(current)
    }

    fun stopParsing() {
        _isLoading.value = false
        parseJob?.cancel()
        trackerScanJob?.cancel()
    }

    fun scanVirusTotal(context: Context) {
        val uri = pendingOriginalUri ?: return
        val fileName = pendingFileName ?: "APK"
        val current = _pendingApkInfo.value ?: return
        scanJob?.cancel()
        Telemetry.feature(TelemetryEvents.FEATURE_VIRUSTOTAL)
        scanJob = scope.launch {
            InstallVirusTotalHelper.scanVirusTotal(
                context = context,
                uri = uri,
                fileName = fileName,
                current = current,
                virusTotalService = virusTotalService,
                virusTotalNotifier = virusTotalNotifier,
                onUpdateSha256 = { sha256 ->
                    _pendingApkInfo.value = _pendingApkInfo.value?.copy(sha256 = sha256)
                },
                onProgress = { vtResult ->
                    _pendingApkInfo.value = _pendingApkInfo.value?.copy(vtResult = vtResult)
                },
            )
        }
    }

    private fun launchHashLookupOnly(context: Context, originalUri: Uri) {
        scope.launch {
            _pendingApkInfo.value = _pendingApkInfo.value?.copy(
                vtResult = VtResult(status = VtStatus.SCANNING),
            )
            val result = InstallVirusTotalHelper.launchHashLookupOnly(context, originalUri, virusTotalService)
            _pendingApkInfo.value = _pendingApkInfo.value?.copy(
                sha256 = result.first,
                vtResult = result.second,
            )
        }
    }

    private fun launchTrackerScan(context: Context, uri: Uri, splitUris: List<Uri>?) {
        trackerScanJob?.cancel()
        trackerScanJob = scope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) {
                updatePendingApkInfo { it.copy(isScanningTrackers = true) }
            }
            val targetUri = splitUris?.firstOrNull { it.path?.contains("base", ignoreCase = true) == true }
                ?: splitUris?.firstOrNull()
                ?: uri
            val detected = DexTrackerScanner.scanApk(context, targetUri)
            withContext(Dispatchers.Main) {
                updatePendingApkInfo {
                    it.copy(isScanningTrackers = false, trackers = detected)
                }
            }
        }
    }
}
