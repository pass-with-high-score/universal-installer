package app.pwhs.universalinstaller.presentation.setting.util

import android.app.Application
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.universalinstaller.domain.manager.ProfileManager
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.RootOptions
import app.pwhs.universalinstaller.presentation.setting.SettingThemeState
import app.pwhs.universalinstaller.presentation.setting.SettingUiState
import app.pwhs.universalinstaller.presentation.setting.ShizukuOptions
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.presentation.setting.SyncOptions
import app.pwhs.universalinstaller.util.BiometricGate

object SettingUiStateBuilder {

    fun build(
        application: Application,
        backendFactory: InstallerBackendFactory,
        flows: Array<*>,
    ): SettingUiState {
        val themeState = flows[0] as SettingThemeState
        val useShizuku = flows[1] as Boolean
        val vtKey = flows[2] as String
        val shizukuState = flows[3] as ShizukuState
        val shizukuOpts = flows[4] as ShizukuOptions
        val deleteApk = flows[5] as Boolean
        val useRoot = flows[6] as Boolean
        val rootState = flows[7] as RootState
        val rootOpts = flows[8] as RootOptions
        val syncOpts = flows[9] as SyncOptions
        @Suppress("UNCHECKED_CAST")
        val biometricFlags = flows[10] as Pair<Boolean, Boolean>
        @Suppress("UNCHECKED_CAST")
        val interfaceFlags = flows[11] as List<Boolean>
        val dialogMode = interfaceFlags[0]
        val autoOpen = interfaceFlags[1]
        val autoConfirm = interfaceFlags[2]
        val showDownload = interfaceFlags[3]
        val autoApprove = interfaceFlags.getOrElse(5) { false }
        val autoApproveBlockTrackers = interfaceFlags.getOrElse(6) { false }
        @Suppress("UNCHECKED_CAST")
        val extractorAndProfiles = flows[12] as List<String>
        val extractorPath = extractorAndProfiles[0]
        val extractorTemplate = extractorAndProfiles[1]
        val profilesJson = extractorAndProfiles[2]
        val mappingJson = extractorAndProfiles[3]
        val autoApproveCount = extractorAndProfiles.getOrNull(4)?.toIntOrNull() ?: 0
        val selectedLang = flows[13] as String
        val isDefault = flows[14] as Boolean
        val useCustomAuthorizer = flows.getOrNull(15) as? Boolean ?: false
        val customAuthorizerCommand = flows.getOrNull(16) as? String ?: ""
        val useMicroG = flows.getOrNull(17) as? Boolean ?: false
        val isDefaultUninstaller = flows.getOrNull(18) as? Boolean ?: false

        val versionName = try {
            application.packageManager
                .getPackageInfo(application.packageName, 0)
                .versionName ?: ""
        } catch (_: Exception) {
            ""
        }

        return SettingUiState(
            isLoading = false,
            themeMode = themeState.mode,
            dynamicColor = themeState.dynamicColor,
            amoledMode = themeState.amoledMode,
            themePreset = themeState.themePreset,
            useShizuku = useShizuku,
            useRoot = useRoot && (rootState == RootState.READY || rootState == RootState.UNKNOWN),
            virusTotalApiKey = vtKey,
            deleteApkAfterInstall = deleteApk,
            autoOpenAfterInstall = autoOpen,
            shizukuState = shizukuState,
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
            biometricEnrolmentAvailable = BiometricGate.canAuthenticate(application),
            dialogInstallMode = dialogMode,
            autoConfirmExternalInstall = autoConfirm,
            autoApproveCallerApps = autoApprove,
            autoApproveCount = autoApproveCount,
            autoApproveBlockTrackers = autoApproveBlockTrackers,
            showDownloadTab = showDownload,
            extractorOutputPath = extractorPath,
            extractorFilenameTemplate = extractorTemplate,
            installerProfiles = ProfileManager.parseProfiles(profilesJson),
            appProfileMapping = ProfileManager.parseMapping(mappingJson),
            selectedLanguage = selectedLang,
            isDefaultInstaller = isDefault,
            isDefaultUninstaller = isDefaultUninstaller,
            useCustomAuthorizer = useCustomAuthorizer,
            customAuthorizerCommand = customAuthorizerCommand,
            useMicroG = useMicroG,
        )
    }
}
