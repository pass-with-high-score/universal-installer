package app.pwhs.universalinstaller.presentation.uninstall

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalinstaller.data.local.UninstallLogDao
import app.pwhs.universalinstaller.data.local.UninstallLogEntity
import app.pwhs.universalinstaller.domain.model.InstalledApp
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
import ru.solrudev.ackpine.shizuku.useShizuku
import timber.log.Timber

data class UninstallUiState(
    val apps: List<InstalledApp> = emptyList(),
    val filteredApps: List<InstalledApp> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val showSystemApps: Boolean = false,
    val selectedPackages: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isAllSelected: Boolean = false,
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

    val uiState: StateFlow<UninstallUiState> = combine(
        _apps,
        _searchQuery,
        _isLoading,
        _showSystemApps,
        _selectedPackages,
    ) { flows ->
        val apps = flows[0] as List<InstalledApp>
        val query = flows[1] as String
        val loading = flows[2] as Boolean
        val showSystem = flows[3] as Boolean
        val selected = flows[4] as Set<String>
        val filtered = apps
            .filter { app ->
                if (!showSystem && app.isSystemApp) return@filter false
                if (query.isBlank()) return@filter true
                app.appName.contains(query, ignoreCase = true) ||
                        app.packageName.contains(query, ignoreCase = true)
            }
            .sortedBy { it.appName.lowercase() }
        UninstallUiState(
            apps = apps,
            filteredApps = filtered,
            searchQuery = query,
            isLoading = loading,
            showSystemApps = showSystem,
            selectedPackages = selected,
            isSelectionMode = selected.isNotEmpty(),
            isAllSelected = filtered.isNotEmpty() && selected.containsAll(filtered.map { it.packageName }.toSet()),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), UninstallUiState())

    init {
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
            val useShizuku = readShizukuPref()
            val total = packages.size
            val notifId = notifier.notifyBatchStart(total)
            var successful = 0
            var failed = 0
            packages.forEachIndexed { index, pkg ->
                val appName = _apps.value.firstOrNull { it.packageName == pkg }?.appName ?: pkg
                notifier.notifyBatchProgress(notifId, completed = index, total = total, currentAppName = appName)
                val ok = performUninstall(pkg, useShizuku)
                if (ok) successful++ else failed++
            }
            notifier.notifyBatchDone(notifId, successful = successful, failed = failed)
        }
    }

    fun uninstallApp(packageName: String) {
        viewModelScope.launch {
            val useShizuku = readShizukuPref()
            val appName = _apps.value.firstOrNull { it.packageName == packageName }?.appName ?: packageName
            val notifId = notifier.notifySingleStart(appName)
            val ok = performUninstall(packageName, useShizuku)
            notifier.notifySingleResult(notifId, appName, success = ok)
        }
    }

    private suspend fun performUninstall(packageName: String, useShizuku: Boolean): Boolean {
        val appName = _apps.value.firstOrNull { it.packageName == packageName }?.appName ?: packageName
        return try {
            val session = packageUninstaller.createSession(packageName) {
                confirmation = Confirmation.IMMEDIATE
                if (useShizuku) {
                    useShizuku {}
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

    private suspend fun readShizukuPref(): Boolean {
        return try {
            val prefs = application.dataStore.data.first()
            prefs[booleanPreferencesKey("use_shizuku")] ?: false
        } catch (_: Exception) {
            false
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

                installedInfos.map { appInfo ->
                    val versionName = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(
                                appInfo.packageName,
                                PackageManager.PackageInfoFlags.of(0)
                            ).versionName ?: ""
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(appInfo.packageName, 0).versionName ?: ""
                        }
                    } catch (_: Exception) {
                        ""
                    }
                    InstalledApp(
                        packageName = appInfo.packageName,
                        appName = appInfo.loadLabel(pm).toString(),
                        versionName = versionName,
                        isSystemApp = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                    )
                }
            }
            _apps.value = apps
            _isLoading.value = false
        }
    }
}