package app.pwhs.universalinstaller.appfunctions

import androidx.appfunctions.AppFunctionSerializable

/** Summary of an application currently installed on the device. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class InstalledAppRecord(
    /** The unique Android package name (e.g., "com.example.app"). */
    val packageName: String,
    /** The user-visible display name of the application. */
    val appName: String,
    /** The installed version name string (e.g., "1.2.0"). */
    val versionName: String,
    /** Whether the application is pre-installed on the system partition. */
    val isSystemApp: Boolean,
    /** The base APK size on disk in bytes. */
    val sizeBytes: Long,
    /** The timestamp when the app was first installed, in epoch milliseconds. */
    val installedAtEpochMillis: Long,
    /** The timestamp when the app was last updated, in epoch milliseconds. */
    val lastUpdatedAtEpochMillis: Long,
    /** Whether the application uses split APKs. */
    val hasSplits: Boolean,
    /** Whether the application is currently enabled on the device. */
    val isEnabled: Boolean,
    /** The package name of the installer that installed this app, or null if sideloaded/unknown. */
    val installerPackage: String?,
    /** Whether the application supports Android Auto. */
    val isAndroidAutoSupported: Boolean,
)

/** Record of a past package installation attempt in Universal Installer. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class InstallHistoryRecord(
    /** The unique database row ID of the history entry. */
    val id: Long,
    /** The display name of the application that was installed. */
    val appName: String,
    /** The Android package name of the application. */
    val packageName: String,
    /** The original package file name (e.g., "app-release.apk"). */
    val fileName: String,
    /** The version name of the package that was installed. */
    val versionName: String,
    /** The previous installed version name before an update or downgrade, or null for new installs. */
    val oldVersionName: String?,
    /** The package file size in bytes. */
    val fileSizeBytes: Long,
    /** Whether the installation succeeded. */
    val isSuccess: Boolean,
    /** The failure reason if the installation failed, or null on success. */
    val errorMessage: String?,
    /** The timestamp when the installation completed, in epoch milliseconds. */
    val installedAtEpochMillis: Long,
    /** The installer backend used (e.g., "Default", "Shizuku", "Root", "Dhizuku"). */
    val installerMode: String?,
    /** The type of operation performed ("INSTALL", "UPDATE", or "DOWNGRADE"). */
    val operationType: String?,
)

/** Record of a past application uninstallation attempt in Universal Installer. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class UninstallLogRecord(
    /** The unique database row ID of the uninstall log entry. */
    val id: Long,
    /** The Android package name of the uninstalled application. */
    val packageName: String,
    /** The display name of the uninstalled application. */
    val appName: String,
    /** Whether the uninstallation succeeded. */
    val isSuccess: Boolean,
    /** The error message if the uninstallation failed, or null on success. */
    val errorMessage: String?,
    /** The timestamp when the uninstallation occurred, in epoch milliseconds. */
    val uninstalledAtEpochMillis: Long,
)

/** Record of a package file downloaded via URL in Universal Installer. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DownloadHistoryRecord(
    /** The unique database row ID of the download entry. */
    val id: Long,
    /** The source HTTP/HTTPS URL from which the package was downloaded. */
    val url: String,
    /** The saved file name of the downloaded package. */
    val fileName: String,
    /** The local filesystem path where the downloaded package is stored. */
    val filePath: String,
    /** The size of the downloaded file in bytes. */
    val sizeBytes: Long,
    /** The timestamp when the download completed, in epoch milliseconds. */
    val downloadedAtEpochMillis: Long,
)

/** Lifetime statistics and milestones summary for Universal Installer. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class InstallerStatsRecord(
    /** Total number of successful package installations. */
    val totalSuccessInstalls: Int,
    /** Total number of failed package installation attempts. */
    val totalFailedInstalls: Int,
    /** Overall installation success rate percentage (0 to 100). */
    val successRatePercent: Int,
    /** Number of fresh package installations. */
    val newInstallCount: Int,
    /** Number of package updates performed. */
    val updateCount: Int,
    /** Number of package downgrades performed. */
    val downgradeCount: Int,
    /** Total number of applications uninstalled through Universal Installer. */
    val totalUninstalls: Int,
    /** Total number of packages downloaded via URL. */
    val totalDownloads: Int,
    /** Total volume of packages installed in bytes. */
    val totalBytesInstalled: Long,
    /** Total storage space freed by auto-cleaning source APK files in bytes. */
    val totalBytesCleaned: Long,
    /** Total number of source package files deleted after installation. */
    val totalFilesCleaned: Int,
    /** Number of single APK files installed. */
    val singleApkCount: Int,
    /** Number of split bundles (.xapk, .apks, .apkm) installed. */
    val splitBundleCount: Int,
    /** Number of packages installed with OBB game data. */
    val obbInstallCount: Int,
    /** Number of VirusTotal security scans performed. */
    val virusTotalScans: Int,
    /** Number of DEX tracker scans performed. */
    val trackerScans: Int,
    /** Current user rank title based on unlocked milestones. */
    val currentRankName: String,
    /** Number of unlocked achievement milestones. */
    val unlockedMilestonesCount: Int,
    /** Total number of available achievement milestones. */
    val totalMilestonesCount: Int,
)

/** Details of an installable package file discovered on device storage. */
@AppFunctionSerializable(isDescribedByKDoc = true)
data class DiscoveredPackageFileRecord(
    /** The absolute filesystem path to the package file. */
    val path: String,
    /** The file name including extension (e.g., "example.apk" or "bundle.xapk"). */
    val fileName: String,
    /** The file size in bytes. */
    val sizeBytes: Long,
    /** The last modified timestamp of the file, in epoch milliseconds. */
    val modifiedAtEpochMillis: Long,
    /** The lowercase file extension ("apk", "apks", "xapk", "apkm", or "apk+"). */
    val extension: String,
    /** The parsed Android package name if available (for standard APK files), or null for archives. */
    val packageName: String?,
    /** The parsed version code of the package if available, or null. */
    val versionCode: Long?,
    /** The parsed version name of the package if available, or null. */
    val versionName: String?,
    /** Comparison state against installed apps: "NotInstalled", "SameVersion", "Newer", "Older", or "Unknown". */
    val installState: String,
    /** Whether the package declares Android Auto support. */
    val isAndroidAutoSupported: Boolean,
    /** Whether the package targets Wear OS watches. */
    val isWearOsSupported: Boolean,
)
