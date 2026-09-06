package app.pwhs.universalinstaller.domain.backup

import kotlinx.serialization.Serializable

@Serializable
data class UniversalInstallerBackup(
    val version: Int = 2,
    val exportedAt: Long = System.currentTimeMillis(),
    val appVersion: String? = null,
    val settings: AppSettingsBackupDto? = null,
    val sourceTokens: SourceTokensBackupDto? = null,
    val trackedApps: List<TrackedAppBackupDto>? = null,
    val uninstallLogs: List<UninstallLogBackupDto>? = null,
)

@Serializable
data class EncryptedBackupEnvelope(
    val isEncrypted: Boolean = true,
    val version: Int = 2,
    val algorithm: String = "AES-256-GCM",
    val kdf: String = "PBKDF2WithHmacSHA256",
    val iterations: Int = 100_000,
    val salt: String,
    val iv: String,
    val ciphertext: String,
)

@Serializable
data class AppSettingsBackupDto(
    // Theme
    val themeMode: String? = null,
    val dynamicColor: Boolean? = null,
    val amoledMode: Boolean? = null,
    val themePreset: String? = null,

    // Security & VirusTotal
    val virusTotalApiKey: String? = null,
    val strictVirusTotalCheck: Boolean? = null,
    val securityLevel: String? = null,

    // General Install Options
    val useShizuku: Boolean? = null,
    val useRoot: Boolean? = null,
    val useDhizuku: Boolean? = null,
    val useCustomAuthorizer: Boolean? = null,
    val useMicrog: Boolean? = null,
    val customAuthorizerCommand: String? = null,
    val installUserId: Int? = null,
    val deleteApkAfterInstall: Boolean? = null,
    val autoOpenAfterInstall: Boolean? = null,
    val externalOpenMode: String? = null,
    val installUiStyle: String? = null,
    val dialogInstallMode: Boolean? = null,
    val autoConfirmExternalInstall: Boolean? = null,
    val autoApproveCallerApps: Boolean? = null,
    val autoApprovePackages: Set<String>? = null,
    val showDownloadTab: Boolean? = null,

    // Shizuku Flags
    val shizukuBypassLowTargetSdk: Boolean? = null,
    val shizukuAllowTest: Boolean? = null,
    val shizukuReplaceExisting: Boolean? = null,
    val shizukuRequestDowngrade: Boolean? = null,
    val shizukuGrantAllPermissions: Boolean? = null,
    val shizukuAllUsers: Boolean? = null,
    val shizukuSetInstallSource: Boolean? = null,
    val shizukuInstallerPackageName: String? = null,
    val shizukuAllowRestrictedPermissions: Boolean? = null,
    val shizukuDontKillApp: Boolean? = null,
    val shizukuDisableVerification: Boolean? = null,
    val shizukuEnableRollback: Boolean? = null,
    val shizukuRequestUpdateOwnership: Boolean? = null,
    val shizukuUninstallKeepData: Boolean? = null,
    val shizukuUninstallAllUsers: Boolean? = null,
    val dhizukuRequestDowngrade: Boolean? = null,

    // Root Flags
    val rootBypassLowTargetSdk: Boolean? = null,
    val rootAllowTest: Boolean? = null,
    val rootReplaceExisting: Boolean? = null,
    val rootRequestDowngrade: Boolean? = null,
    val rootGrantAllPermissions: Boolean? = null,
    val rootAllUsers: Boolean? = null,
    val rootSetInstallSource: Boolean? = null,
    val rootInstallerPackageName: String? = null,
    val rootAllowRestrictedPermissions: Boolean? = null,
    val rootDontKillApp: Boolean? = null,
    val rootDisableVerification: Boolean? = null,
    val rootEnableRollback: Boolean? = null,
    val rootRequestUpdateOwnership: Boolean? = null,

    // Overrides, Profiles & Blacklist
    val installerOverrides: String? = null,
    val installerProfiles: String? = null,
    val appProfileMapping: String? = null,
    val blockedPackages: Set<String>? = null,

    // PIN Lock
    val pinLockEnabled: Boolean? = null,
    val pinLockHash: String? = null,
    val pinLockSalt: String? = null,
    val pinLockInstall: Boolean? = null,
    val pinLockUninstall: Boolean? = null,
    val pinLockAppOpen: Boolean? = null,
    val pinLockProtectSettings: Boolean? = null,

    // Biometrics
    val biometricLockInstall: Boolean? = null,
    val biometricLockUninstall: Boolean? = null,

    // Sync
    val syncRequirePin: Boolean? = null,
    val syncPinCode: String? = null,
    val syncServerPort: String? = null,

    // APK Extractor
    val apkExtractorOutputPath: String? = null,
    val apkExtractorFilenameTemplate: String? = null,
    val apkExtractorSplitFormat: String? = null,

    // Manage Filter
    val manageSortBy: String? = null,
    val manageSortDirection: String? = null,
    val manageGroupBy: String? = null,
    val manageAppFilter: Set<String>? = null,
)

@Serializable
data class SourceTokensBackupDto(
    val githubToken: String? = null,
    val gitlabToken: String? = null,
    val codebergToken: String? = null,
)

@Serializable
data class TrackedAppBackupDto(
    val packageName: String,
    val appName: String,
    val sourceUrl: String,
    val sourceType: String,
    val includePrereleases: Boolean = false,
    val customRegexFilter: String? = null,
    val category: String? = null,
)

@Serializable
data class UninstallLogBackupDto(
    val packageName: String,
    val appName: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val uninstalledAt: Long,
)

data class BackupSummaryInfo(
    val version: Int,
    val exportedAt: Long,
    val appVersion: String?,
    val hasSettings: Boolean,
    val hasSourceTokens: Boolean,
    val trackedAppsCount: Int,
    val uninstallLogsCount: Int,
    val backup: UniversalInstallerBackup,
)
