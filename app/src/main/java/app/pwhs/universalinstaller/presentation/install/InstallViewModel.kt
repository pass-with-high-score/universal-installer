package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.universalinstaller.data.remote.VirusTotalService
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.presentation.install.controller.BaseInstallController
import app.pwhs.universalinstaller.presentation.install.controller.DefaultInstallController
import app.pwhs.universalinstaller.presentation.install.controller.ShizukuInstallController
import app.pwhs.universalinstaller.presentation.setting.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.splits.get
import timber.log.Timber
import java.io.File
import java.util.UUID

class InstallViewModel(
    private val application: android.app.Application,
    packageInstaller: PackageInstaller,
    private val sessionDataRepository: SessionDataRepository,
    private val virusTotalService: VirusTotalService,
    private val virusTotalNotifier: VirusTotalNotifier,
    private val historyDao: InstallHistoryDao,
) : ViewModel() {

    private val defaultController = DefaultInstallController(application, packageInstaller, sessionDataRepository, historyDao)
    private val shizukuController = ShizukuInstallController(application, packageInstaller, sessionDataRepository, historyDao)

    private val _isLoading = MutableStateFlow(false)
    private val _pendingApkInfo = MutableStateFlow<ApkInfo?>(null)

    private var pendingApkUris: List<Uri>? = null
    private var pendingFileName: String? = null
    private var pendingOriginalUri: Uri? = null

    private var scanJob: Job? = null
    private var scanNotifId: Int = -1

    val history = historyDao.getAll()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun clearHistory() {
        viewModelScope.launch { historyDao.clearAll() }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch { historyDao.deleteById(id) }
    }

    val uiState = combine(
        sessionDataRepository.sessions,
        sessionDataRepository.sessionsProgress,
        _isLoading,
        _pendingApkInfo,
    ) { sessions, progress, loading, apkInfo ->
        InstallUiState(
            sessions = sessions,
            sessionsProgress = progress,
            isLoading = loading,
            pendingApkInfo = apkInfo,
        )
    }
        .onStart { activeController().restoreSessionsFromSavedState(viewModelScope) }
        .stateIn(viewModelScope, SharingStarted.Lazily, InstallUiState())

    // ── Public actions ──────────────────────────────────

    fun parseApkInfo(context: Context, uri: Uri, splitPackage: SplitPackage.Provider, fileName: String) {
        // A new file invalidates any in-flight scan.
        cancelActiveScan()
        viewModelScope.launch {
            _isLoading.value = true
            pendingFileName = fileName
            pendingOriginalUri = uri
            val info = withContext(Dispatchers.IO) {
                extractApkInfoAndCacheUris(context, uri, splitPackage, fileName)
            }
            _pendingApkInfo.value = info
            _isLoading.value = false
            // Auto-run a cheap hash lookup — if the file is already known to VT, we skip the upload.
            launchHashLookupOnly(context, uri)
        }
    }

    fun confirmInstall() {
        val uris = pendingApkUris ?: return
        val fn = pendingFileName ?: return
        val originalUri = pendingOriginalUri
        val apkInfo = _pendingApkInfo.value
        _pendingApkInfo.value = null
        pendingApkUris = null
        pendingFileName = null
        pendingOriginalUri = null

        viewModelScope.launch {
            if (uris.isEmpty()) return@launch
            val iconPath = cacheIcon(apkInfo)
            val deleteAfterInstall = readDeleteApkPref()
            val sessionData = SessionData(
                id = UUID.randomUUID(),
                name = fn,
                appName = apkInfo?.appName ?: "",
                iconPath = iconPath,
            )
            activeController().install(
                uris = uris,
                sessionData = sessionData,
                scope = viewModelScope,
                context = application,
                originalUri = originalUri,
                deleteAfterInstall = deleteAfterInstall,
            )
        }
    }

    fun dismissPendingInstall() {
        cancelActiveScan()
        _pendingApkInfo.value = null
        pendingApkUris = null
        pendingOriginalUri = null
        pendingFileName = null
    }

    /**
     * User explicitly asked VirusTotal to scan. Does hash lookup first; if the file isn't in VT's
     * database, streams it up (subject to [VirusTotalService.SIZE_LIMIT_DIRECT]) and polls the
     * analysis until VT returns a verdict. Progress is mirrored to both UI state and a
     * notification so the user can track it if they swipe the sheet away.
     */
    fun scanVirusTotal(context: Context) {
        val uri = pendingOriginalUri ?: return
        val fileName = pendingFileName ?: "APK"
        val current = _pendingApkInfo.value ?: return

        cancelActiveScan()
        scanJob = viewModelScope.launch {
            val apiKey = readVirusTotalApiKey()
            if (apiKey.isBlank()) {
                _pendingApkInfo.value = current.copy(vtResult = VtResult(status = VtStatus.NO_API_KEY))
                return@launch
            }

            val sizeBytes = current.fileSizeBytes
            if (sizeBytes > VirusTotalService.SIZE_LIMIT_LARGE) {
                _pendingApkInfo.value = current.copy(
                    vtResult = VtResult(
                        status = VtStatus.TOO_LARGE,
                        errorMessage = "${sizeBytes / (1024 * 1024)} MB",
                    )
                )
                return@launch
            }

            scanNotifId = virusTotalNotifier.notifyHashing(fileName)
            setVt(VtResult(status = VtStatus.SCANNING))

            // Reuse an already-computed hash if the auto-lookup populated one.
            val sha256 = current.sha256.ifBlank {
                runCatching {
                    withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { input ->
                            virusTotalService.computeSha256(input)
                        } ?: ""
                    }
                }.getOrDefault("")
            }
            if (sha256.isBlank()) {
                finishScanWithError("Could not hash file", fileName)
                return@launch
            }
            _pendingApkInfo.value = _pendingApkInfo.value?.copy(sha256 = sha256)

            val hashResult = virusTotalService.checkFile(apiKey, sha256)
            if (hashResult.status != VtStatus.NOT_FOUND) {
                finishScan(hashResult, fileName)
                return@launch
            }

            // VT doesn't know this file yet — upload. We stream the original URI; for bundles
            // this is the whole .xapk/.apks/.apkm blob, which is what we hashed above.
            val tempFile = runCatching {
                withContext(Dispatchers.IO) {
                    val f = File(context.cacheDir, "vt_upload_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        f.outputStream().use { output -> input.copyTo(output) }
                    } ?: return@withContext null
                    f
                }
            }.getOrNull()
            if (tempFile == null || !tempFile.exists()) {
                finishScanWithError("Could not read file for upload", fileName)
                return@launch
            }

            try {
                setVt(VtResult(status = VtStatus.UPLOADING, uploadProgress = 0))
                virusTotalNotifier.notifyUploading(scanNotifId, fileName, 0)

                val uploadResult = virusTotalService.uploadFile(apiKey, tempFile) { pct ->
                    setVt(VtResult(status = VtStatus.UPLOADING, uploadProgress = pct))
                    virusTotalNotifier.notifyUploading(scanNotifId, fileName, pct)
                }
                val analysisId = uploadResult.getOrElse { e ->
                    finishScanWithError(e.message ?: "Upload failed", fileName)
                    return@launch
                }

                setVt(VtResult(status = VtStatus.QUEUED, analysisId = analysisId))
                virusTotalNotifier.notifyQueued(scanNotifId, fileName)

                val finalResult = virusTotalService.pollAnalysis(apiKey, analysisId) { status ->
                    setVt(_pendingApkInfo.value?.vtResult?.copy(status = status) ?: VtResult(status = status))
                    when (status) {
                        VtStatus.ANALYZING -> virusTotalNotifier.notifyAnalyzing(scanNotifId, fileName)
                        VtStatus.QUEUED -> virusTotalNotifier.notifyQueued(scanNotifId, fileName)
                        else -> {}
                    }
                }
                finishScan(finalResult, fileName)
            } finally {
                runCatching { tempFile.delete() }
            }
        }
    }

    private fun setVt(vt: VtResult) {
        _pendingApkInfo.value = _pendingApkInfo.value?.copy(vtResult = vt)
    }

    private fun finishScan(result: VtResult, fileName: String) {
        setVt(result)
        val (title, text) = resultNotifCopy(result)
        virusTotalNotifier.notifyResult(scanNotifId, fileName, title, text)
        scanNotifId = -1
    }

    private fun finishScanWithError(message: String, fileName: String) {
        finishScan(VtResult(status = VtStatus.ERROR, errorMessage = message), fileName)
    }

    private fun resultNotifCopy(result: VtResult): Pair<String, String> {
        val ctx = application
        return when (result.status) {
            VtStatus.CLEAN -> ctx.getString(R.string.vt_notif_result_clean) to
                ctx.getString(R.string.apk_info_vt_clean)
            VtStatus.MALICIOUS -> ctx.getString(R.string.vt_notif_result_malicious) to
                ctx.getString(R.string.apk_info_vt_malicious, result.malicious)
            VtStatus.SUSPICIOUS -> ctx.getString(R.string.vt_notif_result_suspicious) to
                ctx.getString(R.string.apk_info_vt_suspicious, result.suspicious)
            VtStatus.NOT_FOUND -> ctx.getString(R.string.vt_notif_result_done) to
                ctx.getString(R.string.apk_info_vt_not_found)
            VtStatus.ERROR -> ctx.getString(R.string.vt_notif_result_error) to
                (result.errorMessage.ifBlank { "" })
            else -> ctx.getString(R.string.vt_notif_result_done) to ""
        }
    }

    private fun cancelActiveScan() {
        scanJob?.cancel()
        scanJob = null
        if (scanNotifId >= 0) {
            virusTotalNotifier.cancel(scanNotifId)
            scanNotifId = -1
        }
    }

    private suspend fun readVirusTotalApiKey(): String {
        return try {
            val prefs = application.dataStore.data.first()
            prefs[androidx.datastore.preferences.core.stringPreferencesKey("virustotal_api_key")] ?: ""
        } catch (_: Exception) { "" }
    }

    fun cancelSession(id: UUID) {
        viewModelScope.launch {
            activeController().cancel(id, viewModelScope)
        }
    }

    fun retrySession(id: UUID) {
        viewModelScope.launch {
            activeController().retry(id, viewModelScope)
        }
    }

    // ── Private helpers ─────────────────────────────────

    private suspend fun activeController(): BaseInstallController {
        return if (readShizukuPref()) shizukuController else defaultController
    }

    private suspend fun readDeleteApkPref(): Boolean {
        return try {
            val prefs = application.dataStore.data.first()
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey("delete_apk_after_install")] ?: false
        } catch (_: Exception) { false }
    }

    private suspend fun readShizukuPref(): Boolean {
        return try {
            val prefs = application.dataStore.data.first()
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey("use_shizuku")] ?: false
        } catch (_: Exception) {
            false
        }
    }

    private suspend fun cacheIcon(apkInfo: ApkInfo?): String? {
        val drawable = apkInfo?.icon ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = (drawable as? android.graphics.drawable.BitmapDrawable)?.bitmap
                    ?: android.graphics.Bitmap.createBitmap(192, 192, android.graphics.Bitmap.Config.ARGB_8888).also { bmp ->
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, 192, 192)
                        drawable.draw(canvas)
                    }
                val file = File(application.cacheDir, "session_icon_${System.currentTimeMillis()}.png")
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
                file.absolutePath
            } catch (_: Exception) { null }
        }
    }

    /**
     * Cheap hash-only pass done automatically when a file is picked. Populates [ApkInfo.sha256]
     * so the explicit scan button can skip re-hashing.
     */
    private fun launchHashLookupOnly(context: Context, originalUri: Uri) {
        viewModelScope.launch {
            val apiKey = readVirusTotalApiKey()
            if (apiKey.isBlank()) return@launch

            _pendingApkInfo.value = _pendingApkInfo.value?.copy(
                vtResult = VtResult(status = VtStatus.SCANNING)
            )

            val result = withContext(Dispatchers.IO) {
                runCatching {
                    val sha256 = context.contentResolver.openInputStream(originalUri)?.use { input ->
                        virusTotalService.computeSha256(input)
                    } ?: ""
                    if (sha256.isBlank()) "" to VtResult(
                        status = VtStatus.ERROR,
                        errorMessage = "Could not hash file",
                    )
                    else sha256 to virusTotalService.checkFile(apiKey, sha256)
                }.getOrElse { e ->
                    Timber.e(e, "VirusTotal hash lookup error")
                    "" to VtResult(status = VtStatus.ERROR, errorMessage = e.message ?: "Unknown error")
                }
            }

            _pendingApkInfo.value = _pendingApkInfo.value?.copy(
                sha256 = result.first,
                vtResult = result.second,
            )
        }
    }

    // ── APK parsing ─────────────────────────────────────

    private suspend fun extractApkInfoAndCacheUris(
        context: Context,
        originalUri: Uri,
        splitPackage: SplitPackage.Provider,
        fileName: String,
    ): ApkInfo {
        val pm = context.packageManager
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val fileFormat = when (extension) {
            "apk" -> "APK"
            "apks" -> "APKS (Split Bundle)"
            "xapk" -> "XAPK (Split Bundle)"
            "apkm" -> "APKM (Split Bundle)"
            else -> extension.uppercase()
        }

        val fileSize = try {
            context.contentResolver.query(originalUri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx >= 0) cursor.getLong(idx) else 0L
                    } else 0L
                } ?: 0L
        } catch (_: Exception) { 0L }

        var ackpinePackageName = ""
        var ackpineVersionName = ""
        var ackpineVersionCode = 0L
        var ackpineSize = 0L
        var splitCount = 0
        var baseApkUri: Uri? = null
        val supportedAbis = mutableListOf<String>()
        val supportedLanguages = mutableListOf<String>()

        try {
            val entries = splitPackage.get().toList()
            splitCount = entries.size
            pendingApkUris = entries.map { it.apk.uri }

            for (entry in entries) {
                when (val apk = entry.apk) {
                    is Apk.Base -> {
                        ackpinePackageName = apk.packageName
                        ackpineVersionName = apk.versionName
                        ackpineVersionCode = apk.versionCode
                        ackpineSize = apk.size
                        baseApkUri = apk.uri
                    }
                    is Apk.Libs -> supportedAbis.add(apk.abi.name)
                    is Apk.Localization -> {
                        val displayName = apk.locale.displayLanguage
                        if (displayName.isNotBlank() && displayName !in supportedLanguages) {
                            supportedLanguages.add(displayName)
                        }
                    }
                    else -> {}
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading SplitPackage entries")
        }

        val uriForParsing = baseApkUri ?: originalUri
        var appName = fileName.substringBeforeLast('.')
        var icon: android.graphics.drawable.Drawable? = null
        var permissions = emptyList<String>()
        var minSdk = 0
        var targetSdk = 0

        try {
            val tempFile = File(context.cacheDir, "temp_parse_${System.currentTimeMillis()}.apk")
            context.contentResolver.openInputStream(uriForParsing)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            val packageInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(
                    tempFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                )
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.GET_PERMISSIONS)
            }

            if (packageInfo != null) {
                packageInfo.applicationInfo?.sourceDir = tempFile.absolutePath
                packageInfo.applicationInfo?.publicSourceDir = tempFile.absolutePath

                appName = packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: appName
                icon = try { packageInfo.applicationInfo?.loadIcon(pm) } catch (_: Exception) { null }
                permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
                minSdk = packageInfo.applicationInfo?.minSdkVersion ?: 0
                targetSdk = packageInfo.applicationInfo?.targetSdkVersion ?: 0

                if (ackpinePackageName.isEmpty()) ackpinePackageName = packageInfo.packageName
                if (ackpineVersionName.isEmpty()) ackpineVersionName = packageInfo.versionName ?: ""
                if (ackpineVersionCode == 0L) {
                    ackpineVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
                    }
                }
            }

            if (supportedLanguages.isEmpty() && tempFile.exists()) {
                try {
                    val am = android.content.res.AssetManager::class.java
                        .getDeclaredConstructor().newInstance()
                    val addAssetPath = am.javaClass
                        .getDeclaredMethod("addAssetPath", String::class.java)
                    addAssetPath.isAccessible = true
                    addAssetPath.invoke(am, tempFile.absolutePath)
                    @Suppress("DEPRECATION")
                    val locales = am.locales
                    for (localeStr in locales) {
                        if (localeStr.isBlank()) continue
                        val locale = java.util.Locale.forLanguageTag(localeStr.replace('_', '-'))
                        val displayName = locale.getDisplayLanguage(java.util.Locale.ENGLISH)
                        if (displayName.isNotBlank() && displayName !in supportedLanguages) {
                            supportedLanguages.add(displayName)
                        }
                    }
                    supportedLanguages.sort()
                    am.close()
                } catch (e: Exception) {
                    Timber.d(e, "Error extracting locales via AssetManager")
                }
            }

            if (supportedAbis.isEmpty() && tempFile.exists()) {
                try {
                    java.util.zip.ZipFile(tempFile).use { zip ->
                        val abiRegex = Regex("^lib/([^/]+)/")
                        val foundAbis = mutableSetOf<String>()
                        for (entry in zip.entries()) {
                            abiRegex.find(entry.name)?.groupValues?.get(1)?.let { abi ->
                                foundAbis.add(abi)
                            }
                        }
                        if (foundAbis.isNotEmpty()) supportedAbis.addAll(foundAbis.sorted())
                    }
                } catch (e: Exception) {
                    Timber.d(e, "Error scanning APK for ABIs")
                }
            }

            tempFile.delete()
        } catch (e: Exception) {
            Timber.e(e, "Error parsing APK with PackageManager")
        }

        return ApkInfo(
            appName = appName,
            packageName = ackpinePackageName.ifEmpty { "Unknown" },
            versionName = ackpineVersionName,
            versionCode = ackpineVersionCode,
            icon = icon,
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            fileSizeBytes = if (fileSize > 0) fileSize else ackpineSize,
            permissions = permissions,
            splitCount = splitCount,
            fileFormat = fileFormat,
            supportedAbis = supportedAbis.distinct(),
            supportedLanguages = supportedLanguages.sorted(),
        )
    }
}
