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
)
