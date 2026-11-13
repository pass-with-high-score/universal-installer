package app.pwhs.universalinstaller.presentation.setting.virustotal

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VirusTotalQuotaStore
import app.pwhs.core.network.VtQuotaStatus
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VirusTotalUiState(
    val scanningDisabled: Boolean = false,
    val primaryScanner: String = "virusdetec",
    val backupScanner: String = "virustotal",
    val autoFallbackEnabled: Boolean = true,
    val offlineChecksEnabled: Boolean = false,
    val apiKey: String = "",
    val quotaStatus: VtQuotaStatus = VtQuotaStatus(),
)

class VirusTotalSettingViewModel(
    application: Application,
) : ViewModel() {
    private val dataStore = application.dataStore

    val uiState: StateFlow<VirusTotalUiState> = combine(
        dataStore.data,
        VirusTotalKeyManager.observeQuotaStatus(application),
    ) { prefs, quota ->
        VirusTotalUiState(
            scanningDisabled = SharedPrefsKeys.isSecurityScanningDisabled(prefs),
            primaryScanner = SharedPrefsKeys.getPrimaryScannerId(prefs),
            backupScanner = SharedPrefsKeys.getBackupScannerId(prefs),
            autoFallbackEnabled = SharedPrefsKeys.isAutoFallbackEnabled(prefs),
            offlineChecksEnabled = prefs[SharedPrefsKeys.SECURITY_RUN_OFFLINE_CHECKS] ?: false,
            apiKey = prefs[SharedPrefsKeys.VIRUSTOTAL_API_KEY] ?: "",
            quotaStatus = quota.copy(
                usedToday = VirusTotalQuotaStore.parseAppShieldHashesToday(prefs).size,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VirusTotalUiState())

    fun setScanningDisabled(disabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SharedPrefsKeys.DISABLE_VIRUSTOTAL] = disabled
            }
        }
    }

    fun setPrimaryScanner(scannerId: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SharedPrefsKeys.SECURITY_PRIMARY_SCANNER] = scannerId
                if (scannerId == "virustotal") {
                    prefs[SharedPrefsKeys.SECURITY_BACKUP_SCANNER] = "virusdetec"
                } else {
                    prefs[SharedPrefsKeys.SECURITY_BACKUP_SCANNER] = "virustotal"
                }
            }
        }
    }

    fun setAutoFallbackEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SharedPrefsKeys.SECURITY_AUTO_FALLBACK] = enabled
            }
        }
    }

    fun setOfflineChecksEnabled(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SharedPrefsKeys.SECURITY_RUN_OFFLINE_CHECKS] = enabled
            }
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[SharedPrefsKeys.VIRUSTOTAL_API_KEY] = key.trim()
            }
        }
    }
}
