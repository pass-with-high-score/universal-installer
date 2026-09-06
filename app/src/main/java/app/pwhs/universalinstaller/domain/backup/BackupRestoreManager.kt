package app.pwhs.universalinstaller.domain.backup

import android.content.Context
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.data.local.UninstallLogDao
import app.pwhs.universalinstaller.data.local.UninstallLogEntity
import app.pwhs.universalinstaller.domain.manager.InstallBlacklist
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

data class BackupExportOptions(
    val includeSettings: Boolean = true,
    val includeTokens: Boolean = true,
    val includeTrackedApps: Boolean = true,
    val includeUninstallLogs: Boolean = true,
)

data class BackupRestoreOptions(
    val restoreSettings: Boolean = true,
    val restoreTokens: Boolean = true,
    val restoreTrackedApps: Boolean = true,
    val restoreUninstallLogs: Boolean = true,
)

data class BackupRestoreSummary(
    val settingsRestored: Boolean,
    val tokensRestored: Boolean,
    val trackedAppsCount: Int,
    val uninstallLogsCount: Int,
)

sealed interface BackupInspectResult {
    data class Encrypted(val envelope: EncryptedBackupEnvelope) : BackupInspectResult
    data class Ready(val summary: BackupSummaryInfo) : BackupInspectResult
    data class Error(val message: String) : BackupInspectResult
}

