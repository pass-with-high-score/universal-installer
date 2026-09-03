package app.pwhs.universalinstaller.presentation.install

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.StorageUtil
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.DownloadHistoryDao
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.data.remote.PackageDownloadService
import app.pwhs.universalinstaller.data.remote.VirusTotalNotifier
import app.pwhs.universalinstaller.data.remote.VirusTotalService
import app.pwhs.universalinstaller.domain.manager.InstallBlacklist
import app.pwhs.universalinstaller.domain.model.InstallerProfile
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.presentation.install.controller.BaseInstallController
import app.pwhs.universalinstaller.presentation.install.controller.DefaultInstallController
import app.pwhs.universalinstaller.presentation.install.controller.DhizukuInstallController
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.ManualInstallController
import app.pwhs.universalinstaller.presentation.install.controller.ShizukuInstallController
import app.pwhs.universalinstaller.presentation.install.dialog.StorageWarningInfo
import app.pwhs.universalinstaller.presentation.install.util.InstallActionDelegate
import app.pwhs.universalinstaller.presentation.install.util.InstallBatchDelegate
import app.pwhs.universalinstaller.presentation.install.util.InstallDialogDelegate
import app.pwhs.universalinstaller.presentation.install.util.InstallExecutionCoordinator
import app.pwhs.universalinstaller.presentation.install.util.InstallObbDelegate
import app.pwhs.universalinstaller.presentation.install.util.InstallParseDelegate
import app.pwhs.universalinstaller.presentation.install.util.InstallScanDelegate
import app.pwhs.universalinstaller.presentation.install.util.InstallSessionManager
import app.pwhs.universalinstaller.presentation.install.util.InstallUiStateBuilder
import app.pwhs.universalinstaller.presentation.install.util.InstallWearDelegate
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import app.pwhs.universalinstaller.util.DhizukuCompat
import app.pwhs.universalinstaller.util.SignatureCheck
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.uninstaller.PackageUninstaller
import java.util.UUID

