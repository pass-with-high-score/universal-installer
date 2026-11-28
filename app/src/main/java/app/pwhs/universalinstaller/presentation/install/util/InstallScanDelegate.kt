package app.pwhs.universalinstaller.presentation.install.util

import android.app.Application
import android.content.Context
import android.net.Uri
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.DownloadHistoryDao
import app.pwhs.universalinstaller.data.remote.DownloadSegment
import app.pwhs.universalinstaller.data.remote.PackageDownloadService
import app.pwhs.universalinstaller.data.remote.SegmentedDownloadEngine
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.install.ActiveDownloadItem
import app.pwhs.universalinstaller.presentation.install.ActiveDownloadStatus
import app.pwhs.universalinstaller.presentation.install.DownloadNotifier
import app.pwhs.universalinstaller.presentation.install.DownloadPartProgress
import app.pwhs.universalinstaller.presentation.install.DownloadState
import app.pwhs.universalinstaller.presentation.install.FoundPackageFile
import app.pwhs.universalinstaller.presentation.install.ScanState
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.solrudev.ackpine.splits.SplitPackage
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

class InstallScanDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val packageDownloadService: PackageDownloadService,
    private val downloadHistoryDao: DownloadHistoryDao,
    private val downloadNotifier: DownloadNotifier,
    private val onApkParsed: (Context, Uri, SplitPackage.Provider, String, Boolean?) -> Unit,
    private val onBatchSelected: (Context, List<Uri>) -> Unit,
) {
    private class DownloadTaskRecord(
        val id: String,
        val url: String,
        val destination: File,
        val displayName: String,
        @Volatile var totalBytes: Long = -1L,
        @Volatile var segments: List<DownloadSegment> = emptyList(),
        val paused: AtomicBoolean = AtomicBoolean(false),
    )

    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    val downloadState: StateFlow<DownloadState> = _downloadState.asStateFlow()

    private val _activeDownloads = MutableStateFlow<List<ActiveDownloadItem>>(emptyList())
    val activeDownloads: StateFlow<List<ActiveDownloadItem>> = _activeDownloads.asStateFlow()

    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val scanState: StateFlow<ScanState> = _scanState.asStateFlow()

    private val downloadJobs = ConcurrentHashMap<String, Job>()
    private val taskRecords = ConcurrentHashMap<String, DownloadTaskRecord>()
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
        // Ignore duplicate tap for an URL already actively downloading.
        val alreadyRunning = _activeDownloads.value.any {
            it.url == trimmed && (it.status == ActiveDownloadStatus.PREPARING || it.status == ActiveDownloadStatus.DOWNLOADING)
        }
        if (alreadyRunning) return

        _downloadState.value = DownloadState.Idle
        val (destination, displayName) = InstallDownloadHelper.prepareDestinationFile(context, trimmed)
        val id = UUID.randomUUID().toString()
        val record = DownloadTaskRecord(
            id = id,
            url = trimmed,
            destination = destination,
            displayName = displayName,
        )
        taskRecords[id] = record
        upsertActiveItem(
            ActiveDownloadItem(
                id = id,
                url = trimmed,
                fileName = displayName,
                status = ActiveDownloadStatus.PREPARING,
            )
        )
        Telemetry.feature(TelemetryEvents.FEATURE_URL_DOWNLOAD)
        launchDownloadTask(context, record)
    }

    fun pauseDownload(id: String) {
        val record = taskRecords[id] ?: return
        // Only pause if we have resumable segments; otherwise fall back to cancel.
        if (record.segments.isEmpty() || record.totalBytes <= 0L) {
            cancelDownload(id)
            return
        }
        record.paused.set(true)
        downloadJobs.remove(id)?.cancel()
        updateActiveItem(id) {
            it.copy(
                status = ActiveDownloadStatus.PAUSED,
                speedBytesPerSec = 0L,
                etaSeconds = null,
            )
        }
    }

    fun resumeDownload(context: Context, id: String) {
        val record = taskRecords[id] ?: return
        if (downloadJobs[id]?.isActive == true) return
        record.paused.set(false)
        updateActiveItem(id) { it.copy(status = ActiveDownloadStatus.DOWNLOADING, errorMessage = null) }
        launchDownloadTask(context, record)
    }

    fun cancelDownload(id: String) {
        val record = taskRecords.remove(id)
        record?.paused?.set(false)
        downloadJobs.remove(id)?.cancel()
        record?.destination?.let { runCatching { it.delete() } }
        removeActiveItem(id)
        if (_activeDownloads.value.isEmpty()) {
            _downloadState.value = DownloadState.Idle
            downloadNotifier.cancel()
        }
    }

    fun cancelDownload() {
        val ids = taskRecords.keys.toList()
        ids.forEach { cancelDownload(it) }
        _downloadState.value = DownloadState.Idle
        downloadNotifier.cancel()
    }

    fun dismissDownloadError() {
        if (_downloadState.value is DownloadState.Error) {
            _downloadState.value = DownloadState.Idle
            downloadNotifier.cancel()
        }
    }

    private fun launchDownloadTask(context: Context, record: DownloadTaskRecord) {
        val id = record.id
        downloadJobs[id]?.cancel()
        val job = scope.launch {
            val unregister = DownloadCancellationManager.register { cancelDownload(id) }
            try {
                val maxConnections = runCatching {
                    application.dataStore.data.first()[PreferencesKeys.DOWNLOAD_MAX_CONNECTIONS]
                }.getOrNull()?.coerceIn(1, 8) ?: SegmentedDownloadEngine.DEFAULT_MAX_CONNECTIONS
                InstallDownloadHelper.executeSegmentedDownload(
                    context = context,
                    url = record.url,
                    destination = record.destination,
                    displayName = record.displayName,
                    packageDownloadService = packageDownloadService,
                    downloadNotifier = downloadNotifier,
                    downloadHistoryDao = downloadHistoryDao,
                    maxConnections = maxConnections,
                    initialSegments = record.segments,
                    knownTotalBytes = record.totalBytes,
                    shouldKeepPartialOnCancel = { record.paused.get() },
                    onProgress = { read, total, speed, eta, segments, threads ->
                        record.totalBytes = total
                        if (segments.isNotEmpty()) {
                            record.segments = segments
                        }
                        val uiParts = segments.map {
                            DownloadPartProgress(it.startByte, it.currentByte, it.endByte)
                        }
                        updateActiveItem(id) {
                            it.copy(
                                bytesRead = read,
                                totalBytes = total,
                                speedBytesPerSec = speed,
                                etaSeconds = eta,
                                status = ActiveDownloadStatus.DOWNLOADING,
                                threadCount = threads,
                                parts = uiParts,
                            )
                        }
                    },
                    onError = { msg ->
                        taskRecords.remove(id)
                        removeActiveItem(id)
                        _downloadState.value = DownloadState.Error(msg)
                    },
                    onSuccess = { file, name, ext ->
                        taskRecords.remove(id)
                        removeActiveItem(id)
                        handleDownloadedFile(context, file, name, ext)
                    },
                )
            } catch (e: CancellationException) {
                throw e
            } finally {
                unregister()
                if (!record.paused.get()) {
                    downloadJobs.remove(id)
                }
            }
        }
        downloadJobs[id] = job
    }

    private fun upsertActiveItem(item: ActiveDownloadItem) {
        _activeDownloads.update { list ->
            val idx = list.indexOfFirst { it.id == item.id }
            if (idx >= 0) list.toMutableList().apply { set(idx, item) }
            else list + item
        }
    }

    private fun updateActiveItem(id: String, transform: (ActiveDownloadItem) -> ActiveDownloadItem) {
        _activeDownloads.update { list ->
            list.map { if (it.id == id) transform(it) else it }
        }
    }

    private fun removeActiveItem(id: String) {
        _activeDownloads.update { list -> list.filterNot { it.id == id } }
    }

    fun startDeviceScan(context: Context) {
        deviceScanJob?.cancel()
        _scanState.value = ScanState.Scanning()
        deviceScanJob = scope.launch {
            val readyState = InstallScanHelper.performDeviceScan(application) { status, count, progress ->
                _scanState.value = ScanState.Scanning(status = status, foundCount = count, progress = progress)
            }
            if (readyState is ScanState.Ready) {
                _scanState.value = ScanState.Scanning(
                    status = application.getString(R.string.find_auto_count, readyState.files.size),
                    foundCount = readyState.files.size,
                    progress = 1.0f,
                )
                kotlinx.coroutines.delay(300)
            }
            _scanState.value = readyState
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
