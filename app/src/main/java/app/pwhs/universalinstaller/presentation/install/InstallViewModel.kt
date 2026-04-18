package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalinstaller.R
import androidx.core.content.FileProvider
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.data.remote.PackageDownloadService
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.universalinstaller.data.remote.VirusTotalService
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.model.SessionProgress
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.presentation.install.controller.BaseInstallController
import app.pwhs.universalinstaller.presentation.install.controller.DefaultInstallController
import app.pwhs.universalinstaller.presentation.install.controller.ShizukuInstallController
import androidx.datastore.preferences.core.edit
import app.pwhs.universalinstaller.presentation.setting.dataStore
import app.pwhs.universalinstaller.util.extension.getDisplayName
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
import ru.solrudev.ackpine.splits.ApkSplits.validate
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.splits.SplitPackage.Companion.toSplitPackage
import ru.solrudev.ackpine.splits.ZippedApkSplits
import ru.solrudev.ackpine.splits.get
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.UUID

class InstallViewModel(
    private val application: android.app.Application,
    packageInstaller: PackageInstaller,
    private val sessionDataRepository: SessionDataRepository,
    private val virusTotalService: VirusTotalService,
    private val virusTotalNotifier: VirusTotalNotifier,
    private val packageDownloadService: PackageDownloadService,
    private val historyDao: InstallHistoryDao,
) : ViewModel() {

    private val defaultController = DefaultInstallController(application, packageInstaller, sessionDataRepository, historyDao)
    private val shizukuController = ShizukuInstallController(application, packageInstaller, sessionDataRepository, historyDao)

    private val _isLoading = MutableStateFlow(false)
    private val _pendingApkInfo = MutableStateFlow<ApkInfo?>(null)
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    private val _obbCopyState = MutableStateFlow<ObbCopyState>(ObbCopyState.Idle)
    private val _attachedObbFiles = MutableStateFlow<List<AttachedObb>>(emptyList())

    private var pendingApkUris: List<Uri>? = null
    private var pendingFileName: String? = null
    private var pendingOriginalUri: Uri? = null
    private var pendingDownloadedFile: File? = null
    private var pendingObbEntries: List<ObbEntry> = emptyList()

    /**
     * Snapshot of the most recent OBB copy job — kept around so a SAF-grant callback can
     * resume the copy after the user grants tree access. Cleared once the copy resolves.
     */
    private data class ObbCopyJob(
        val sourceUri: Uri?,
        val entries: List<ObbEntry>,
        val attached: List<AttachedObb>,
        val packageName: String,
        val appName: String,
    )
    private var pendingObbCopyJob: ObbCopyJob? = null

    private var scanJob: Job? = null
    private var scanNotifId: Int = -1
    private var downloadJob: Job? = null
    private var deviceScanJob: Job? = null

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
        _downloadState,
        _scanState,
        _obbCopyState,
        _attachedObbFiles,
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        InstallUiState(
            sessions = flows[0] as List<SessionData>,
            sessionsProgress = flows[1] as List<SessionProgress>,
            isLoading = flows[2] as Boolean,
            pendingApkInfo = flows[3] as ApkInfo?,
            downloadState = flows[4] as DownloadState,
            scanState = flows[5] as ScanState,
            obbCopyState = flows[6] as ObbCopyState,
            attachedObbFiles = flows[7] as List<AttachedObb>,
        )
    }
        .onStart { activeController().restoreSessionsFromSavedState(viewModelScope) }
        .stateIn(viewModelScope, SharingStarted.Lazily, InstallUiState())

    // ── Public actions ──────────────────────────────────

    fun parseApkInfo(context: Context, uri: Uri, splitPackage: SplitPackage.Provider, fileName: String) {
        // A new file invalidates any in-flight scan.
        cancelActiveScan()
        pendingObbEntries = emptyList()
        viewModelScope.launch {
            _isLoading.value = true
            pendingFileName = fileName
            pendingOriginalUri = uri
            val info = withContext(Dispatchers.IO) {
                extractApkInfoAndCacheUris(context, uri, splitPackage, fileName)
            }
            // OBB scan — only for archive-type bundles; skip raw APKs to avoid pointless I/O.
            val ext = fileName.substringAfterLast('.', "").lowercase()
            val archiveExts = setOf("apks", "xapk", "apkm", "zip")
            val obbEntries = if (ext in archiveExts) ObbExtractor.scan(context, uri) else emptyList()
            pendingObbEntries = obbEntries
            _pendingApkInfo.value = info.copy(
                obbFileNames = obbEntries.map { it.fileName },
                obbTotalBytes = obbEntries.sumOf { it.sizeBytes.coerceAtLeast(0L) },
            )
            _isLoading.value = false
            launchHashLookupOnly(context, uri)
        }
    }

    fun confirmInstall() {
        val uris = pendingApkUris ?: return
        val fn = pendingFileName ?: return
        val originalUri = pendingOriginalUri
        val apkInfo = _pendingApkInfo.value
        val obbEntries = pendingObbEntries
        val attachedObbs = _attachedObbFiles.value
        _pendingApkInfo.value = null
        pendingApkUris = null
        pendingFileName = null
        pendingOriginalUri = null
        pendingObbEntries = emptyList()
        _attachedObbFiles.value = emptyList()
        pendingDownloadedFile = null

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
            val hasZipObbs = obbEntries.isNotEmpty() && originalUri != null
            val hasAttachedObbs = attachedObbs.isNotEmpty()
            val onSuccess: (suspend () -> Unit)? = if ((hasZipObbs || hasAttachedObbs) && apkInfo != null) {
                val pkg = apkInfo.packageName
                val appName = apkInfo.appName.ifBlank { pkg }
                val hook: suspend () -> Unit = {
                    copyObbFiles(application, originalUri, obbEntries, attachedObbs, pkg, appName)
                }
                hook
            } else null
            activeController().install(
                uris = uris,
                sessionData = sessionData,
                scope = viewModelScope,
                context = application,
                originalUri = originalUri,
                deleteAfterInstall = deleteAfterInstall,
                onSuccess = onSuccess,
            )
        }
    }

    /**
     * Copies OBBs into `/sdcard/Android/obb/<pkg>/`. Strategy priority:
     *   1. Pre-Android-11 → direct File I/O (legacy storage permits it).
     *   2. Shizuku ready → stream via shell (runs as shell UID, has obb write access).
     *   3. Stored SAF tree grant for this package → DocumentFile writer.
     *   4. Otherwise → emit `NeedSafGrant` and remember the job so the UI-driven grant
     *      flow can resume via [onObbTreeGranted].
     */
    private suspend fun copyObbFiles(
        context: Context,
        sourceUri: Uri?,
        entries: List<ObbEntry>,
        attached: List<AttachedObb>,
        packageName: String,
        appName: String,
    ) {
        val zipTotal = entries.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        val attachedTotal = attached.sumOf { it.sizeBytes.coerceAtLeast(0L) }
        val combinedTotal = zipTotal + attachedTotal
        _obbCopyState.value = ObbCopyState.Running(appName, packageName, 0L, combinedTotal)
        pendingObbCopyJob = ObbCopyJob(sourceUri, entries, attached, packageName, appName)

        val strategy = selectObbStrategy(packageName)
        when (strategy) {
            ObbStrategy.Direct -> runObbCopyDirect(
                context, sourceUri, entries, attached, packageName, appName, combinedTotal,
            )
            ObbStrategy.Shizuku -> runObbCopyShizuku(
                context, sourceUri, entries, attached, packageName, appName, combinedTotal,
            )
            is ObbStrategy.Saf -> runObbCopySaf(
                context, sourceUri, entries, attached, packageName, appName, combinedTotal,
                strategy.treeUri,
            )
            ObbStrategy.NeedSafGrant -> {
                _obbCopyState.value = ObbCopyState.NeedSafGrant(appName, packageName)
            }
        }
    }

    private sealed interface ObbStrategy {
        data object Direct : ObbStrategy
        data object Shizuku : ObbStrategy
        data class Saf(val treeUri: Uri) : ObbStrategy
        data object NeedSafGrant : ObbStrategy
    }

    private suspend fun selectObbStrategy(packageName: String): ObbStrategy {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ObbStrategy.Direct
        if (ShizukuObbWriter.isReady()) return ObbStrategy.Shizuku
        val savedTree = readObbTreeGrant(packageName)
        if (savedTree != null && treeUriStillGranted(savedTree)) return ObbStrategy.Saf(savedTree)
        return ObbStrategy.NeedSafGrant
    }

    private fun treeUriStillGranted(uri: Uri): Boolean {
        return try {
            application.contentResolver.persistedUriPermissions.any {
                it.uri == uri && it.isReadPermission && it.isWritePermission
            }
        } catch (_: Exception) { false }
    }

    private suspend fun runObbCopyDirect(
        context: Context,
        sourceUri: Uri?,
        entries: List<ObbEntry>,
        attached: List<AttachedObb>,
        packageName: String,
        appName: String,
        combinedTotal: Long,
    ) {
        var bytesSoFar = 0L
        var totalCopied = 0
        if (entries.isNotEmpty() && sourceUri != null) {
            val r = ObbExtractor.extract(
                context = context,
                uri = sourceUri,
                entries = entries,
                packageName = packageName,
                onProgress = { copied, _ ->
                    _obbCopyState.value = ObbCopyState.Running(
                        appName, packageName, bytesSoFar + copied, combinedTotal,
                    )
                },
            )
            r.fold(
                onSuccess = {
                    totalCopied += it
                    bytesSoFar += entries.sumOf { e -> e.sizeBytes.coerceAtLeast(0L) }
                },
                onFailure = { t -> return finishObbWithError(appName, t) },
            )
        }
        if (attached.isNotEmpty()) {
            val r = ObbExtractor.extractFromUris(
                context = context,
                files = attached,
                packageName = packageName,
                onProgress = { copied, _ ->
                    _obbCopyState.value = ObbCopyState.Running(
                        appName, packageName, bytesSoFar + copied, combinedTotal,
                    )
                },
            )
            r.fold(
                onSuccess = { totalCopied += it },
                onFailure = { t -> return finishObbWithError(appName, t) },
            )
        }
        finishObbSuccess(appName, totalCopied)
    }

    private suspend fun runObbCopyShizuku(
        context: Context,
        sourceUri: Uri?,
        entries: List<ObbEntry>,
        attached: List<AttachedObb>,
        packageName: String,
        appName: String,
        combinedTotal: Long,
    ) {
        var bytesSoFar = 0L
        var totalCopied = 0

        suspend fun copyOne(open: () -> java.io.InputStream?, fileName: String): Boolean {
            val input = open() ?: run {
                finishObbWithError(appName, IOException("Cannot open $fileName"))
                return false
            }
            val r = input.use {
                ShizukuObbWriter.copy(
                    input = it,
                    packageName = packageName,
                    fileName = fileName,
                    onBytesProgress = { fileBytes ->
                        _obbCopyState.value = ObbCopyState.Running(
                            appName, packageName, bytesSoFar + fileBytes, combinedTotal,
                        )
                    },
                )
            }
            r.fold(
                onSuccess = { totalCopied += 1 },
                onFailure = { t -> finishObbWithError(appName, t); return false },
            )
            return true
        }

        // Bundle-embedded OBBs: re-read zip, pipe matching entries into Shizuku writer.
        if (entries.isNotEmpty() && sourceUri != null) {
            val entryPaths = entries.map { it.entryPath }.toSet()
            try {
                context.contentResolver.openInputStream(sourceUri)?.buffered()?.use { inputStream ->
                    java.util.zip.ZipInputStream(inputStream).use { zis ->
                        while (true) {
                            val entry = zis.nextEntry ?: break
                            if (entry.isDirectory || entry.name !in entryPaths) {
                                zis.closeEntry(); continue
                            }
                            val fileName = entry.name.substringAfterLast('/')
                            val beforeBytes = bytesSoFar
                            val r = ShizukuObbWriter.copy(
                                input = zis,
                                packageName = packageName,
                                fileName = fileName,
                                onBytesProgress = { fileBytes ->
                                    _obbCopyState.value = ObbCopyState.Running(
                                        appName, packageName, beforeBytes + fileBytes, combinedTotal,
                                    )
                                },
                            )
                            r.fold(
                                onSuccess = {
                                    totalCopied += 1
                                    val entryObj = entries.find { it.entryPath == entry.name }
                                    bytesSoFar += (entryObj?.sizeBytes ?: 0L).coerceAtLeast(0L)
                                },
                                onFailure = { t -> return finishObbWithError(appName, t) },
                            )
                            zis.closeEntry()
                        }
                    }
                } ?: return finishObbWithError(appName, IOException("Cannot open source"))
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                return finishObbWithError(appName, t)
            }
        }

        // Attached standalone OBBs.
        for (obb in attached) {
            val beforeBytes = bytesSoFar
            if (!copyOne({ context.contentResolver.openInputStream(obb.uri) }, obb.fileName)) return
            bytesSoFar = beforeBytes + obb.sizeBytes.coerceAtLeast(0L)
        }

        finishObbSuccess(appName, totalCopied)
    }

    private suspend fun runObbCopySaf(
        context: Context,
        sourceUri: Uri?,
        entries: List<ObbEntry>,
        attached: List<AttachedObb>,
        packageName: String,
        appName: String,
        combinedTotal: Long,
        treeUri: Uri,
    ) {
        var bytesSoFar = 0L
        var totalCopied = 0

        if (entries.isNotEmpty() && sourceUri != null) {
            val entryPaths = entries.map { it.entryPath }.toSet()
            try {
                context.contentResolver.openInputStream(sourceUri)?.buffered()?.use { inputStream ->
                    java.util.zip.ZipInputStream(inputStream).use { zis ->
                        while (true) {
                            val entry = zis.nextEntry ?: break
                            if (entry.isDirectory || entry.name !in entryPaths) {
                                zis.closeEntry(); continue
                            }
                            val fileName = entry.name.substringAfterLast('/')
                            val beforeBytes = bytesSoFar
                            val r = SafObbWriter.copy(
                                context = context,
                                input = zis,
                                treeUri = treeUri,
                                fileName = fileName,
                                onBytesProgress = { fileBytes ->
                                    _obbCopyState.value = ObbCopyState.Running(
                                        appName, packageName, beforeBytes + fileBytes, combinedTotal,
                                    )
                                },
                            )
                            r.fold(
                                onSuccess = {
                                    totalCopied += 1
                                    val e = entries.find { it.entryPath == entry.name }
                                    bytesSoFar += (e?.sizeBytes ?: 0L).coerceAtLeast(0L)
                                },
                                onFailure = { t -> return finishObbWithError(appName, t) },
                            )
                            zis.closeEntry()
                        }
                    }
                } ?: return finishObbWithError(appName, IOException("Cannot open source"))
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                return finishObbWithError(appName, t)
            }
        }

        for (obb in attached) {
            val beforeBytes = bytesSoFar
            val input = context.contentResolver.openInputStream(obb.uri)
                ?: return finishObbWithError(appName, IOException("Cannot open ${obb.fileName}"))
            val r = input.use {
                SafObbWriter.copy(
                    context = context,
                    input = it,
                    treeUri = treeUri,
                    fileName = obb.fileName,
                    onBytesProgress = { fileBytes ->
                        _obbCopyState.value = ObbCopyState.Running(
                            appName, packageName, beforeBytes + fileBytes, combinedTotal,
                        )
                    },
                )
            }
            r.fold(
                onSuccess = { totalCopied += 1; bytesSoFar = beforeBytes + obb.sizeBytes.coerceAtLeast(0L) },
                onFailure = { t -> return finishObbWithError(appName, t) },
            )
        }

        finishObbSuccess(appName, totalCopied)
    }

    private fun finishObbSuccess(appName: String, count: Int) {
        pendingObbCopyJob = null
        _obbCopyState.value = ObbCopyState.Done(appName, count)
    }

    private fun finishObbWithError(appName: String, t: Throwable) {
        pendingObbCopyJob = null
        _obbCopyState.value = ObbCopyState.Error(appName, t.message ?: "Copy failed")
    }

    /** User just granted (or denied) the SAF tree URI for `<pkg>`. Resume the pending job. */
    fun onObbTreeGranted(uri: Uri?) {
        val job = pendingObbCopyJob ?: return
        if (uri == null) {
            finishObbWithError(job.appName, IOException("OBB folder access not granted"))
            return
        }
        if (!SafObbWriter.isTreeForObbOf(uri, job.packageName)) {
            finishObbWithError(
                job.appName,
                IOException("Wrong folder picked — expected Android/obb/${job.packageName}/"),
            )
            return
        }
        try {
            application.contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) { /* best-effort */ }
        viewModelScope.launch {
            saveObbTreeGrant(job.packageName, uri)
            copyObbFiles(
                context = application,
                sourceUri = job.sourceUri,
                entries = job.entries,
                attached = job.attached,
                packageName = job.packageName,
                appName = job.appName,
            )
        }
    }

    fun obbTreeHintUri(): Uri? {
        val job = pendingObbCopyJob ?: return null
        return SafObbWriter.buildObbTreeHintUri(job.packageName)
    }

    private suspend fun readObbTreeGrant(packageName: String): Uri? = try {
        val prefs = application.dataStore.data.first()
        val key = androidx.datastore.preferences.core.stringPreferencesKey("obb_tree_$packageName")
        prefs[key]?.let(android.net.Uri::parse)
    } catch (_: Exception) { null }

    private suspend fun saveObbTreeGrant(packageName: String, uri: Uri) {
        try {
            application.dataStore.edit { prefs ->
                val key = androidx.datastore.preferences.core.stringPreferencesKey("obb_tree_$packageName")
                prefs[key] = uri.toString()
            }
        } catch (_: Exception) { /* best-effort */ }
    }

    fun dismissObbCopy() {
        _obbCopyState.value = ObbCopyState.Idle
    }

    /**
     * User attached a standalone `.obb` file via the preview sheet picker. Silently ignores
     * non-`.obb` extensions and duplicates. Takes a persistable read permission so the URI
     * survives until install success (may be minutes later for large APKs).
     */
    fun attachObbFile(context: Context, uri: Uri) {
        val displayName = context.contentResolver.getDisplayName(uri)
        if (!displayName.lowercase().endsWith(".obb")) return
        if (_attachedObbFiles.value.any { it.uri == uri }) return
        try {
            context.contentResolver.takePersistableUriPermission(
                uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (_: Exception) { /* best-effort; some providers don't support it */ }
        val size = try {
            context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (idx >= 0) c.getLong(idx) else 0L
                    } else 0L
                } ?: 0L
        } catch (_: Exception) { 0L }
        _attachedObbFiles.value = _attachedObbFiles.value + AttachedObb(uri, displayName, size)
    }

    fun removeAttachedObb(uri: Uri) {
        _attachedObbFiles.value = _attachedObbFiles.value.filterNot { it.uri == uri }
    }

    fun dismissPendingInstall() {
        cancelActiveScan()
        _pendingApkInfo.value = null
        pendingApkUris = null
        pendingOriginalUri = null
        pendingFileName = null
        pendingObbEntries = emptyList()
        _attachedObbFiles.value = emptyList()
        deletePendingDownloadedFile()
    }

    /**
     * Download a package from [url] into cacheDir, then run it through the same parse flow as a
     * locally-picked file. Progress surfaces via [InstallUiState.downloadState]; on success the
     * download card resets and the preview sheet opens.
     */
    fun downloadFromUrl(context: Context, url: String) {
        val trimmed = url.trim()
        if (!trimmed.startsWith("http://", ignoreCase = true) &&
            !trimmed.startsWith("https://", ignoreCase = true)
        ) {
            _downloadState.value = DownloadState.Error(
                context.getString(R.string.remote_download_invalid_url)
            )
            return
        }
        downloadJob?.cancel()
        _downloadState.value = DownloadState.Running(url = trimmed, bytesRead = 0L, totalBytes = -1L)
        downloadJob = viewModelScope.launch {
            val destName = trimmed.substringAfterLast('/').substringBefore('?')
                .ifBlank { "download_${System.currentTimeMillis()}" }
            val cacheDir = File(application.cacheDir, "downloads").apply { mkdirs() }
            val destination = File(cacheDir, "${System.currentTimeMillis()}_$destName")
            val result = packageDownloadService.download(trimmed, destination) { read, total ->
                _downloadState.value = DownloadState.Running(
                    url = trimmed,
                    bytesRead = read,
                    totalBytes = total,
                )
            }
            result.fold(
                onSuccess = { downloaded ->
                    val ext = downloaded.fileName.substringAfterLast('.', "").lowercase()
                    val validExtensions = listOf("apk", "apks", "xapk", "apkm", "zip")
                    if (ext !in validExtensions) {
                        downloaded.file.delete()
                        _downloadState.value = DownloadState.Error(
                            context.getString(R.string.remote_download_unsupported)
                        )
                        return@fold
                    }
                    _downloadState.value = DownloadState.Idle
                    handleDownloadedFile(context, downloaded.file, downloaded.fileName, ext)
                },
                onFailure = { e ->
                    if (e is kotlinx.coroutines.CancellationException) throw e
                    Timber.e(e, "Download failed")
                    _downloadState.value = DownloadState.Error(
                        context.getString(
                            R.string.remote_download_failed,
                            e.message ?: e::class.java.simpleName,
                        )
                    )
                },
            )
        }
    }

    fun cancelDownload() {
        downloadJob?.cancel()
        downloadJob = null
        _downloadState.value = DownloadState.Idle
    }

    fun dismissDownloadError() {
        if (_downloadState.value is DownloadState.Error) {
            _downloadState.value = DownloadState.Idle
        }
    }

    // ── Find automatic (device scan) ──────────────────────

    fun startDeviceScan(context: Context) {
        if (!ApkScanner.hasAllFilesAccess(context)) {
            _scanState.value = ScanState.PermissionNeeded
            return
        }
        deviceScanJob?.cancel()
        _scanState.value = ScanState.Scanning
        deviceScanJob = viewModelScope.launch {
            val results = try {
                ApkScanner.scan()
            } catch (ce: kotlinx.coroutines.CancellationException) {
                throw ce
            } catch (t: Throwable) {
                Timber.e(t, "Device scan failed")
                emptyList()
            }
            _scanState.value = ScanState.Ready(results)
        }
    }

    fun dismissDeviceScan() {
        deviceScanJob?.cancel()
        deviceScanJob = null
        _scanState.value = ScanState.Idle
    }

    /** User picked a file from the scan results — hand it to the normal parse flow. */
    fun pickFromScan(context: Context, found: FoundPackageFile) {
        val file = File(found.path)
        if (!file.exists()) return
        val uri = androidx.core.content.FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        val splitProvider = if (found.extension == "apk") {
            SingletonApkSequence(uri, context).toSplitPackage()
        } else {
            ZippedApkSplits.getApksForUri(uri, context)
                .validate()
                .toSplitPackage()
                .filterCompatible(context)
        }
        _scanState.value = ScanState.Idle
        deviceScanJob?.cancel()
        parseApkInfo(context, uri, splitProvider, found.name)
    }

    private fun handleDownloadedFile(context: Context, file: File, displayName: String, extension: String) {
        // Clean up any previous download that was never installed.
        deletePendingDownloadedFile()
        pendingDownloadedFile = file
        val uri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            file,
        )
        val splitProvider = if (extension == "apk") {
            SingletonApkSequence(uri, context).toSplitPackage()
        } else {
            ZippedApkSplits.getApksForUri(uri, context)
                .validate()
                .toSplitPackage()
                .filterCompatible(context)
        }
        parseApkInfo(context, uri, splitProvider, displayName)
    }

    private fun deletePendingDownloadedFile() {
        pendingDownloadedFile?.let { runCatching { it.delete() } }
        pendingDownloadedFile = null
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
