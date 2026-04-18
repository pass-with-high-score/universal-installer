package app.pwhs.universalinstaller.presentation.setting

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
    val SHIZUKU_SET_INSTALL_SOURCE = booleanPreferencesKey("shizuku_set_install_source")
    val SHIZUKU_INSTALLER_PACKAGE_NAME = stringPreferencesKey("shizuku_installer_package_name")
}

enum class ThemeMode(val label: String) {
    System("System"),
    Light("Light"),
    Dark("Dark"),
}

enum class ShizukuState {
    NOT_INSTALLED,   // no Shizuku app and no Sui — nothing to talk to
    NOT_RUNNING,     // Shizuku app installed but service not started (binder dead)
    UNSUPPORTED,     // pre-v11 Shizuku — modern API calls unavailable
    NO_PERMISSION,   // binder alive, permission not granted
    READY,           // binder alive, permission granted
}

data class ShizukuOptions(
    val bypassLowTargetSdk: Boolean = false,
    val allowTest: Boolean = false,
    val replaceExisting: Boolean = false,
    val requestDowngrade: Boolean = false,
    val grantAllPermissions: Boolean = false,
    val allUsers: Boolean = false,
    val setInstallSource: Boolean = false,
    val installerPackageName: String = DEFAULT_INSTALLER_PACKAGE_NAME,
)

const val DEFAULT_INSTALLER_PACKAGE_NAME = "com.android.vending"

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
        // Binder went away — decide if the Shizuku/Sui host is still installed.
        _shizukuState.value = if (hasShizukuPackage()) ShizukuState.NOT_RUNNING
        else ShizukuState.NOT_INSTALLED
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
                setInstallSource = prefs[PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE] ?: false,
                installerPackageName = prefs[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME]
                    ?: DEFAULT_INSTALLER_PACKAGE_NAME,
            )
        },
        dataStore.data.map { prefs ->
            prefs[PreferencesKeys.DELETE_APK_AFTER_INSTALL] ?: false
        },
    ) { flows ->
        val theme = flows[0] as ThemeMode
        val useShizuku = flows[1] as Boolean
        val vtKey = flows[2] as String
        val shizukuState = flows[3] as ShizukuState
        val shizukuOpts = flows[4] as ShizukuOptions
        val deleteApk = flows[5] as Boolean
        val versionName = try {
            application.packageManager
                .getPackageInfo(application.packageName, 0)
                .versionName ?: ""
        } catch (_: Exception) { "" }
        SettingUiState(
            themeMode = theme,
            useShizuku = useShizuku && shizukuState == ShizukuState.READY,
            virusTotalApiKey = vtKey,
            deleteApkAfterInstall = deleteApk,
            shizukuState = shizukuState,
            // "Available" means the binder is alive — we can talk to Shizuku/Sui now.
            shizukuAvailable = shizukuState == ShizukuState.READY ||
                    shizukuState == ShizukuState.NO_PERMISSION,
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
        if (!enabled) {
            viewModelScope.launch {
                dataStore.edit { prefs -> prefs[PreferencesKeys.USE_SHIZUKU] = false }
            }
            return
        }

        // Re-probe in case the user just started Shizuku while this screen was open.
        updateShizukuState()

        when (_shizukuState.value) {
            ShizukuState.NOT_INSTALLED, ShizukuState.NOT_RUNNING, ShizukuState.UNSUPPORTED -> {
                Timber.d("Cannot enable Shizuku: state=${_shizukuState.value}")
                return
            }
            ShizukuState.READY -> {
                viewModelScope.launch {
                    dataStore.edit { prefs -> prefs[PreferencesKeys.USE_SHIZUKU] = true }
                }
            }
            ShizukuState.NO_PERMISSION -> try {
                // shouldShowRequestPermissionRationale == true means user selected "Deny & Don't ask".
                if (Shizuku.shouldShowRequestPermissionRationale()) {
                    Timber.d("Shizuku permission permanently denied — user must re-enable in Shizuku app")
                    return
                }
                Shizuku.requestPermission(SHIZUKU_REQUEST_CODE)
            } catch (e: Exception) {
                Timber.e(e, "Error requesting Shizuku permission")
            }
        }
    }

    fun setDeleteApkAfterInstall(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.DELETE_APK_AFTER_INSTALL] = enabled
            }
        }
    }

    fun setShizukuOption(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    fun setShizukuInstallerPackageName(value: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME] = value
            }
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
        // Sticky listener synchronously fires if binder is already alive (common case when
        // Shizuku/Sui started before the app launched).
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        // Resolve state up front — covers the case where the sticky listener does NOT fire
        // (no binder yet) and we need to differentiate NOT_INSTALLED vs NOT_RUNNING.
        updateShizukuState()
    }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
    }

    /**
     * Single source of truth for Shizuku state. Ordered from strongest to weakest signal:
     *   1. Binder alive (pingBinder) → service is actually running; Shizuku OR Sui.
     *   2. Binder dead but Shizuku app installed → NOT_RUNNING (needs user to start it).
     *   3. Neither → NOT_INSTALLED.
     *
     * We deliberately do not gate on PackageManager first — that check misses Sui (rooted
     * variant with no Shizuku package) and was the reason the app reported "not installed"
     * for users who had Shizuku/Sui actually running.
     */
    private fun updateShizukuState() {
        val binderAlive = try {
            Shizuku.pingBinder()
        } catch (e: Exception) {
            Timber.e(e, "Shizuku.pingBinder threw")
            false
        }

        if (!binderAlive) {
            _shizukuState.value = if (hasShizukuPackage()) ShizukuState.NOT_RUNNING
            else ShizukuState.NOT_INSTALLED
            return
        }

        // Binder is alive — but pre-v11 Shizuku doesn't expose the modern permission API.
        val preV11 = try {
            Shizuku.isPreV11()
        } catch (e: Exception) {
            Timber.e(e, "Shizuku.isPreV11 threw")
            false
        }
        if (preV11) {
            _shizukuState.value = ShizukuState.UNSUPPORTED
            return
        }

        _shizukuState.value = try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
                ShizukuState.READY
            else
                ShizukuState.NO_PERMISSION
        } catch (e: Exception) {
            Timber.e(e, "Shizuku.checkSelfPermission threw despite live binder")
            ShizukuState.NO_PERMISSION
        }
    }

    private fun hasShizukuPackage(): Boolean = try {
        application.packageManager.getPackageInfo(SHIZUKU_PACKAGE, 0)
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }

    companion object {
        private const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
        private const val SHIZUKU_REQUEST_CODE = 0
    }
}