class InstallViewModel(
    private val application: Application,
    packageInstaller: PackageInstaller,
    private val sessionDataRepository: SessionDataRepository,
    virusTotalService: VirusTotalService,
    virusTotalNotifier: VirusTotalNotifier,
    packageDownloadService: PackageDownloadService,
    private val historyDao: InstallHistoryDao,
    downloadHistoryDao: DownloadHistoryDao,
    private val backendFactory: InstallerBackendFactory,
    private val packageUninstaller: PackageUninstaller,
    private val appScope: CoroutineScope,
) : ViewModel() {

    private val defaultController = DefaultInstallController(
        application, packageInstaller, sessionDataRepository, historyDao,
    )
    private val shizukuController = ShizukuInstallController(
        application, packageInstaller, sessionDataRepository, historyDao,
    )
    private val manualController = ManualInstallController(
        application, packageInstaller, sessionDataRepository, historyDao, backendFactory,
    )
    private val dhizukuController: BaseInstallController? by lazy {
        if (!DhizukuCompat.isSupported) null
        else DhizukuInstallController(application, packageInstaller, sessionDataRepository, historyDao)
    }
    private val rootController: BaseInstallController? = backendFactory.createRootController(
        application, packageInstaller, sessionDataRepository, historyDao,
    )

    private val dialogDelegate = InstallDialogDelegate()
    private val obbDelegate = InstallObbDelegate(application, viewModelScope)
    private val batchDelegate = InstallBatchDelegate(
        application = application,
        scope = viewModelScope,
        resolveController = { activeController(it) },
        onStorageInsufficient = { showStorageWarning(it) },
    )

    private val parseDelegate = InstallParseDelegate(
        application = application,
        scope = viewModelScope,
        virusTotalService = virusTotalService,
        virusTotalNotifier = virusTotalNotifier,
        obbDelegate = obbDelegate,
        onProfileMatched = { _selectedProfileId.value = it },
    )

    private val scanDelegate = InstallScanDelegate(
        application = application,
        scope = viewModelScope,
        packageDownloadService = packageDownloadService,
        downloadHistoryDao = downloadHistoryDao,
        downloadNotifier = DownloadNotifier(application),
        onApkParsed = { ctx, uri, splitProvider, name, isAa ->
            parseApkInfo(ctx, uri, splitProvider, name, isAa)
        },
        onBatchSelected = { ctx, uris ->
            parseBatch(ctx, uris)
        },
    )

    private val wearDelegate = InstallWearDelegate(application, viewModelScope)

    val dialogTarget: StateFlow<DialogTarget?> = dialogDelegate.dialogTarget

    private val _mergeSplits = MutableStateFlow(false)
    private val _selectedProfileId = MutableStateFlow<String?>(null)

    private val blacklist: StateFlow<Set<String>> = application.dataStore.data
        .map { InstallBlacklist.read(it) }
        .catch { emit(emptySet()) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val history = historyDao.getAll().stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val uiState = combine(
        listOf(
            sessionDataRepository.sessions,
            sessionDataRepository.sessionsProgress,
            parseDelegate.isLoading,
            parseDelegate.pendingApkInfo,
            scanDelegate.downloadState,
            scanDelegate.scanState,
            obbDelegate.obbCopyState,
            obbDelegate.attachedObbFiles,
            batchDelegate.batchState,
            dialogDelegate.dialogStage,
            _mergeSplits,
            application.dataStore.data.map { it[PreferencesKeys.INSTALLER_PROFILES] },
            application.dataStore.data.map { it[PreferencesKeys.APP_PROFILE_MAPPING] },
            app.pwhs.universalinstaller.presentation.sync.SyncManager.state,
            _selectedProfileId,
            application.dataStore.data.map { it[PreferencesKeys.SHIZUKU_ALL_USERS] ?: false },
            application.dataStore.data.map { it[PreferencesKeys.INSTALL_USER_ID] },
            parseDelegate.isApk,
            batchDelegate.batchDetailUri,
            dialogDelegate.downloadProgress,
            wearDelegate.watchSendState,
        )
    ) { flows ->
        InstallUiStateBuilder.build(flows)
    }
        .onStart { activeController().restoreSessionsFromSavedState(viewModelScope) }
        .stateIn(viewModelScope, SharingStarted.Lazily, InstallUiState())

    private suspend fun activeController(profileId: String? = null): BaseInstallController =
        InstallSessionManager.activeController(
            context = application,
            profileId = profileId,
            defaultController = defaultController,
            shizukuController = shizukuController,
            rootController = rootController,
            dhizukuController = dhizukuController,
            backendFactory = backendFactory,
        )

    fun clearHistory() {
        viewModelScope.launch {
            historyDao.clearAll()
        }
    }

    fun deleteHistoryEntry(id: Long) {
        viewModelScope.launch {
            historyDao.deleteById(id)
        }
    }

    fun setAllUsers(enabled: Boolean) {
        viewModelScope.launch {
            InstallActionDelegate.setAllUsers(application, enabled)
        }
    }

    fun setUserId(id: Int?) {
        viewModelScope.launch {
            InstallActionDelegate.setUserId(application, id)
        }
    }

    // ── Dialog Delegates ────────────────────────────────────────────────────

    fun dialogStartLoading() = dialogDelegate.startLoading()
    fun dialogShowPrepare() = dialogDelegate.showPrepare()
    fun dialogShowMenu() = dialogDelegate.showMenu()
    fun dialogBackToPrepare() = dialogDelegate.backToPrepare()
    fun dialogStartInstalling() = dialogDelegate.startInstalling()
    fun dialogInstallSuccess() = dialogDelegate.installSuccess()
    fun dialogInstallFailed(error: String) = dialogDelegate.installFailed(error)
    fun dialogReadFailed(reason: String) = dialogDelegate.readFailed(reason)
    fun dialogParseFailed(reason: String) = dialogDelegate.parseFailed(reason)
    fun dialogPermissionRequired() = dialogDelegate.permissionRequired()
    private val _storageWarningInfo = MutableStateFlow<StorageWarningInfo?>(null)
    val storageWarningInfo: StateFlow<StorageWarningInfo?> = _storageWarningInfo.asStateFlow()

    fun showStorageWarning(requiredBytes: Long = 0L) {
        _storageWarningInfo.value = StorageWarningInfo.create(requiredBytes)
    }

    fun dismissStorageWarning() {
        _storageWarningInfo.value = null
    }

    fun updateDialogDownloadProgress(progress: app.pwhs.core.network.DownloadProgress?) =
        dialogDelegate.updateDownloadProgress(progress)
    fun dialogClose() = dialogDelegate.close()

    fun cancelDialogDownload() = dialogDelegate.cancelDownload(application)

    fun startDialogNetworkDownload(
        context: Context,
        uri: Uri,
        onFileDownloaded: (java.io.File, String) -> Unit,
    ) = dialogDelegate.startNetworkDownload(context, uri, onFileDownloaded)

    fun setMergeSplits(merge: Boolean) {
        _mergeSplits.value = merge
        val state = batchDelegate.batchState.value
        if (state is BatchInstallState.Ready) {
            parseBatch(application, state.entries.map { it.uri })
        }
    }

    fun applyProfile(profile: InstallerProfile) {
        Telemetry.feature(TelemetryEvents.FEATURE_INSTALLER_PROFILE)
        _selectedProfileId.value = profile.id
    }

    fun selectProfile(profileId: String?) {
        _selectedProfileId.value = profileId
    }

    fun clearDialogTarget() {
        dialogDelegate.clearTarget()
        _selectedProfileId.value = null
    }

    fun getAppLaunchIntent(packageName: String) = application.packageManager.getLaunchIntentForPackage(packageName)

    fun setAppProfileMapping(packageName: String, profileId: String?) {
        viewModelScope.launch {
            InstallActionDelegate.setAppProfileMapping(application, packageName, profileId)
        }
    }

    // ── Single Package Parsing ──────────────────────────────────────────────

    fun parseApkInfo(
        context: Context,
        uri: Uri,
        splitPackage: SplitPackage.Provider,
        fileName: String,
        isAndroidAuto: Boolean? = null,
    ) {
        parseDelegate.parseApkInfo(
            context = context,
            uri = uri,
            splitPackage = splitPackage,
            fileName = fileName,
            isAndroidAuto = isAndroidAuto,
            blacklist = blacklist.value,
            currentProfiles = uiState.value.installerProfiles,
            appProfileMapping = uiState.value.appProfileMapping,
        )
    }

    fun toggleSplit(index: Int) = parseDelegate.toggleSplit(index)
    fun dismissPendingInstall() = parseDelegate.dismissPendingInstall()
    suspend fun stashPendingInstall() = parseDelegate.stashPendingInstall()
    fun restorePendingInstall(entry: PendingInstallStore.Entry) = parseDelegate.restorePendingInstall(entry)
    fun scanVirusTotal(context: Context) = parseDelegate.scanVirusTotal(context)

    fun confirmInstall(trackDialogTarget: Boolean = false) {
        scanDelegate.resetScanState()
        val apkInfo = parseDelegate.pendingApkInfo.value
        val uris = if (apkInfo != null && apkInfo.splitEntries.isNotEmpty()) {
            apkInfo.splitEntries.filter { it.selected }.map { it.uri }
        } else {
            parseDelegate.pendingApkUris
        }
        if (uris.isNullOrEmpty()) {
            android.widget.Toast.makeText(
                application,
                application.getString(R.string.install_no_splits_error),
                android.widget.Toast.LENGTH_LONG,
            ).show()
            return
        }
        val blockedPackage = apkInfo?.packageName.orEmpty()
        if (blockedPackage.isNotBlank() && blockedPackage in blacklist.value) {
            android.widget.Toast.makeText(
                application,
                application.getString(R.string.install_blocked_by_blacklist, blockedPackage),
                android.widget.Toast.LENGTH_LONG,
            ).show()
            dismissPendingInstall()
            return
        }
        val apkSize = apkInfo?.fileSizeBytes ?: 0L
        if (!StorageUtil.hasSufficientStorage(apkSize)) {
            showStorageWarning(apkSize)
            return
        }
        val fn = parseDelegate.pendingFileName ?: return
        val originalUri = parseDelegate.pendingOriginalUri
        val obbEntries = obbDelegate.pendingObbEntries
        val attachedObbs = obbDelegate.attachedObbFiles.value
        dismissPendingInstall()

        viewModelScope.launch {
            InstallExecutionCoordinator.executeSingleInstall(
                application = application,
                scope = viewModelScope,
                appScope = appScope,
                trackDialogTarget = trackDialogTarget,
                apkInfo = apkInfo,
                fileName = fn,
                originalUri = originalUri,
                uris = uris,
                obbEntries = obbEntries,
                attachedObbs = attachedObbs,
                currentProfileId = _selectedProfileId.value,
                rootController = rootController,
                backendFactory = backendFactory,
                manualController = manualController,
                resolveActiveController = { activeController(it) },
                onDialogTargetCreated = { dialogDelegate.setTarget(it) },
                onCopyObbs = { src, obbs, attached, pkg, name ->
                    obbDelegate.copyObbFiles(src, obbs, attached, pkg, name)
                },
            )
        }
    }

    // ── OBB Delegates ───────────────────────────────────────────────────────

    fun onObbTreeGranted(uri: Uri?) = obbDelegate.onObbTreeGranted(uri)
    fun obbTreeHintUri(): Uri? = obbDelegate.obbTreeHintUri()
    fun dismissObbCopy() = obbDelegate.dismissObbCopy()
    fun attachObbFile(context: Context, uri: Uri) = obbDelegate.attachObbFile(context, uri)
    fun removeAttachedObb(uri: Uri) = obbDelegate.removeAttachedObb(uri)

    // ── Batch Delegates ─────────────────────────────────────────────────────

    fun parseBatch(context: Context, uris: List<Uri>) {
        batchDelegate.parseBatch(context, uris, _mergeSplits.value)
    }

    fun toggleBatchSelection(uri: Uri) = batchDelegate.toggleBatchSelection(uri)
    fun setBatchAllSelected(selected: Boolean) = batchDelegate.setBatchAllSelected(selected)
    fun dismissBatchInstall() = batchDelegate.dismissBatchInstall()
    fun openBatchDetail(uri: Uri) = batchDelegate.openBatchDetail(uri)
    fun closeBatchDetail() = batchDelegate.closeBatchDetail()
    fun saveBatchDetail(uri: Uri, newSplitUris: List<Uri>) = batchDelegate.saveBatchDetail(uri, newSplitUris)
    fun confirmBatchInstall() {
        scanDelegate.resetScanState()
        batchDelegate.confirmBatchInstall(_selectedProfileId.value)
    }
    fun skipBatchParseAndInstall() {
        scanDelegate.resetScanState()
        batchDelegate.skipBatchParseAndInstall()
    }

    fun skipParseAndInstallSingle() {
        scanDelegate.resetScanState()
        parseDelegate.stopParsing()
        val uri = parseDelegate.pendingOriginalUri ?: return
        val fileName = parseDelegate.pendingFileName ?: uri.lastPathSegment ?: "Unknown"
        val newTarget = DialogTarget(UUID.randomUUID(), "", fileName, null)
        dialogDelegate.setTarget(newTarget)
        dialogDelegate.startInstalling()
        viewModelScope.launch {
            InstallExecutionCoordinator.executeSkipSingle(
                application = application,
                scope = viewModelScope,
                uri = uri,
                fileName = fileName,
                sessionId = newTarget.sessionId,
                resolveActiveController = { activeController() },
                onSuccess = {
                    dialogDelegate.installSuccess()
                    dialogDelegate.clearTarget()
                },
            )
        }
    }

    // ── Download & Device Scan Delegates ────────────────────────────────────

    fun downloadFromUrl(context: Context, url: String) = scanDelegate.downloadFromUrl(context, url)
    fun cancelDownload() = scanDelegate.cancelDownload()
    fun dismissDownloadError() = scanDelegate.dismissDownloadError()
    fun startDeviceScan(context: Context) = scanDelegate.startDeviceScan(context)
    fun dismissDeviceScan() = scanDelegate.dismissDeviceScan()
    fun deleteFoundFiles(context: Context, files: List<FoundPackageFile>) = scanDelegate.deleteFoundFiles(context, files)
    fun pickFromScan(context: Context, found: FoundPackageFile) = scanDelegate.pickFromScan(context, found)
    fun pickManyFromScan(context: Context, found: List<FoundPackageFile>) = scanDelegate.pickManyFromScan(context, found)

    // ── Session Controls ────────────────────────────────────────────────────

    fun cancelSession(id: UUID) {
        viewModelScope.launch {
            activeController().cancel(id, viewModelScope)
        }
    }

    fun dismissSession(id: UUID) {
        viewModelScope.launch {
            activeController().dismiss(id)
        }
    }

    fun retrySession(id: UUID) {
        viewModelScope.launch {
            activeController().retry(id, viewModelScope, application)
        }
    }

    fun retryDialogInstall() {
        val target = dialogDelegate.dialogTarget.value ?: return
        dialogDelegate.startInstalling()
        viewModelScope.launch {
            activeController().retry(target.sessionId, appScope, application) { newId ->
                dialogDelegate.setTarget(target.copy(sessionId = newId))
            }
        }
    }

    fun unblockPackage(packageName: String) {
        if (packageName.isBlank()) return
        viewModelScope.launch {
            InstallActionDelegate.unblockPackage(application, packageName)
            parseDelegate.updatePendingApkInfo { it.copy(isBlocked = false) }
        }
    }

    suspend fun uninstallConflictingApp(packageName: String): Boolean {
        val removed = InstallSessionManager.uninstallConflictingApp(
            context = application,
            packageName = packageName,
            profileId = _selectedProfileId.value,
            defaultController = defaultController,
            shizukuController = shizukuController,
            rootController = rootController,
            dhizukuController = dhizukuController,
            backendFactory = backendFactory,
            packageUninstaller = packageUninstaller,
        )
        if (removed) {
            onConflictingAppUninstalled()
        }
        return removed
    }

    fun onConflictingAppUninstalled() {
        val info = parseDelegate.pendingApkInfo.value ?: return
        if (SignatureCheck.isInstalled(application, info.packageName)) return
        parseDelegate.updatePendingApkInfo {
            it.copy(
                installedVersionName = null,
                installedVersionCode = null,
                signatureMismatch = false,
            )
        }
    }

    // ── Watch (Wear OS) ──────────────────────────────────────────────────────

    val watchAvailable: StateFlow<Boolean> = wearDelegate.watchAvailable

    fun refreshWatchAvailability() = wearDelegate.refreshWatchAvailability()

    fun sendToWatch(apkUri: Uri? = null, fileName: String? = null) {
        if (apkUri != null) {
            wearDelegate.sendToWatch(apkUri, fileName ?: fallbackFileName())
            return
        }

        val originalUri = parseDelegate.pendingOriginalUri
        val splitUris = parseDelegate.pendingApkUris.orEmpty()
        val name = fileName ?: parseDelegate.pendingFileName ?: fallbackFileName()

        // Loose splits have no single file to hand over; only a bundle archive or one APK works.
        if (originalUri == null && splitUris.size > 1) {
            wearDelegate.reportUnsupported(
                application.getString(R.string.watch_send_unsupported_splits)
            )
            return
        }

        val uri = originalUri ?: splitUris.firstOrNull() ?: return
        wearDelegate.sendToWatch(uri, name)
    }

    private fun fallbackFileName(): String =
        "${parseDelegate.pendingApkInfo.value?.appName ?: "app"}.apk"

    fun dismissWatchSend() = wearDelegate.dismissWatchSend()
}
