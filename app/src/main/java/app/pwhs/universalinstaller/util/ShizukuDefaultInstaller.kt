package app.pwhs.universalinstaller.util

import android.content.ComponentName
import android.content.pm.IPackageManager
import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Drives [DefaultInstallerLogic] over Shizuku's binder. Caller has already checked that
 * Shizuku is READY (binder alive, permission granted).
 *
 * `hasSystemLevelPermission` is only true when Sui (or a privileged Shizuku) is running
 * as UID 0 — in that case we can also call the persistent variants. Otherwise we're on
 * shell UID 2000 and skip them.
 */
object ShizukuDefaultInstaller {

    suspend fun setDefaultInstaller(component: ComponentName, lock: Boolean): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    HiddenApiBypass.addHiddenApiExemptions(
                        "Landroid/content/pm/IPackageManager;",
                    )
                }
                val packageBinder = SystemServiceHelper.getSystemService("package")
                    ?: error("system_service 'package' returned null binder")
                val iPm: IPackageManager = IPackageManager.Stub.asInterface(
                    ShizukuBinderWrapper(packageBinder),
                )
                val hasSystemLevel = runCatching { Shizuku.getUid() == 0 }.getOrDefault(false)
                DefaultInstallerLogic.setDefaultInstaller(iPm, component, lock, hasSystemLevel)
            }
        }
}
