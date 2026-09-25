package app.pwhs.updater.presentation.util

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import app.pwhs.updater.presentation.InstalledAppItem

object InstalledAppsLoader {

    fun load(context: Context, trackedPkgSet: Set<String>): List<InstalledAppItem> {
        val pm = context.packageManager
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(0))
        } else {
            pm.getInstalledPackages(0)
        }

        return installedPackages.mapNotNull { pkg ->
            val appInfo = pkg.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
            if (isSystem && pkg.packageName != "app.pwhs.universalinstaller") return@mapNotNull null

            val appName = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrDefault(pkg.packageName)
            val vCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pkg.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                pkg.versionCode.toLong()
            }

            InstalledAppItem(
                packageName = pkg.packageName,
                appName = appName,
                versionName = pkg.versionName ?: "1.0",
                versionCode = vCode,
                isTracked = trackedPkgSet.contains(pkg.packageName),
            )
        }.sortedBy { it.appName.lowercase() }
    }
}