class BackupRestoreManager(
    private val context: Context,
    private val trackedAppsDataSource: TrackedAppsBackupDataSource,
    private val uninstallLogDao: UninstallLogDao,
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
        encodeDefaults = true
    }


    suspend fun createBackup(options: BackupExportOptions, password: String? = null): String {
        val prefs = context.dataStore.data.first()

        val settingsDto = if (options.includeSettings) {
            AppSettingsBackupDto(
                themeMode = prefs[PreferencesKeys.THEME_MODE] ?: prefs[SharedPrefsKeys.THEME_MODE],
                dynamicColor = prefs[PreferencesKeys.DYNAMIC_COLOR] ?: prefs[SharedPrefsKeys.DYNAMIC_COLOR],
                amoledMode = prefs[PreferencesKeys.AMOLED_MODE] ?: prefs[SharedPrefsKeys.AMOLED_MODE],
                themePreset = prefs[PreferencesKeys.THEME_PRESET] ?: prefs[SharedPrefsKeys.THEME_PRESET],
                virusTotalApiKey = prefs[PreferencesKeys.VIRUSTOTAL_API_KEY] ?: prefs[SharedPrefsKeys.VIRUSTOTAL_API_KEY],
                strictVirusTotalCheck = prefs[PreferencesKeys.STRICT_VIRUSTOTAL_CHECK] ?: prefs[SharedPrefsKeys.STRICT_VIRUSTOTAL_CHECK],
                securityLevel = prefs[PreferencesKeys.SECURITY_LEVEL] ?: prefs[SharedPrefsKeys.SECURITY_LEVEL],
                useShizuku = prefs[PreferencesKeys.USE_SHIZUKU],
                useRoot = prefs[PreferencesKeys.USE_ROOT],
                useDhizuku = prefs[PreferencesKeys.USE_DHIZUKU],
                useCustomAuthorizer = prefs[PreferencesKeys.USE_CUSTOM_AUTHORIZER],
                useMicrog = prefs[PreferencesKeys.USE_MICROG],
                customAuthorizerCommand = prefs[PreferencesKeys.CUSTOM_AUTHORIZER_COMMAND],
                installUserId = prefs[PreferencesKeys.INSTALL_USER_ID],
                deleteApkAfterInstall = prefs[PreferencesKeys.DELETE_APK_AFTER_INSTALL],
                autoOpenAfterInstall = prefs[PreferencesKeys.AUTO_OPEN_AFTER_INSTALL],
                externalOpenMode = prefs[PreferencesKeys.EXTERNAL_OPEN_MODE],
                installUiStyle = prefs[PreferencesKeys.INSTALL_UI_STYLE],
                dialogInstallMode = prefs[PreferencesKeys.DIALOG_INSTALL_MODE],
                autoConfirmExternalInstall = prefs[PreferencesKeys.AUTO_CONFIRM_EXTERNAL_INSTALL],
                autoApproveCallerApps = prefs[PreferencesKeys.AUTO_APPROVE_CALLER_APPS],
                autoApprovePackages = prefs[PreferencesKeys.AUTO_APPROVE_PACKAGES],
                showDownloadTab = prefs[PreferencesKeys.SHOW_DOWNLOAD_TAB],
                shizukuBypassLowTargetSdk = prefs[PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK],
                shizukuAllowTest = prefs[PreferencesKeys.SHIZUKU_ALLOW_TEST],
                shizukuReplaceExisting = prefs[PreferencesKeys.SHIZUKU_REPLACE_EXISTING],
                shizukuRequestDowngrade = prefs[PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE],
                shizukuGrantAllPermissions = prefs[PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS],
                shizukuAllUsers = prefs[PreferencesKeys.SHIZUKU_ALL_USERS],
                shizukuSetInstallSource = prefs[PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE],
                shizukuInstallerPackageName = prefs[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME],
                shizukuAllowRestrictedPermissions = prefs[PreferencesKeys.SHIZUKU_ALLOW_RESTRICTED_PERMISSIONS],
                shizukuDontKillApp = prefs[PreferencesKeys.SHIZUKU_DONT_KILL_APP],
                shizukuDisableVerification = prefs[PreferencesKeys.SHIZUKU_DISABLE_VERIFICATION],
                shizukuEnableRollback = prefs[PreferencesKeys.SHIZUKU_ENABLE_ROLLBACK],
                shizukuRequestUpdateOwnership = prefs[PreferencesKeys.SHIZUKU_REQUEST_UPDATE_OWNERSHIP],
                shizukuUninstallKeepData = prefs[PreferencesKeys.SHIZUKU_UNINSTALL_KEEP_DATA],
                shizukuUninstallAllUsers = prefs[PreferencesKeys.SHIZUKU_UNINSTALL_ALL_USERS],
                dhizukuRequestDowngrade = prefs[PreferencesKeys.DHIZUKU_REQUEST_DOWNGRADE],
                rootBypassLowTargetSdk = prefs[PreferencesKeys.ROOT_BYPASS_LOW_TARGET_SDK],
                rootAllowTest = prefs[PreferencesKeys.ROOT_ALLOW_TEST],
                rootReplaceExisting = prefs[PreferencesKeys.ROOT_REPLACE_EXISTING],
                rootRequestDowngrade = prefs[PreferencesKeys.ROOT_REQUEST_DOWNGRADE],
                rootGrantAllPermissions = prefs[PreferencesKeys.ROOT_GRANT_ALL_PERMISSIONS],
                rootAllUsers = prefs[PreferencesKeys.ROOT_ALL_USERS],
                rootSetInstallSource = prefs[PreferencesKeys.ROOT_SET_INSTALL_SOURCE],
                rootInstallerPackageName = prefs[PreferencesKeys.ROOT_INSTALLER_PACKAGE_NAME],
                rootAllowRestrictedPermissions = prefs[PreferencesKeys.ROOT_ALLOW_RESTRICTED_PERMISSIONS],
                rootDontKillApp = prefs[PreferencesKeys.ROOT_DONT_KILL_APP],
                rootDisableVerification = prefs[PreferencesKeys.ROOT_DISABLE_VERIFICATION],
                rootEnableRollback = prefs[PreferencesKeys.ROOT_ENABLE_ROLLBACK],
                rootRequestUpdateOwnership = prefs[PreferencesKeys.ROOT_REQUEST_UPDATE_OWNERSHIP],
                installerOverrides = prefs[PreferencesKeys.INSTALLER_OVERRIDES],
                installerProfiles = prefs[PreferencesKeys.INSTALLER_PROFILES],
                appProfileMapping = prefs[PreferencesKeys.APP_PROFILE_MAPPING],
                blockedPackages = prefs[InstallBlacklist.KEY],
                pinLockEnabled = prefs[PreferencesKeys.PIN_LOCK_ENABLED],
                pinLockHash = prefs[PreferencesKeys.PIN_LOCK_HASH],
                pinLockSalt = prefs[PreferencesKeys.PIN_LOCK_SALT],
                pinLockInstall = prefs[PreferencesKeys.PIN_LOCK_INSTALL],
                pinLockUninstall = prefs[PreferencesKeys.PIN_LOCK_UNINSTALL],
                pinLockAppOpen = prefs[PreferencesKeys.PIN_LOCK_APP_OPEN],
                pinLockProtectSettings = prefs[PreferencesKeys.PIN_LOCK_PROTECT_SETTINGS],
                biometricLockInstall = prefs[PreferencesKeys.BIOMETRIC_LOCK_INSTALL],
                biometricLockUninstall = prefs[PreferencesKeys.BIOMETRIC_LOCK_UNINSTALL],
                syncRequirePin = prefs[PreferencesKeys.SYNC_REQUIRE_PIN],
                syncPinCode = prefs[PreferencesKeys.SYNC_PIN_CODE],
                syncServerPort = prefs[PreferencesKeys.SYNC_SERVER_PORT],
                apkExtractorOutputPath = prefs[PreferencesKeys.APK_EXTRACTOR_OUTPUT_PATH],
                apkExtractorFilenameTemplate = prefs[PreferencesKeys.APK_EXTRACTOR_FILENAME_TEMPLATE],
                apkExtractorSplitFormat = prefs[PreferencesKeys.APK_EXTRACTOR_SPLIT_FORMAT],
                manageSortBy = prefs[PreferencesKeys.MANAGE_SORT_BY],
                manageSortDirection = prefs[PreferencesKeys.MANAGE_SORT_DIRECTION],
                manageGroupBy = prefs[PreferencesKeys.MANAGE_GROUP_BY],
                manageAppFilter = prefs[PreferencesKeys.MANAGE_APP_FILTER],
            )
        } else null

        val tokensDto = if (options.includeTokens) {
            SourceTokensBackupDto(
                githubToken = prefs[SharedPrefsKeys.GITHUB_PAT_TOKEN]?.takeIf { it.isNotBlank() },
                gitlabToken = prefs[SharedPrefsKeys.GITLAB_PAT_TOKEN]?.takeIf { it.isNotBlank() },
                codebergToken = prefs[SharedPrefsKeys.CODEBERG_PAT_TOKEN]?.takeIf { it.isNotBlank() },
            )
        } else null

        val trackedApps = if (options.includeTrackedApps && trackedAppsDataSource.isAvailable()) {
            trackedAppsDataSource.getTrackedApps()
        } else null

        val logsDto = if (options.includeUninstallLogs) {
            runCatching {
                uninstallLogDao.getAll().first().map { log ->
                    UninstallLogBackupDto(
                        packageName = log.packageName,
                        appName = log.appName,
                        success = log.success,
                        errorMessage = log.errorMessage,
                        uninstalledAt = log.uninstalledAt,
                    )
                }
            }.getOrDefault(emptyList())
        } else null

        val backup = UniversalInstallerBackup(
            version = 2,
            exportedAt = System.currentTimeMillis(),
            appVersion = BuildConfig.VERSION_NAME,
            settings = settingsDto,
            sourceTokens = tokensDto,
            trackedApps = trackedApps,
            uninstallLogs = logsDto,
        )

        val rawJson = json.encodeToString(UniversalInstallerBackup.serializer(), backup)

        return if (!password.isNullOrBlank()) {
            val envelope = BackupCryptoHelper.encrypt(rawJson, password)
            json.encodeToString(EncryptedBackupEnvelope.serializer(), envelope)
        } else {
            rawJson
        }
    }

    fun inspectBackupFile(rawContent: String): BackupInspectResult {
        val trimmed = rawContent.trim()
        if (trimmed.isEmpty()) return BackupInspectResult.Error("Empty backup file")

        // 1. Check if encrypted envelope
        runCatching {
            val envelope = json.decodeFromString(EncryptedBackupEnvelope.serializer(), trimmed)
            if (envelope.isEncrypted && envelope.ciphertext.isNotBlank()) {
                return BackupInspectResult.Encrypted(envelope)
            }
        }

        // 2. Try parsing Universal Installer Backup v2
        runCatching {
            val backup = json.decodeFromString(UniversalInstallerBackup.serializer(), trimmed)
            if (backup.version >= 2 || backup.settings != null || backup.trackedApps != null || backup.sourceTokens != null || backup.uninstallLogs != null) {
                return BackupInspectResult.Ready(toSummary(backup))
            }
        }

        // 3. Fallback: Parse v1 format or Obtainium format for tracked apps
        val trackedApps = parseLegacyTrackedApps(trimmed)
        if (trackedApps.isNotEmpty()) {
            val fallbackBackup = UniversalInstallerBackup(
                version = 1,
                exportedAt = System.currentTimeMillis(),
                trackedApps = trackedApps,
            )
            return BackupInspectResult.Ready(toSummary(fallbackBackup))
        }

        return BackupInspectResult.Error("Unsupported or invalid backup format")
    }

    fun decryptAndInspect(envelope: EncryptedBackupEnvelope, password: String): BackupInspectResult {
        return try {
            val plainText = BackupCryptoHelper.decrypt(envelope, password)
            inspectBackupFile(plainText)
        } catch (e: InvalidPasswordException) {
            BackupInspectResult.Error(e.message ?: "Invalid password")
        } catch (e: Exception) {
            BackupInspectResult.Error("Decryption failed: ${e.message}")
        }
    }

    suspend fun restoreBackup(
        backup: UniversalInstallerBackup,
        options: BackupRestoreOptions,
    ): BackupRestoreSummary {
        var settingsRestored = false
        var tokensRestored = false
        var trackedAppsRestoredCount = 0
        var uninstallLogsRestoredCount = 0

        // 1. Restore App Settings
        if (options.restoreSettings && backup.settings != null) {
            val s = backup.settings
            context.dataStore.edit { prefs ->
                s.themeMode?.let { prefs[PreferencesKeys.THEME_MODE] = it; prefs[SharedPrefsKeys.THEME_MODE] = it }
                s.dynamicColor?.let { prefs[PreferencesKeys.DYNAMIC_COLOR] = it; prefs[SharedPrefsKeys.DYNAMIC_COLOR] = it }
                s.amoledMode?.let { prefs[PreferencesKeys.AMOLED_MODE] = it; prefs[SharedPrefsKeys.AMOLED_MODE] = it }
                s.themePreset?.let { prefs[PreferencesKeys.THEME_PRESET] = it; prefs[SharedPrefsKeys.THEME_PRESET] = it }
                s.virusTotalApiKey?.let { prefs[PreferencesKeys.VIRUSTOTAL_API_KEY] = it; prefs[SharedPrefsKeys.VIRUSTOTAL_API_KEY] = it }
                s.strictVirusTotalCheck?.let { prefs[PreferencesKeys.STRICT_VIRUSTOTAL_CHECK] = it; prefs[SharedPrefsKeys.STRICT_VIRUSTOTAL_CHECK] = it }
                s.securityLevel?.let { prefs[PreferencesKeys.SECURITY_LEVEL] = it; prefs[SharedPrefsKeys.SECURITY_LEVEL] = it }
                s.useShizuku?.let { prefs[PreferencesKeys.USE_SHIZUKU] = it }
                s.useRoot?.let { prefs[PreferencesKeys.USE_ROOT] = it }
                s.useDhizuku?.let { prefs[PreferencesKeys.USE_DHIZUKU] = it }
                s.useCustomAuthorizer?.let { prefs[PreferencesKeys.USE_CUSTOM_AUTHORIZER] = it }
                s.useMicrog?.let { prefs[PreferencesKeys.USE_MICROG] = it }
                s.customAuthorizerCommand?.let { prefs[PreferencesKeys.CUSTOM_AUTHORIZER_COMMAND] = it }
                s.installUserId?.let { prefs[PreferencesKeys.INSTALL_USER_ID] = it }
                s.deleteApkAfterInstall?.let { prefs[PreferencesKeys.DELETE_APK_AFTER_INSTALL] = it }
                s.autoOpenAfterInstall?.let { prefs[PreferencesKeys.AUTO_OPEN_AFTER_INSTALL] = it }
                s.externalOpenMode?.let { prefs[PreferencesKeys.EXTERNAL_OPEN_MODE] = it }
                s.installUiStyle?.let { prefs[PreferencesKeys.INSTALL_UI_STYLE] = it }
                s.dialogInstallMode?.let { prefs[PreferencesKeys.DIALOG_INSTALL_MODE] = it }
                s.autoConfirmExternalInstall?.let { prefs[PreferencesKeys.AUTO_CONFIRM_EXTERNAL_INSTALL] = it }
                s.autoApproveCallerApps?.let { prefs[PreferencesKeys.AUTO_APPROVE_CALLER_APPS] = it }
                s.autoApprovePackages?.let { prefs[PreferencesKeys.AUTO_APPROVE_PACKAGES] = it }
                s.showDownloadTab?.let { prefs[PreferencesKeys.SHOW_DOWNLOAD_TAB] = it }

                // Shizuku
                s.shizukuBypassLowTargetSdk?.let { prefs[PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK] = it }
                s.shizukuAllowTest?.let { prefs[PreferencesKeys.SHIZUKU_ALLOW_TEST] = it }
                s.shizukuReplaceExisting?.let { prefs[PreferencesKeys.SHIZUKU_REPLACE_EXISTING] = it }
                s.shizukuRequestDowngrade?.let { prefs[PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE] = it }
                s.shizukuGrantAllPermissions?.let { prefs[PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS] = it }
                s.shizukuAllUsers?.let { prefs[PreferencesKeys.SHIZUKU_ALL_USERS] = it }
                s.shizukuSetInstallSource?.let { prefs[PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE] = it }
                s.shizukuInstallerPackageName?.let { prefs[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME] = it }
                s.shizukuAllowRestrictedPermissions?.let { prefs[PreferencesKeys.SHIZUKU_ALLOW_RESTRICTED_PERMISSIONS] = it }
                s.shizukuDontKillApp?.let { prefs[PreferencesKeys.SHIZUKU_DONT_KILL_APP] = it }
                s.shizukuDisableVerification?.let { prefs[PreferencesKeys.SHIZUKU_DISABLE_VERIFICATION] = it }
                s.shizukuEnableRollback?.let { prefs[PreferencesKeys.SHIZUKU_ENABLE_ROLLBACK] = it }
                s.shizukuRequestUpdateOwnership?.let { prefs[PreferencesKeys.SHIZUKU_REQUEST_UPDATE_OWNERSHIP] = it }
                s.shizukuUninstallKeepData?.let { prefs[PreferencesKeys.SHIZUKU_UNINSTALL_KEEP_DATA] = it }
                s.shizukuUninstallAllUsers?.let { prefs[PreferencesKeys.SHIZUKU_UNINSTALL_ALL_USERS] = it }
                s.dhizukuRequestDowngrade?.let { prefs[PreferencesKeys.DHIZUKU_REQUEST_DOWNGRADE] = it }

                // Root
                s.rootBypassLowTargetSdk?.let { prefs[PreferencesKeys.ROOT_BYPASS_LOW_TARGET_SDK] = it }
                s.rootAllowTest?.let { prefs[PreferencesKeys.ROOT_ALLOW_TEST] = it }
                s.rootReplaceExisting?.let { prefs[PreferencesKeys.ROOT_REPLACE_EXISTING] = it }
                s.rootRequestDowngrade?.let { prefs[PreferencesKeys.ROOT_REQUEST_DOWNGRADE] = it }
                s.rootGrantAllPermissions?.let { prefs[PreferencesKeys.ROOT_GRANT_ALL_PERMISSIONS] = it }
                s.rootAllUsers?.let { prefs[PreferencesKeys.ROOT_ALL_USERS] = it }
                s.rootSetInstallSource?.let { prefs[PreferencesKeys.ROOT_SET_INSTALL_SOURCE] = it }
                s.rootInstallerPackageName?.let { prefs[PreferencesKeys.ROOT_INSTALLER_PACKAGE_NAME] = it }
                s.rootAllowRestrictedPermissions?.let { prefs[PreferencesKeys.ROOT_ALLOW_RESTRICTED_PERMISSIONS] = it }
                s.rootDontKillApp?.let { prefs[PreferencesKeys.ROOT_DONT_KILL_APP] = it }
                s.rootDisableVerification?.let { prefs[PreferencesKeys.ROOT_DISABLE_VERIFICATION] = it }
                s.rootEnableRollback?.let { prefs[PreferencesKeys.ROOT_ENABLE_ROLLBACK] = it }
                s.rootRequestUpdateOwnership?.let { prefs[PreferencesKeys.ROOT_REQUEST_UPDATE_OWNERSHIP] = it }

                // Overrides, Profiles & Blacklist
                s.installerOverrides?.let { prefs[PreferencesKeys.INSTALLER_OVERRIDES] = it }
                s.installerProfiles?.let { prefs[PreferencesKeys.INSTALLER_PROFILES] = it }
                s.appProfileMapping?.let { prefs[PreferencesKeys.APP_PROFILE_MAPPING] = it }
                s.blockedPackages?.let { prefs[InstallBlacklist.KEY] = it }

                // Security: PIN & Biometrics & Sync
                s.pinLockEnabled?.let { prefs[PreferencesKeys.PIN_LOCK_ENABLED] = it }
                s.pinLockHash?.let { prefs[PreferencesKeys.PIN_LOCK_HASH] = it }
                s.pinLockSalt?.let { prefs[PreferencesKeys.PIN_LOCK_SALT] = it }
                s.pinLockInstall?.let { prefs[PreferencesKeys.PIN_LOCK_INSTALL] = it }
                s.pinLockUninstall?.let { prefs[PreferencesKeys.PIN_LOCK_UNINSTALL] = it }
                s.pinLockAppOpen?.let { prefs[PreferencesKeys.PIN_LOCK_APP_OPEN] = it }
                s.pinLockProtectSettings?.let { prefs[PreferencesKeys.PIN_LOCK_PROTECT_SETTINGS] = it }
                s.biometricLockInstall?.let { prefs[PreferencesKeys.BIOMETRIC_LOCK_INSTALL] = it }
                s.biometricLockUninstall?.let { prefs[PreferencesKeys.BIOMETRIC_LOCK_UNINSTALL] = it }
                s.syncRequirePin?.let { prefs[PreferencesKeys.SYNC_REQUIRE_PIN] = it }
                s.syncPinCode?.let { prefs[PreferencesKeys.SYNC_PIN_CODE] = it }
                s.syncServerPort?.let { prefs[PreferencesKeys.SYNC_SERVER_PORT] = it }

                // APK Extractor
                s.apkExtractorOutputPath?.let { prefs[PreferencesKeys.APK_EXTRACTOR_OUTPUT_PATH] = it }
                s.apkExtractorFilenameTemplate?.let { prefs[PreferencesKeys.APK_EXTRACTOR_FILENAME_TEMPLATE] = it }
                s.apkExtractorSplitFormat?.let { prefs[PreferencesKeys.APK_EXTRACTOR_SPLIT_FORMAT] = it }

                // Manage Filter
                s.manageSortBy?.let { prefs[PreferencesKeys.MANAGE_SORT_BY] = it }
                s.manageSortDirection?.let { prefs[PreferencesKeys.MANAGE_SORT_DIRECTION] = it }
                s.manageGroupBy?.let { prefs[PreferencesKeys.MANAGE_GROUP_BY] = it }
                s.manageAppFilter?.let { prefs[PreferencesKeys.MANAGE_APP_FILTER] = it }
            }
            settingsRestored = true
        }

        // 2. Restore Source Tokens
        if (options.restoreTokens && backup.sourceTokens != null) {
            context.dataStore.edit { prefs ->
                backup.sourceTokens.githubToken?.let { prefs[SharedPrefsKeys.GITHUB_PAT_TOKEN] = it }
                backup.sourceTokens.gitlabToken?.let { prefs[SharedPrefsKeys.GITLAB_PAT_TOKEN] = it }
                backup.sourceTokens.codebergToken?.let { prefs[SharedPrefsKeys.CODEBERG_PAT_TOKEN] = it }
            }
            tokensRestored = true
        }

        // 3. Restore Tracked Apps
        if (options.restoreTrackedApps && !backup.trackedApps.isNullOrEmpty() && trackedAppsDataSource.isAvailable()) {
            trackedAppsRestoredCount = trackedAppsDataSource.restoreTrackedApps(backup.trackedApps)
        }

        // 4. Restore Uninstall Logs
        if (options.restoreUninstallLogs && !backup.uninstallLogs.isNullOrEmpty()) {
            for (logDto in backup.uninstallLogs) {
                runCatching {
                    uninstallLogDao.insert(
                        UninstallLogEntity(
                            packageName = logDto.packageName,
                            appName = logDto.appName,
                            success = logDto.success,
                            errorMessage = logDto.errorMessage,
                            uninstalledAt = logDto.uninstalledAt,
                        )
                    )
                    uninstallLogsRestoredCount++
                }
            }
        }

        return BackupRestoreSummary(
            settingsRestored = settingsRestored,
            tokensRestored = tokensRestored,
            trackedAppsCount = trackedAppsRestoredCount,
            uninstallLogsCount = uninstallLogsRestoredCount,
        )
    }

    private fun toSummary(backup: UniversalInstallerBackup): BackupSummaryInfo {
        return BackupSummaryInfo(
            version = backup.version,
            exportedAt = backup.exportedAt,
            appVersion = backup.appVersion,
            hasSettings = backup.settings != null,
            hasSourceTokens = backup.sourceTokens != null,
            trackedAppsCount = backup.trackedApps?.size ?: 0,
            uninstallLogsCount = backup.uninstallLogs?.size ?: 0,
            backup = backup,
        )
    }

    private fun parseLegacyTrackedApps(jsonString: String): List<TrackedAppBackupDto> {
        val results = mutableListOf<TrackedAppBackupDto>()
        runCatching {
            val element = json.parseToJsonElement(jsonString)
            val appArray = when {
                element is JsonObject && element.containsKey("apps") -> element["apps"]?.jsonArray
                element is JsonObject && element.containsKey("trackedApps") -> element["trackedApps"]?.jsonArray
                element is JsonObject && element.containsKey("app_sources") -> element["app_sources"]?.jsonArray
                element is kotlinx.serialization.json.JsonArray -> element
                else -> null
            }

            appArray?.forEach { item ->
                val obj = item.jsonObject
                val url = obj["url"]?.jsonPrimitive?.content
                    ?: obj["source_url"]?.jsonPrimitive?.content
                    ?: obj["sourceUrl"]?.jsonPrimitive?.content
                    ?: return@forEach

                val name = obj["name"]?.jsonPrimitive?.content
                    ?: obj["appName"]?.jsonPrimitive?.content
                    ?: url.substringBefore('?').substringAfterLast('/')

                val id = obj["id"]?.jsonPrimitive?.content
                    ?: obj["package_name"]?.jsonPrimitive?.content
                    ?: obj["packageName"]?.jsonPrimitive?.content
                    ?: "tracked.${name.lowercase().replace(Regex("[^a-z0-9_]"), "_")}"

                val includePrereleases = obj["include_prereleases"]?.jsonPrimitive?.booleanOrNull
                    ?: obj["includePrereleases"]?.jsonPrimitive?.booleanOrNull
                    ?: false

                val customFilter = obj["filter"]?.jsonPrimitive?.content
                    ?: obj["customRegexFilter"]?.jsonPrimitive?.content

                val category = obj["category"]?.jsonPrimitive?.content
                    ?: obj["categories"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content

                val sourceType = obj["source_type"]?.jsonPrimitive?.content
                    ?: obj["sourceType"]?.jsonPrimitive?.content
                    ?: "UNKNOWN"

                results.add(
                    TrackedAppBackupDto(
                        packageName = id,
                        appName = name,
                        sourceUrl = url,
                        sourceType = sourceType,
                        includePrereleases = includePrereleases,
                        customRegexFilter = customFilter,
                        category = category,
                    )
                )
            }
        }.onFailure { Timber.d(it, "Legacy tracked apps parsing error") }

        return results
    }
}
