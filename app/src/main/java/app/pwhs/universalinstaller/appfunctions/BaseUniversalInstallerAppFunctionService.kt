package app.pwhs.universalinstaller.appfunctions

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.appfunctions.AppFunctionDeclaration
import androidx.appfunctions.AppFunctionElementNotFoundException
import androidx.appfunctions.AppFunctionInvalidArgumentException
import androidx.appfunctions.AppFunctionService
import androidx.appfunctions.AppFunctionServiceEntryPoint
import androidx.core.net.toUri
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.data.local.DownloadHistoryDao
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.data.local.UninstallLogDao
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.domain.stats.UserStatsTracker
import app.pwhs.universalinstaller.presentation.download.DownloadHistoryActivity
import app.pwhs.universalinstaller.presentation.install.ApkScanner
import app.pwhs.universalinstaller.presentation.install.InstallActivity
import app.pwhs.universalinstaller.presentation.manage.ManageActivity
import app.pwhs.universalinstaller.presentation.manage.logs.UninstallLogsActivity
import app.pwhs.universalinstaller.presentation.manage.uninstall.DialogUninstallActivity
import app.pwhs.universalinstaller.presentation.manage.util.InstalledAppsLoader
import app.pwhs.universalinstaller.presentation.setting.SettingActivity
import app.pwhs.universalinstaller.presentation.stats.StatsActivity
import app.pwhs.universalinstaller.presentation.sync.SyncActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import java.util.Locale

@RequiresApi(Build.VERSION_CODES.BAKLAVA)
@AppFunctionServiceEntryPoint(
    serviceName = "UniversalInstallerAppFunctionService",
    appFunctionXmlFileName = "universal_installer_app_function_service",
)
abstract class BaseUniversalInstallerAppFunctionService : AppFunctionService() {

    private val installHistoryDao: InstallHistoryDao by inject()
    private val uninstallLogDao: UninstallLogDao by inject()
    private val downloadHistoryDao: DownloadHistoryDao by inject()

