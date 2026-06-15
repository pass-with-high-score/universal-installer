package app.pwhs.tv.presentation.manage

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.AppRepository
import app.pwhs.core.domain.InstalledApp
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

enum class AppFilter { User, System, Disabled }
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

data class ManageUiState(
    val apps: List<InstalledApp> = emptyList(),
    val filteredApps: List<InstalledApp> = emptyList(),
    val isLoading: Boolean = true,
    val filter: AppFilter = AppFilter.User,
    val sortBy: SortBy = SortBy.Name,
    val searchQuery: String = "",
    val extractState: ExtractState = ExtractState.Idle
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

    private var extractJob: kotlinx.coroutines.Job? = null

    val uiState: StateFlow<ManageUiState> = combine(
        listOf(_apps, _isLoading, _filter, _sortBy, _searchQuery, _extractState)
    ) { flows ->
        @Suppress("UNCHECKED_CAST")
        val apps = flows[0] as List<InstalledApp>
        val loading = flows[1] as Boolean
        val filter = flows[2] as AppFilter
        val sortBy = flows[3] as SortBy
        val query = flows[4] as String
        val extract = flows[5] as ExtractState
        
        val filtered = apps.filter { app ->
            val matchesFilter = when (filter) {
                AppFilter.User -> !app.isSystemApp && app.enabled
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
        ManageUiState(apps, filtered, loading, filter, sortBy, query, extract)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ManageUiState())

    fun loadApps() {
        viewModelScope.launch {
            _isLoading.value = true
            val allApps = withContext(Dispatchers.IO) { repo.getInstalledApps(includeSystem = true) }
            _apps.value = allApps
            _isLoading.value = false
        }
    }

    fun extractApp(packageName: String, appName: String) {
        if (_extractState.value is ExtractState.Running) return
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
