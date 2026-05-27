package app.pwhs.universalinstaller.presentation.setting

import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import android.content.Intent
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import app.pwhs.universalinstaller.domain.model.InstallerProfile
import app.pwhs.universalinstaller.domain.manager.ProfileManager
import rikka.shizuku.Shizuku
import timber.log.Timber

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object PreferencesKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
    val USE_SHIZUKU = booleanPreferencesKey("use_shizuku")
    val USE_ROOT = booleanPreferencesKey("use_root")
    val INSTALL_USER_ID = intPreferencesKey("install_user_id")
    val VIRUSTOTAL_API_KEY = stringPreferencesKey("virustotal_api_key")
    val DELETE_APK_AFTER_INSTALL = booleanPreferencesKey("delete_apk_after_install")

    /** Open the app automatically after a successful install (with a 3-second cancellable countdown). */
    val AUTO_OPEN_AFTER_INSTALL = booleanPreferencesKey("auto_open_after_install")

    /**
     * Per-package installer-source overrides. Stored as one entry per line
     * (`pkg=installer`) so we don't pull in a JSON dependency. See
     * [app.pwhs.universalinstaller.presentation.install.dialog.InstallerOverrides]
     * for the parser. Empty / missing → no override; the global Shizuku/Root
     * installer pref is used.
     */
    val INSTALLER_OVERRIDES = stringPreferencesKey("installer_overrides")

    // Shizuku install options
    val SHIZUKU_BYPASS_LOW_TARGET_SDK = booleanPreferencesKey("shizuku_bypass_low_target_sdk")
    val SHIZUKU_ALLOW_TEST = booleanPreferencesKey("shizuku_allow_test")
    val SHIZUKU_REPLACE_EXISTING = booleanPreferencesKey("shizuku_replace_existing")
    val SHIZUKU_REQUEST_DOWNGRADE = booleanPreferencesKey("shizuku_request_downgrade")
    val SHIZUKU_GRANT_ALL_PERMISSIONS = booleanPreferencesKey("shizuku_grant_all_permissions")
    val SHIZUKU_ALL_USERS = booleanPreferencesKey("shizuku_all_users")
    val SHIZUKU_SET_INSTALL_SOURCE = booleanPreferencesKey("shizuku_set_install_source")
    val SHIZUKU_INSTALLER_PACKAGE_NAME = stringPreferencesKey("shizuku_installer_package_name")

    // Shizuku uninstall options (pm uninstall -k / --user all)
    val SHIZUKU_UNINSTALL_KEEP_DATA = booleanPreferencesKey("shizuku_uninstall_keep_data")
    val SHIZUKU_UNINSTALL_ALL_USERS = booleanPreferencesKey("shizuku_uninstall_all_users")

    // Root (libsu) install options — full flavor only, but the keys live here so common
    // code can read them unconditionally. On the store flavor these stay at their defaults.
    val ROOT_BYPASS_LOW_TARGET_SDK = booleanPreferencesKey("root_bypass_low_target_sdk")
    val ROOT_ALLOW_TEST = booleanPreferencesKey("root_allow_test")
    val ROOT_REPLACE_EXISTING = booleanPreferencesKey("root_replace_existing")
    val ROOT_REQUEST_DOWNGRADE = booleanPreferencesKey("root_request_downgrade")
    val ROOT_GRANT_ALL_PERMISSIONS = booleanPreferencesKey("root_grant_all_permissions")
    val ROOT_ALL_USERS = booleanPreferencesKey("root_all_users")
    val ROOT_SET_INSTALL_SOURCE = booleanPreferencesKey("root_set_install_source")
    val ROOT_INSTALLER_PACKAGE_NAME = stringPreferencesKey("root_installer_package_name")

    // Sync options
    val SYNC_REQUIRE_PIN = booleanPreferencesKey("sync_require_pin")
    val SYNC_PIN_CODE = stringPreferencesKey("sync_pin_code")
    val SYNC_SERVER_PORT = stringPreferencesKey("sync_server_port")

    // Biometric gate — independent toggles so users can guard install but not uninstall (or
    // vice versa) without one switch implying the other.
    val BIOMETRIC_LOCK_INSTALL = booleanPreferencesKey("biometric_lock_install")
    val BIOMETRIC_LOCK_UNINSTALL = booleanPreferencesKey("biometric_lock_uninstall")

    /**
     * Automatically start the installation when an APK is opened from an external intent
     * (e.g. from a file manager or Obtainium) without showing the confirmation dialog.
     */
    val AUTO_CONFIRM_EXTERNAL_INSTALL = booleanPreferencesKey("auto_confirm_external_install")

    /**
     * When true (default), external VIEW/SEND intents land in DialogInstallActivity instead
     * of the full InstallActivity — i.e. opening an APK from a file manager pops up a focused
     * dialog over the calling app rather than launching our full UI. Off → fall back to the
     * historical InstallActivity flow (full screen with bottom bar).
     */
    val DIALOG_INSTALL_MODE = booleanPreferencesKey("dialog_install_mode")

    // Manage screen filter-sheet state — persisted so the user's sort/group/filter survives
    // process death. Enums stored by `name` so renaming a constant breaks loudly rather
    // than silently mapping to ordinal 0.
    val MANAGE_SORT_BY = stringPreferencesKey("manage_sort_by")
    val MANAGE_SORT_DIRECTION = stringPreferencesKey("manage_sort_direction")
    val MANAGE_GROUP_BY = stringPreferencesKey("manage_group_by")
    val MANAGE_APP_FILTER = stringSetPreferencesKey("manage_app_filter")

    // APK Extractor options
    val APK_EXTRACTOR_OUTPUT_PATH = stringPreferencesKey("apk_extractor_output_path")
    val APK_EXTRACTOR_FILENAME_TEMPLATE = stringPreferencesKey("apk_extractor_filename_template")

    // Installer Profiles
    val INSTALLER_PROFILES = stringPreferencesKey("installer_profiles")
    val APP_PROFILE_MAPPING = stringPreferencesKey("app_profile_mapping")
}