    /**
     * Searches applications installed on the device by display name or package name.
     * If searchQuery is null or blank, returns the most recently updated installed applications.
     *
     * @param searchQuery Case-insensitive substring to match against app display name or package name.
     * @param includeSystemApps Whether to include pre-installed system applications in the results (defaults to false).
     * @param maxResults Maximum number of matching applications to return (1 to 100, defaults to 25).
     * @return List of matching installed applications sorted by most recently updated first.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun searchInstalledApps(
        searchQuery: String? = null,
        includeSystemApps: Boolean = false,
        maxResults: Int = 0,
    ): List<InstalledAppRecord> = withContext(Dispatchers.IO) {
        val effectiveLimit = if (maxResults <= 0) 25 else maxResults.coerceIn(1, 100)
        val normalizedQuery = searchQuery?.trim()?.lowercase(Locale.ROOT).orEmpty()

        InstalledAppsLoader.loadInstalledApps(applicationContext)
            .asSequence()
            .filter { app -> includeSystemApps || !app.isSystemApp }
            .filter { app ->
                normalizedQuery.isEmpty() ||
                    app.appName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    app.packageName.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
            .sortedByDescending { it.lastUpdatedAt }
            .take(effectiveLimit)
            .map { it.toRecord() }
            .toList()
    }

    /**
     * Retrieves detailed metadata for a single installed application by its exact Android package name.
     *
     * @param packageName Exact Android package name of the installed application (e.g., "org.mozilla.firefox").
     * @return Detailed record of the installed application.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getInstalledAppDetails(
        packageName: String,
    ): InstalledAppRecord = withContext(Dispatchers.IO) {
        val target = packageName.trim()
        if (target.isEmpty()) {
            throw AppFunctionInvalidArgumentException("packageName must not be blank.")
        }
        val match = InstalledAppsLoader.loadInstalledApps(applicationContext)
            .firstOrNull { it.packageName.equals(target, ignoreCase = true) }
            ?: throw AppFunctionElementNotFoundException("No installed app found with packageName '$target'.")

        match.toRecord()
    }

    /**
     * Retrieves recent package installation history entries recorded by Universal Installer.
     * If packageQuery is null, returns the most recent installation attempts across all packages.
     *
     * @param packageQuery Optional substring filter for app name or package name.
     * @param maxResults Maximum number of history records to return (1 to 100, defaults to 20).
     * @return List of recent installation records ordered from newest to oldest.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getRecentInstallHistory(
        packageQuery: String? = null,
        maxResults: Int = 0,
    ): List<InstallHistoryRecord> = withContext(Dispatchers.IO) {
        val effectiveLimit = if (maxResults <= 0) 20 else maxResults.coerceIn(1, 100)
        val normalizedQuery = packageQuery?.trim()?.lowercase(Locale.ROOT).orEmpty()

        installHistoryDao.getAll().first()
            .asSequence()
            .filter { entry ->
                normalizedQuery.isEmpty() ||
                    entry.appName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    entry.packageName.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    entry.fileName.lowercase(Locale.ROOT).contains(normalizedQuery)
            }
            .take(effectiveLimit)
            .map { entry ->
                InstallHistoryRecord(
                    id = entry.id,
                    appName = entry.appName,
                    packageName = entry.packageName,
                    fileName = entry.fileName,
                    versionName = entry.versionName,
                    oldVersionName = entry.oldVersionName,
                    fileSizeBytes = entry.fileSizeBytes,
                    isSuccess = entry.success,
                    errorMessage = entry.errorMessage,
                    installedAtEpochMillis = entry.installedAt,
                    installerMode = entry.installerMode,
                    operationType = entry.operationType,
                )
            }
            .toList()
    }

    /**
     * Retrieves recent application uninstallation log entries recorded by Universal Installer.
     *
     * @param maxResults Maximum number of uninstall log entries to return (1 to 100, defaults to 20).
     * @return List of recent uninstallation records ordered from newest to oldest.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getRecentUninstallLogs(
        maxResults: Int = 0,
    ): List<UninstallLogRecord> = withContext(Dispatchers.IO) {
        val effectiveLimit = if (maxResults <= 0) 20 else maxResults.coerceIn(1, 100)
        uninstallLogDao.getAll().first()
            .take(effectiveLimit)
            .map { entry ->
                UninstallLogRecord(
                    id = entry.id,
                    packageName = entry.packageName,
                    appName = entry.appName,
                    isSuccess = entry.success,
                    errorMessage = entry.errorMessage,
                    uninstalledAtEpochMillis = entry.uninstalledAt,
                )
            }
    }

    /**
     * Retrieves recent package download history entries for packages downloaded via URL.
     *
     * @param maxResults Maximum number of download history records to return (1 to 100, defaults to 20).
     * @return List of recent package download records ordered from newest to oldest.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getRecentDownloadHistory(
        maxResults: Int = 0,
    ): List<DownloadHistoryRecord> = withContext(Dispatchers.IO) {
        val effectiveLimit = if (maxResults <= 0) 20 else maxResults.coerceIn(1, 100)
        downloadHistoryDao.getAll().first()
            .take(effectiveLimit)
            .map { entry ->
                DownloadHistoryRecord(
                    id = entry.id,
                    url = entry.url,
                    fileName = entry.fileName,
                    filePath = entry.filePath,
                    sizeBytes = entry.sizeBytes,
                    downloadedAtEpochMillis = entry.downloadedAt,
                )
            }
    }

    /**
     * Retrieves lifetime installation, uninstallation, storage cleanup, security scan statistics, and milestone rank.
     *
     * @return Summary of lifetime statistics and achievement progress in Universal Installer.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun getInstallerStats(): InstallerStatsRecord = withContext(Dispatchers.IO) {
        val prefs = applicationContext.dataStore.data.first()
        val installHistory = installHistoryDao.getAll().first()
        val uninstallLogs = uninstallLogDao.getAll().first()
        val downloadHistory = downloadHistoryDao.getAll().first()

        val summary = UserStatsTracker.buildSummary(
            prefs = prefs,
            installHistory = installHistory,
            uninstallLogs = uninstallLogs,
            downloadHistory = downloadHistory,
        )

        InstallerStatsRecord(
            totalSuccessInstalls = summary.totalSuccessInstalls,
            totalFailedInstalls = summary.totalFailedInstalls,
            successRatePercent = summary.successRatePercent,
            newInstallCount = summary.newInstallCount,
            updateCount = summary.updateCount,
            downgradeCount = summary.downgradeCount,
            totalUninstalls = summary.totalUninstalls,
            totalDownloads = summary.totalDownloads,
            totalBytesInstalled = summary.totalBytesInstalled,
            totalBytesCleaned = summary.totalBytesCleaned,
            totalFilesCleaned = summary.totalFilesCleaned,
            singleApkCount = summary.singleApkCount,
            splitBundleCount = summary.splitBundleCount,
            obbInstallCount = summary.obbInstallCount,
            virusTotalScans = summary.virusTotalScans,
            trackerScans = summary.trackerScans,
            currentRankName = summary.rank.name,
            unlockedMilestonesCount = summary.unlockedCount,
            totalMilestonesCount = summary.totalMilestones,
        )
    }

    /**
     * Scans device storage for installable package files (.apk, .apks, .xapk, .apkm) and compares them with installed versions.
     *
     * @param searchQuery Optional case-insensitive substring filter for file name or package name.
     * @param maxResults Maximum number of discovered package files to return (1 to 100, defaults to 25).
     * @return List of discovered package files sorted by most recently modified first.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun scanDeviceForPackageFiles(
        searchQuery: String? = null,
        maxResults: Int = 0,
    ): List<DiscoveredPackageFileRecord> = withContext(Dispatchers.IO) {
        val effectiveLimit = if (maxResults <= 0) 25 else maxResults.coerceIn(1, 100)
        val normalizedQuery = searchQuery?.trim()?.lowercase(Locale.ROOT).orEmpty()

        ApkScanner.scan(applicationContext)
            .asSequence()
            .filter { file ->
                normalizedQuery.isEmpty() ||
                    file.name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                    (file.packageName?.lowercase(Locale.ROOT)?.contains(normalizedQuery) == true)
            }
            .take(effectiveLimit)
            .map { file ->
                DiscoveredPackageFileRecord(
                    path = file.path,
                    fileName = file.name,
                    sizeBytes = file.sizeBytes,
                    modifiedAtEpochMillis = file.modifiedMillis,
                    extension = file.extension,
                    packageName = file.packageName,
                    versionCode = file.versionCode,
                    versionName = file.versionName,
                    installState = file.installState.name,
                    isAndroidAutoSupported = file.isAndroidAutoSupported,
                    isWearOsSupported = file.isWearOsSupported,
                )
            }
            .toList()
    }

    /**
     * Creates a PendingIntent that opens the Universal Installer uninstallation confirmation dialog for a specific app.
     *
     * @param packageName Exact Android package name of the installed application to uninstall.
     * @return PendingIntent that launches the uninstall confirmation dialog when triggered.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun openUninstallDialog(
        packageName: String,
    ): PendingIntent = withContext(Dispatchers.IO) {
        val target = packageName.trim()
        if (target.isEmpty()) {
            throw AppFunctionInvalidArgumentException("packageName must not be blank.")
        }
        val exists = InstalledAppsLoader.loadInstalledApps(applicationContext)
            .any { it.packageName.equals(target, ignoreCase = true) }
        if (!exists) {
            throw AppFunctionElementNotFoundException("No installed app found with packageName '$target'.")
        }

        val intent = Intent(applicationContext, DialogUninstallActivity::class.java).apply {
            action = Intent.ACTION_DELETE
            data = "package:$target".toUri()
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        PendingIntent.getActivity(
            applicationContext,
            target.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * Creates a PendingIntent to open a specific screen inside Universal Installer.
     *
     * @param screenName Target screen identifier: "install", "manage", "stats", "downloads", "uninstall_logs", "sync", or "settings".
     * @return PendingIntent that opens the requested screen in Universal Installer.
     */
    @AppFunctionDeclaration(isDescribedByKDoc = true)
    suspend fun openAppScreen(
        screenName: String,
    ): PendingIntent = withContext(Dispatchers.IO) {
        val normalized = screenName.trim().lowercase(Locale.ROOT)
        val targetActivity = when (normalized) {
            "install", "home" -> InstallActivity::class.java
            "manage", "apps", "uninstall" -> ManageActivity::class.java
            "stats", "milestones", "statistics" -> StatsActivity::class.java
            "downloads", "download_history" -> DownloadHistoryActivity::class.java
            "uninstall_logs", "logs" -> UninstallLogsActivity::class.java
            "sync", "tv" -> SyncActivity::class.java
            "settings", "setting" -> SettingActivity::class.java
            else -> throw AppFunctionInvalidArgumentException(
                "Unsupported screenName '$screenName'. Valid values are: install, manage, stats, downloads, uninstall_logs, sync, settings.",
            )
        }

        val intent = Intent(applicationContext, targetActivity).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        PendingIntent.getActivity(
            applicationContext,
            normalized.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun InstalledApp.toRecord(): InstalledAppRecord = InstalledAppRecord(
        packageName = packageName,
        appName = appName,
        versionName = versionName,
        isSystemApp = isSystemApp,
        sizeBytes = sizeBytes,
        installedAtEpochMillis = installedAt,
        lastUpdatedAtEpochMillis = lastUpdatedAt,
        hasSplits = hasSplits,
        isEnabled = enabled,
        installerPackage = installerPackage,
        isAndroidAutoSupported = isAndroidAutoSupported,
    )
}
