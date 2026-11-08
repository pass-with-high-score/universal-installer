package app.pwhs.universalinstaller.presentation.setting.virustotal

import android.app.Application
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.billing.BillingManager
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.SecurityLevel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class VirusTotalUiState(
    val virusTotalDisabled: Boolean = false,
    val apiKey: String = "",
    val securityLevel: SecurityLevel = SecurityLevel.Normal,
)

class VirusTotalSettingViewModel(
    application: Application,
    val billingManager: BillingManager,
) : ViewModel() {
    private val dataStore = application.dataStore

    val uiState: StateFlow<VirusTotalUiState> = dataStore.data.map { prefs ->
        VirusTotalUiState(
            virusTotalDisabled = prefs[PreferencesKeys.DISABLE_VIRUSTOTAL] ?: false,
            apiKey = prefs[PreferencesKeys.VIRUSTOTAL_API_KEY] ?: "",
            securityLevel = SecurityLevel.from(
                stored = prefs[PreferencesKeys.SECURITY_LEVEL],
                legacyStrict = prefs[PreferencesKeys.STRICT_VIRUSTOTAL_CHECK] ?: false,
            ),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), VirusTotalUiState())

    fun setVirusTotalDisabled(disabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.DISABLE_VIRUSTOTAL] = disabled
            }
        }
    }

    fun setApiKey(key: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.VIRUSTOTAL_API_KEY] = key
            }
        }
    }

    fun setSecurityLevel(level: SecurityLevel) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.SECURITY_LEVEL] = level.name
                prefs[PreferencesKeys.STRICT_VIRUSTOTAL_CHECK] = level == SecurityLevel.Strict
            }
        }
    }
}
