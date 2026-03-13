package com.nqmgaming.universalinstaller.presentation.manager

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nqmgaming.universalinstaller.domain.repository.AppManagerRepository
import com.nqmgaming.universalinstaller.domain.repository.InstalledApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class AppManagerUiState(
    val isLoading: Boolean = false,
    val installedApps: List<InstalledApp> = emptyList(),
    val isExtracting: Boolean = false,
    val extractMessage: String? = null
)

class AppManagerViewModel(
    private val repository: AppManagerRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    private val _installedApps = MutableStateFlow<List<InstalledApp>>(emptyList())
    private val _isExtracting = MutableStateFlow(false)
    private val _extractMessage = MutableStateFlow<String?>(null)

    val uiState: StateFlow<AppManagerUiState> = combine(
        _isLoading, _installedApps, _isExtracting, _extractMessage
    ) { isLoading, apps, isExtracting, message ->
        AppManagerUiState(isLoading, apps, isExtracting, message)
    }.stateIn(viewModelScope, SharingStarted.Lazily, AppManagerUiState())

    init {
        loadApps()
    }

    fun loadApps(includeSystemApps: Boolean = false) {
        viewModelScope.launch {
            _isLoading.value = true
            _installedApps.value = repository.getInstalledApps(includeSystemApps)
            _isLoading.value = false
        }
    }

    fun extractApp(app: InstalledApp) {
        viewModelScope.launch {
            _isExtracting.value = true
            _extractMessage.value = null
            
            val result = repository.extractApp(app)
            
            _isExtracting.value = false
            _extractMessage.value = if (result.isSuccess) {
                "Extracted to: ${result.getOrNull()}"
            } else {
                "Failed: ${result.exceptionOrNull()?.message}"
            }
        }
    }

    fun clearMessage() {
        _extractMessage.value = null
    }
}
