package app.pwhs.universalinstaller.presentation.extract

import android.app.Application
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalinstaller.domain.model.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

sealed interface ExtractState {
    data object Idle : ExtractState
    data class Running(
        val packageName: String,
        val appName: String,
        val bytesCopied: Long,
        val totalBytes: Long,
    ) : ExtractState
    data class Done(val appName: String, val file: File) : ExtractState
    data class Error(val appName: String, val message: String) : ExtractState
}

data class ExtractUiState(
    val apps: List<InstalledApp> = emptyList(),
    val filteredApps: List<InstalledApp> = emptyList(),
    val searchQuery: String = "",
    val showSystemApps: Boolean = false,
    val isLoading: Boolean = true,
    val extractState: ExtractState = ExtractState.Idle,
)

class ExtractViewModel(
    private val application: Application,
) : ViewModel() {

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _query = MutableStateFlow("")
    private val _showSystem = MutableStateFlow(false)
    private val _isLoading = MutableStateFlow(true)
    private val _extractState = MutableStateFlow<ExtractState>(ExtractState.Idle)

    val uiState: StateFlow<ExtractUiState> = combine(
        _apps, _query, _showSystem, _isLoading, _extractState,
    ) { apps, query, showSystem, loading, extract ->
        val filtered = apps
            .asSequence()
            .filter { showSystem || !it.isSystemApp }
            .filter {
                query.isBlank() ||
                    it.appName.contains(query, ignoreCase = true) ||
                    it.packageName.contains(query, ignoreCase = true)
            }
            .sortedBy { it.appName.lowercase() }
            .toList()
        ExtractUiState(
            apps = apps,
            filteredApps = filtered,
            searchQuery = query,
            showSystemApps = showSystem,
            isLoading = loading,
            extractState = extract,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000L), ExtractUiState())

    private var extractJob: Job? = null

    init {
        loadInstalledApps()
    }

    fun setQuery(q: String) { _query.value = q }
    fun toggleSystemApps() { _showSystem.value = !_showSystem.value }
    fun refresh() = loadInstalledApps()

    fun extract(packageName: String, appName: String) {
        // One extraction at a time — second tap during a copy would race on the output file.
        if (_extractState.value is ExtractState.Running) return
        extractJob?.cancel()
        _extractState.value = ExtractState.Running(packageName, appName, 0L, 1L)
        extractJob = viewModelScope.launch {
            val result = ApkExtractor.extract(application, packageName) { bytes, total ->
                _extractState.value = ExtractState.Running(packageName, appName, bytes, total)
            }
            _extractState.value = when (result) {
                is ApkExtractor.Result.Success -> ExtractState.Done(appName, result.file)
                is ApkExtractor.Result.Failure -> ExtractState.Error(appName, result.message)
            }
        }
    }

    fun dismissResult() {
        _extractState.value = ExtractState.Idle
    }

    private fun loadInstalledApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val apps = withContext(Dispatchers.IO) {
                val pm = application.packageManager
                val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
                } else {
                    @Suppress("DEPRECATION")
                    pm.getInstalledApplications(0)
                }
                installed.map { info ->
                    val pkgInfo = try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            pm.getPackageInfo(
                                info.packageName,
                                PackageManager.PackageInfoFlags.of(0),
                            )
                        } else {
                            @Suppress("DEPRECATION")
                            pm.getPackageInfo(info.packageName, 0)
                        }
                    } catch (_: Exception) { null }
                    val baseSize = info.sourceDir
                        ?.let { runCatching { File(it).length() }.getOrDefault(0L) }
                        ?: 0L
                    val splitsSize = info.splitSourceDirs
                        ?.sumOf { p -> p?.let { File(it).length() } ?: 0L }
                        ?: 0L
                    InstalledApp(
                        packageName = info.packageName,
                        appName = info.loadLabel(pm).toString(),
                        versionName = pkgInfo?.versionName ?: "",
                        isSystemApp = (info.flags and ApplicationInfo.FLAG_SYSTEM) != 0,
                        sizeBytes = baseSize + splitsSize,
                        installedAt = pkgInfo?.firstInstallTime ?: 0L,
                        hasSplits = !info.splitSourceDirs.isNullOrEmpty(),
                    )
                }
            }
            _apps.value = apps
            _isLoading.value = false
        }
    }
}
