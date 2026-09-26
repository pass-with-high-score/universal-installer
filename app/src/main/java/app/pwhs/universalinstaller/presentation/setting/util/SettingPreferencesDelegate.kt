package app.pwhs.universalinstaller.presentation.setting.util

import android.app.Application
import android.net.Uri
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.manager.AutoApproveApps
import app.pwhs.universalinstaller.domain.manager.InstallBlacklist
import app.pwhs.universalinstaller.domain.model.ExternalOpenMode
import app.pwhs.universalinstaller.domain.model.InstallUiStyle
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.SecurityLevel
import app.pwhs.universalinstaller.telemetry.Telemetry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingPreferencesDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
    private val emitEvent: (Int) -> Unit,
) {
    private val dataStore = application.dataStore

    val securityLevel: StateFlow<SecurityLevel> = dataStore.data
        .map {
            SecurityLevel.from(
                stored = it[PreferencesKeys.SECURITY_LEVEL],
                legacyStrict = it[PreferencesKeys.STRICT_VIRUSTOTAL_CHECK] ?: false,
            )
        }
        .stateIn(scope, SharingStarted.Eagerly, SecurityLevel.Normal)

    val externalOpenMode: StateFlow<ExternalOpenMode> = dataStore.data
        .map { ExternalOpenMode.from(it[PreferencesKeys.EXTERNAL_OPEN_MODE]) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), ExternalOpenMode.Dialog)

    val installUiStyle: StateFlow<InstallUiStyle> = dataStore.data
        .map { InstallUiStyle.from(it[PreferencesKeys.INSTALL_UI_STYLE]) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), InstallUiStyle.Dialog)

    val blacklist: StateFlow<List<String>> = dataStore.data
        .map { InstallBlacklist.read(it).sorted() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    val autoApproveEnabled: StateFlow<Boolean> = dataStore.data
        .map { AutoApproveApps.isEnabled(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), false)

    val autoApprovePackages: StateFlow<Set<String>> = dataStore.data
        .map { AutoApproveApps.read(it) }
        .stateIn(scope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val analyticsEnabled: StateFlow<Boolean> = dataStore.data
        .map { prefs -> prefs[SharedPrefsKeys.ANALYTICS_ENABLED] ?: true }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), true)

    val githubPatToken: StateFlow<String> = dataStore.data
        .map { it[SharedPrefsKeys.GITHUB_PAT_TOKEN].orEmpty() }
        .stateIn(scope, SharingStarted.WhileSubscribed(5000), "")

    fun setGithubPatToken(token: String) {
        scope.launch {
            dataStore.edit { prefs -> prefs[SharedPrefsKeys.GITHUB_PAT_TOKEN] = token }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.THEME_MODE] = mode.name }
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.DYNAMIC_COLOR] = enabled }
        }
    }

    fun setAmoledMode(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.AMOLED_MODE] = enabled }
        }
    }

    fun setThemePreset(preset: AppThemePreset) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.THEME_PRESET] = preset.name }
        }
    }

    fun setInstallUiStyle(style: InstallUiStyle) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.INSTALL_UI_STYLE] = style.value }
        }
    }

    fun setExternalOpenMode(mode: ExternalOpenMode) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.EXTERNAL_OPEN_MODE] = mode.value }
        }
    }

    fun setSecurityLevel(level: SecurityLevel) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.SECURITY_LEVEL] = level.name
                prefs[PreferencesKeys.STRICT_VIRUSTOTAL_CHECK] = level == SecurityLevel.Strict
            }
        }
    }

    fun addToBlacklist(packageName: String) {
        val trimmed = packageName.trim()
        if (trimmed.isBlank()) return
        scope.launch {
            dataStore.edit { p ->
                p[InstallBlacklist.KEY] = InstallBlacklist.add(InstallBlacklist.read(p), trimmed)
            }
        }
    }

    fun removeFromBlacklist(packageName: String) {
        scope.launch {
            dataStore.edit { p ->
                p[InstallBlacklist.KEY] = InstallBlacklist.remove(InstallBlacklist.read(p), packageName)
            }
        }
    }

    fun setVirusTotalApiKey(key: String) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.VIRUSTOTAL_API_KEY] = key }
        }
    }

    fun setStrictVirusTotalCheck(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.STRICT_VIRUSTOTAL_CHECK] = enabled }
        }
    }

    fun setDeleteApkAfterInstall(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.DELETE_APK_AFTER_INSTALL] = enabled }
        }
    }

    fun setAutoOpenAfterInstall(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.AUTO_OPEN_AFTER_INSTALL] = enabled }
        }
    }

    fun setBiometricLockInstall(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.BIOMETRIC_LOCK_INSTALL] = enabled }
            emitEvent(
                if (enabled) R.string.setting_biometric_install_enabled
                else R.string.setting_biometric_install_disabled,
            )
        }
    }

    fun setBiometricLockUninstall(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.BIOMETRIC_LOCK_UNINSTALL] = enabled }
            emitEvent(
                if (enabled) R.string.setting_biometric_uninstall_enabled
                else R.string.setting_biometric_uninstall_disabled,
            )
        }
    }

    fun setDialogInstallMode(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.DIALOG_INSTALL_MODE] = enabled }
        }
    }

    fun setAutoConfirmExternalInstall(enabled: Boolean) {
        scope.launch {
            dataStore.edit { it[PreferencesKeys.AUTO_CONFIRM_EXTERNAL_INSTALL] = enabled }
        }
    }

    fun setAutoApproveEnabled(enabled: Boolean) {
        scope.launch {
            dataStore.edit { it[PreferencesKeys.AUTO_APPROVE_CALLER_APPS] = enabled }
        }
    }

    fun setAutoApproveBlockTrackers(enabled: Boolean) {
        scope.launch {
            dataStore.edit { it[PreferencesKeys.AUTO_APPROVE_BLOCK_TRACKERS] = enabled }
        }
    }

    fun toggleAutoApprovePackage(packageName: String, approved: Boolean) {
        scope.launch {
            dataStore.edit { prefs ->
                val current = AutoApproveApps.read(prefs)
                val updated = if (approved) {
                    AutoApproveApps.add(current, packageName)
                } else {
                    AutoApproveApps.remove(current, packageName)
                }
                prefs[PreferencesKeys.AUTO_APPROVE_PACKAGES] = updated
            }
        }
    }

    fun setAutoApprovePackages(packages: Set<String>) {
        scope.launch {
            dataStore.edit { prefs ->
                prefs[PreferencesKeys.AUTO_APPROVE_PACKAGES] = packages
            }
        }
    }

    fun setShowDownloadTab(enabled: Boolean) {
        scope.launch {
            dataStore.edit { it[PreferencesKeys.SHOW_DOWNLOAD_TAB] = enabled }
        }
    }

    fun setShizukuOption(key: Preferences.Key<Boolean>, value: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    fun setShizukuInstallerPackageName(name: String) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME] = name }
        }
    }

    fun setRootOption(key: Preferences.Key<Boolean>, value: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[key] = value }
        }
    }

    fun setRootInstallerPackageName(name: String) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.ROOT_INSTALLER_PACKAGE_NAME] = name }
        }
    }

    fun setSyncRequirePin(enabled: Boolean) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.SYNC_REQUIRE_PIN] = enabled }
        }
    }

    fun setSyncPinCode(code: String) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.SYNC_PIN_CODE] = code }
        }
    }

    fun setSyncServerPort(port: String) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.SYNC_SERVER_PORT] = port }
        }
    }

    fun setExtractorOutputPath(path: String) {
        scope.launch {
            if (path.startsWith("content://")) {
                runCatching {
                    application.contentResolver.takePersistableUriPermission(
                        Uri.parse(path),
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                    )
                }
            }
            dataStore.edit { prefs -> prefs[PreferencesKeys.APK_EXTRACTOR_OUTPUT_PATH] = path }
        }
    }

    fun setExtractorFilenameTemplate(template: String) {
        scope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.APK_EXTRACTOR_FILENAME_TEMPLATE] = template }
        }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        Telemetry.setCollectionEnabled(enabled)
        scope.launch {
            dataStore.edit { prefs -> prefs[SharedPrefsKeys.ANALYTICS_ENABLED] = enabled }
        }
    }
}