data class SyncOptions(
    val requirePin: Boolean = true,
    val pinCode: String = "",
    val serverPort: String = "8080"
)

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
    val replaceExisting: Boolean = true,
    val requestDowngrade: Boolean = false,
    val grantAllPermissions: Boolean = false,
    val allUsers: Boolean = false,
    val setInstallSource: Boolean = false,
    val installerPackageName: String = DEFAULT_INSTALLER_PACKAGE_NAME,
    val uninstallKeepData: Boolean = false,
    val uninstallAllUsers: Boolean = false,
)

const val DEFAULT_INSTALLER_PACKAGE_NAME = "com.android.vending"

data class RootOptions(
    val bypassLowTargetSdk: Boolean = false,
    val allowTest: Boolean = false,
    val replaceExisting: Boolean = true,
    val requestDowngrade: Boolean = false,
    val grantAllPermissions: Boolean = false,
    val allUsers: Boolean = false,
    val setInstallSource: Boolean = false,
    val installerPackageName: String = DEFAULT_INSTALLER_PACKAGE_NAME,
)

data class SettingUiState(
    val themeMode: ThemeMode = ThemeMode.System,
    val dynamicColor: Boolean = true,
    val amoledMode: Boolean = false,
    val useShizuku: Boolean = false,
    val useRoot: Boolean = false,
    val virusTotalApiKey: String = "",
    val deleteApkAfterInstall: Boolean = false,
    val autoOpenAfterInstall: Boolean = false,
    val shizukuState: ShizukuState = ShizukuState.NOT_INSTALLED,
    val shizukuAvailable: Boolean = false,
    val shizukuOptions: ShizukuOptions = ShizukuOptions(),
    val rootSupported: Boolean = false,
    val rootState: RootState = RootState.UNAVAILABLE,
    val rootOptions: RootOptions = RootOptions(),
    val syncOptions: SyncOptions = SyncOptions(),
    val appVersion: String = "",
    val biometricLockInstall: Boolean = false,
    val biometricLockUninstall: Boolean = false,
    val dialogInstallMode: Boolean = true,
    val autoConfirmExternalInstall: Boolean = false,
    val extractorOutputPath: String = "",
    val extractorFilenameTemplate: String = "{name}-{version}",
    val installerProfiles: List<InstallerProfile> = emptyList(),
    val appProfileMapping: Map<String, String> = emptyMap(),
    val isDefaultInstaller: Boolean = false,
    val selectedLanguage: String = "",
    /**
     * True when the device has at least one biometric or device-credential enrolled.
     * Used to greyly inform the user that the toggles will be no-ops until they
     * enrol a fingerprint or set a screen lock. Computed at VM init from
     * BiometricManager so we don't keep a live device-state dependency.
     */
    val biometricEnrolmentAvailable: Boolean = false,
)

