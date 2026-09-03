package app.pwhs.universalinstaller.wearos.domain

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.data.WearApkInfo

/**
 * Cheap pre-install screening: a phone APK usually installs on a watch but does not run on one.
 */
class ApkCompatibilityCheck(private val context: Context) {

    /** Returns a human-readable reason when the package looks wrong for this watch, else null. */
    fun check(info: WearApkInfo): String? {
        if (info.minSdk > Build.VERSION.SDK_INT) {
            return context.getString(
                R.string.incompatible_min_sdk, info.minSdk, Build.VERSION.SDK_INT
            )
        }
        if (!declaresWatchFeature(info.cachedFilePath)) {
            return context.getString(R.string.incompatible_not_a_watch_app)
        }
        return null
    }

    @Suppress("DEPRECATION")
    private fun declaresWatchFeature(path: String): Boolean = runCatching {
        val flags = PackageManager.GET_CONFIGURATIONS
        val pkgInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                path, PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            context.packageManager.getPackageArchiveInfo(path, flags)
        }
        pkgInfo?.reqFeatures?.any { it.name == WATCH_FEATURE } == true
    }.getOrDefault(false)

    private companion object {
        const val WATCH_FEATURE = "android.hardware.type.watch"
    }
}
