package app.pwhs.core.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import app.pwhs.core.domain.InstalledApp
import java.io.File

/**
 * Reads installed packages off the [PackageManager]. UI-agnostic and permission-light
 * (no UsageStats here — that needs a special grant; the TV MVP doesn't surface last-used).
 *
 * [getInstalledApps] is a blocking call (it does IPC + file-size stats over every
 * package). Callers MUST run it off the main thread, e.g. `withContext(Dispatchers.IO)`.
 */
class AppRepository(private val context: Context) {

    fun getInstalledApps(includeSystem: Boolean = false): List<InstalledApp> {
        val pm = context.packageManager
        val infos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            pm.getInstalledApplications(0)
        }

        return infos.asSequence()
            .map { it.toInstalledApp(pm) }
            .filter { includeSystem || !it.isSystemApp }
            .sortedBy { it.appName.lowercase() }
            .toList()
    }

    private fun ApplicationInfo.toInstalledApp(pm: PackageManager): InstalledApp {
        val pkgInfo = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, 0)
            }
        }.getOrNull()

        val size = sourceDir?.takeIf { it.isNotBlank() }
            ?.let { runCatching { File(it).length() }.getOrDefault(0L) } ?: 0L

        var installing: String? = null
        var initiating: String? = null
        var originating: String? = null

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            runCatching {
                val sourceInfo = pm.getInstallSourceInfo(packageName)
                installing = sourceInfo.installingPackageName
                initiating = sourceInfo.initiatingPackageName
                originating = sourceInfo.originatingPackageName
            }
        } else {
            @Suppress("DEPRECATION")
            installing = runCatching { pm.getInstallerPackageName(packageName) }.getOrNull()
        }

        val isLeanback = pm.getLeanbackLaunchIntentForPackage(packageName) != null

        return InstalledApp(
            packageName = packageName,
            appName = loadLabel(pm).toString(),
            versionName = pkgInfo?.versionName ?: "",
            isSystemApp = (flags and ApplicationInfo.FLAG_SYSTEM) != 0,
            sizeBytes = size,
            installedAt = pkgInfo?.firstInstallTime ?: 0L,
            hasSplits = !splitSourceDirs.isNullOrEmpty(),
            enabled = enabled,
            installerPackage = installing,
            initiatingPackage = initiating,
            originatingPackage = originating,
            isLeanbackSupported = isLeanback,
        )
    }
}
