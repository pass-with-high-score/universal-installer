package app.pwhs.universalinstaller.privileged

import android.content.ComponentName
import android.content.Intent
import android.content.pm.IPackageManager
import android.os.IBinder
import android.os.ServiceManager
import app.pwhs.universalinstaller.util.DefaultInstallerLogic
import com.topjohnwu.superuser.ipc.RootService

/**
 * libsu-spawned process running as UID 0. We hand back a Binder implementing
 * [IPrivilegedService]; calls into it execute with root authority, which is the only way
 * to reach hidden `IPackageManager.addPreferredActivity` & friends — those reject shell
 * UID 2000 and certainly reject the app's own UID.
 */
class PrivilegedRootService : RootService() {

    override fun onBind(intent: Intent): IBinder = ServiceBinder() as IBinder

    private class ServiceBinder : IPrivilegedService.Stub() {
        override fun setDefaultInstaller(component: ComponentName, lock: Boolean) {
            // ServiceManager.getService runs in this root process so the returned binder is
            // not wrapped — direct calls land at PackageManagerService with caller UID = 0.
            val pmBinder = ServiceManager.getService("package")
                ?: error("system_service 'package' returned null in root process")
            val iPm = IPackageManager.Stub.asInterface(pmBinder)
            DefaultInstallerLogic.setDefaultInstaller(
                iPackageManager = iPm,
                component = component,
                lock = lock,
                hasSystemLevelPermission = true,
            )
        }
    }
}
