package com.nqmgaming.universalinstaller.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nqmgaming.universalinstaller.domain.installer.InstallMethod
import com.nqmgaming.universalinstaller.domain.repository.SettingsRepository
import rikka.shizuku.Shizuku
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val selectedInstallMethod: InstallMethod = InstallMethod.STANDARD,
    val isShizukuAvailable: Boolean = false,
    val isRootAvailable: Boolean = false
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository
) : ViewModel() {

    private val isShizukuAvailable = checkShizuku()
    private val isRootAvailable = checkRoot()

    val uiState: StateFlow<SettingsUiState> = settingsRepository.installMethodFlow
        .map { 
            SettingsUiState(
                selectedInstallMethod = it,
                isShizukuAvailable = isShizukuAvailable,
                isRootAvailable = isRootAvailable
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = SettingsUiState(
                isShizukuAvailable = isShizukuAvailable,
                isRootAvailable = isRootAvailable
            )
        )

    private fun checkShizuku(): Boolean {
        return try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            false
        }
    }

    private fun checkRoot(): Boolean {
        return try {
            val process = Runtime.getRuntime().exec("su -c id")
            val exitCode = process.waitFor()
            exitCode == 0
        } catch (e: Exception) {
            false
        }
    }

    fun updateInstallMethod(method: InstallMethod) {
        viewModelScope.launch {
            settingsRepository.setInstallMethod(method)
        }
    }
}
