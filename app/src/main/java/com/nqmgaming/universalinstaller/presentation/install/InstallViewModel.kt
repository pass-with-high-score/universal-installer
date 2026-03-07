package com.nqmgaming.universalinstaller.presentation.install

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nqmgaming.universalinstaller.R
import com.nqmgaming.universalinstaller.data.remote.VirusTotalService
import com.nqmgaming.universalinstaller.domain.model.ApkInfo
import com.nqmgaming.universalinstaller.domain.model.SessionData
import com.nqmgaming.universalinstaller.domain.model.VtResult
import com.nqmgaming.universalinstaller.domain.model.VtStatus
import com.nqmgaming.universalinstaller.domain.repository.SessionDataRepository
import com.nqmgaming.universalinstaller.presentation.setting.dataStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.DelicateAckpineApi
import ru.solrudev.ackpine.exceptions.ConflictingBaseApkException
import ru.solrudev.ackpine.exceptions.ConflictingPackageNameException
import ru.solrudev.ackpine.exceptions.ConflictingSplitNameException
import ru.solrudev.ackpine.exceptions.ConflictingVersionCodeException
import ru.solrudev.ackpine.exceptions.NoBaseApkException
import ru.solrudev.ackpine.exceptions.SplitPackageException
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.createSession
import ru.solrudev.ackpine.installer.getSession
import ru.solrudev.ackpine.resources.ResolvableString
import ru.solrudev.ackpine.session.ProgressSession
import ru.solrudev.ackpine.session.Session
import ru.solrudev.ackpine.session.await
import ru.solrudev.ackpine.session.parameters.Confirmation
import ru.solrudev.ackpine.session.progress
import ru.solrudev.ackpine.session.state
import ru.solrudev.ackpine.shizuku.useShizuku
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.splits.get
import timber.log.Timber
import java.io.File
import java.util.UUID

