package app.pwhs.tv.presentation.manage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.AppRepository
import app.pwhs.core.data.PrivilegedAppOps
import app.pwhs.core.domain.InstalledApp
import app.pwhs.core.util.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import app.pwhs.core.install.ApkExtractor
import app.pwhs.core.telemetry.AnalyticsHelper
import app.pwhs.core.telemetry.TelemetryEvents

enum class AppFilter { User, Sideloaded, System, Disabled }
enum class SortBy { Name, Size, Date }

sealed interface ExtractState {
    data object Idle : ExtractState
    data class Running(
        val packageName: String,
        val appName: String,
        val bytesCopied: Long,
        val totalBytes: Long,
    ) : ExtractState
    data class Done(
        val appName: String,
        val uri: android.net.Uri,
    ) : ExtractState
    data class Error(
        val appName: String,
        val message: String,
    ) : ExtractState
}

/** One-shot feedback for a privileged Manage action, surfaced as a dismissable pill. */
data class ActionResult(val message: String, val isError: Boolean)

data class ManageUiState(
    val apps: List<InstalledApp> = emptyList(),
    val filteredApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val filter: AppFilter = AppFilter.User,
    val sortBy: SortBy = SortBy.Name,
    val searchQuery: String = "",
    val extractState: ExtractState = ExtractState.Idle,
    val rootAvailable: Boolean = false,
    val selectionMode: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
)

class ManageViewModel(application: Application) : AndroidViewModel(application) {

