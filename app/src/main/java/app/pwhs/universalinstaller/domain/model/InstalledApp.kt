package app.pwhs.universalinstaller.domain.model

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    /** APK size on disk, bytes. 0 if unknown. */
    val sizeBytes: Long = 0L,
    /** First install time, epoch ms. 0 if unknown. */
    val installedAt: Long = 0L,
    /** Last time the app was used (UsageStatsManager). 0 if unknown or no permission. */
    val lastUsedAt: Long = 0L,
    /** True when the install has split APKs (`splitSourceDirs` non-empty). */
    val hasSplits: Boolean = false,
    /** Mirror of [android.content.pm.ApplicationInfo.enabled]. False after `pm disable`. */
    val enabled: Boolean = true,
    /**
     * The package that registered as the installer for this app, if known. Examples:
     * `com.android.vending` (Play), `org.fdroid.fdroid`, `com.aurora.store`. Null on
     * sideload, unknown, or platform pre-installed apps.
     */
    val installerPackage: String? = null,
)
