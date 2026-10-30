package app.pwhs.core.domain

/**
 * A package installed on the device. UI-agnostic so both the phone and TV front-ends
 * render from the same shape. Mirrors the model the phone app grew; lives in :core so
 * it is the single source of truth as `:mobile` migrates onto the shared engine.
 */
data class InstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
    /** APK size on disk, bytes. 0 if unknown. */
    val sizeBytes: Long = 0L,
    /** First install time, epoch ms. 0 if unknown. */
    val installedAt: Long = 0L,
    /** True when the install has split APKs (`splitSourceDirs` non-empty). */
    val hasSplits: Boolean = false,
    /** Mirror of [android.content.pm.ApplicationInfo.enabled]. False after `pm disable`. */
    val enabled: Boolean = true,
    /** Package that registered as the installer, if known (Play, F-Droid, …); null on sideload. */
    val installerPackage: String? = null,
    /** Package that initiated the installation request (Android 11+). */
    val initiatingPackage: String? = null,
    /** Package from which the install originated (e.g. app that downloaded it, Android 11+). */
    val originatingPackage: String? = null,
    /** Whether the app declares Android Auto compatibility. */
    val isAndroidAutoSupported: Boolean = false,
    /** Whether the app has Leanback launcher support (TV-friendly). */
    val isLeanbackSupported: Boolean = false,
)
