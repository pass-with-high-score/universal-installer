package app.pwhs.universalinstaller.presentation.install.util

import android.app.Application
import android.content.Context
import android.net.Uri
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.DownloadHistoryDao
import app.pwhs.universalinstaller.data.remote.PackageDownloadService
import app.pwhs.universalinstaller.presentation.install.DownloadNotifier
import app.pwhs.universalinstaller.presentation.install.DownloadState
import app.pwhs.universalinstaller.presentation.install.FoundPackageFile
import app.pwhs.universalinstaller.presentation.install.ScanState
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import ru.solrudev.ackpine.splits.SplitPackage
import java.io.File

class InstallScanDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val packageDownloadService: PackageDownloadService,
    private val downloadHistoryDao: DownloadHistoryDao,
    private val downloadNotifier: DownloadNotifier,
    private val onApkParsed: (Context, Uri, SplitPackage.Provider, String, Boolean?) -> Unit,
    private val onBatchSelected: (Context, List<Uri>) -> Unit,
) {
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private var downloadJob: Job? = null
    private var deviceScanJob: Job? = null

    fun resetScanState() {
        _scanState.value = ScanState.Idle
    }

    fun downloadFromUrl(context: Context, url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            _downloadState.value = DownloadState.Error(context.getString(R.string.remote_download_invalid_url))
            return
        }
        downloadJob?.cancel()
        Telemetry.feature(TelemetryEvents.FEATURE_URL_DOWNLOAD)
        downloadJob = scope.launch {
            val unregister = DownloadCancellationManager.register { cancelDownload() }
            try {
                InstallDownloadHelper.executeDownload(
                    context = context,
                    url = trimmed,
                    packageDownloadService = packageDownloadService,
                    downloadNotifier = downloadNotifier,
                    downloadHistoryDao = downloadHistoryDao,
                    onProgress = { _downloadState.value = it },
                    onSuccess = { file, name, ext ->
                        handleDownloadedFile(context, file, name, ext)
                    },
                )
            } finally {
                unregister()
            }
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = DownloadState.Idle
        downloadNotifier.cancel()
    }

    fun dismissDownloadError() {
        if (_downloadState.value is DownloadState.Error) {
            _downloadState.value = DownloadState.Idle
            downloadNotifier.cancel()
        }
    }

    fun startDeviceScan(context: Context) {
        deviceScanJob?.cancel()
        _scanState.value = ScanState.Scanning()
        deviceScanJob = scope.launch {
            _scanState.value = InstallScanHelper.performDeviceScan(application) { status, count ->
                _scanState.value = ScanState.Scanning(status = status, foundCount = count)
            }
        }
    }

    fun dismissDeviceScan() {
        deviceScanJob?.cancel()
        deviceScanJob = null
        _scanState.value = ScanState.Idle
    }

    fun deleteFoundFiles(context: Context, files: List<FoundPackageFile>) {
        if (files.isEmpty()) return
        scope.launch {
            InstallScanHelper.deleteFoundFiles(files)
            startDeviceScan(context)
        }
    }

    fun pickFromScan(context: Context, found: FoundPackageFile) {
        val file = File(found.path)
        if (!file.exists()) return
        val uri = InstallScanHelper.resolveUriForFile(context, file)
        val splitProvider = InstallApkSplitsHelper.buildSplitProvider(context, uri, found.extension)
        onApkParsed(context, uri, splitProvider, found.name, found.isAndroidAutoSupported)
    }

    fun pickManyFromScan(context: Context, found: List<FoundPackageFile>) {
        val uris = InstallScanHelper.collectUrisFromScan(context, found)
        if (uris.size >= 2) {
            onBatchSelected(context, uris)
        } else if (uris.size == 1) {
            found.firstOrNull { File(it.path).exists() }?.let { pickFromScan(context, it) }
        }
    }

    private fun handleDownloadedFile(context: Context, file: File, displayName: String, extension: String) {
        val uri = InstallScanHelper.resolveUriForFile(context, file)
        val splitProvider = InstallApkSplitsHelper.buildSplitProvider(context, uri, extension)
        onApkParsed(context, uri, splitProvider, displayName, null)
    }
}
