package app.pwhs.core.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Whether a package declares `uses-feature android.hardware.type.watch`.
 *
 * A false answer is a hint, not a verdict — plenty of standalone apps run fine on Wear OS without
 * declaring the feature, so callers should warn rather than block.
 */
object WatchAppCheck {

    const val WATCH_FEATURE = "android.hardware.type.watch"

    fun declaresWatchFeature(context: Context, apkPath: String): Boolean = runCatching {
        val flags = PackageManager.GET_CONFIGURATIONS
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                apkPath, PackageManager.PackageInfoFlags.of(flags.toLong())
            )
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageArchiveInfo(apkPath, flags)
        }
        info?.reqFeatures?.any { it.name == WATCH_FEATURE } == true
    }.getOrDefault(false)

    /** Unreadable or unparsable content resolves to `true` so an uncertain read never blocks a send. */
    suspend fun declaresWatchFeature(context: Context, uri: Uri): Boolean =
        withContext(Dispatchers.IO) {
            val temp = File(context.cacheDir, "watch_feature_check_${System.currentTimeMillis()}.apk")
            try {
                context.contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { output -> input.copyTo(output) }
                } ?: return@withContext true
                declaresWatchFeature(context, temp.absolutePath)
            } catch (_: Exception) {
                true
            } finally {
                temp.delete()
            }
        }
}
