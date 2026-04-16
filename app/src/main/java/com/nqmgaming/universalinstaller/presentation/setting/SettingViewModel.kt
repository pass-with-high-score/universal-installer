package com.nqmgaming.universalinstaller.presentation.setting

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku
import timber.log.Timber

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object PreferencesKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val USE_SHIZUKU = booleanPreferencesKey("use_shizuku")
    val VIRUSTOTAL_API_KEY = stringPreferencesKey("virustotal_api_key")
    val DELETE_APK_AFTER_INSTALL = booleanPreferencesKey("delete_apk_after_install")

    // Shizuku install options
    val SHIZUKU_BYPASS_LOW_TARGET_SDK = booleanPreferencesKey("shizuku_bypass_low_target_sdk")
    val SHIZUKU_ALLOW_TEST = booleanPreferencesKey("shizuku_allow_test")
    val SHIZUKU_REPLACE_EXISTING = booleanPreferencesKey("shizuku_replace_existing")
    val SHIZUKU_REQUEST_DOWNGRADE = booleanPreferencesKey("shizuku_request_downgrade")
    val SHIZUKU_GRANT_ALL_PERMISSIONS = booleanPreferencesKey("shizuku_grant_all_permissions")
    val SHIZUKU_ALL_USERS = booleanPreferencesKey("shizuku_all_users")
}

enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

enum class ShizukuState {
    NOT_INSTALLED,
    NOT_RUNNING,
    NO_PERMISSION,
    READY,
}

data class ShizukuOptions(
    val bypassLowTargetSdk: Boolean = false,
    val allowTest: Boolean = false,
    val replaceExisting: Boolean = false,
    val requestDowngrade: Boolean = false,
    val grantAllPermissions: Boolean = false,
    val allUsers: Boolean = false,
)

data class SettingUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val useShizuku: Boolean = false,
    val virusTotalApiKey: String = "",
    val deleteApkAfterInstall: Boolean = false,
    val shizukuState: ShizukuState = ShizukuState.NOT_INSTALLED,
    val shizukuAvailable: Boolean = false,
    val shizukuOptions: ShizukuOptions = ShizukuOptions(),
    val appVersion: String = "",
)

class SettingViewModel(
    private val application: Application,
) : ViewModel() {

    private val dataStore = application.dataStore

    private val _shizukuState = MutableStateFlow(ShizukuState.NOT_INSTALLED)

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Timber.d("Shizuku binder received")
        updateShizukuState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Timber.d("Shizuku binder dead")
        _shizukuState.value = ShizukuState.NOT_RUNNING
    }

    private val permissionResultListener =
        Shizuku.OnRequestPermissionResultListener { _: Int, grantResult: Int ->
            if (grantResult == PackageManager.PERMISSION_GRANTED) {
                Timber.d("Shizuku permission granted")
                _shizukuState.value = ShizukuState.READY
                viewModelScope.launch {
                    dataStore.edit { prefs ->
                        prefs[PreferencesKeys.USE_SHIZUKU] = true
                    }
                }
            } else {
                Timber.d("Shizuku permission denied")
                _shizukuState.value = ShizukuState.NO_PERMISSION
            }
        }

    val uiState: StateFlow<SettingUiState> = combine(
        dataStore.data.map { prefs ->
            val themeName = prefs[PreferencesKeys.THEME_MODE] ?: ThemeMode.System.name
            ThemeMode.entries.find { it.name == themeName } ?: ThemeMode.System
        },
        dataStore.data.map { prefs ->
            prefs[PreferencesKeys.USE_SHIZUKU] ?: false
        },
        dataStore.data.map { prefs ->
            prefs[PreferencesKeys.VIRUSTOTAL_API_KEY] ?: ""
        },
        _shizukuState,
        dataStore.data.map { prefs ->
            ShizukuOptions(
                bypassLowTargetSdk = prefs[PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK] ?: false,
                allowTest = prefs[PreferencesKeys.SHIZUKU_ALLOW_TEST] ?: false,
                replaceExisting = prefs[PreferencesKeys.SHIZUKU_REPLACE_EXISTING] ?: false,
                requestDowngrade = prefs[PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE] ?: false,
                grantAllPermissions = prefs[PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS] ?: false,
                allUsers = prefs[PreferencesKeys.SHIZUKU_ALL_USERS] ?: false,
            )
        },
    ) { flows ->
        val theme = flows[0] as ThemeMode
        val useShizuku = flows[1] as Boolean
        val vtKey = flows[2] as String
        val shizukuState = flows[3] as ShizukuState
        val shizukuOpts = flows[4] as ShizukuOptions
        val versionName = try {
            application.packageManager
                .getPackageInfo(application.packageName, 0)
                .versionName ?: ""
        } catch (_: Exception) { "" }
        SettingUiState(
            themeMode = theme,
            useShizuku = useShizuku && shizukuState == ShizukuState.READY,
            virusTotalApiKey = vtKey,
            shizukuState = shizukuState,
            shizukuAvailable = shizukuState == ShizukuState.READY || shizukuState == ShizukuState.NO_PERMISSION,
            shizukuOptions = shizukuOpts,
            appVersion = versionName,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingUiState())

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.THEME_MODE] = mode.name
            }
        }
    }

    /**
     * Called when user toggles the Shizuku switch.
     * If turning on: check permission, request if needed.
     * If turning off: just save the preference.
     */
    fun setUseShizuku(enabled: Boolean) {
        if (enabled) {
            if (_shizukuState.value == ShizukuState.NOT_RUNNING ||
                _shizukuState.value == ShizukuState.NOT_INSTALLED
            ) {
                return
            }
            try {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    _shizukuState.value = ShizukuState.READY
                    viewModelScope.launch {
                        dataStore.edit { prefs ->
                            prefs[PreferencesKeys.USE_SHIZUKU] = true
                        }
                    }
                } else {
                    // Always request permission — Shizuku service shows its own dialog
                    Shizuku.requestPermission(0)
                }
            } catch (e: Exception) {
                Timber.e(e, "Error checking Shizuku permission")
            }
        } else {
            viewModelScope.launch {
                dataStore.edit { prefs ->
                    prefs[PreferencesKeys.USE_SHIZUKU] = false
                }
            }
        }
    }

    fun setShizukuOption(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    fun setVirusTotalApiKey(key: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.VIRUSTOTAL_API_KEY] = key
            }
        }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        checkShizukuInstalled()
    }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    private fun checkShizukuInstalled() {
        try {
            application.packageManager.getPackageInfo("moe.shizuku.privileged.api", 0)
            // Shizuku is installed, but we need to wait for binder
            // The binderReceivedListener will fire if binder is alive
            _shizukuState.value = ShizukuState.NOT_RUNNING
        } catch (_: PackageManager.NameNotFoundException) {
            _shizukuState.value = ShizukuState.NOT_INSTALLED
            Timber.d("Shizuku not installed on this device")
        }
    }

    private fun updateShizukuState() {
        try {
            if (Shizuku.pingBinder()) {
                if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                    _shizukuState.value = ShizukuState.READY
                } else {
                    _shizukuState.value = ShizukuState.NO_PERMISSION
                }
            } else {
                _shizukuState.value = ShizukuState.NOT_RUNNING
            }
        } catch (e: Exception) {
            Timber.e(e, "Error checking Shizuku state")
            _shizukuState.value = ShizukuState.NOT_RUNNING
        }
    }
}