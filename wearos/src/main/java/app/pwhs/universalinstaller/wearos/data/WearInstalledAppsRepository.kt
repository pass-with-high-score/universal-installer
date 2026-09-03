package app.pwhs.universalinstaller.wearos.data

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import app.pwhs.core.domain.InstalledApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Packages installed on the watch. Deliberately thinner than the phone's loader — a watch has no
 * usage-stats permission worth asking for and nothing to say about Android Auto.
 */
class WearInstalledAppsRepository(private val context: Context) {

    private val _apps = MutableStateFlow<List<InstalledApp>>(emptyList())
    val apps: StateFlow<List<InstalledApp>> = _apps.asStateFlow()

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    suspend fun refresh(includeSystem: Boolean) = withContext(Dispatchers.IO) {
        _isLoading.value = true
        val pm = context.packageManager
        val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getInstalledApplications(PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getInstalledApplications(0)
        }
        _apps.value = installed
            .filter { includeSystem || !it.isSystem() }
            .map { it.toInstalledApp(pm) }
            .sortedBy { it.appName.lowercase() }
        _isLoading.value = false
    }

    /** Source dir doubles as the path Coil's APK icon fetcher reads. */
    fun sourceDirOf(packageName: String): String? = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getApplicationInfo(
                packageName, PackageManager.ApplicationInfoFlags.of(0)
            )
        } else {
            @Suppress("DEPRECATION") context.packageManager.getApplicationInfo(packageName, 0)
        }.sourceDir
    }.getOrNull()

    // An updated system app is one the user chose to install over, so it belongs in the user list.
    private fun ApplicationInfo.isSystem(): Boolean =
        flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
            flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0

    private fun ApplicationInfo.toInstalledApp(pm: PackageManager): InstalledApp {
        val version = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION") pm.getPackageInfo(packageName, 0)
            }.versionName
        }.getOrNull().orEmpty()

        return InstalledApp(
            packageName = packageName,
            appName = loadLabel(pm).toString(),
            versionName = version,
            isSystemApp = isSystem(),
            sizeBytes = runCatching { File(sourceDir).length() }.getOrDefault(0L),
            hasSplits = !splitSourceDirs.isNullOrEmpty(),
            enabled = enabled,
        )
    }
}
