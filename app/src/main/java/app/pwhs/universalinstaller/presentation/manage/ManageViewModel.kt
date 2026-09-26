@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.pwhs.universalinstaller.presentation.manage

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.UninstallLogDao
import app.pwhs.universalinstaller.domain.manager.InstallBlacklist
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.domain.provider.PrivilegedExecutor
import app.pwhs.universalinstaller.domain.provider.PrivilegedProvider
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.ShizukuShellExecutor
import app.pwhs.universalinstaller.presentation.install.controller.SystemAppMethod
import app.pwhs.universalinstaller.presentation.manage.util.InstalledAppsLoader
import app.pwhs.universalinstaller.presentation.manage.util.ManageExtractHelper
import app.pwhs.universalinstaller.presentation.manage.util.ManageFilterHelper
import app.pwhs.universalinstaller.presentation.manage.util.ManagePrivilegedActionHelper
import app.pwhs.universalinstaller.presentation.manage.util.ManageUninstallHelper
import app.pwhs.universalinstaller.presentation.manage.util.ManageUsageStatsHelper
import app.pwhs.core.telemetry.AnalyticsHelper
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.uninstaller.PackageUninstaller
import java.io.File

class ManageViewModel(
    private val application: Application,
    private val packageUninstaller: PackageUninstaller,
    private val uninstallLogDao: UninstallLogDao,
    private val backendFactory: InstallerBackendFactory,
    private val privilegedProvider: PrivilegedProvider,
) : ViewModel() {

    private val notifier = UninstallNotifier(application)

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)
    private val _isRefreshing = MutableStateFlow(false)
    private val _appFilter = MutableStateFlow(setOf(AppFilter.User))
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    private val _sortBy = MutableStateFlow(UninstallSortBy.Name)
    private val _sortDirection = MutableStateFlow(SortDirection.Asc)
    private val _groupBy = MutableStateFlow(GroupBy.None)
    private val _usageAccess = MutableStateFlow(false)
    private val _systemAppPrompt = MutableStateFlow<SystemAppPrompt?>(null)
    private val _extractState = MutableStateFlow<ExtractState>(ExtractState.Idle)
    private val _batchExtractState = MutableStateFlow<BatchExtractState>(BatchExtractState.Idle)
    private val _privilegedReady = MutableStateFlow(false)
    private val _privilegedActionResult = MutableStateFlow<PrivilegedActionResult?>(null)

    private var extractJob: Job? = null

    val blacklist: StateFlow<Set<String>> = application.dataStore.data
        .map { InstallBlacklist.read(it) }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptySet())

    val uiState: StateFlow<ManageUiState> = combine(
        listOf(
            _apps, _searchQuery, _isLoading, _appFilter, _selectedPackages,
            _sortBy, _sortDirection, _usageAccess, _systemAppPrompt, _extractState,
            _privilegedReady, _privilegedActionResult, _groupBy, _batchExtractState, _isRefreshing,
            blacklist
        )
    ) { flows ->
        ManageFilterHelper.buildManageUiState(flows)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ManageUiState())

    init {
        refreshUsageAccess()
        viewModelScope.launch {
            val savedPrefs = ManageFilterHelper.loadFilterPreferences(application)
            _sortBy.value = savedPrefs.sortBy
            _sortDirection.value = savedPrefs.sortDirection
            _groupBy.value = savedPrefs.groupBy
            _appFilter.value = savedPrefs.appFilter
        }
        loadInstalledApps()
        refreshPrivilegedReady()
    }

    // ── Extraction Actions ──────────────────────────────────────────────────

    fun extractApp(packageName: String, appName: String) =
        runExtraction(packageName, appName, ExtractMode.Backup, outputDir = null)

    fun shareApp(packageName: String, appName: String) {
        val shareDir = File(application.cacheDir, "share").apply { mkdirs() }
        shareDir.listFiles()?.forEach { runCatching { it.delete() } }
        runExtraction(packageName, appName, ExtractMode.Share, outputDir = shareDir)
    }

    fun reinstallApp(packageName: String, appName: String) {
        val reinstallDir = File(application.cacheDir, "reinstall").apply { mkdirs() }
        reinstallDir.listFiles()?.forEach { runCatching { it.delete() } }
        runExtraction(packageName, appName, ExtractMode.Reinstall, outputDir = reinstallDir)
    }

    fun scanVirusTotal(context: android.content.Context, app: InstalledApp) {
        AnalyticsHelper.logAppManagementAction("scan_virustotal")
        viewModelScope.launch { ManageExtractHelper.scanVirusTotal(context, app) }
    }

    fun addToServer(packageName: String, appName: String) {
        val serverDir = File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "UniversalInstaller"
        ).apply { mkdirs() }
        runExtraction(packageName, appName, ExtractMode.Server, outputDir = serverDir)
    }

    private fun runExtraction(
        packageName: String,
        appName: String,
        mode: ExtractMode,
        outputDir: File?,
    ) {
        if (_extractState.value is ExtractState.Running) return
        if (mode == ExtractMode.Backup) Telemetry.feature(TelemetryEvents.FEATURE_APK_BACKUP)
        val actionType = when (mode) {
            ExtractMode.Backup -> app.pwhs.core.telemetry.TelemetryEvents.ACTION_BACKUP_APK
            ExtractMode.Share -> app.pwhs.core.telemetry.TelemetryEvents.ACTION_SHARE_APK
            ExtractMode.Reinstall -> app.pwhs.core.telemetry.TelemetryEvents.ACTION_EXTRACT_SPLITS
            else -> null
        }
        actionType?.let { AnalyticsHelper.logAppManagementAction(it) }
        extractJob?.cancel()
        _extractState.value = ExtractState.Running(packageName, appName, 0L, 1L, mode)
        extractJob = viewModelScope.launch {
            _extractState.value = ManageExtractHelper.extractApp(
                context = application,
                packageName = packageName,
                appName = appName,
                mode = mode,
                outputDir = outputDir,
            ) { bytes, total ->
                _extractState.value = ExtractState.Running(packageName, appName, bytes, total, mode)
            }
        }
    }

    fun dismissExtractResult() {
        _extractState.value = ExtractState.Idle
    }

    fun extractSelected() {
        val packages = _selectedPackages.value.toList()
        if (packages.isEmpty()) return
        if (_extractState.value is ExtractState.Running) return
        if (_batchExtractState.value is BatchExtractState.Running) return
        _selectedPackages.value = emptySet()

        extractJob = viewModelScope.launch {
            _batchExtractState.value = ManageExtractHelper.extractBatch(
                context = application,
                packages = packages,
                apps = _apps.value,
            ) { progress ->
                _batchExtractState.value = progress
            }
        }
    }

    fun dismissBatchExtractResult() {
        _batchExtractState.value = BatchExtractState.Idle
    }

    // ── Privileged Actions ──────────────────────────────────────────────────

    fun refreshPrivilegedReady() {
        viewModelScope.launch {
            _privilegedReady.value = privilegedProvider.resolveExecutor() != null
        }
    }

    fun openAppPrivileged(packageName: String, appName: String) {
        viewModelScope.launch {
            _privilegedActionResult.value = ManagePrivilegedActionHelper.openAppPrivileged(
                application, packageName, appName, privilegedProvider, backendFactory
            )
        }
    }

    fun forceStop(packageName: String, appName: String) {
        viewModelScope.launch {
            _privilegedActionResult.value = ManagePrivilegedActionHelper.forceStop(
                application, packageName, appName, privilegedProvider, backendFactory
            )
        }
    }

    fun setEnabled(packageName: String, appName: String, enabled: Boolean) {
        viewModelScope.launch {
            val result = ManagePrivilegedActionHelper.setEnabled(
                application, packageName, appName, enabled, privilegedProvider, backendFactory
            )
            _privilegedActionResult.value = result
            if (result is PrivilegedActionResult.Success) {
                loadInstalledApps()
            }
        }
    }

    fun dismissPrivilegedActionResult() {
        _privilegedActionResult.value = null
    }

    fun disableSelected() = runPrivilegedBatch(
        actionLabelRes = R.string.manage_batch_action_disable,
        reloadAfter = true,
    ) { executor, pkg ->
        when (executor) {
            PrivilegedExecutor.Root -> backendFactory.setEnabledViaRoot(pkg, false)
            PrivilegedExecutor.Shizuku -> ShizukuShellExecutor.setEnabled(pkg, false)
        }
    }

    fun forceStopSelected() = runPrivilegedBatch(
        actionLabelRes = R.string.manage_batch_action_force_stop,
        reloadAfter = false,
    ) { executor, pkg ->
        when (executor) {
            PrivilegedExecutor.Root -> backendFactory.forceStopViaRoot(pkg)
            PrivilegedExecutor.Shizuku -> ShizukuShellExecutor.forceStop(pkg)
        }
    }

    fun clearDataSelected() = runPrivilegedBatch(
        actionLabelRes = R.string.manage_batch_action_clear_data,
        reloadAfter = false,
    ) { executor, pkg ->
        when (executor) {
            PrivilegedExecutor.Root -> backendFactory.clearAppDataViaRoot(pkg)
            PrivilegedExecutor.Shizuku -> ShizukuShellExecutor.clearAppData(pkg)
        }
    }

    private fun runPrivilegedBatch(
        actionLabelRes: Int,
        reloadAfter: Boolean,
        op: suspend (PrivilegedExecutor, String) -> Result<*>,
    ) {
        val packages = _selectedPackages.value.toList()
        if (packages.isEmpty()) return
        _selectedPackages.value = emptySet()
        viewModelScope.launch {
            _privilegedActionResult.value = ManagePrivilegedActionHelper.runPrivilegedBatch(
                application, packages, actionLabelRes, privilegedProvider, op
            )
            if (reloadAfter) loadInstalledApps()
        }
    }

    suspend fun queryUsageBuckets(packageName: String): List<UsageBucket> =
        ManageUsageStatsHelper.queryUsageBuckets(application, packageName)

    suspend fun queryStorageStats(packageName: String): StorageBreakdown? =
        ManageUsageStatsHelper.queryStorageStats(application, packageName)

    fun clearAllData(packageName: String, appName: String) {
        viewModelScope.launch {
            _privilegedActionResult.value = ManagePrivilegedActionHelper.clearAllData(
                application, packageName, appName, privilegedProvider, backendFactory
            )
        }
    }

    // ── Filter & Search ─────────────────────────────────────────────────────

    fun setSort(sortBy: UninstallSortBy) {
        val newDirection = if (_sortBy.value == sortBy) {
            if (_sortDirection.value == SortDirection.Asc) SortDirection.Desc else SortDirection.Asc
        } else {
            if (sortBy == UninstallSortBy.Name) SortDirection.Asc else SortDirection.Desc
        }
        _sortBy.value = sortBy
        _sortDirection.value = newDirection
        AnalyticsHelper.logManageSortChanged(sortBy.name.lowercase(), newDirection.name.lowercase())
        persistFilterSheetState()
    }

    fun refreshUsageAccess() {
        _usageAccess.value = ManageUsageStatsHelper.hasUsageAccess(application)
    }

    private fun persistFilterSheetState() {
        viewModelScope.launch {
            ManageFilterHelper.saveFilterPreferences(
                application, _sortBy.value, _sortDirection.value, _groupBy.value, _appFilter.value
            )
        }
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleAppFilter(filter: AppFilter) {
        val current = _appFilter.value
        val isAdding = filter !in current
        val next = if (isAdding) current + filter else current - filter
        if (next.isEmpty()) return
        _appFilter.value = next
        AnalyticsHelper.logManageFilterChanged(filter.name.lowercase(), isAdding)
        persistFilterSheetState()
    }

    fun setGroupBy(groupBy: GroupBy) {
        _groupBy.value = groupBy
        AnalyticsHelper.logManageGroupChanged(groupBy.name.lowercase())
        persistFilterSheetState()
    }

    fun resetFilters() {
        _sortBy.value = UninstallSortBy.Name
        _sortDirection.value = SortDirection.Asc
        _groupBy.value = GroupBy.None
        _appFilter.value = setOf(AppFilter.User)
        AnalyticsHelper.logManageFiltersReset()
        persistFilterSheetState()
    }

    // ── Selection & Blacklist ───────────────────────────────────────────────

    fun toggleSelection(packageName: String) {
        val current = _selectedPackages.value
        _selectedPackages.value = if (packageName in current) current - packageName else current + packageName
    }

    fun clearSelection() {
        _selectedPackages.value = emptySet()
    }

    fun toggleSelectAll() {
        val allPackages = uiState.value.filteredApps.map { it.packageName }.toSet()
        _selectedPackages.value = if (_selectedPackages.value == allPackages) emptySet() else allPackages
    }

    fun toggleBlockPackage(packageName: String) {
        if (packageName.isBlank()) return
        val wasBlocked = packageName in blacklist.value
        viewModelScope.launch {
            application.dataStore.edit { p ->
                val current = InstallBlacklist.read(p)
                p[InstallBlacklist.KEY] = if (wasBlocked) {
                    InstallBlacklist.remove(current, packageName)
                } else {
                    InstallBlacklist.add(current, packageName)
                }
            }
            withContext(Dispatchers.Main) {
                android.widget.Toast.makeText(
                    application,
                    application.getString(
                        if (wasBlocked) R.string.manage_unblocked_toast
                        else R.string.manage_blocked_toast,
                        packageName,
                    ),
                    android.widget.Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    // ── Uninstall Operations ────────────────────────────────────────────────

    fun uninstallSelected() {
        val packages = _selectedPackages.value.toList()
        if (packages.isEmpty()) return
        if (packages.size == 1) {
            _selectedPackages.value = emptySet()
            uninstallApp(packages.first())
            return
        }

        val (systemApps, userApps) = ManageUninstallHelper.partitionByKind(packages, _apps.value)
        if (systemApps.isEmpty()) {
            _selectedPackages.value = emptySet()
            viewModelScope.launch { runBatchUninstall(userApps.map { it.first }) }
            return
        }

        viewModelScope.launch {
            _systemAppPrompt.value = if (privilegedProvider.resolveExecutor() == null) {
                SystemAppPrompt.PrivilegedRequired(
                    systemApps = systemApps,
                    userAppsAvailable = userApps.map { it.first },
                )
            } else {
                SystemAppPrompt.Batch(systemApps = systemApps, userApps = userApps)
            }
        }
    }

    fun uninstallApp(packageName: String) {
        Telemetry.feature(TelemetryEvents.FEATURE_UNINSTALL)
        AnalyticsHelper.logAppManagementAction(app.pwhs.core.telemetry.TelemetryEvents.ACTION_UNINSTALL_APP)
        val app = _apps.value.firstOrNull { it.packageName == packageName }
        if (app != null && app.isSystemApp) {
            viewModelScope.launch {
                _systemAppPrompt.value = if (privilegedProvider.resolveExecutor() == null) {
                    SystemAppPrompt.PrivilegedRequired(
                        systemApps = listOf(packageName to app.appName),
                        userAppsAvailable = emptyList(),
                    )
                } else {
                    SystemAppPrompt.Single(pkg = packageName, appName = app.appName)
                }
            }
            return
        }

        viewModelScope.launch {
            val appName = app?.appName ?: packageName
            ManageUninstallHelper.uninstallSingle(
                context = application,
                packageName = packageName,
                appName = appName,
                packageUninstaller = packageUninstaller,
                uninstallLogDao = uninstallLogDao,
                notifier = notifier,
                onSuccess = { _apps.value = _apps.value.filter { it.packageName != packageName } }
            )
        }
    }

    fun confirmSystemAppPrompt(systemMethod: SystemAppMethod?) {
        val prompt = _systemAppPrompt.value ?: return
        _systemAppPrompt.value = null
        _selectedPackages.value = emptySet()

        viewModelScope.launch {
            ManageUninstallHelper.handleSystemAppPromptConfirmation(
                context = application,
                prompt = prompt,
                systemMethod = systemMethod,
                privilegedProvider = privilegedProvider,
                packageUninstaller = packageUninstaller,
                uninstallLogDao = uninstallLogDao,
                backendFactory = backendFactory,
                notifier = notifier,
                apps = _apps.value,
                onAppRemoved = { removedPkg ->
                    _apps.value = _apps.value.filter { it.packageName != removedPkg }
                }
            )
        }
    }

    fun dismissSystemAppPrompt() { _systemAppPrompt.value = null }
    fun refreshApps() = loadInstalledApps(isRefresh = true)

    private suspend fun runBatchUninstall(packages: List<String>) {
        ManageUninstallHelper.runBatchUninstall(
            context = application,
            packages = packages,
            apps = _apps.value,
            packageUninstaller = packageUninstaller,
            uninstallLogDao = uninstallLogDao,
            notifier = notifier,
            onAppRemoved = { removedPkg ->
                _apps.value = _apps.value.filter { it.packageName != removedPkg }
            }
        )
    }

    private fun loadInstalledApps(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _isRefreshing.value = true
            } else {
                _isLoading.value = true
            }
            val apps = InstalledAppsLoader.loadInstalledApps(application)
            _apps.value = apps
            _isLoading.value = false
            _isRefreshing.value = false
        }
    }
}
