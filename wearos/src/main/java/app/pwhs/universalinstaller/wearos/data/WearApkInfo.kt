package app.pwhs.universalinstaller.wearos.data

/**
 * APK metadata for a package received from the paired phone and cached on the watch.
 */
data class WearApkInfo(
    /** File name on disk — stable across process restarts, unlike a generated id. */
    val id: String,
    val fileName: String,
    val appName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val minSdk: Int,
    val isBundle: Boolean,
    val sizeBytes: Long,
    /** Absolute path to the cached package file in internal storage. */
    val cachedFilePath: String,
    val receivedAt: Long = System.currentTimeMillis(),
)