class SettingViewModel(
    private val application: Application,
    private val backendFactory: InstallerBackendFactory,
) : ViewModel() {

    private val dataStore = application.dataStore

    private val _shizukuState = MutableStateFlow(ShizukuState.NOT_INSTALLED)
    private val _rootState = MutableStateFlow(
        if (backendFactory.rootSupportCompiledIn) RootState.UNKNOWN else RootState.UNAVAILABLE,
    )

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        Timber.d("Shizuku binder received")
        updateShizukuState()
    }

    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        Timber.d("Shizuku binder dead")
        updateShizukuState()
    }

    init {
        updateShizukuState()
        Shizuku.addBinderReceivedListener(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        // Cheap non-blocking root probe — does not trigger a SuperUser prompt.
        if (backendFactory.rootSupportCompiledIn) {
            viewModelScope.launch {
                _rootState.value = backendFactory.probeRootState()
            }
        }
        updateDefaultInstallerStatus()
    }

    override fun onCleared() {
        super.onCleared()
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.THEME_MODE] = mode.name }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.DYNAMIC_COLOR] = enabled }
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.AMOLED_MODE] = enabled }
        }
    }

    fun setUseShizuku(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.USE_SHIZUKU] = enabled }
        }
    }

    fun setUseRoot(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.USE_ROOT] = enabled }
        }
    }

    fun retryRoot() {
        viewModelScope.launch {
            _rootState.value = RootState.UNKNOWN
            // Backend factory re-checks su availability internally
        }
    }

    fun setVirusTotalApiKey(key: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.VIRUSTOTAL_API_KEY] = key }
        }
    }

    fun setDeleteApkAfterInstall(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.DELETE_APK_AFTER_INSTALL] = enabled }
        }
    }

    fun setAutoOpenAfterInstall(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.AUTO_OPEN_AFTER_INSTALL] = enabled }
        }
    }

    fun setBiometricLockInstall(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.BIOMETRIC_LOCK_INSTALL] = enabled }
        }
    }

    fun setBiometricLockUninstall(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.BIOMETRIC_LOCK_UNINSTALL] = enabled }
        }
    }

    fun setDialogInstallMode(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.DIALOG_INSTALL_MODE] = enabled }
        }
    }

    fun setAutoConfirmExternalInstall(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.AUTO_CONFIRM_EXTERNAL_INSTALL] = enabled }
        }
    }

    fun setShizukuOption(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    fun setShizukuInstallerPackageName(name: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME] = name }
        }
    }

    fun setRootOption(key: Preferences.Key<Boolean>, value: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    fun setRootInstallerPackageName(name: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.ROOT_INSTALLER_PACKAGE_NAME] = name }
        }
    }

    fun setSyncRequirePin(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.SYNC_REQUIRE_PIN] = enabled }
        }
    }

    fun setSyncPinCode(code: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.SYNC_PIN_CODE] = code }
        }
    }

    fun setSyncServerPort(port: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.SYNC_SERVER_PORT] = port }
        }
    }

    fun setExtractorOutputPath(path: String) {
        viewModelScope.launch {
            if (path.startsWith("content://")) {
                runCatching {
                    application.contentResolver.takePersistableUriPermission(
                        android.net.Uri.parse(path),
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
            dataStore.edit { prefs -> prefs[PreferencesKeys.APK_EXTRACTOR_OUTPUT_PATH] = path }
        }
    }

    fun setExtractorFilenameTemplate(template: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.APK_EXTRACTOR_FILENAME_TEMPLATE] = template }
        }
    }

    fun saveProfile(profile: InstallerProfile) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val current = ProfileManager.parseProfiles(prefs[PreferencesKeys.INSTALLER_PROFILES])
                val index = current.indexOfFirst { it.id == profile.id }
                val updated = if (index != -1) {
                    current.toMutableList().apply { set(index, profile) }
                } else {
                    current + profile
                }
                prefs[PreferencesKeys.INSTALLER_PROFILES] = ProfileManager.serializeProfiles(updated)
            }
        }
    }

    fun deleteProfile(profileId: String) {
        viewModelScope.launch {
            dataStore.edit { prefs ->
                val current = ProfileManager.parseProfiles(prefs[PreferencesKeys.INSTALLER_PROFILES])
                val updated = current.filterNot { it.id == profileId }
                prefs[PreferencesKeys.INSTALLER_PROFILES] = ProfileManager.serializeProfiles(updated)

                // Cleanup mappings
                val currentMapping = ProfileManager.parseMapping(prefs[PreferencesKeys.APP_PROFILE_MAPPING])
                val updatedMapping = currentMapping.filterValues { it != profileId }
                prefs[PreferencesKeys.APP_PROFILE_MAPPING] = ProfileManager.serializeMapping(updatedMapping)
            }
        }
    }

    private val _isDefaultInstaller = MutableStateFlow(false)

    /**
     * Toggles Universal Installer as the default system installer.
     * Requires Root or Shizuku (ADB).
     */
    fun toggleDefaultInstaller(enabled: Boolean) {
        viewModelScope.launch(Dispatchers.IO) {
            val systemInstallers = getSystemInstallerPackages()
            if (systemInstallers.isEmpty()) {
                Timber.w("No system installer found to toggle")
                return@launch
            }

            try {
                val newState = if (enabled) {
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                } else {
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
                }

                if (_shizukuState.value == ShizukuState.READY) {
                    systemInstallers.forEach { pkg ->
                        app.pwhs.universalinstaller.util.HiddenApiHacks.setApplicationEnabledSetting(pkg, newState, 0)
                    }
                } else if (_rootState.value == RootState.READY) {
                    systemInstallers.forEach { pkg ->
                        backendFactory.setSystemAppEnabled(pkg, enabled)
                    }
                }
                updateDefaultInstallerStatus()
            } catch (e: Exception) {
                Timber.e(e, "Failed to toggle default installer")
            }
        }
    }

    private fun updateDefaultInstallerStatus() {
        viewModelScope.launch(Dispatchers.IO) {
            val systemInstallers = getSystemInstallerPackages()
            if (systemInstallers.isEmpty()) {
                _isDefaultInstaller.value = false
                return@launch
            }
            val allDisabled = systemInstallers.all { pkg ->
                try {
                    val info = application.packageManager.getApplicationInfo(pkg, 0)
                    !info.enabled
                } catch (e: Exception) {
                    // If we can't find it, assume it's not a concern
                    true
                }
            }
            _isDefaultInstaller.value = allDisabled
        }
    }

    private fun getSystemInstallerPackages(): List<String> {
        val intent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
            val file = java.io.File(application.cacheDir, "test.apk")
            setDataAndType(android.net.Uri.fromFile(file), "application/vnd.android.package-archive")
        }
        val resolveInfos = application.packageManager.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return resolveInfos.map { it.activityInfo.packageName }
            .filter { it != application.packageName && isSystemApp(it) }
    }

    private fun isSystemApp(packageName: String): Boolean {
        return try {
            val info = application.packageManager.getApplicationInfo(packageName, 0)
            (info.flags and android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0
        } catch (e: Exception) {
            false
        }
    }

    private fun updateShizukuState() {
        _shizukuState.value = when {
            !Shizuku.pingBinder() -> ShizukuState.NOT_RUNNING
            Shizuku.getVersion() < 11 -> ShizukuState.UNSUPPORTED
            Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED -> ShizukuState.NO_PERMISSION
            else -> ShizukuState.READY
        }
    }

    fun setLanguage(tag: String) {
        app.pwhs.universalinstaller.util.LocaleHelper.setAppLanguage(application, tag)
        _selectedLanguage.value = tag
    }

    private val _selectedLanguage = MutableStateFlow(app.pwhs.universalinstaller.util.LocaleHelper.getStoredLanguage(application))

    val uiState: StateFlow<SettingUiState> = combine(
        dataStore.data.map { prefs ->
            val name = prefs[PreferencesKeys.THEME_MODE] ?: ThemeMode.System.name
            ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
        },
        dataStore.data.map { it[PreferencesKeys.DYNAMIC_COLOR] ?: true },
        dataStore.data.map { it[PreferencesKeys.AMOLED_MODE] ?: false },
        dataStore.data.map { it[PreferencesKeys.USE_SHIZUKU] ?: false },
        dataStore.data.map { it[PreferencesKeys.VIRUSTOTAL_API_KEY] ?: "" },
        _shizukuState,
        dataStore.data.map { prefs ->
            ShizukuOptions(
                bypassLowTargetSdk = prefs[PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK] ?: false,
                allowTest = prefs[PreferencesKeys.SHIZUKU_ALLOW_TEST] ?: false,
                replaceExisting = prefs[PreferencesKeys.SHIZUKU_REPLACE_EXISTING] ?: true,
                requestDowngrade = prefs[PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE] ?: false,
                grantAllPermissions = prefs[PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS] ?: false,
                allUsers = prefs[PreferencesKeys.SHIZUKU_ALL_USERS] ?: false,
                setInstallSource = prefs[PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE] ?: false,
                installerPackageName = prefs[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME]
                    ?: DEFAULT_INSTALLER_PACKAGE_NAME,
                uninstallKeepData = prefs[PreferencesKeys.SHIZUKU_UNINSTALL_KEEP_DATA] ?: false,
                uninstallAllUsers = prefs[PreferencesKeys.SHIZUKU_UNINSTALL_ALL_USERS] ?: false,
            )
        },
        dataStore.data.map { it[PreferencesKeys.DELETE_APK_AFTER_INSTALL] ?: false },
        dataStore.data.map { it[PreferencesKeys.USE_ROOT] ?: false },
        _rootState,
        dataStore.data.map { prefs ->
            RootOptions(
                bypassLowTargetSdk = prefs[PreferencesKeys.ROOT_BYPASS_LOW_TARGET_SDK] ?: false,
                allowTest = prefs[PreferencesKeys.ROOT_ALLOW_TEST] ?: false,
                replaceExisting = prefs[PreferencesKeys.ROOT_REPLACE_EXISTING] ?: true,
                requestDowngrade = prefs[PreferencesKeys.ROOT_REQUEST_DOWNGRADE] ?: false,
                grantAllPermissions = prefs[PreferencesKeys.ROOT_GRANT_ALL_PERMISSIONS] ?: false,
                allUsers = prefs[PreferencesKeys.ROOT_ALL_USERS] ?: false,
                setInstallSource = prefs[PreferencesKeys.ROOT_SET_INSTALL_SOURCE] ?: false,
                installerPackageName = prefs[PreferencesKeys.ROOT_INSTALLER_PACKAGE_NAME]
                    ?: DEFAULT_INSTALLER_PACKAGE_NAME,
            )
        },
        dataStore.data.map { prefs ->
            SyncOptions(
                requirePin = prefs[PreferencesKeys.SYNC_REQUIRE_PIN] ?: true,
                pinCode = prefs[PreferencesKeys.SYNC_PIN_CODE] ?: "",
                serverPort = prefs[PreferencesKeys.SYNC_SERVER_PORT] ?: "8080"
            )
        },
        dataStore.data.map { prefs ->
            // Both biometric toggles share one Flow so we don't blow past combine()'s
            // vararg comfort zone — Pair carries them through to the SettingUiState build.
            (prefs[PreferencesKeys.BIOMETRIC_LOCK_INSTALL] ?: false) to
                    (prefs[PreferencesKeys.BIOMETRIC_LOCK_UNINSTALL] ?: false)
        },
        dataStore.data.map { prefs ->
            // Trio: dialog-mode, auto-open-after-install, and auto-confirm-external-install
            Triple(
                prefs[PreferencesKeys.DIALOG_INSTALL_MODE] ?: true,
                prefs[PreferencesKeys.AUTO_OPEN_AFTER_INSTALL] ?: false,
                prefs[PreferencesKeys.AUTO_CONFIRM_EXTERNAL_INSTALL] ?: false
            )
        },
        dataStore.data.map { prefs ->
            // Actually, let's just use a data class or a List to group them.
            listOf(
                prefs[PreferencesKeys.APK_EXTRACTOR_OUTPUT_PATH] ?: "",
                prefs[PreferencesKeys.APK_EXTRACTOR_FILENAME_TEMPLATE] ?: "{name}-{version}",
                prefs[PreferencesKeys.INSTALLER_PROFILES] ?: "",
                prefs[PreferencesKeys.APP_PROFILE_MAPPING] ?: ""
            )
        },
        _selectedLanguage,
        _isDefaultInstaller,
    ) { flows ->
        val theme = flows[0] as ThemeMode
        val dynamicColor = flows[1] as Boolean
        val amoledMode = flows[2] as Boolean
        val useShizuku = flows[3] as Boolean
        val vtKey = flows[4] as String
        val shizukuState = flows[5] as ShizukuState
        val shizukuOpts = flows[6] as ShizukuOptions
        val deleteApk = flows[7] as Boolean
        val useRoot = flows[8] as Boolean
        val rootState = flows[9] as RootState
        val rootOpts = flows[10] as RootOptions
        val syncOpts = flows[11] as SyncOptions
        @Suppress("UNCHECKED_CAST")
        val biometricFlags = flows[12] as Pair<Boolean, Boolean>
        @Suppress("UNCHECKED_CAST")
        val tripleFlags = flows[13] as Triple<Boolean, Boolean, Boolean>
        val dialogMode = tripleFlags.first
        val autoOpen = tripleFlags.second
        val autoConfirm = tripleFlags.third
        @Suppress("UNCHECKED_CAST")
        val extractorAndProfiles = flows[14] as List<String>
        val extractorPath = extractorAndProfiles[0]
        val extractorTemplate = extractorAndProfiles[1]
        val profilesJson = extractorAndProfiles[2]
        val mappingJson = extractorAndProfiles[3]
        val selectedLang = flows[15] as String
        val isDefault = flows[16] as Boolean

        val versionName = try {
            application.packageManager
                .getPackageInfo(application.packageName, 0)
                .versionName ?: ""
        } catch (_: Exception) {
            ""
        }
        SettingUiState(
            themeMode = theme,
            dynamicColor = dynamicColor,
            amoledMode = amoledMode,
            useShizuku = useShizuku && shizukuState == ShizukuState.READY,
            useRoot = useRoot && (rootState == RootState.READY || rootState == RootState.UNKNOWN),
            virusTotalApiKey = vtKey,
            deleteApkAfterInstall = deleteApk,
            autoOpenAfterInstall = autoOpen,
            shizukuState = shizukuState,
            // "Available" means the binder is alive — we can talk to Shizuku/Sui now.
            shizukuAvailable = shizukuState == ShizukuState.READY ||
                    shizukuState == ShizukuState.NO_PERMISSION,
            shizukuOptions = shizukuOpts,
            rootSupported = backendFactory.rootSupportCompiledIn,
            rootState = rootState,
            rootOptions = rootOpts,
            syncOptions = syncOpts,
            appVersion = versionName,
            biometricLockInstall = biometricFlags.first,
            biometricLockUninstall = biometricFlags.second,
            biometricEnrolmentAvailable = app.pwhs.universalinstaller.util
                .BiometricGate.canAuthenticate(application),
            dialogInstallMode = dialogMode,
            autoConfirmExternalInstall = autoConfirm,
            extractorOutputPath = extractorPath,
            extractorFilenameTemplate = extractorTemplate,
            installerProfiles = ProfileManager.parseProfiles(profilesJson),
            appProfileMapping = ProfileManager.parseMapping(mappingJson),
            selectedLanguage = selectedLang,
            isDefaultInstaller = isDefault,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingUiState(),
    )
}
