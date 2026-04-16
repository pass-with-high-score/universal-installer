package com.nqmgaming.universalinstaller.presentation.install

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nqmgaming.universalinstaller.data.remote.VirusTotalService
import com.nqmgaming.universalinstaller.domain.model.ApkInfo
import com.nqmgaming.universalinstaller.domain.model.SessionData
import com.nqmgaming.universalinstaller.domain.model.VtResult
import com.nqmgaming.universalinstaller.domain.model.VtStatus
import com.nqmgaming.universalinstaller.domain.repository.SessionDataRepository
import com.nqmgaming.universalinstaller.presentation.install.controller.BaseInstallController
import com.nqmgaming.universalinstaller.presentation.install.controller.DefaultInstallController
import com.nqmgaming.universalinstaller.presentation.install.controller.ShizukuInstallController
import com.nqmgaming.universalinstaller.presentation.setting.dataStore
import kotlinx.coroutines.Dispatchers
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
) : ViewModel() {

    private val defaultController = DefaultInstallController(packageInstaller, sessionDataRepository)
    private val shizukuController = ShizukuInstallController(application, packageInstaller, sessionDataRepository)

    private val _isLoading = MutableStateFlow(false)
    private val _pendingApkInfo = MutableStateFlow<ApkInfo?>(null)

    private var pendingApkUris: List<Uri>? = null
    private var pendingFileName: String? = null

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
        viewModelScope.launch {
            _isLoading.value = true
            pendingFileName = fileName
            val info = withContext(Dispatchers.IO) {
                extractApkInfoAndCacheUris(context, uri, splitPackage, fileName)
            }
            _pendingApkInfo.value = info
            _isLoading.value = false
            launchVirusTotalScan(context, uri)
        }
    }

    fun confirmInstall() {
        val uris = pendingApkUris ?: return
        val fn = pendingFileName ?: return
        val apkInfo = _pendingApkInfo.value
        _pendingApkInfo.value = null
        pendingApkUris = null
        pendingFileName = null

        viewModelScope.launch {
            if (uris.isEmpty()) return@launch
            val iconPath = cacheIcon(apkInfo)
            val sessionData = SessionData(
                id = UUID.randomUUID(),
                name = fn,
                appName = apkInfo?.appName ?: "",
                iconPath = iconPath,
            )
            activeController().install(uris, sessionData, viewModelScope)
        }
    }

    fun dismissPendingInstall() {
        _pendingApkInfo.value = null
        pendingApkUris = null
        pendingFileName = null
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
                    ?: android.graphics.Bitmap.createBitmap(48, 48, android.graphics.Bitmap.Config.ARGB_8888).also { bmp ->
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, 48, 48)
                        drawable.draw(canvas)
                    }
                val file = File(application.cacheDir, "session_icon_${System.currentTimeMillis()}.png")
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
                file.absolutePath
            } catch (_: Exception) { null }
        }
    }

    private fun launchVirusTotalScan(context: Context, originalUri: Uri) {
        viewModelScope.launch {
            val prefs = context.dataStore.data.first()
            val apiKey = prefs[androidx.datastore.preferences.core.stringPreferencesKey("virustotal_api_key")] ?: ""
            if (apiKey.isBlank()) return@launch

            _pendingApkInfo.value = _pendingApkInfo.value?.copy(
                vtResult = VtResult(status = VtStatus.SCANNING)
            )

            val result = withContext(Dispatchers.IO) {
                try {
                    val tempFile = File(context.cacheDir, "temp_vt_${System.currentTimeMillis()}")
                    context.contentResolver.openInputStream(originalUri)?.use { input ->
                        tempFile.outputStream().use { output -> input.copyTo(output) }
                    }
                    val sha256 = virusTotalService.computeSha256(tempFile)
                    tempFile.delete()
                    val vtResult = virusTotalService.checkFile(apiKey, sha256)
                    sha256 to vtResult
                } catch (e: Exception) {
                    Timber.e(e, "VirusTotal scan error")
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
