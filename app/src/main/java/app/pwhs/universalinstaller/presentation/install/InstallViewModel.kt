package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
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
import app.pwhs.universalinstaller.domain.model.SplitEntry
import app.pwhs.universalinstaller.domain.model.SplitType
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.model.SessionProgress
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.presentation.install.controller.BaseInstallController
import app.pwhs.universalinstaller.presentation.install.controller.DefaultInstallController
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.ShizukuInstallController
import androidx.datastore.preferences.core.edit
import androidx.work.ExistingWorkPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.dataStore
import app.pwhs.universalinstaller.util.extension.getDisplayName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.CloseableSequence
import ru.solrudev.ackpine.splits.ApkSplits.validate
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.splits.SplitPackage.Companion.toSplitPackage
import ru.solrudev.ackpine.splits.ZippedApkSplits
import ru.solrudev.ackpine.splits.get
import timber.log.Timber
import java.io.File
import java.io.IOException
import java.util.UUID
import androidx.core.graphics.createBitmap
import java.util.zip.ZipFile

class InstallViewModel(
    private val application: android.app.Application,
    packageInstaller: PackageInstaller,
    private val sessionDataRepository: SessionDataRepository,
    private val virusTotalService: VirusTotalService,
    private val virusTotalNotifier: VirusTotalNotifier,
    private val packageDownloadService: PackageDownloadService,
    private val historyDao: InstallHistoryDao,
    private val downloadHistoryDao: app.pwhs.universalinstaller.data.local.DownloadHistoryDao,
    private val backendFactory: InstallerBackendFactory,
) : ViewModel() {

    private val defaultController = DefaultInstallController(application, packageInstaller, sessionDataRepository, historyDao)
    private val shizukuController = ShizukuInstallController(application, packageInstaller, sessionDataRepository, historyDao)

    // Null on the store flavor — activeController() silently skips the Root branch there.
    private val rootController: BaseInstallController? = backendFactory.createRootController(
        application, packageInstaller, sessionDataRepository, historyDao,
    )

    private val _isLoading = MutableStateFlow(false)
    private val _pendingApkInfo = MutableStateFlow<ApkInfo?>(null)
    private val _downloadState = MutableStateFlow<DownloadState>(DownloadState.Idle)
    private val _scanState = MutableStateFlow<ScanState>(ScanState.Idle)
    private val _obbCopyState = MutableStateFlow<ObbCopyState>(ObbCopyState.Idle)
    private val _attachedObbFiles = MutableStateFlow<List<AttachedObb>>(emptyList())
    private val _batchState = MutableStateFlow<BatchInstallState>(BatchInstallState.Idle)
    private val _dialogStage = MutableStateFlow<DialogStage>(DialogStage.None)
    private val _mergeSplits = MutableStateFlow(false)

    /**
     * Snapshot of the install target captured at confirmInstall time. We can't read
     * pendingApkInfo in Success/Failed stages because confirmInstall clears it; this
     * keeps just enough to render the post-install UI (Open button, app name, icon).
     */
    private val _dialogTarget = MutableStateFlow<DialogTarget?>(null)
    val dialogTarget: StateFlow<DialogTarget?> = _dialogTarget.asStateFlow()

    private var pendingApkUris: List<Uri>? = null
    private var pendingFileName: String? = null
    private var pendingOriginalUri: Uri? = null
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

    init {
        // Re-attach to any OBB worker still running from a previous app session so the user
        // sees live progress / error state on the install screen even after closing + reopening.
        reattachRunningObbWorker()
    }

    private fun reattachRunningObbWorker() {
        viewModelScope.launch {
            try {
                val wm = WorkManager.getInstance(application)
                val active = wm.getWorkInfosByTag(ObbCopyWorker.WORK_TAG).get()
                    .firstOrNull { !it.state.isFinished }
                    ?: return@launch
                // App-name is already encoded in the notification; we don't preserve it across
                // process death, so fall back to a generic label for the UI card.
                observeObbWorker(active.id, appName = "", packageName = "")
            } catch (t: Throwable) {
                Timber.w(t, "Could not re-attach to OBB worker")
            }
        }
    }

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
        _batchState,
        _dialogStage,
        _mergeSplits,
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
            batchState = flows[8] as BatchInstallState,
            dialogStage = flows[9] as DialogStage,
            mergeSplits = flows[10] as Boolean,
        )
    }
        .onStart { activeController().restoreSessionsFromSavedState(viewModelScope) }
        .stateIn(viewModelScope, SharingStarted.Lazily, InstallUiState())

    // ── Public actions ──────────────────────────────────

    // ── Dialog stage navigation (InstallerX-style multi-stage) ──

    /** Transition dialog to Loading stage (called when parse starts). */
    fun dialogStartLoading() { _dialogStage.value = DialogStage.Loading }

    /** Transition dialog to Prepare stage (called when parse completes). */
    fun dialogShowPrepare() { _dialogStage.value = DialogStage.Prepare }

    /** User tapped Menu → show extended options. */
    fun dialogShowMenu() { _dialogStage.value = DialogStage.Menu }

    /** User tapped Back in Menu → return to Prepare. */
    fun dialogBackToPrepare() { _dialogStage.value = DialogStage.Prepare }

    /** Transition to Installing stage. */
    fun dialogStartInstalling() { _dialogStage.value = DialogStage.Installing }

    /** Install completed successfully. */
    fun dialogInstallSuccess() { _dialogStage.value = DialogStage.Success }

    /** Install failed. */
    fun dialogInstallFailed(error: String) { _dialogStage.value = DialogStage.Failed(error) }

    /** Close dialog entirely. */
    fun dialogClose() { _dialogStage.value = DialogStage.None }

    /** Toggle whether to merge split APKs in batch install. */
    fun setMergeSplits(merge: Boolean) {
        _mergeSplits.value = merge
        // If we're already in a batch Ready state, re-parse to apply the change.
        val state = _batchState.value
        if (state is BatchInstallState.Ready) {
            val uris = state.entries.map { it.uri }
            parseBatch(application, uris)
        }
    }

    /** Clear the captured install target — call when the dialog is fully closed. */
    fun clearDialogTarget() { _dialogTarget.value = null }

    /**
     * Returns the launch intent for an installed package, or null if not launchable
     * (services, libraries, packages without a MAIN/LAUNCHER activity).
     */
    fun getAppLaunchIntent(packageName: String): android.content.Intent? =
        application.packageManager.getLaunchIntentForPackage(packageName)

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
            // Look up the currently-installed version so the dialog can flag downgrades and
            // gate them behind a confirmation. Returns nulls if not installed — the most
            // common case — and that's a clean signal for "no downgrade risk".
            val installed = withContext(Dispatchers.IO) {
                lookupInstalledVersion(context, info.packageName)
            }
            _pendingApkInfo.value = info.copy(
                obbFileNames = obbEntries.map { it.fileName },
                obbTotalBytes = obbEntries.sumOf { it.sizeBytes.coerceAtLeast(0L) },
                installedVersionName = installed?.first,
                installedVersionCode = installed?.second,
            )
            _isLoading.value = false
            launchHashLookupOnly(context, uri)
        }
    }

    fun toggleSplit(index: Int) {
        val info = _pendingApkInfo.value ?: return
        val entries = info.splitEntries.toMutableList()
        if (index !in entries.indices) return
        val entry = entries[index]
        // Base APK cannot be deselected
        if (entry.type == SplitType.Base) return
        entries[index] = entry.copy(selected = !entry.selected)
        _pendingApkInfo.value = info.copy(splitEntries = entries)
        // Update pendingApkUris to match selected splits
        pendingApkUris = entries.filter { it.selected }.map { it.uri }
    }

    fun confirmInstall(trackDialogTarget: Boolean = false) {
        // Use selected splits from ApkInfo if available, otherwise fall back to cached URIs
        val apkInfo = _pendingApkInfo.value
        val uris = if (apkInfo != null && apkInfo.splitEntries.isNotEmpty()) {
            apkInfo.splitEntries.filter { it.selected }.map { it.uri }
        } else {
            pendingApkUris
        }
        if (uris.isNullOrEmpty()) {
            Timber.e("confirmInstall: pendingApkUris=${uris} — parse failed or splits incompatible")
            android.widget.Toast.makeText(
                application,
                application.getString(R.string.install_no_splits_error),
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        val fn = pendingFileName ?: return
        val originalUri = pendingOriginalUri
        val obbEntries = pendingObbEntries
        val attachedObbs = _attachedObbFiles.value
        _pendingApkInfo.value = null
        pendingApkUris = null
        pendingFileName = null
        pendingOriginalUri = null
        pendingObbEntries = emptyList()
        _attachedObbFiles.value = emptyList()

        viewModelScope.launch {
            val iconPath = cacheIcon(apkInfo)
            val deleteAfterInstall = readDeleteApkPref()
            val sessionData = SessionData(
                // ackpine generates its own session ID inside controller.install() and the
                // repository entry is stored under that one — this placeholder is overwritten.
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
            // Capture target metadata in locals — the apkInfo reference is closed over but
            // pendingApkInfo has already been cleared, so the callback below only sees these.
            val pkgForTarget = apkInfo?.packageName.orEmpty()
            val nameForTarget = apkInfo?.appName.orEmpty().ifBlank { fn }
            activeController().install(
                uris = uris,
                sessionData = sessionData,
                scope = viewModelScope,
                context = application,
                originalUri = originalUri,
                deleteAfterInstall = deleteAfterInstall,
                onSuccess = onSuccess,
                onSessionCreated = if (trackDialogTarget) {
                    { realSessionId ->
                        _dialogTarget.value = DialogTarget(
                            sessionId = realSessionId,
                            packageName = pkgForTarget,
                            appName = nameForTarget,
                            iconPath = iconPath,
                        )
                    }
                } else null,
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

        when (val strategy = selectObbStrategy(packageName)) {
            ObbStrategy.Direct -> enqueueObbWorker(
                ObbCopyWorker.STRATEGY_DIRECT, null, sourceUri, entries, attached, packageName, appName,
            )
            ObbStrategy.Shizuku -> enqueueObbWorker(
                ObbCopyWorker.STRATEGY_SHIZUKU, null, sourceUri, entries, attached, packageName, appName,
            )
            is ObbStrategy.Saf -> enqueueObbWorker(
                ObbCopyWorker.STRATEGY_SAF, strategy.treeUri, sourceUri, entries, attached, packageName, appName,
            )
            ObbStrategy.NeedSafGrant -> {
                _obbCopyState.value = ObbCopyState.NeedSafGrant(appName, packageName)
            }
        }
    }

    /**
     * Enqueue the foreground worker and mirror its WorkInfo back into [_obbCopyState] so the
     * Install screen UI stays in sync whether or not the app is in the foreground.
     */
    private fun enqueueObbWorker(
        strategy: String,
        treeUri: Uri?,
        sourceUri: Uri?,
        entries: List<ObbEntry>,
        attached: List<AttachedObb>,
        packageName: String,
        appName: String,
    ) {
        val wm = WorkManager.getInstance(application)
        val data = ObbCopyWorker.buildInputData(
            strategy, packageName, appName, sourceUri, entries, attached, treeUri,
        )
        val workName = ObbCopyWorker.workNameFor(UUID.nameUUIDFromBytes(packageName.toByteArray()))
        val request = ObbCopyWorker.buildRequest(workName, data)
        wm.enqueueUniqueWork(workName, ExistingWorkPolicy.REPLACE, request)
        observeObbWorker(request.id, appName, packageName)
    }

    private var obbWorkerObserverJob: Job? = null

    private fun observeObbWorker(workId: UUID, appName: String, packageName: String) {
        obbWorkerObserverJob?.cancel()
        obbWorkerObserverJob = viewModelScope.launch {
            WorkManager.getInstance(application)
                .getWorkInfoByIdFlow(workId)
                .collect { info ->
                    if (info == null) return@collect
                    when (info.state) {
                        WorkInfo.State.ENQUEUED,
                        WorkInfo.State.RUNNING,
                        WorkInfo.State.BLOCKED -> {
                            val bytes = info.progress.getLong(ObbCopyWorker.KEY_PROGRESS_BYTES, 0L)
                            val total = info.progress.getLong(ObbCopyWorker.KEY_PROGRESS_TOTAL, 0L)
                            _obbCopyState.value = ObbCopyState.Running(
                                appName = appName,
                                packageName = packageName,
                                bytesCopied = bytes,
                                totalBytes = total,
                            )
                        }
                        WorkInfo.State.SUCCEEDED -> {
                            val count = info.outputData.getInt(ObbCopyWorker.KEY_RESULT_FILE_COUNT, 0)
                            pendingObbCopyJob = null
                            _obbCopyState.value = ObbCopyState.Done(appName, count)
                        }
                        WorkInfo.State.FAILED -> {
                            val err = info.outputData.getString(ObbCopyWorker.KEY_RESULT_ERROR) ?: "Copy failed"
                            pendingObbCopyJob = null
                            _obbCopyState.value = ObbCopyState.Error(appName, err)
                        }
                        WorkInfo.State.CANCELLED -> {
                            pendingObbCopyJob = null
                            _obbCopyState.value = ObbCopyState.Error(appName, "Cancelled")
                        }
                    }
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
        prefs[key]?.let(Uri::parse)
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
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { c ->
                    if (c.moveToFirst()) {
                        val idx = c.getColumnIndex(OpenableColumns.SIZE)
                        if (idx >= 0) c.getLong(idx) else 0L
                    } else 0L
                } ?: 0L
        } catch (_: Exception) { 0L }
        _attachedObbFiles.value += AttachedObb(uri, displayName, size)
    }

    fun removeAttachedObb(uri: Uri) {
        _attachedObbFiles.value = _attachedObbFiles.value.filterNot { it.uri == uri }
    }

    // ── Batch install ────────────────────────────────────

    /**
     * Parse N files in a single pass. Each URI is resolved to a [BatchApkEntry] containing
     * the full [ApkInfo] + installable split URIs. Failed parses produce an entry with
     * `parseError` set so the user can still see what went wrong instead of silent drops.
     * Emits `Parsing(processed, total)` as a loading indicator between `Idle` and `Ready`.
     */
    fun parseBatch(context: Context, uris: List<Uri>) {
        if (uris.size <= 1) return
        _batchState.value = BatchInstallState.Parsing(processed = 0, total = uris.size)
        viewModelScope.launch {
            val entries = mutableListOf<BatchApkEntry>()
            withContext(Dispatchers.IO) {
                uris.forEachIndexed { index, uri ->
                    val displayName = context.contentResolver.getDisplayName(uri)
                    val extension = displayName.substringAfterLast('.', "").lowercase()
                    try {
                        val splitProvider = buildSplitProvider(context, uri, extension)
                        // Single enumeration: extractApkInfoAndCacheUris also populates
                        // pendingApkUris from the same sequence, so we grab it and reset.
                        // Avoids iterating a (potentially large) ZIP twice for XAPK/APKS.
                        val (info, splitUris) = parseApkInfoForBatch(
                            context, uri, splitProvider, displayName,
                        )
                        entries += BatchApkEntry(
                            uri = uri,
                            fileName = displayName,
                            apkInfo = info,
                            splitUris = splitUris,
                            selected = splitUris.isNotEmpty(),
                            parseError = if (splitUris.isEmpty())
                                "No installable splits found" else null,
                        )
                    } catch (t: Throwable) {
                        Timber.e(t, "Batch parse failed for $uri")
                        entries += BatchApkEntry(
                            uri = uri,
                            fileName = displayName,
                            apkInfo = ApkInfo(
                                appName = displayName.substringBeforeLast('.'),
                                packageName = "Unknown",
                                versionName = "",
                                versionCode = 0L,
                                icon = null,
                                minSdkVersion = 0,
                                targetSdkVersion = 0,
                                fileSizeBytes = 0,
                                permissions = emptyList(),
                            ),
                            splitUris = emptyList(),
                            selected = false,
                            parseError = t.message ?: "Parse failed",
                        )
                    }
                    _batchState.value = BatchInstallState.Parsing(
                        processed = index + 1, total = uris.size,
                    )
                }
            }
            // Flag duplicate-package entries: 1st occurrence wins (stays selected), later
            // duplicates get a soft warning and are auto-deselected. This defends against the
            // common mistake of picking two versions/signatures of the same app by accident —
            // installing both serially usually fails or surprises the user (downgrade / signature
            // mismatch). They can still re-check manually if that's really what they want.
            val dupLabel = application.getString(R.string.batch_install_dup_package)
            val mergedLabel = application.getString(R.string.batch_install_merged_splits)
            val useMerge = _mergeSplits.value

            // Group by package name and version code. Versions must match to be merged.
            val processedEntries = if (useMerge) {
                entries.groupBy { "${it.apkInfo.packageName}_${it.apkInfo.versionCode}" }
                    .map { (_, group) ->
                        if (group.size > 1 && group.all { it.parseError == null }) {
                            // Merge splits: prefer the entry that contains a Base APK as the
                            // representative (for correct name/icon).
                            val representative = group.find { g ->
                                g.apkInfo.splitEntries.any { it.type == SplitType.Base }
                            } ?: group.first()
                            
                            val allSplitUris = group.flatMap { it.splitUris }.distinct()
                            representative.copy(
                                splitUris = allSplitUris,
                                conflictLabel = mergedLabel,
                                apkInfo = representative.apkInfo.copy(
                                    splitCount = allSplitUris.size,
                                    fileSizeBytes = group.sumOf { it.apkInfo.fileSizeBytes }
                                )
                            )
                        } else {
                            group.first()
                        }
                    }
            } else {
                val seen = mutableSetOf<String>()
                entries.map { e ->
                    when {
                        e.parseError != null -> e
                        e.apkInfo.packageName.isBlank() || e.apkInfo.packageName == "Unknown" -> e
                        e.apkInfo.packageName in seen -> e.copy(
                            selected = false,
                            conflictLabel = dupLabel,
                        )
                        else -> {
                            seen += e.apkInfo.packageName
                            e
                        }
                    }
                }
            }
            _batchState.value = BatchInstallState.Ready(processedEntries)
        }
    }

    /**
     * Runs [extractApkInfoAndCacheUris] once, captures the resolved split URIs it would
     * have assigned to `pendingApkUris`, then restores the single-install slot. Returns
     * both so the caller can enumerate the package exactly once.
     */
    private suspend fun parseApkInfoForBatch(
        context: Context,
        uri: Uri,
        splitPackage: SplitPackage.Provider,
        fileName: String,
    ): Pair<ApkInfo, List<Uri>> {
        val backup = pendingApkUris
        return try {
            val info = extractApkInfoAndCacheUris(context, uri, splitPackage, fileName)
            info to (pendingApkUris ?: emptyList())
        } finally {
            pendingApkUris = backup
        }
    }

    private fun buildSplitProvider(
        context: Context,
        uri: Uri,
        extension: String,
    ): SplitPackage.Provider {
        return when {
            extension == "apk" ||
                context.contentResolver.getType(uri)?.lowercase() ==
                "application/vnd.android.package-archive" ->
                SingletonApkSequence(uri, context).toSplitPackage()
            extension in listOf("apks", "xapk", "apkm", "zip") ->
                ZippedApkSplits.getApksForUri(uri, context)
                    .validate()
                    .toSplitPackage()
                    .filterCompatible(context)
            else -> SingletonApkSequence(uri, context).toSplitPackage()
        }
    }

    fun toggleBatchSelection(uri: Uri) {
        val ready = _batchState.value as? BatchInstallState.Ready ?: return
        _batchState.value = BatchInstallState.Ready(
            ready.entries.map { e ->
                if (e.uri == uri && e.parseError == null) e.copy(selected = !e.selected) else e
            }
        )
    }

    fun setBatchAllSelected(selected: Boolean) {
        val ready = _batchState.value as? BatchInstallState.Ready ?: return
        _batchState.value = BatchInstallState.Ready(
            ready.entries.map { e ->
                if (e.parseError == null) e.copy(selected = selected) else e
            }
        )
    }

    fun dismissBatchInstall() {
        _batchState.value = BatchInstallState.Idle
    }

    /**
     * Enqueue install sessions for every selected batch entry. Sessions run in parallel via
     * ackpine; non-Shizuku installs serialize naturally on the OS confirmation prompt.
     * After enqueue we clear batch state and rely on the existing SessionCard feed to
     * surface per-app progress.
     */
    fun confirmBatchInstall() {
        val ready = _batchState.value as? BatchInstallState.Ready ?: return
        val picked = ready.entries.filter { it.selected && it.splitUris.isNotEmpty() }
        _batchState.value = BatchInstallState.Idle
        if (picked.isEmpty()) return

        viewModelScope.launch {
            val deleteAfterInstall = readDeleteApkPref()
            val controller = activeController()
            for (entry in picked) {
                val iconPath = cacheIcon(entry.apkInfo)
                val sessionData = SessionData(
                    id = UUID.randomUUID(),
                    name = entry.fileName,
                    appName = entry.apkInfo.appName,
                    iconPath = iconPath,
                )
                controller.install(
                    uris = entry.splitUris,
                    sessionData = sessionData,
                    scope = viewModelScope,
                    context = application,
                    originalUri = entry.uri,
                    deleteAfterInstall = deleteAfterInstall,
                    onSuccess = null,  // batch install skips OBB flow — standalone APKs only
                )
            }
        }
    }

    fun dismissPendingInstall() {
        cancelActiveScan()
        _pendingApkInfo.value = null
        pendingApkUris = null
        pendingOriginalUri = null
        pendingFileName = null
        pendingObbEntries = emptyList()
        _attachedObbFiles.value = emptyList()
    }

    /**
     * Download a package from [url] into cacheDir, then run it through the same parse flow as a
     * locally-picked file. Progress surfaces via [InstallUiState.downloadState]; on success the
     * download card resets and the preview sheet opens.
     */
    private val downloadNotifier by lazy { DownloadNotifier(application) }

    /**
     * Rename the downloaded file to the Content-Disposition–reported name (e.g.
     * `app-arm64-v8a-release.apk`) so users see a meaningful filename when they browse
     * `/sdcard/Download/UniversalInstaller/` in any file manager. Collides safely — if a
     * previous download of the same name is still on disk, the new file gets a ` (N)` suffix.
     */
    private fun renameToDisplayName(file: File, desiredName: String): File {
        if (file.name == desiredName) return file
        val parent = file.parentFile ?: return file
        val targetName = uniqueFileName(parent, desiredName)
        val target = File(parent, targetName)
        return if (file.renameTo(target)) target else file
    }

    private fun uniqueFileName(dir: File, desired: String): String {
        if (!File(dir, desired).exists()) return desired
        val dot = desired.lastIndexOf('.')
        val stem = if (dot > 0) desired.take(dot) else desired
        val ext = if (dot > 0) desired.substring(dot) else ""
        var i = 1
        while (File(dir, "$stem ($i)$ext").exists()) i++
        return "$stem ($i)$ext"
    }

    companion object {
        /** User-facing folder under /sdcard/Download/ so downloads are easy to browse. */
        const val DOWNLOADS_SUBFOLDER = "UniversalInstaller"

        private val ABI_TOKENS = setOf(
            "armeabi_v7a", "arm64_v8a", "x86_64", "armeabi", "x86", "mips64", "mips",
        )
        private val DPI_TOKENS = setOf(
            "ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi",
        )
    }

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
        val displayName = trimmed.substringAfterLast('/').substringBefore('?')
            .ifBlank { "download_${System.currentTimeMillis()}" }
        _downloadState.value = DownloadState.Running(url = trimmed, bytesRead = 0L, totalBytes = -1L)
        downloadNotifier.notifyProgress(displayName, 0L, -1L)
        downloadJob = viewModelScope.launch {
            // Public Downloads subfolder — survives uninstall, visible in the Downloads app,
            // and user can manage (copy, share, delete) through any file manager.
            val downloadsDir = File(
                android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS
                ),
                DOWNLOADS_SUBFOLDER,
            ).apply { mkdirs() }
            val destination = File(downloadsDir, uniqueFileName(downloadsDir, displayName))
            val result = packageDownloadService.download(trimmed, destination) { read, total ->
                _downloadState.value = DownloadState.Running(
                    url = trimmed,
                    bytesRead = read,
                    totalBytes = total,
                )
                downloadNotifier.notifyProgress(displayName, read, total)
            }
            result.fold(
                onSuccess = { downloaded ->
                    val ext = downloaded.fileName.substringAfterLast('.', "").lowercase()
                    val validExtensions = listOf("apk", "apks", "xapk", "apkm", "zip")
                    if (ext !in validExtensions) {
                        downloaded.file.delete()
                        val msg = context.getString(R.string.remote_download_unsupported)
                        _downloadState.value = DownloadState.Error(msg)
                        downloadNotifier.notifyFailed(msg)
                        return@fold
                    }
                    // Rename the on-disk file to match the Content-Disposition name so users
                    // see the same filename in a file manager as in our Download history UI.
                    // Also fixes ackpine's `File.isApk` check (literal `.apk` suffix).
                    val finalFile = renameToDisplayName(downloaded.file, downloaded.fileName)
                    _downloadState.value = DownloadState.Idle
                    downloadNotifier.notifyDone(finalFile.name)
                    runCatching {
                        downloadHistoryDao.insert(
                            app.pwhs.universalinstaller.data.local.DownloadHistoryEntity(
                                url = trimmed,
                                fileName = finalFile.name,
                                filePath = finalFile.absolutePath,
                                sizeBytes = finalFile.length(),
                            )
                        )
                    }.onFailure { Timber.e(it, "Failed to insert download history") }
                    handleDownloadedFile(context, finalFile, finalFile.name, ext)
                },
                onFailure = { e ->
                    if (e is kotlinx.coroutines.CancellationException) {
                        downloadNotifier.cancel()
                        throw e
                    }
                    Timber.e(e, "Download failed")
                    val msg = context.getString(
                        R.string.remote_download_failed,
                        e.message ?: e::class.java.simpleName,
                    )
                    _downloadState.value = DownloadState.Error(msg)
                    downloadNotifier.notifyFailed(msg)
                },
            )
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
                ApkScanner.scan(application)
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

    /**
     * Deletes the given files from disk and refreshes the scan results. Relies on
     * MANAGE_EXTERNAL_STORAGE (already granted for scanning) to delete from arbitrary
     * folders. Files that fail to delete are silently skipped — no point blocking the UI on
     * one stubborn file; the rescan will show which ones remain.
     */
    fun deleteFoundFiles(context: Context, files: List<FoundPackageFile>) {
        if (files.isEmpty()) return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                files.forEach { entry ->
                    runCatching { File(entry.path).delete() }
                }
            }
            startDeviceScan(context)
        }
    }

    /** User picked a file from the scan results — hand it to the normal parse flow. */
    fun pickFromScan(context: Context, found: FoundPackageFile) {
        val file = File(found.path)
        if (!file.exists()) return
        val uri = FileProvider.getUriForFile(
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

    /**
     * User multi-selected N files from the scan results. Converts each to a FileProvider URI
     * and hands the list to [parseBatch] so the normal batch sheet takes over. Files that
     * were deleted between scan and tap are silently skipped.
     */
    fun pickManyFromScan(context: Context, found: List<FoundPackageFile>) {
        val uris = found.mapNotNull { entry ->
            val f = File(entry.path)
            if (!f.exists()) return@mapNotNull null
            runCatching {
                FileProvider.getUriForFile(
                    context,
                    "${BuildConfig.APPLICATION_ID}.fileprovider",
                    f,
                )
            }.getOrNull()
        }
        _scanState.value = ScanState.Idle
        deviceScanJob?.cancel()
        if (uris.size >= 2) parseBatch(context, uris)
        else if (uris.size == 1) {
            // Degenerate case: only one file survived — fall back to single flow.
            found.firstOrNull { File(it.path).exists() }?.let { pickFromScan(context, it) }
        }
    }

    private fun handleDownloadedFile(context: Context, file: File, displayName: String, extension: String) {
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
        // Root wins over Shizuku if both are enabled (the settings UI enforces mutual
        // exclusion, but handle the race cleanly by preferring the stronger backend).
        val prefs = try { application.dataStore.data.first() } catch (_: Exception) { null }
        val useRoot = prefs?.get(PreferencesKeys.USE_ROOT) ?: false
        if (useRoot && rootController != null) {
            // Verify root is still granted — user may have revoked in Magisk/KernelSU
            // between toggling the pref and actually installing. Without this check the
            // install crashes deep in libsu instead of falling back to the stock installer.
            val state = backendFactory.probeRootState()
            if (state == app.pwhs.universalinstaller.presentation.install.controller.RootState.READY) {
                return rootController
            }
            Timber.w("USE_ROOT set but root probe=$state — falling back to next backend")
        }
        val useShizuku = prefs?.get(PreferencesKeys.USE_SHIZUKU) ?: false
        if (useShizuku && isShizukuReadyForInstall()) {
            return shizukuController
        }
        if (useShizuku) {
            Timber.w("USE_SHIZUKU set but Shizuku binder/permission not ready — falling back to default installer")
        }
        return defaultController
    }

    // Mirrors the root probe: verify Shizuku is actually usable before handing the install
    // off to ackpine's shizuku backend. Without this, a stale toggle (Shizuku app stopped,
    // permission revoked, pre-v11 build) crashes the session deep in BaseInstallController
    // with "binder haven't been received".
    private fun isShizukuReadyForInstall(): Boolean = try {
        rikka.shizuku.Shizuku.pingBinder() &&
            !rikka.shizuku.Shizuku.isPreV11() &&
            rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        Timber.w(t, "Shizuku readiness probe failed")
        false
    }

    private suspend fun readDeleteApkPref(): Boolean {
        return try {
            val prefs = application.dataStore.data.first()
            prefs[androidx.datastore.preferences.core.booleanPreferencesKey("delete_apk_after_install")] ?: false
        } catch (_: Exception) { false }
    }

    /**
     * Returns (versionName, versionCode) for the installed package, or null if not installed.
     * Uses longVersionCode on API 28+ and falls back to the deprecated int versionCode below.
     */
    private fun lookupInstalledVersion(context: Context, packageName: String): Pair<String, Long>? {
        if (packageName.isBlank()) return null
        return try {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            val name = pi.versionName.orEmpty()
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION") pi.versionCode.toLong()
            }
            name to code
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun cacheIcon(apkInfo: ApkInfo?): String? {
        val drawable = apkInfo?.icon ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?: createBitmap(192, 192).also { bmp ->
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
            if (apiKey.isBlank()) {
                // Surface the missing-key state instead of silently leaving the card empty —
                // otherwise the Check button flips between label states with no feedback.
                _pendingApkInfo.value = _pendingApkInfo.value?.copy(
                    vtResult = VtResult(status = VtStatus.NO_API_KEY)
                )
                return@launch
            }

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

        val splitEntries = mutableListOf<SplitEntry>()
        try {
            val sequence = splitPackage.get()
            var entries = try {
                sequence.toList()
            } finally {
                (sequence as? CloseableSequence<*>)?.close()
            }
            // Bundle parsers (apks/xapk/apkm/zip) look for `.apk` files INSIDE the zip and
            // come back empty when the file is actually a single APK that's just been
            // served/saved with a non-apk extension — e.g. F-Droid's
            // `net.typeblog.shelter_445.zip` is really one APK. The system installer doesn't
            // care; reparse via the singleton path so we don't surface a misleading "may be
            // corrupt" error.
            if (entries.isEmpty() && extension in setOf("apks", "xapk", "apkm", "zip")) {
                Timber.w(
                    "SplitPackage enumerated 0 entries for $originalUri (fileName=$fileName ext=$extension) — " +
                        "retrying as single APK"
                )
                val fallbackSequence = SingletonApkSequence(originalUri, context).toSplitPackage().get()
                entries = try {
                    fallbackSequence.toList()
                } finally {
                    (fallbackSequence as? CloseableSequence<*>)?.close()
                }
            }
            splitCount = entries.size
            if (entries.isEmpty()) {
                Timber.e(
                    "SplitPackage enumerated 0 entries for $originalUri (fileName=$fileName ext=$extension) — " +
                        "remote downloads: check file isn't truncated; bundles: filterCompatible() may have excluded all"
                )
            }

            for (entry in entries) {
                val apk = entry.apk
                // Pull package metadata from ANY split type — Ackpine parses the manifest of
                // each split, and we need this to group splits for merging even if the system
                // parser (PackageManager) fails.
                if (ackpinePackageName.isEmpty()) {
                    ackpinePackageName = when (apk) {
                        is Apk.Base -> apk.packageName
                        is Apk.Libs -> apk.packageName
                        is Apk.Localization -> apk.packageName
                        is Apk.ScreenDensity -> apk.packageName
                        is Apk.Feature -> apk.packageName
                        is Apk.Other -> apk.packageName
                    }
                }
                if (ackpineVersionName.isEmpty()) {
                    ackpineVersionName = when (apk) {
                        is Apk.Base -> apk.versionName
                        is Apk.Libs -> apk.versionName
                        is Apk.Localization -> apk.versionName
                        is Apk.ScreenDensity -> apk.versionName
                        is Apk.Feature -> apk.versionName
                        is Apk.Other -> apk.versionName
                    }
                }
                if (ackpineVersionCode == 0L) {
                    ackpineVersionCode = when (apk) {
                        is Apk.Base -> apk.versionCode
                        is Apk.Libs -> apk.versionCode
                        is Apk.Localization -> apk.versionCode
                        is Apk.ScreenDensity -> apk.versionCode
                        is Apk.Feature -> apk.versionCode
                        is Apk.Other -> apk.versionCode
                    }
                }
                if (ackpineSize == 0L) {
                    ackpineSize = when (apk) {
                        is Apk.Base -> apk.size
                        is Apk.Libs -> apk.size
                        is Apk.Localization -> apk.size
                        is Apk.ScreenDensity -> apk.size
                        is Apk.Feature -> apk.size
                        is Apk.Other -> apk.size
                    }
                }

                when (apk) {
                    is Apk.Base -> {
                        baseApkUri = apk.uri
                        splitEntries.add(SplitEntry(
                            name = "Base APK",
                            type = SplitType.Base,
                            uri = apk.uri,
                            sizeBytes = apk.size,
                        ))
                    }
                    is Apk.Libs -> {
                        supportedAbis.add(apk.abi.name)
                        splitEntries.add(SplitEntry(
                            name = apk.abi.name,
                            type = SplitType.Libs,
                            uri = apk.uri,
                            sizeBytes = apk.size,
                        ))
                    }
                    is Apk.Localization -> {
                        // We don't filter inline anymore — apply a relative-size filter
                        // AFTER the loop, once we know the largest locale split. See the
                        // post-loop block below for the rationale. Always keep the split
                        // in splitEntries so the user can still toggle it in the picker.
                        splitEntries.add(SplitEntry(
                            name = apk.locale.displayLanguage.ifBlank { apk.locale.toLanguageTag() },
                            type = SplitType.Locale,
                            uri = apk.uri,
                            sizeBytes = apk.size,
                        ))
                    }
                    is Apk.ScreenDensity -> {
                        splitEntries.add(SplitEntry(
                            name = "${apk.dpi}dpi",
                            type = SplitType.ScreenDensity,
                            uri = apk.uri,
                            sizeBytes = apk.size,
                        ))
                    }
                    is Apk.Feature -> {
                        splitEntries.add(SplitEntry(
                            name = apk.name,
                            type = SplitType.Feature,
                            uri = apk.uri,
                            sizeBytes = apk.size,
                        ))
                    }
                    else -> {
                        // Ackpine returns Apk.Other when its split-name parser fails to
                        // extract a known ABI/DPI/locale token — typically because the
                        // splitName contains an extra suffix like `.v2` (signature scheme
                        // marker) that defeats `splitTypePart()`'s "after last `config.`"
                        // strategy, e.g. `config.arm64_v8a.v2`. Reclassify by scanning each
                        // dot-separated segment for ABI/DPI tokens before falling back to
                        // the generic Other bucket.
                        val reclassified = reclassifyOtherSplit(apk.name)
                        if (reclassified != null) {
                            if (reclassified.first == SplitType.Libs) {
                                supportedAbis.add(reclassified.second)
                            }
                            splitEntries.add(SplitEntry(
                                name = reclassified.second,
                                type = reclassified.first,
                                uri = apk.uri,
                                sizeBytes = apk.size,
                            ))
                        } else {
                            splitEntries.add(SplitEntry(
                                name = apk.name.ifBlank { apk.uri.lastPathSegment ?: "unknown" },
                                type = SplitType.Other,
                                uri = apk.uri,
                                sizeBytes = apk.size,
                            ))
                        }
                    }
                }
            }

            // Smart split picker — ackpine's filterCompatible() keeps every split this device
            // CAN run, so a multi-ABI bundle (arm64 + armeabi-v7a + x86) lands here with all
            // three Libs splits even on an arm64-only device. Trim to the best fit per type:
            //   - Libs: top match in Build.SUPPORTED_ABIS (primary ABI)
            //   - ScreenDensity: closest to device's actual densityDpi
            //   - Locale: matches top user locale; always keep base for unspecified strings
            //   - Feature/Other: keep all (developer's choice)
            applySmartPick(splitEntries, context)
            pendingApkUris = splitEntries.filter { it.selected }.map { it.uri }
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

            // PackageManager.getPackageArchiveInfo can throw ExceptionInInitializerError on
            // some Android 14/15 devices due to a missing /vendor/etc/aconfig_flags.pb file.
            // Catch Throwable to prevent crashing the app and fall back to Ackpine's metadata.
            val packageInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(
                        tempFile.absolutePath,
                        PackageManager.PackageInfoFlags.of(PackageManager.GET_PERMISSIONS.toLong())
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(tempFile.absolutePath, PackageManager.GET_PERMISSIONS)
                }
            } catch (t: Throwable) {
                Timber.w(t, "PackageManager failed to parse $fileName — falling back to metadata")
                null
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


            if (supportedAbis.isEmpty() && tempFile.exists()) {
                try {
                    withContext(Dispatchers.IO) {
                        ZipFile(tempFile)
                    }.use { zip ->
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
            splitEntries = splitEntries,
        )
    }

    // Recover ABI/DPI splits that ackpine surfaces as Apk.Other because the splitName has an
    // extra suffix like `.v2` that breaks its `config.<token>` parse. Returns null if the
    // name yields no recognizable token, in which case the caller keeps the Other category.
    private fun reclassifyOtherSplit(splitName: String): Pair<SplitType, String>? {
        if (splitName.isBlank()) return null
        for (token in splitName.lowercase().split('.')) {
            if (token in ABI_TOKENS) return SplitType.Libs to token.uppercase()
            if (token in DPI_TOKENS) return SplitType.ScreenDensity to "${token.uppercase()}dpi"
        }
        return null
    }

    /**
     * In-place toggle each [SplitEntry.selected] flag to the smart-pick default. Base/Feature/
     * Other are always selected. Libs/ScreenDensity/Locale are reduced to the single best
     * match for this device. The user can still override via [toggleSplit].
     */
    private fun applySmartPick(entries: MutableList<SplitEntry>, context: Context) {
        if (entries.size <= 1) {
            // If the file only has one split (common for individual split APKs), keep it
            // by default so it can be merged even if it doesn't match the current device
            // locale/ABI. The user can always deselect it in the preview sheet.
            for (i in entries.indices) {
                if (!entries[i].selected) entries[i] = entries[i].copy(selected = true)
            }
            return
        }

        // Libs: prefer the highest-priority ABI in Build.SUPPORTED_ABIS. Splits are tagged
        // with the canonical ackpine ABI name, which uses underscores (`arm64_v8a`) while
        // Build.SUPPORTED_ABIS uses dashes (`arm64-v8a`). Normalise both sides before match.
        val abiPriority = Build.SUPPORTED_ABIS.orEmpty().mapIndexed { i, abi ->
            abi.replace('-', '_').lowercase() to i
        }.toMap()
        val libsBest = entries
            .filter { it.type == SplitType.Libs }
            .minByOrNull { abiPriority[it.name.lowercase()] ?: Int.MAX_VALUE }
            ?.uri

        // Density: closest dpi to the device's actual densityDpi. Names parse as "${dpi}dpi".
        val deviceDpi = context.resources.displayMetrics.densityDpi
        val densityBest = entries
            .filter { it.type == SplitType.ScreenDensity }
            .minByOrNull {
                val dpi = it.name.removeSuffix("dpi").toIntOrNull() ?: Int.MAX_VALUE
                kotlin.math.abs(dpi - deviceDpi)
            }
            ?.uri

        // Locale: include splits whose displayed language matches any of the user's preferred
        // locales. We match against the top-priority locale's display language (English form
        // matches `Apk.Localization.locale.displayLanguage` from the parser); fallback English
        // also stays selected so apps with English-only resources don't lose all strings.
        val userLangs = run {
            val list = androidx.core.os.LocaleListCompat.getDefault()
            (0 until list.size()).mapNotNull { 
                list[it]?.getDisplayLanguage(java.util.Locale.ENGLISH)?.lowercase()
            }
        }.toSet() + "english"

        for (i in entries.indices) {
            val e = entries[i]
            val keep = when (e.type) {
                SplitType.Base, SplitType.Feature, SplitType.Other -> true
                SplitType.Libs -> e.uri == libsBest
                SplitType.ScreenDensity -> e.uri == densityBest
                SplitType.Locale -> e.name.lowercase() in userLangs
            }
            if (e.selected != keep) entries[i] = e.copy(selected = keep)
        }
    }
}
