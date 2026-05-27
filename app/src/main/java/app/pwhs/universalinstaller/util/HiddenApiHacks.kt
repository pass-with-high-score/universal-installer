package app.pwhs.universalinstaller.util

import android.content.Context
import android.content.pm.IPackageInstaller
import android.content.pm.IPackageManager
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.IBinder
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/**
 * Utility to access hidden Android APIs for multi-user installation support.
 * Inspired by InstallerX-Revived.
 */
object HiddenApiHacks {

    fun createPackageInstallerForUser(context: Context, userId: Int): PackageInstaller? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/content/pm/IPackageManager;",
                "Landroid/content/pm/IPackageInstaller;",
                "Landroid/content/pm/PackageInstaller;",
                "Landroid/os/UserHandle;"
            )
        }

        return try {
            val packageBinder = SystemServiceHelper.getSystemService("package")
            val iPackageManager = IPackageManager.Stub.asInterface(ShizukuBinderWrapper(packageBinder))
            val iPackageInstaller = IPackageInstaller.Stub.asInterface(
                ShizukuBinderWrapper(iPackageManager.packageInstaller.asBinder())
            )
            
            val installerPackageName = if (rikka.shizuku.Shizuku.getUid() == 0) {
                context.packageName
            } else {
                "com.android.shell"
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val constructor = PackageInstaller::class.java.getDeclaredConstructor(
                    IPackageInstaller::class.java,
                    String::class.java,
                    String::class.java,
                    Int::class.java
                )
                constructor.isAccessible = true
                constructor.newInstance(iPackageInstaller, installerPackageName, context.attributionTag, userId)
            } else {
                val constructor = PackageInstaller::class.java.getDeclaredConstructor(
                    IPackageInstaller::class.java,
                    String::class.java,
                    Int::class.java
                )
                constructor.isAccessible = true
                constructor.newInstance(iPackageInstaller, installerPackageName, userId)
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Toggles the enabled state of a package using Shizuku.
     * Uses reflection to handle different IPackageManager.setApplicationEnabledSetting signatures.
     */
    fun setApplicationEnabledSetting(packageName: String, newState: Int, flags: Int) {
        try {
            val packageBinder = SystemServiceHelper.getSystemService("package")
            val iPackageManager = IPackageManager.Stub.asInterface(ShizukuBinderWrapper(packageBinder))
            
            // Try 5-param version (Android 10+)
            try {
                val method = iPackageManager.javaClass.getMethod(
                    "setApplicationEnabledSetting",
                    String::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java,
                    String::class.java
                )
                method.invoke(iPackageManager, packageName, newState, flags, 0, "com.android.shell")
                return
            } catch (e: NoSuchMethodException) {
                // Try 4-param version (Older Android)
                val method = iPackageManager.javaClass.getMethod(
                    "setApplicationEnabledSetting",
                    String::class.java,
                    Int::class.java,
                    Int::class.java,
                    Int::class.java
                )
                method.invoke(iPackageManager, packageName, newState, flags, 0)
            }
        } catch (e: Exception) {
            try {
                val newProcessMethod = rikka.shizuku.Shizuku::class.java.getMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                )
                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("pm", if (newState <= 1) "enable" else "disable-user", packageName),
                    null,
                    null
                ) as Process
                process.waitFor()
            } catch (ex: Exception) {
                // Ignore
            }
        }
    }
}
