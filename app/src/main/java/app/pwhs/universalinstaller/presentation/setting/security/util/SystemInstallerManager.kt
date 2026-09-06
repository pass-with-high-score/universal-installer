package app.pwhs.universalinstaller.presentation.setting.security.util

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import app.pwhs.universalinstaller.presentation.install.controller.ShizukuShellExecutor
import app.pwhs.universalinstaller.util.HiddenApiHacks
import com.topjohnwu.superuser.Shell
import timber.log.Timber

/**
 * Manages the state of the Android system package installer and other apps'
 * installation permissions using Shizuku or Root privileges.
 */
object SystemInstallerManager {

    private val CANDIDATE_INSTALLERS = listOf(
        "com.google.android.packageinstaller",
        "com.android.packageinstaller",
        "com.samsung.android.packageinstaller",
    )

    /**
     * Resolves the primary system package installer on this device.
     */
    fun findSystemPackageInstaller(context: Context): String? {
        val pm = context.packageManager
        for (pkg in CANDIDATE_INSTALLERS) {
            try {
                // Check if package exists even if disabled
                pm.getPackageInfo(pkg, PackageManager.MATCH_UNINSTALLED_PACKAGES)
                return pkg
            } catch (_: PackageManager.NameNotFoundException) {
                // Continue searching
            }
        }
        return null
    }

    /**
     * Checks if the system package installer is currently disabled.
     */
    fun isSystemPackageInstallerDisabled(context: Context): Boolean {
        val pkg = findSystemPackageInstaller(context) ?: return false
        return try {
            val state = context.packageManager.getApplicationEnabledSetting(pkg)
            state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER ||
                state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        } catch (_: Throwable) {
            false
        }
    }

    /**
     * Enables or disables (freezes) the system package installer using Shizuku or Root.
     * Returns true if the operation succeeded.
     */
    suspend fun setSystemInstallerEnabled(
        context: Context,
        enabled: Boolean,
        isRoot: Boolean,
        isShizuku: Boolean,
    ): Boolean {
        val pkg = findSystemPackageInstaller(context) ?: return false
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
        }

        val cmd = if (enabled) {
            "pm enable --user 0 $pkg"
        } else {
            "pm disable-user --user 0 $pkg"
        }

        if (isRoot && Shell.isAppGrantedRoot() == true) {
            val result = Shell.cmd(cmd).exec()
            if (result.isSuccess) {
                Timber.i("System installer $pkg set enabled=$enabled via Root")
                return true
            }
        }

        if (isShizuku && ShizukuShellExecutor.isReady()) {
            try {
                HiddenApiHacks.setApplicationEnabledSetting(pkg, newState, 0)
                Timber.i("System installer $pkg set enabled=$enabled via Shizuku HiddenApi")
                return true
            } catch (t: Throwable) {
                Timber.w(t, "HiddenApi failed, trying Shizuku shell")
                val result = ShizukuShellExecutor.setEnabled(pkg, enabled)
                if (result.isSuccess) {
                    Timber.i("System installer $pkg set enabled=$enabled via Shizuku shell")
                    return true
                }
            }
        }

        return false
    }

    /**
     * Revokes `REQUEST_INSTALL_PACKAGES` permission from all third-party apps on the device
     * using `appops set <pkg> REQUEST_INSTALL_PACKAGES ignore`.
     * Returns the count of affected packages.
     */
    suspend fun revokeOtherAppsInstallPermission(
        context: Context,
        isRoot: Boolean,
        isShizuku: Boolean,
    ): Int {
        val pm = context.packageManager
        val packages = pm.getInstalledPackages(PackageManager.GET_PERMISSIONS)
        val targetPackages = mutableListOf<String>()

        for (info in packages) {
            if (info.packageName == context.packageName) continue
            val requested = info.requestedPermissions ?: continue
            if (Manifest.permission.REQUEST_INSTALL_PACKAGES in requested) {
                targetPackages.add(info.packageName)
            }
        }

        if (targetPackages.isEmpty()) return 0

        var revokedCount = 0
        for (pkg in targetPackages) {
            var success = false

            if (isRoot && Shell.isAppGrantedRoot() == true) {
                val cmd = "appops set $pkg REQUEST_INSTALL_PACKAGES ignore"
                val res = Shell.cmd(cmd).exec()
                success = res.isSuccess
            } else if (isShizuku && ShizukuShellExecutor.isReady()) {
                val res = ShizukuShellExecutor.setAppOp(pkg, "REQUEST_INSTALL_PACKAGES", "ignore")
                success = res.isSuccess
            }

            if (success) {
                revokedCount++
            }
        }

        Timber.i("Revoked REQUEST_INSTALL_PACKAGES from $revokedCount / ${targetPackages.size} apps")
        return revokedCount
    }
}
