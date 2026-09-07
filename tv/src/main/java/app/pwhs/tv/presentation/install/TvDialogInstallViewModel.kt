package app.pwhs.tv.presentation.install

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.ApkMetadataReader
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.install.ApkInstaller
import app.pwhs.core.install.RootInstaller
import app.pwhs.core.telemetry.AnalyticsHelper
import app.pwhs.core.telemetry.TelemetryEvents
import app.pwhs.core.util.RootShell
import app.pwhs.core.util.SourceFileDeleter
import app.pwhs.tv.install.ShizukuInstaller
import app.pwhs.tv.install.TvInstallBackend
import app.pwhs.tv.install.TvShizuku
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class TvDialogInstallViewModel(application: Application) : AndroidViewModel(application) {

    private val context: Context = application.applicationContext
    private val metadataReader = ApkMetadataReader(context)
    private val installer = ApkInstaller(context)
    private val rootInstaller = RootInstaller(context)
    private val shizukuInstaller = ShizukuInstaller(context)

    private val _state = MutableStateFlow<TvInstallState>(TvInstallState.Loading)
    val state: StateFlow<TvInstallState> = _state.asStateFlow()

    private var initialUri: Uri? = null

    fun loadApk(uri: Uri) {
        initialUri = uri
        viewModelScope.launch {
            _state.value = TvInstallState.Loading
            val staged = stageApk(uri)
            if (staged == null) {
                _state.value = TvInstallState.ParseError("Could not read APK file.")
                return@launch
            }

            val (stagedUri, sizeBytes, fileName) = staged
            val isBundle = fileName.isBundleName()
            val meta = metadataReader.readMetadata(stagedUri, isBundle)

            if (meta == null) {
                _state.value = TvInstallState.ParseError("Could not parse APK metadata.")
                return@launch
            }

            val installedVersion = runCatching {
                val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(meta.packageName, PackageManager.PackageInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(meta.packageName, 0)
                }
                info.versionName
            }.getOrNull()

            _state.value = TvInstallState.Ready(
                uri = stagedUri,
                metadata = meta,
                isBundle = isBundle,
                sizeBytes = sizeBytes,
                installedVersion = installedVersion,
                showPermissions = false
            )
        }
    }

    fun togglePermissions() {
        val current = _state.value as? TvInstallState.Ready ?: return
        _state.value = current.copy(showPermissions = !current.showPermissions)
    }

    fun install() {
        val current = _state.value as? TvInstallState.Ready ?: return
        val uri = current.uri
        val meta = current.metadata
        val isBundle = current.isBundle
        val sizeBytes = current.sizeBytes

        _state.value = TvInstallState.Installing(meta.appName, if (sizeBytes > 0) 0f else null)

        viewModelScope.launch {
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
                    shizukuInstaller.install(uri, isBundle) { progress ->
                        _state.value = TvInstallState.Installing(meta.appName, progress)
                    }
                } else if (useRoot) {
                    rootInstaller.install(uri, isBundle) { progress ->
                        _state.value = TvInstallState.Installing(meta.appName, progress)
                    }
                } else {
                    installer.install(uri, isBundle, totalBytes = sizeBytes) { written, total ->
                        val progress = if (total > 0) (written.toFloat() / total).coerceIn(0f, 1f) else null
                        _state.value = TvInstallState.Installing(meta.appName, progress)
                    }
                }
            }

            val durationMs = System.currentTimeMillis() - startTime
            val status = if (result is ApkInstaller.Result.Success) {
                TelemetryEvents.RESULT_SUCCESS
            } else {
                TelemetryEvents.RESULT_FAILURE
            }
            val err = (result as? ApkInstaller.Result.Failure)?.message

            AnalyticsHelper.logInstallResult(
                fileType = ext,
                status = status,
                errorCode = err,
                installMode = installMode,
                durationMs = durationMs
            )

            when (result) {
                is ApkInstaller.Result.Success -> {
                    val deleteAfterInstall = prefs[SharedPrefsKeys.DELETE_APK_AFTER_INSTALL] ?: false
                    if (deleteAfterInstall) {
                        withContext(Dispatchers.IO) {
                            SourceFileDeleter.deleteSourceFile(context, uri)
                        }
                    }
                    _state.value = TvInstallState.Success(meta.appName, meta.packageName)
                }
                is ApkInstaller.Result.Failure -> {
                    _state.value = TvInstallState.Failure(meta.appName, result.message)
                }
            }
        }
    }

    fun retry() {
        initialUri?.let { loadApk(it) }
    }

    private suspend fun stageApk(uri: Uri): Triple<Uri, Long, String>? = withContext(Dispatchers.IO) {
        val fileName = getDisplayName(uri)
        if (uri.scheme == "file") {
            val existing = uri.path?.let(::File)
            if (existing != null && existing.canRead()) {
                return@withContext Triple(uri, existing.length(), fileName)
            }
        }

        val dir = File(context.cacheDir, "incoming").apply { mkdirs() }
        val target = File(dir, fileName)
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext null
            Triple(Uri.fromFile(target), target.length(), fileName)
        } catch (e: Exception) {
            null
        }
    }

    private fun getDisplayName(uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "package.apk"
        val fromProvider = runCatching {
            context.contentResolver
                .query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()
        return fromProvider ?: uri.lastPathSegment ?: "package.apk"
    }

    private fun String.isBundleName(): Boolean =
        substringAfterLast('.', "").lowercase() in setOf("apks", "xapk", "apkm", "apk+", "zip")
}
