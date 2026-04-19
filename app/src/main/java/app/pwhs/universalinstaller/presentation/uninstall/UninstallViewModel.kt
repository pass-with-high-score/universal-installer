package app.pwhs.universalinstaller.presentation.uninstall

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalinstaller.data.local.UninstallLogDao
import app.pwhs.universalinstaller.data.local.UninstallLogEntity
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.uninstaller.PackageUninstaller
import ru.solrudev.ackpine.uninstaller.createSession
import ru.solrudev.ackpine.session.await
import ru.solrudev.ackpine.session.Session
import ru.solrudev.ackpine.session.parameters.Confirmation
import ru.solrudev.ackpine.shizuku.shizuku
import timber.log.Timber

enum class UninstallSortBy { Name, Size, InstalledAt, LastUsed }
enum class SortDirection { Asc, Desc }

data class UninstallUiState(
    val apps: List<InstalledApp> = emptyList(),
    val filteredApps: List<InstalledApp> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val showSystemApps: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isAllSelected: Boolean = false,
    val sortBy: UninstallSortBy = UninstallSortBy.Name,
    val sortDirection: SortDirection = SortDirection.Asc,
    val usageAccessGranted: Boolean = false,
)

class UninstallViewModel(
    private val application: Application,
    private val packageUninstaller: PackageUninstaller,
    private val uninstallLogDao: UninstallLogDao,
) : ViewModel() {

    private val notifier = UninstallNotifier(application)

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    private val _isLoading = MutableStateFlow(true)
    private val _showSystemApps = MutableStateFlow(false)
    private val _selectedPackages = MutableStateFlow<Set<String>>(emptySet())
    private val _sortBy = MutableStateFlow(UninstallSortBy.Name)
    private val _sortDirection = MutableStateFlow(SortDirection.Asc)
    private val _usageAccess = MutableStateFlow(false)

    val uiState: StateFlow<UninstallUiState> = combine(
        listOf(_apps, _searchQuery, _isLoading, _showSystemApps, _selectedPackages,
            _sortBy, _sortDirection, _usageAccess)
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val apps = flows[0] as List<InstalledApp>
        val query = flows[1] as String
        val loading = flows[2] as Boolean
        val showSystem = flows[3] as Boolean
        @Suppress("UNCHECKED_CAST")
        val selected = flows[4] as Set<String>
        val sortBy = flows[5] as UninstallSortBy
        val direction = flows[6] as SortDirection
        val usage = flows[7] as Boolean
        val filtered = apps
            .filter { app ->
                if (!showSystem && app.isSystemApp) return@filter false
                if (query.isBlank()) return@filter true
                app.appName.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
            }
            .let { applySort(it, sortBy, direction) }
        UninstallUiState(
            apps = apps,
            filteredApps = filtered,
            searchQuery = query,
            isLoading = loading,
            showSystemApps = showSystem,
            selectedPackages = selected,
            isSelectionMode = selected.isNotEmpty(),
            isAllSelected = filtered.isNotEmpty() && selected.containsAll(filtered.map { it.packageName }.toSet()),
            sortBy = sortBy,
            sortDirection = direction,
            usageAccessGranted = usage,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UninstallUiState())

    private fun applySort(
        list: List<InstalledApp>,
        sortBy: UninstallSortBy,
        direction: SortDirection,
    ): List<InstalledApp> {
        // Primary key chosen per sortBy; name is always the stable tiebreaker so equal
        // sizes / equal dates still render in a predictable order.
        val nameKey: (InstalledApp) -> String = { it.appName.lowercase() }
        val comparator: Comparator<InstalledApp> = when (sortBy) {
            UninstallSortBy.Name -> compareBy(nameKey)
            UninstallSortBy.Size -> compareBy<InstalledApp> { it.sizeBytes }.thenBy(nameKey)
            UninstallSortBy.InstalledAt -> compareBy<InstalledApp> { it.installedAt }.thenBy(nameKey)
            UninstallSortBy.LastUsed -> compareBy<InstalledApp> { it.lastUsedAt }.thenBy(nameKey)
        }
        val sorted = list.sortedWith(comparator)
        return if (direction == SortDirection.Asc) sorted else sorted.reversed()
    }

    fun setSort(sortBy: UninstallSortBy) {
        if (_sortBy.value == sortBy) {
            // Same axis: flip direction
            _sortDirection.value =
                if (_sortDirection.value == SortDirection.Asc) SortDirection.Desc else SortDirection.Asc
        } else {
            _sortBy.value = sortBy
            // Sensible defaults: Name → Asc (A-Z), Size/date/usage → Desc (big/recent first)
            _sortDirection.value = if (sortBy == UninstallSortBy.Name) SortDirection.Asc else SortDirection.Desc
        }
    }

    fun refreshUsageAccess() {
        _usageAccess.value = hasUsageAccess()
    }

    private fun hasUsageAccess(): Boolean {
        val appOps = application.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                application.packageName,
            )
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(
                android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
                android.os.Process.myUid(),
                application.packageName,
            )
        }
        return mode == android.app.AppOpsManager.MODE_ALLOWED
    }

    init {
        _usageAccess.value = hasUsageAccess()
        loadInstalledApps()
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleSystemApps() {
        _showSystemApps.value = !_showSystemApps.value
    }

    fun toggleSelection(packageName: String) {
        _selectedPackages.value = _selectedPackages.value.toMutableSet().apply {
            if (contains(packageName)) remove(packageName) else add(packageName)
        }
    }

    fun clearSelection() {
        _selectedPackages.value = emptySet()
    }

    fun toggleSelectAll() {
        val allPackages = uiState.value.filteredApps.map { it.packageName }.toSet()
        _selectedPackages.value = if (_selectedPackages.value == allPackages) emptySet() else allPackages
    }

    fun uninstallSelected() {
        val packages = _selectedPackages.value.toList()
        _selectedPackages.value = emptySet()
        if (packages.isEmpty()) return
        if (packages.size == 1) {
            uninstallApp(packages.first())
            return
        }

        viewModelScope.launch {
            val opts = readUninstallOptions()
            val total = packages.size
            val notifId = notifier.notifyBatchStart(total)
            var successful = 0
            var failed = 0
            packages.forEachIndexed { index, pkg ->
                val appName = _apps.value.firstOrNull { it.packageName == pkg }?.appName ?: pkg
                notifier.notifyBatchProgress(notifId, completed = index, total = total, currentAppName = appName)
                val ok = performUninstall(pkg, opts)
                if (ok) successful++ else failed++
            }
            notifier.notifyBatchDone(notifId, successful = successful, failed = failed)
        }
    }

    fun uninstallApp(packageName: String) {
        viewModelScope.launch {
            val opts = readUninstallOptions()
            val appName = _apps.value.firstOrNull { it.packageName == packageName }?.appName ?: packageName
            val notifId = notifier.notifySingleStart(appName)
            val ok = performUninstall(packageName, opts)
            notifier.notifySingleResult(notifId, appName, success = ok)
        }
    }

    private data class UninstallOptions(
        val useShizuku: Boolean,
        val keepData: Boolean,
        val allUsers: Boolean,
    )

    private suspend fun performUninstall(packageName: String, opts: UninstallOptions): Boolean {
        val appName = _apps.value.firstOrNull { it.packageName == packageName }?.appName ?: packageName
        return try {
            val session = packageUninstaller.createSession(packageName) {
                confirmation = Confirmation.IMMEDIATE
                if (opts.useShizuku) {
                    shizuku {
                        keepData = opts.keepData
                        allUsers = opts.allUsers
                    }
                }
            }
            when (val result = session.await()) {
                Session.State.Succeeded -> {
                    Timber.d("Uninstalled $packageName successfully")
                    _apps.value = _apps.value.filter { it.packageName != packageName }
                    saveLog(packageName, appName, success = true, errorMessage = null)
                    true
                }
                is Session.State.Failed -> {
                    val reason = result.failure.message?.takeIf { it.isNotBlank() }
                        ?: "Uninstall failed (no reason reported)"
                    Timber.e("Failed to uninstall $packageName — $reason")
                    saveLog(packageName, appName, success = false, errorMessage = reason)
                    false
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "Error uninstalling $packageName")
            saveLog(packageName, appName, success = false, errorMessage = e.message ?: e::class.java.simpleName)
            false
        }
    }

    private suspend fun saveLog(
        packageName: String,
        appName: String,
        success: Boolean,
        errorMessage: String?,
    ) {
        try {
            uninstallLogDao.insert(
                UninstallLogEntity(
                    packageName = packageName,
                    appName = appName,
                    success = success,
                    errorMessage = errorMessage,
                )
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to persist uninstall log")
        }
    }

    private suspend fun readUninstallOptions(): UninstallOptions {
        return try {
            val prefs = application.dataStore.data.first()
            val useShizuku = prefs[PreferencesKeys.USE_SHIZUKU] ?: false
            UninstallOptions(
                useShizuku = useShizuku,
                // Keep-data / all-users are Shizuku-only flags — the stock PackageInstaller
                // session has no equivalent, so we ignore them when Shizuku is off.
                keepData = useShizuku && (prefs[PreferencesKeys.SHIZUKU_UNINSTALL_KEEP_DATA] ?: false),
                allUsers = useShizuku && (prefs[PreferencesKeys.SHIZUKU_UNINSTALL_ALL_USERS] ?: false),
            )
        } catch (_: Exception) {
            UninstallOptions(useShizuku = false, keepData = false, allUsers = false)
        }
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = withContext(Dispatchers.IO) {
                val pm = application.packageManager
                val installedInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(
                        PackageManager.ApplicationInfoFlags.of(0)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(0)
                }

                // Last-used lookup: single batch query over the past year — much cheaper than
                // querying per package. Skipped entirely when the permission isn't granted.
                val lastUsedMap = queryLastUsedMap()

                installedInfos.map { appInfo ->
                    val pkgInfo = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(
                                appInfo.packageName,
                                PackageManager.PackageInfoFlags.of(0)
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(appInfo.packageName, 0)
                        }
                    } catch (_: Exception) { null }

                    val sourceDir = appInfo.sourceDir
                    val sizeBytes = if (!sourceDir.isNullOrBlank()) {
                        runCatching { java.io.File(sourceDir).length() }.getOrDefault(0L)
                    } else 0L

                    InstalledApp(
                        packageName = appInfo.packageName,
                        appName = appInfo.loadLabel(pm).toString(),
                        versionName = pkgInfo?.versionName ?: "",
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        sizeBytes = sizeBytes,
                        installedAt = pkgInfo?.firstInstallTime ?: 0L,
                        lastUsedAt = lastUsedMap[appInfo.packageName] ?: 0L,
                    )
                }
            }
            _apps.value = apps
            _isLoading.value = false
        }
    }

    /**
     * Batch lookup for last-used timestamps via `UsageStatsManager`. Requires the user to
     * have granted "Usage access" in system settings — we silently return an empty map if
     * they haven't, and the UI offers a "grant" action from the Last Used sort option.
     */
    private fun queryLastUsedMap(): Map<String, Long> {
        if (!hasUsageAccess()) return emptyMap()
        return try {
            val usm = application.getSystemService(android.content.Context.USAGE_STATS_SERVICE)
                as android.app.usage.UsageStatsManager
            val now = System.currentTimeMillis()
            val yearAgo = now - 365L * 24 * 60 * 60 * 1000
            val stats = usm.queryUsageStats(
                android.app.usage.UsageStatsManager.INTERVAL_YEARLY, yearAgo, now
            ) ?: return emptyMap()
            // A package may appear multiple times — keep the max lastTimeUsed.
            val map = HashMap<String, Long>(stats.size)
            for (s in stats) {
                val t = s.lastTimeUsed
                if (t <= 0L) continue
                val prev = map[s.packageName] ?: 0L
                if (t > prev) map[s.packageName] = t
            }
            map
        } catch (e: Exception) {
            Timber.w(e, "queryUsageStats failed")
            emptyMap()
        }
    }
}