    private val appContext = application.applicationContext
    private val repo = AppRepository(appContext)

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _isLoading = MutableStateFlow(true)
    private val _filter = MutableStateFlow(AppFilter.User)
    private val _sortBy = MutableStateFlow(SortBy.Name)
    private val _searchQuery = MutableStateFlow("")
    private val _extractState = MutableStateFlow<ExtractState>(ExtractState.Idle)
    private val _rootAvailable = MutableStateFlow(false)
    private val _selectionMode = MutableStateFlow(false)
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())

    private val _actionResult = MutableStateFlow<ActionResult?>(null)
    val actionResult: StateFlow<ActionResult?> = _actionResult.asStateFlow()

    private var actionInFlight = false
    private var extractJob: kotlinx.coroutines.Job? = null

    val uiState: StateFlow<ManageUiState> = combine(
        listOf(
            _apps, _isLoading, _filter, _sortBy, _searchQuery, _extractState,
            _rootAvailable, _selectionMode, _selectedPackages,
        )
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val apps = flows[0] as List<InstalledApp>
        val loading = flows[1] as Boolean
        val filter = flows[2] as AppFilter
        val sortBy = flows[3] as SortBy
        val query = flows[4] as String
        val extract = flows[5] as ExtractState
        val root = flows[6] as Boolean
        val selectionMode = flows[7] as Boolean
        @Suppress("UNCHECKED_CAST")
        val selected = flows[8] as Set<String>

        val filtered = apps.filter { app ->
            val matchesFilter = when (filter) {
                AppFilter.User -> !app.isSystemApp && app.enabled
                AppFilter.Sideloaded -> !app.isSystemApp && !app.isLeanbackSupported && app.enabled
                AppFilter.System -> app.isSystemApp
                AppFilter.Disabled -> !app.enabled
            }
            val matchesQuery = query.isBlank() ||
                app.appName.contains(query, ignoreCase = true) ||
                app.packageName.contains(query, ignoreCase = true)
            matchesFilter && matchesQuery
        }.let { list ->
            when (sortBy) {
                SortBy.Name -> list.sortedBy { it.appName.lowercase() }
                SortBy.Size -> list.sortedByDescending { it.sizeBytes }
                SortBy.Date -> list.sortedByDescending { it.installedAt }
            }
        }
        ManageUiState(apps, filtered, loading, filter, sortBy, query, extract, root, selectionMode, selected)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ManageUiState())

    init {
        viewModelScope.launch { _rootAvailable.value = RootShell.isAvailable() }
        // Load once at ViewModel creation. The VM is retained in its Navigation back-stack entry
        // across tab switches, so revisiting Manage reuses this list instead of re-querying the
        // PackageManager and re-flashing the loading skeleton on every visit.
        loadApps()
    }

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            _apps.value = withContext(Dispatchers.IO) { repo.getInstalledApps(includeSystem = true) }
            _isLoading.value = false
        }
    }

    /** Refresh the list without flashing the loading skeleton (used after quick privileged ops). */
    private suspend fun reloadSilently() {
        _apps.value = withContext(Dispatchers.IO) { repo.getInstalledApps(includeSystem = true) }
    }

    /** Silent refresh entry point for the UI (e.g. after a system-dialog uninstall completes). */
    fun refresh() {
        viewModelScope.launch { reloadSilently() }
    }

    // ── Privileged single-app actions (root) ─────────────────────────────────

    fun forceStop(app: InstalledApp) = runAction {
        PrivilegedAppOps.forceStop(app.packageName)
            .toResult("Force-stopped ${app.appName}", "Force-stop failed")
    }

    fun setEnabled(app: InstalledApp, enabled: Boolean) = runAction {
        PrivilegedAppOps.setEnabled(app.packageName, enabled)
            .toResult(if (enabled) "Enabled ${app.appName}" else "Disabled ${app.appName}", "Failed")
    }

    fun clearData(app: InstalledApp) = runAction {
        PrivilegedAppOps.clearData(app.packageName)
            .toResult("Cleared data of ${app.appName}", "Clear data failed")
    }

    fun uninstallSilent(app: InstalledApp) = runAction {
        AnalyticsHelper.logAppManagementAction(TelemetryEvents.ACTION_UNINSTALL_APP)
        PrivilegedAppOps.uninstall(app.packageName, system = app.isSystemApp)
            .toResult("Uninstalled ${app.appName}", "Uninstall failed")
    }

    fun batchUninstall() {
        val targets = _selectedPackages.value
        if (targets.isEmpty()) return
        runAction {
            val apps = _apps.value.associateBy { it.packageName }
            var ok = 0
            var fail = 0
            targets.forEach { pkg ->
                val app = apps[pkg]
                val r = PrivilegedAppOps.uninstall(pkg, system = app?.isSystemApp ?: false)
                if (r.isSuccess) ok++ else fail++
            }
            exitSelection()
            ActionResult(
                message = if (fail == 0) "Uninstalled $ok app(s)" else "Uninstalled $ok · $fail failed",
                isError = fail > 0,
            )
        }
    }

    /** Runs a privileged op with single-flight guard, then silently reloads and posts the result. */
    private fun runAction(block: suspend () -> ActionResult) {
        if (actionInFlight) return
        actionInFlight = true
        viewModelScope.launch {
            _actionResult.value = try {
                block()
            } catch (t: Throwable) {
                ActionResult(t.message ?: "Action failed", isError = true)
            } finally {
                actionInFlight = false
            }
            reloadSilently()
        }
    }

    private fun Result<Unit>.toResult(success: String, failurePrefix: String): ActionResult =
        fold(
            onSuccess = { ActionResult(success, isError = false) },
            onFailure = { ActionResult("$failurePrefix: ${it.message}", isError = true) },
        )

    fun clearActionResult() { _actionResult.value = null }

    // ── Multi-select ─────────────────────────────────────────────────────────

    fun enterSelection() { _selectionMode.value = true }

    fun exitSelection() {
        _selectionMode.value = false
        _selectedPackages.value = emptySet()
    }

    fun toggleSelection(packageName: String) {
        _selectedPackages.value = _selectedPackages.value.toMutableSet().apply {
            if (!add(packageName)) remove(packageName)
        }
    }

    fun selectAllVisible() {
        _selectedPackages.value = uiState.value.filteredApps.map { it.packageName }.toSet()
    }

    // ── Extract ────────────────────────────────────────────────────────────

    fun extractApp(packageName: String, appName: String) {
        if (_extractState.value is ExtractState.Running) return
        AnalyticsHelper.logAppManagementAction(TelemetryEvents.ACTION_BACKUP_APK)
        extractJob?.cancel()
        _extractState.value = ExtractState.Running(packageName, appName, 0L, 1L)
        extractJob = viewModelScope.launch {
            val result = ApkExtractor.extract(
                context = appContext,
                packageName = packageName,
                outputDir = null, // Uses default public Downloads dir
                filenameTemplate = "{name}-{version}"
            ) { bytes, total ->
                _extractState.value = ExtractState.Running(packageName, appName, bytes, total)
            }
            _extractState.value = when (result) {
                is ApkExtractor.Result.Success -> ExtractState.Done(appName, result.uri)
                is ApkExtractor.Result.Failure -> ExtractState.Error(appName, result.message)
            }
        }
    }

    fun dismissExtractResult() {
        _extractState.value = ExtractState.Idle
    }

    fun setFilter(filter: AppFilter) {
        _filter.value = filter
    }

    fun setSortBy(sortBy: SortBy) {
        _sortBy.value = sortBy
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }
}
