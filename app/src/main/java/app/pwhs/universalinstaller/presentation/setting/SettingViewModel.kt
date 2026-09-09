package app.pwhs.universalinstaller.presentation.setting

import android.app.Application
import androidx.datastore.preferences.core.Preferences
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.universalinstaller.domain.model.ExternalOpenMode
import app.pwhs.universalinstaller.domain.model.InstallUiStyle
import app.pwhs.universalinstaller.domain.model.InstallerProfile
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.setting.util.SettingPreferencesDelegate
import app.pwhs.universalinstaller.presentation.setting.util.SettingPrivilegeDelegate
import app.pwhs.universalinstaller.presentation.setting.util.SettingProfilesDelegate
import app.pwhs.universalinstaller.presentation.setting.util.SettingUiStateBuilder
import app.pwhs.universalinstaller.util.CustomShellExecutor
import app.pwhs.universalinstaller.util.DhizukuState
import app.pwhs.universalinstaller.util.LocaleHelper
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingViewModel(
    private val application: Application,
    backendFactory: InstallerBackendFactory,
) : ViewModel() {

    enum class PrivilegedOption(
        internal val shizukuKey: Preferences.Key<Boolean>,
        internal val rootKey: Preferences.Key<Boolean>,
        /** Dhizuku only supports downgrade; the rest have no equivalent there. */
        internal val dhizukuKey: Preferences.Key<Boolean>? = null,
    ) {
        ReplaceExisting(
            PreferencesKeys.SHIZUKU_REPLACE_EXISTING,
            PreferencesKeys.ROOT_REPLACE_EXISTING,
        ),
        RequestDowngrade(
            PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE,
            PreferencesKeys.ROOT_REQUEST_DOWNGRADE,
            PreferencesKeys.DHIZUKU_REQUEST_DOWNGRADE,
        ),
        GrantAllPermissions(
            PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS,
            PreferencesKeys.ROOT_GRANT_ALL_PERMISSIONS,
        ),
        AllowTest(
            PreferencesKeys.SHIZUKU_ALLOW_TEST,
            PreferencesKeys.ROOT_ALLOW_TEST,
        ),
        BypassLowTargetSdk(
            PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK,
            PreferencesKeys.ROOT_BYPASS_LOW_TARGET_SDK,
        ),
        AllUsers(
            PreferencesKeys.SHIZUKU_ALL_USERS,
            PreferencesKeys.ROOT_ALL_USERS,
        ),
        AllowRestrictedPermissions(
            PreferencesKeys.SHIZUKU_ALLOW_RESTRICTED_PERMISSIONS,
            PreferencesKeys.ROOT_ALLOW_RESTRICTED_PERMISSIONS,
        ),
        DontKillApp(
            PreferencesKeys.SHIZUKU_DONT_KILL_APP,
            PreferencesKeys.ROOT_DONT_KILL_APP,
        ),
        DisableVerification(
            PreferencesKeys.SHIZUKU_DISABLE_VERIFICATION,
            PreferencesKeys.ROOT_DISABLE_VERIFICATION,
        ),
        EnableRollback(
            PreferencesKeys.SHIZUKU_ENABLE_ROLLBACK,
            PreferencesKeys.ROOT_ENABLE_ROLLBACK,
        ),
        RequestUpdateOwnership(
            PreferencesKeys.SHIZUKU_REQUEST_UPDATE_OWNERSHIP,
            PreferencesKeys.ROOT_REQUEST_UPDATE_OWNERSHIP,
        ),
        SetInstallSource(
            PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE,
            PreferencesKeys.ROOT_SET_INSTALL_SOURCE,
        ),
        Dex2oatOptimization(
            PreferencesKeys.DEX2OAT_OPTIMIZATION,
            PreferencesKeys.DEX2OAT_OPTIMIZATION,
        ),
    }

    private val dataStore = application.dataStore

    private val _events = Channel<Int>(Channel.BUFFERED)
    val events: Flow<Int> = _events.receiveAsFlow()

    private fun emitEvent(stringRes: Int) {
        viewModelScope.launch {
            _events.send(stringRes)
        }
    }

    private val privilegeDelegate = SettingPrivilegeDelegate(
        application = application,
        scope = viewModelScope,
        backendFactory = backendFactory,
        emitEvent = { emitEvent(it) },
    )

    private val preferencesDelegate = SettingPreferencesDelegate(
        application = application,
        scope = viewModelScope,
        emitEvent = { emitEvent(it) },
    )

    private val profilesDelegate = SettingProfilesDelegate(
        application = application,
        scope = viewModelScope,
    )

    val dhizukuState: StateFlow<DhizukuState> = privilegeDelegate.dhizukuState
    val useDhizuku: StateFlow<Boolean> = privilegeDelegate.useDhizuku
    val securityLevel: StateFlow<SecurityLevel> = preferencesDelegate.securityLevel
    val externalOpenMode: StateFlow<ExternalOpenMode> = preferencesDelegate.externalOpenMode
    val installUiStyle: StateFlow<InstallUiStyle> = preferencesDelegate.installUiStyle
    val blacklist: StateFlow<List<String>> = preferencesDelegate.blacklist
    val autoApproveEnabled: StateFlow<Boolean> = preferencesDelegate.autoApproveEnabled
    val autoApprovePackages: StateFlow<Set<String>> = preferencesDelegate.autoApprovePackages
    val analyticsEnabled: StateFlow<Boolean> = preferencesDelegate.analyticsEnabled

    private val _selectedLanguage = MutableStateFlow(LocaleHelper.getStoredLanguage(application))

    val uiState: StateFlow<SettingUiState> = combine(
        dataStore.data.map { prefs ->
            val modeName = prefs[PreferencesKeys.THEME_MODE] ?: ThemeMode.System.name
            val mode = ThemeMode.entries.find { it.name == modeName } ?: ThemeMode.System
            val dynamicColor = prefs[PreferencesKeys.DYNAMIC_COLOR] ?: false
            val amoledMode = prefs[PreferencesKeys.AMOLED_MODE] ?: false
            val presetName = prefs[PreferencesKeys.THEME_PRESET] ?: AppThemePreset.Orange.name
            val preset = AppThemePreset.entries.find { it.name == presetName } ?: AppThemePreset.Orange
            SettingThemeState(mode, dynamicColor, amoledMode, preset)
        },
        dataStore.data.map { it[PreferencesKeys.USE_SHIZUKU] ?: false },
        dataStore.data.map { it[PreferencesKeys.VIRUSTOTAL_API_KEY] ?: "" },
        privilegeDelegate.shizukuState,
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
                allowRestrictedPermissions = prefs[PreferencesKeys.SHIZUKU_ALLOW_RESTRICTED_PERMISSIONS] ?: false,
                dontKillApp = prefs[PreferencesKeys.SHIZUKU_DONT_KILL_APP] ?: false,
                disableVerification = prefs[PreferencesKeys.SHIZUKU_DISABLE_VERIFICATION] ?: false,
                enableRollback = prefs[PreferencesKeys.SHIZUKU_ENABLE_ROLLBACK] ?: false,
                requestUpdateOwnership = prefs[PreferencesKeys.SHIZUKU_REQUEST_UPDATE_OWNERSHIP] ?: false,
                uninstallKeepData = prefs[PreferencesKeys.SHIZUKU_UNINSTALL_KEEP_DATA] ?: false,
                uninstallAllUsers = prefs[PreferencesKeys.SHIZUKU_UNINSTALL_ALL_USERS] ?: false,
                dex2oatOptimization = prefs[PreferencesKeys.DEX2OAT_OPTIMIZATION] ?: false,
            )
        },
        dataStore.data.map { it[PreferencesKeys.DELETE_APK_AFTER_INSTALL] ?: false },
        dataStore.data.map { it[PreferencesKeys.USE_ROOT] ?: false },
        privilegeDelegate.rootState,
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
                allowRestrictedPermissions = prefs[PreferencesKeys.ROOT_ALLOW_RESTRICTED_PERMISSIONS] ?: false,
                dontKillApp = prefs[PreferencesKeys.ROOT_DONT_KILL_APP] ?: false,
                disableVerification = prefs[PreferencesKeys.ROOT_DISABLE_VERIFICATION] ?: false,
                enableRollback = prefs[PreferencesKeys.ROOT_ENABLE_ROLLBACK] ?: false,
                requestUpdateOwnership = prefs[PreferencesKeys.ROOT_REQUEST_UPDATE_OWNERSHIP] ?: false,
                dex2oatOptimization = prefs[PreferencesKeys.DEX2OAT_OPTIMIZATION] ?: false,
            )
        },
        dataStore.data.map { prefs ->
            SyncOptions(
                requirePin = prefs[PreferencesKeys.SYNC_REQUIRE_PIN] ?: true,
                pinCode = prefs[PreferencesKeys.SYNC_PIN_CODE] ?: "",
                serverPort = prefs[PreferencesKeys.SYNC_SERVER_PORT] ?: "8080",
            )
        },
        dataStore.data.map { prefs ->
            (prefs[PreferencesKeys.BIOMETRIC_LOCK_INSTALL] ?: false) to
                (prefs[PreferencesKeys.BIOMETRIC_LOCK_UNINSTALL] ?: false)
        },
        dataStore.data.map { prefs ->
            listOf(
                prefs[PreferencesKeys.DIALOG_INSTALL_MODE] ?: true,
                prefs[PreferencesKeys.AUTO_OPEN_AFTER_INSTALL] ?: false,
                prefs[PreferencesKeys.AUTO_CONFIRM_EXTERNAL_INSTALL] ?: false,
                prefs[PreferencesKeys.SHOW_DOWNLOAD_TAB] ?: true,
                prefs[PreferencesKeys.STRICT_VIRUSTOTAL_CHECK] ?: false,
                prefs[PreferencesKeys.AUTO_APPROVE_CALLER_APPS] ?: false,
            )
        },
        dataStore.data.map { prefs ->
            listOf(
                prefs[PreferencesKeys.APK_EXTRACTOR_OUTPUT_PATH] ?: "",
                prefs[PreferencesKeys.APK_EXTRACTOR_FILENAME_TEMPLATE] ?: "{name}-{version}",
                prefs[PreferencesKeys.INSTALLER_PROFILES] ?: "",
                prefs[PreferencesKeys.APP_PROFILE_MAPPING] ?: "",
                (prefs[PreferencesKeys.AUTO_APPROVE_PACKAGES]?.size ?: 0).toString(),
            )
        },
        _selectedLanguage,
        privilegeDelegate.isDefaultInstaller,
        privilegeDelegate.useCustomAuthorizer,
        privilegeDelegate.customAuthorizerCommand,
        privilegeDelegate.useMicroG,
    ) { flows ->
        SettingUiStateBuilder.build(application, backendFactory, flows)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SettingUiState(),
    )

    override fun onCleared() {
        super.onCleared()
        privilegeDelegate.cleanUp()
    }

    // ── Theme Delegates ─────────────────────────────────────────────────────

    fun setThemeMode(mode: ThemeMode) = preferencesDelegate.setThemeMode(mode)
    fun setDynamicColor(enabled: Boolean) = preferencesDelegate.setDynamicColor(enabled)
    fun setAmoledMode(enabled: Boolean) = preferencesDelegate.setAmoledMode(enabled)
    fun setThemePreset(preset: AppThemePreset) = preferencesDelegate.setThemePreset(preset)

    // ── Privilege Delegates ─────────────────────────────────────────────────

    fun setInstallMode(mode: InstallMode) = privilegeDelegate.setInstallMode(mode)
    fun setUseShizuku(enabled: Boolean) = privilegeDelegate.setUseShizuku(enabled)
    fun setUseRoot(enabled: Boolean) = privilegeDelegate.setUseRoot(enabled)
    fun retryRoot() = privilegeDelegate.retryRoot()
    fun setUseDhizuku(enabled: Boolean) = privilegeDelegate.setUseDhizuku(enabled)
    fun refreshDhizukuState() = privilegeDelegate.refreshDhizukuState()
    fun setPrivilegedOption(option: PrivilegedOption, enabled: Boolean) = privilegeDelegate.setPrivilegedOption(option, enabled)
    fun setInstallerPackageName(packageName: String) = privilegeDelegate.setInstallerPackageName(packageName)
    fun toggleDefaultInstaller(enabled: Boolean) = privilegeDelegate.toggleDefaultInstaller(enabled)
    val useCustomAuthorizer: StateFlow<Boolean> = privilegeDelegate.useCustomAuthorizer
    val customAuthorizerCommand: StateFlow<String> = privilegeDelegate.customAuthorizerCommand
    fun setCustomAuthorizerCommand(command: String) = privilegeDelegate.setCustomAuthorizerCommand(command)
    suspend fun testCustomAuthorizerCommand(command: String): Result<String> =
        CustomShellExecutor.testCommand(command)

    // ── Preferences Delegates ───────────────────────────────────────────────

    fun setInstallUiStyle(style: InstallUiStyle) = preferencesDelegate.setInstallUiStyle(style)
    fun setExternalOpenMode(mode: ExternalOpenMode) = preferencesDelegate.setExternalOpenMode(mode)
    fun setSecurityLevel(level: SecurityLevel) = preferencesDelegate.setSecurityLevel(level)
    fun addToBlacklist(packageName: String) = preferencesDelegate.addToBlacklist(packageName)
    fun removeFromBlacklist(packageName: String) = preferencesDelegate.removeFromBlacklist(packageName)
    fun setVirusTotalApiKey(key: String) = preferencesDelegate.setVirusTotalApiKey(key)
    fun setStrictVirusTotalCheck(enabled: Boolean) = preferencesDelegate.setStrictVirusTotalCheck(enabled)
    fun setDeleteApkAfterInstall(enabled: Boolean) = preferencesDelegate.setDeleteApkAfterInstall(enabled)
    fun setAutoOpenAfterInstall(enabled: Boolean) = preferencesDelegate.setAutoOpenAfterInstall(enabled)
    fun setBiometricLockInstall(enabled: Boolean) = preferencesDelegate.setBiometricLockInstall(enabled)
    fun setBiometricLockUninstall(enabled: Boolean) = preferencesDelegate.setBiometricLockUninstall(enabled)
    fun setDialogInstallMode(enabled: Boolean) = preferencesDelegate.setDialogInstallMode(enabled)
    fun setAutoConfirmExternalInstall(enabled: Boolean) = preferencesDelegate.setAutoConfirmExternalInstall(enabled)
    fun setAutoApproveEnabled(enabled: Boolean) = preferencesDelegate.setAutoApproveEnabled(enabled)
    fun toggleAutoApprovePackage(packageName: String, approved: Boolean) =
        preferencesDelegate.toggleAutoApprovePackage(packageName, approved)
    fun setAutoApprovePackages(packages: Set<String>) = preferencesDelegate.setAutoApprovePackages(packages)
    fun setShowDownloadTab(enabled: Boolean) = preferencesDelegate.setShowDownloadTab(enabled)
    fun setShizukuOption(key: Preferences.Key<Boolean>, value: Boolean) = preferencesDelegate.setShizukuOption(key, value)
    fun setShizukuInstallerPackageName(name: String) = preferencesDelegate.setShizukuInstallerPackageName(name)
    fun setRootOption(key: Preferences.Key<Boolean>, value: Boolean) = preferencesDelegate.setRootOption(key, value)
    fun setRootInstallerPackageName(name: String) = preferencesDelegate.setRootInstallerPackageName(name)
    fun setSyncRequirePin(enabled: Boolean) = preferencesDelegate.setSyncRequirePin(enabled)
    fun setSyncPinCode(code: String) = preferencesDelegate.setSyncPinCode(code)
    fun setSyncServerPort(port: String) = preferencesDelegate.setSyncServerPort(port)
    fun setExtractorOutputPath(path: String) = preferencesDelegate.setExtractorOutputPath(path)
    fun setExtractorFilenameTemplate(template: String) = preferencesDelegate.setExtractorFilenameTemplate(template)
    fun setAnalyticsEnabled(enabled: Boolean) = preferencesDelegate.setAnalyticsEnabled(enabled)
    val githubPatToken: StateFlow<String> = preferencesDelegate.githubPatToken
    fun setGithubPatToken(token: String) = preferencesDelegate.setGithubPatToken(token)

    // ── Profiles Delegates ──────────────────────────────────────────────────

    fun saveProfile(profile: InstallerProfile, onSaved: () -> Unit = {}) = profilesDelegate.saveProfile(profile, onSaved)
    fun deleteProfile(profileId: String) = profilesDelegate.deleteProfile(profileId)

    // ── Language ────────────────────────────────────────────────────────────

    fun setLanguage(tag: String) {
        LocaleHelper.setAppLanguage(application, tag)
        _selectedLanguage.value = tag
    }
}
