package app.pwhs.tv.presentation.receive

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.ApkMetadataReader
import app.pwhs.core.data.DownloadsApkScanner
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.ApkFile
import app.pwhs.core.install.ApkInstaller
import app.pwhs.core.install.RootInstaller
import app.pwhs.tv.install.TvInstallBackend
import app.pwhs.tv.install.TvShizuku
import app.pwhs.tv.install.ShizukuInstaller
import app.pwhs.core.receiver.ConnectedClient
import app.pwhs.core.receiver.ReceivedApk
import app.pwhs.core.receiver.ReceivingProgress
import app.pwhs.core.receiver.ReceiverStatus
import app.pwhs.core.receiver.TvReceiverState
import app.pwhs.core.receiver.TvReceiver
import app.pwhs.core.telemetry.AnalyticsHelper
import app.pwhs.core.telemetry.TelemetryEvents
import app.pwhs.core.util.RootShell
import app.pwhs.core.util.SourceFileDeleter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class ReceiveViewModel(application: Application) : AndroidViewModel(application) {

    private val context = application.applicationContext
    private val metadataReader = ApkMetadataReader(context)
    private val installer = ApkInstaller(context)
    private val rootInstaller = RootInstaller(context)
    private val shizukuInstaller = ShizukuInstaller(context)

    val status: StateFlow<ReceiverStatus> = TvReceiverState.status
    val connectedClient: StateFlow<ConnectedClient?> = TvReceiverState.connectedClient
    val receivingProgress: StateFlow<ReceivingProgress?> = TvReceiverState.receivingProgress

    private val _pendingApk = MutableStateFlow<ReceivedApk?>(null)
    val pendingApk: StateFlow<ReceivedApk?> = _pendingApk.asStateFlow()

    private val _downloads = MutableStateFlow<List<ApkFile>>(emptyList())
    val downloads: StateFlow<List<ApkFile>> = _downloads.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    private val _installingLabel = MutableStateFlow<String?>(null)
    val installingLabel: StateFlow<String?> = _installingLabel.asStateFlow()

    /** 0f..1f while the session is being written, null when the total size is unknown/idle. */
    private val _installProgress = MutableStateFlow<Float?>(null)
    val installProgress: StateFlow<Float?> = _installProgress.asStateFlow()

    private val _installResult = MutableStateFlow<InstallOutcome?>(null)
    val installResult: StateFlow<InstallOutcome?> = _installResult.asStateFlow()

    private val _deleteOutcome = MutableStateFlow<DeleteOutcome?>(null)
    val deleteOutcome: StateFlow<DeleteOutcome?> = _deleteOutcome.asStateFlow()

    /** Kept so the result overlay can offer a one-tap Retry without re-picking the APK. */
    private var lastInstall: InstallRequest? = null

    sealed interface InstallOutcome {
        data class Success(val label: String, val silent: Boolean = false) : InstallOutcome
        data class Failure(val label: String, val message: String) : InstallOutcome
    }

    sealed interface DeleteOutcome {
        data class Success(val label: String) : DeleteOutcome
        data class Failure(val label: String) : DeleteOutcome
    }

    private data class InstallRequest(val uri: Uri, val isBundle: Boolean, val label: String, val sizeBytes: Long)

    init {
        // Ensure TvReceiver is running
        if (TvReceiverState.status.value is ReceiverStatus.Stopped) {
            TvReceiver.start(context)
        }
        // Clear any stale progress from previous session
        TvReceiverState.emitReceivingProgress(null)

        viewModelScope.launch {
            while (isActive) {
                delay(3000)
                val current = TvReceiverState.connectedClient.value
                if (current != null && System.currentTimeMillis() - current.lastSeenTimestamp > 45_000L) {
                    TvReceiverState.updateConnectedClient(null)
                }
            }
        }
        viewModelScope.launch {
            TvReceiverState.received.collectLatest { received ->
                _installResult.value = null
                val ext = received.fileName.substringAfterLast('.', "")
                AnalyticsHelper.logFilePicked(
                    fileType = ext,
                    fileCount = 1,
                    source = TelemetryEvents.SOURCE_TV_RECEIVED
                )
                // Extract metadata immediately for received APK
                val metadata = metadataReader.readMetadata(Uri.fromFile(File(received.path)), received.fileName.isBundleName())
                val status = if (metadata != null) TelemetryEvents.PARSE_SUCCESS else TelemetryEvents.PARSE_CORRUPTED
                AnalyticsHelper.logPackageParseResult(
                    fileType = ext,
                    status = status,
                    hasObb = false,
                    targetSdk = metadata?.targetSdk ?: 0
                )
                _pendingApk.value = received.copy(metadata = metadata)
            }
        }
    }

    fun disconnect() {
        TvReceiverState.updateConnectedClient(null)
        TvReceiverState.emitReceivingProgress(null)
        TvReceiver.restart(context)
    }

    /** True once a scan has been kicked off, so tab revisits don't redundantly re-scan. */
    private var hasScannedOnce = false

    /** Scan only if we never have — used on screen entry; the Rescan button calls [scanLocalApks]. */
    fun scanLocalApksIfNeeded() {
        if (hasScannedOnce) return
        scanLocalApks()
    }

    fun scanLocalApks() {
        if (_isScanning.value) return
        hasScannedOnce = true
        viewModelScope.launch {
            _isScanning.value = true
            val files = withContext(Dispatchers.IO) { DownloadsApkScanner.scan(context) }
            _downloads.value = files
            
            // Optionally load metadata for local files lazily or all at once if small
            // For now, let's load them all to show icons in the list
            val enriched = files.map { file ->
                val meta = metadataReader.readMetadata(Uri.parse(file.uri), file.isBundle)
                file.copy(metadata = meta)
            }
            _downloads.value = enriched
            _isScanning.value = false
        }
    }

    fun install(uri: Uri, isBundle: Boolean, label: String, sizeBytes: Long) {
        if (_installingLabel.value != null) return
        lastInstall = InstallRequest(uri, isBundle, label, sizeBytes)
        viewModelScope.launch {
            _installResult.value = null
            _installingLabel.value = label
            _installProgress.value = if (sizeBytes > 0) 0f else null
            val prefs = context.dataStore.data.first()
            val backend = TvInstallBackend.select(
                shizukuEnabled = prefs[TvShizuku.enabledKey] ?: false,
                rootEnabled = prefs[SharedPrefsKeys.ROOT_SILENT_INSTALL] ?: true,
                shizukuReady = { TvShizuku.status(context) == TvShizuku.Status.Ready },
                rootReady = { RootShell.isAvailable() },
            )
            val useShizuku = backend == TvInstallBackend.Shizuku
            val useRoot = backend == TvInstallBackend.Root
            val startTime = System.currentTimeMillis()
            val ext = uri.lastPathSegment?.substringAfterLast('.', "") ?: "apk"
            val installMode = when {
                useShizuku -> TelemetryEvents.MODE_SHIZUKU
                useRoot -> TelemetryEvents.MODE_ROOT
                else -> TelemetryEvents.MODE_SESSION_INSTALLER
            }
            AnalyticsHelper.logInstallStarted(
                fileType = ext,
                installMode = installMode,
                isSplit = isBundle,
                fileSizeBytes = sizeBytes
            )
            val result = withContext(Dispatchers.IO) {
                if (useShizuku) {
                    shizukuInstaller.install(uri, isBundle) { _installProgress.value = it }
                } else if (useRoot) {
                    rootInstaller.install(uri, isBundle) { f -> _installProgress.value = f }
                } else {
                    installer.install(uri, isBundle, totalBytes = sizeBytes) { written, total ->
                        if (total > 0) _installProgress.value = (written.toFloat() / total).coerceIn(0f, 1f)
                    }
                }
            }
            val durationMs = System.currentTimeMillis() - startTime
            val status = if (result is ApkInstaller.Result.Success) TelemetryEvents.RESULT_SUCCESS else TelemetryEvents.RESULT_FAILURE
            val err = (result as? ApkInstaller.Result.Failure)?.message
            AnalyticsHelper.logInstallResult(
                fileType = ext,
                status = status,
                errorCode = err,
                installMode = installMode,
                durationMs = durationMs
            )
            _installingLabel.value = null
            _installProgress.value = null
            _installResult.value = when (result) {
                is ApkInstaller.Result.Success -> {
                    _pendingApk.value = null // received APK is installed — clear the hero so the QR returns
                    val deleteAfterInstall = prefs[SharedPrefsKeys.DELETE_APK_AFTER_INSTALL] ?: false
                    if (deleteAfterInstall) {
                        withContext(Dispatchers.IO) {
                            SourceFileDeleter.deleteSourceFile(context, uri)
                        }
                        _downloads.value = _downloads.value.filterNot { it.uri == uri.toString() }
                    }
                    InstallOutcome.Success(label, silent = useRoot || useShizuku)
                }
                is ApkInstaller.Result.Failure -> InstallOutcome.Failure(label, result.message)
            }
        }
    }

    fun retryInstall() {
        lastInstall?.let { install(it.uri, it.isBundle, it.label, it.sizeBytes) }
    }

    fun clearInstallResult() {
        _installResult.value = null
    }

    fun dismissPending() {
        _pendingApk.value = null
        _installResult.value = null
    }

    fun deleteLocalApk(apk: ApkFile) {
        viewModelScope.launch {
            val label = apk.metadata?.appName ?: apk.displayName
            val success = withContext(Dispatchers.IO) {
                app.pwhs.core.util.SourceFileDeleter.deleteSourceFile(context, Uri.parse(apk.uri))
            }
            if (success) {
                _downloads.value = _downloads.value.filterNot { it.uri == apk.uri }
                _deleteOutcome.value = DeleteOutcome.Success(label)
            } else {
                _deleteOutcome.value = DeleteOutcome.Failure(label)
            }
        }
    }

    fun clearDeleteOutcome() {
        _deleteOutcome.value = null
    }

    private fun String.isBundleName(): Boolean =
        substringAfterLast('.', "").lowercase() in setOf("apks", "xapk", "apkm", "apk+", "zip")
}
