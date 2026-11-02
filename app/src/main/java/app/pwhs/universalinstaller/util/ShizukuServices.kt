package app.pwhs.universalinstaller.util

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import app.pwhs.core.util.ShizukuStarterHelper
import eu.darken.porter.sdk.Porter
import eu.darken.porter.sdk.PorterBackend
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import rikka.shizuku.Shizuku

/**
 * The apps that serve the Shizuku API here: Shizuku itself (or forks like Shevery),
 * and Porter, which the app hands to the Shizuku API through Porter's bridge while it is running.
 */
object ShizukuServices {

    const val SHIZUKU_PACKAGE = ShizukuStarterHelper.SHIZUKU_PACKAGE
    const val SHEVERY_PACKAGE = ShizukuStarterHelper.SHEVERY_PACKAGE
    const val PORTER_PACKAGE = ShizukuStarterHelper.PORTER_PACKAGE
    const val SHEVERY_PERMISSION = ShizukuStarterHelper.SHEVERY_PERMISSION

    fun isShizukuInstalled(context: Context): Boolean = ShizukuStarterHelper.isManagerInstalled(context)

    fun isPorterInstalled(context: Context): Boolean = isInstalled(context, PORTER_PACKAGE)

    fun isSheveryInstalled(context: Context): Boolean = ShizukuStarterHelper.isShevery(context)

    /**
     * Checks if Shizuku permission is granted, supporting both official Shizuku API
     * and Shevery privileged runtime permission.
     */
    fun hasShizukuPermission(context: Context? = null): Boolean {
        if (!Shizuku.pingBinder()) return false
        val defaultGranted = runCatching {
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        }.getOrDefault(false)
        if (defaultGranted) return true

        if (context != null) {
            val sheveryGranted = runCatching {
                context.checkSelfPermission(SHEVERY_PERMISSION) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            if (sheveryGranted) return true
        }
        return false
    }

    /** A stopped service is Porter's: Porter is installed and Shizuku is not. */
    fun isPorterOnly(context: Context): Boolean = !isShizukuInstalled(context) && isPorterInstalled(context)

    /** Whether Porter, not a Shizuku server, is what the Shizuku API talks to. */
    val porterServing: Flow<Boolean> = Porter.connection.map { it?.backend == PorterBackend.PORTER }

    fun isPorterServing(): Boolean = Porter.connection.value?.backend == PorterBackend.PORTER

    private fun isInstalled(context: Context, packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