class InstallViewModel(
    private val application: android.app.Application,
    private val packageInstaller: PackageInstaller,
    private val sessionDataRepository: SessionDataRepository,
    private val virusTotalService: VirusTotalService,
) : ViewModel() {

    val error = MutableStateFlow(ResolvableString.empty())
    private val _isLoading = MutableStateFlow(false)
    private val _pendingApkInfo = MutableStateFlow<ApkInfo?>(null)
    private var activeSessions = mutableMapOf<UUID, ProgressSession<InstallFailure>>()

    // Cached from first .get() call — sequence can only be consumed once
    private var pendingApkUris: List<Uri>? = null
    private var pendingFileName: String? = null

    val uiState = combine(
        error,
        sessionDataRepository.sessions,
        sessionDataRepository.sessionsProgress,
        _isLoading,
        _pendingApkInfo,
    ) { err, sessions, progress, loading, apkInfo ->
        InstallUiState(
            error = err,
            sessions = sessions,
            sessionsProgress = progress,
            isLoading = loading,
            pendingApkInfo = apkInfo,
        )
    }
        .onStart { awaitSessionsFromSavedState() }
        .stateIn(viewModelScope, SharingStarted.Lazily, InstallUiState())

    fun parseApkInfo(context: Context, uri: Uri, splitPackage: SplitPackage.Provider, fileName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            pendingFileName = fileName

            val info = withContext(Dispatchers.IO) {
                extractApkInfoAndCacheUris(context, uri, splitPackage, fileName)
            }
            _pendingApkInfo.value = info
            _isLoading.value = false

            // Run VirusTotal scan in background after showing info
            launchVirusTotalScan(context, uri)
        }
    }

    private fun launchVirusTotalScan(context: Context, originalUri: Uri) {
        viewModelScope.launch {
            val prefs = context.dataStore.data.first()
            val apiKey = prefs[androidx.datastore.preferences.core.stringPreferencesKey("virustotal_api_key")] ?: ""
            if (apiKey.isBlank()) return@launch

            // Update to show scanning state
            _pendingApkInfo.value = _pendingApkInfo.value?.copy(
                vtResult = VtResult(status = VtStatus.SCANNING)
            )

            val result = withContext(Dispatchers.IO) {
                try {
                    // Copy file to compute SHA-256
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

    @OptIn(DelicateAckpineApi::class)
    fun confirmInstall() {
        val uris = pendingApkUris ?: return
        val fn = pendingFileName ?: return
        _pendingApkInfo.value = null
        pendingApkUris = null
        pendingFileName = null

        viewModelScope.launch {
            if (uris.isEmpty()) return@launch
            val useShizuku = readShizukuPref()
            val session = packageInstaller.createSession(uris) {
                name = fn
                confirmation = Confirmation.IMMEDIATE
                if (useShizuku) {
                    useShizuku {}
                }
            }
            activeSessions[session.id] = session
            val sessionData = SessionData(session.id, fn)
            sessionDataRepository.addSessionData(sessionData)
            awaitSession(session)
        }
    }

    fun dismissPendingInstall() {
        _pendingApkInfo.value = null
        pendingApkUris = null
        pendingFileName = null
    }

    @OptIn(DelicateAckpineApi::class)
    fun installPackage(splitPackage: SplitPackage.Provider, fileName: String) =
        viewModelScope.launch {
            _isLoading.value = true
            val uris = getApkUris(splitPackage)
            _isLoading.value = false
            if (uris.isEmpty()) return@launch
            val useShizuku = readShizukuPref()
            val session = packageInstaller.createSession(uris) {
                name = fileName
                confirmation = Confirmation.IMMEDIATE
                if (useShizuku) {
                    useShizuku {}
                }
            }
            activeSessions[session.id] = session
            val sessionData = SessionData(session.id, fileName)
            sessionDataRepository.addSessionData(sessionData)
            awaitSession(session)
        }

    fun cancelSession(id: UUID) {
        viewModelScope.launch {
            activeSessions[id]?.cancel()
            activeSessions.remove(id)
            sessionDataRepository.removeSessionData(id)
        }
    }

    private suspend fun readShizukuPref(): Boolean {
        return try {
            val prefs = application.dataStore.data.first()
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey("use_shizuku")] ?: false
        } catch (_: Exception) {
            false
        }
    }

    /**
     * Extract APK info and cache URIs (sequence can only be consumed once!):
     * 1. SplitPackage entries → packageName, version, size, ABIs, languages + cache URIs
     * 2. Base APK URI → copy to temp → PackageManager → icon, label, permissions, SDK
     * 3. AssetManager.getLocales() → languages fallback for single APKs
     * 4. ZipFile scan → ABIs fallback for single APKs
     */
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

        // Get total file size from ContentResolver
        val fileSize = try {
            context.contentResolver.query(originalUri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx >= 0) cursor.getLong(idx) else 0L
                    } else 0L
                } ?: 0L
        } catch (_: Exception) { 0L }

        // ── Step 1: Ackpine SplitPackage entries ──────────────────────
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
            // Cache all URIs from this single consumption of the sequence
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
                    is Apk.Libs -> {
                        supportedAbis.add(apk.abi.name)
                    }
                    is Apk.Localization -> {
                        val displayName = apk.locale.displayLanguage
                        if (displayName.isNotBlank() && displayName !in supportedLanguages) {
                            supportedLanguages.add(displayName)
                        }
                    }
                    else -> { /* ScreenDensity, Other, Feature — skip */ }
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error reading SplitPackage entries")
        }

        // ── Step 2: PackageManager for icon, label, permissions, SDK ──
        val uriForParsing = baseApkUri ?: originalUri
        var appName = fileName.substringBeforeLast('.')
        var icon: android.graphics.drawable.Drawable? = null
        var permissions = emptyList<String>()
        var minSdk = 0
        var targetSdk = 0

        try {
            val tempFile = File(context.cacheDir, "temp_parse_${System.currentTimeMillis()}.apk")
            context.contentResolver.openInputStream(uriForParsing)?.use { input ->
                tempFile.outputStream().use { output ->
                    input.copyTo(output)
                }
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
                icon = try {
                    packageInfo.applicationInfo?.loadIcon(pm)
                } catch (_: Exception) { null }
                permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
                minSdk = packageInfo.applicationInfo?.minSdkVersion ?: 0
                targetSdk = packageInfo.applicationInfo?.targetSdkVersion ?: 0

                if (ackpinePackageName.isEmpty()) {
                    ackpinePackageName = packageInfo.packageName
                }
                if (ackpineVersionName.isEmpty()) {
                    ackpineVersionName = packageInfo.versionName ?: ""
                }
                if (ackpineVersionCode == 0L) {
                    ackpineVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION")
                        packageInfo.versionCode.toLong()
                    }
                }
            }

            // ── Step 3: Extract languages via AssetManager.getLocales() ──
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

            // ── Step 4: Extract ABIs from lib/ directory inside APK ──
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
                        if (foundAbis.isNotEmpty()) {
                            supportedAbis.addAll(foundAbis.sorted())
                        }
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

    private suspend fun getApkUris(splitPackage: SplitPackage.Provider): List<Uri> {
        try {
            return splitPackage
                .get()
                .toList()
                .map { it.apk.uri }
        } catch (exception: SplitPackageException) {
            Timber.e("Error getting APK URIs: $exception")
            error.value = when (exception) {
                is NoBaseApkException -> ResolvableString.transientResource(R.string.error_no_base_apk)
                is ConflictingBaseApkException -> ResolvableString.transientResource(R.string.error_conflicting_base_apk)
                is ConflictingSplitNameException -> ResolvableString.transientResource(
                    R.string.error_conflicting_split_name, exception.name
                )
                is ConflictingPackageNameException -> ResolvableString.transientResource(
                    R.string.error_conflicting_package_name, exception.expected, exception.actual, exception.name
                )
                is ConflictingVersionCodeException -> ResolvableString.transientResource(
                    R.string.error_conflicting_version_code, exception.expected, exception.actual, exception.name
                )
            }
            return emptyList()
        } catch (cancellationException: CancellationException) {
            throw cancellationException
        } catch (exception: Exception) {
            error.value = ResolvableString.raw(exception.message.orEmpty())
            Timber.tag("InstallViewModel").e(exception)
            return emptyList()
        }
    }

    private fun awaitSession(session: ProgressSession<InstallFailure>) = viewModelScope.launch {
        session.progress
            .onEach { progress -> sessionDataRepository.updateSessionProgress(session.id, progress) }
            .launchIn(this)
        session.state
            .filterIsInstance<Session.State.Committed>()
            .onEach { sessionDataRepository.updateSessionIsCancellable(session.id, isCancellable = false) }
            .launchIn(this)
        try {
            when (val result = session.await()) {
                Session.State.Succeeded -> {
                    sessionDataRepository.removeSessionData(session.id)
                    activeSessions.remove(session.id)
                }
                is Session.State.Failed -> handleSessionError(result.failure.message, session.id)
            }
        } catch (exception: CancellationException) {
            sessionDataRepository.removeSessionData(session.id)
            activeSessions.remove(session.id)
            throw exception
        } catch (exception: Exception) {
            handleSessionError(exception.message, session.id)
            Timber.tag("InstallViewModel").e(exception)
        }
    }

    private fun handleSessionError(message: String?, sessionId: UUID) {
        val err = if (message != null) {
            ResolvableString.transientResource(R.string.session_error_with_reason, message)
        } else {
            ResolvableString.transientResource(R.string.session_error)
        }
        sessionDataRepository.setError(sessionId, err)
    }

    private fun awaitSessionsFromSavedState() = viewModelScope.launch {
        val sessions = sessionDataRepository.sessions.value
        if (sessions.isNotEmpty()) {
            sessions
                .map { sessionData -> async { packageInstaller.getSession(sessionData.id) } }
                .awaitAll()
                .filterNotNull()
                .forEach { session ->
                    activeSessions[session.id] = session
                    awaitSession(session)
                }
        }
    }
}