package app.pwhs.tv.install

import android.content.Context
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.booleanPreferencesKey
import app.pwhs.core.util.ShizukuStarterHelper
import rikka.shizuku.Shizuku

/** Binder readiness is authoritative, including for compatible TV manager forks. */
object TvShizuku {
    val enabledKey = booleanPreferencesKey("tv_shizuku_enabled")
    const val PERMISSION_REQUEST = 70

    enum class Status { Missing, Stopped, Unsupported, PermissionRequired, Ready }

    fun hasPermission(context: Context): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ||
            context.checkSelfPermission(ShizukuStarterHelper.SHEVERY_PERMISSION) == PackageManager.PERMISSION_GRANTED
    } catch (_: Throwable) {
        false
    }

    fun status(context: Context): Status = try {
        when {
            !Shizuku.pingBinder() -> {
                val installed = ShizukuStarterHelper.isManagerInstalled(context)
                if (installed) Status.Stopped else Status.Missing
            }
            Shizuku.isPreV11() -> Status.Unsupported
            !hasPermission(context) -> Status.PermissionRequired
            else -> Status.Ready
        }
    } catch (_: Exception) {
        Status.Stopped
    }
}
