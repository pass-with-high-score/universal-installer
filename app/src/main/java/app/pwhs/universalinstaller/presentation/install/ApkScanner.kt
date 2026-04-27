package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File

/**
 * State of a found APK relative to what's currently installed on the device.
 * Drives the chip tag shown next to each scan result.
 */
enum class InstallState {
    /** Couldn't determine — typically split-bundle archives we don't unpack during scan. */
    Unknown,
    /** Package isn't installed on this device. */
    NotInstalled,
    /** Same versionCode is already installed (re-install / no-op upgrade). */
    SameVersion,
    /** APK is older than what's installed (would be a downgrade). */
    Older,
    /** APK is newer than what's installed (would be an update). */
    Newer,
}

data class FoundPackageFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val extension: String,
    /** Parsed from the APK archive at scan time; null for split bundles or parse failures. */
    val packageName: String? = null,
    val versionCode: Long? = null,
    val versionName: String? = null,
    val installState: InstallState = InstallState.Unknown,
)

object ApkScanner {

    private val SUPPORTED_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm")
    private val ARCHIVE_EXTENSIONS = setOf("apks", "xapk", "apkm")

    fun hasAllFilesAccess(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                android.Manifest.permission.READ_EXTERNAL_STORAGE,
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun buildGrantIntent(context: Context): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = "package:${context.packageName}".toUri()
            }
        } else {
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = "package:${context.packageName}".toUri()
            }
        }
    }

    /**
     * Walk external storage looking for installable package files. Returns entries sorted
     * newest-first. Respects coroutine cancellation so the caller can bail on a long scan.
     *
     * For raw .apk files we parse the archive manifest (via PackageManager.getPackageArchiveInfo)
     * and compare its versionCode against the currently-installed package. This drives the
     * "New / Installed / Update / Older" chip tags in the UI. Split-bundle archives
     * (.apks/.xapk/.apkm) are zipped containers — we'd need to extract base.apk to read the
     * manifest, which is too slow during a scan, so they stay InstallState.Unknown and only
     * get the "Split" chip.
     */
    suspend fun scan(context: Context): List<FoundPackageFile> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory() ?: return@withContext emptyList()
        val raw = mutableListOf<FoundPackageFile>()
        scanRecursive(root, raw, depth = 0, maxDepth = 10)
        val pm = context.packageManager
        raw.map { file ->
            currentCoroutineContext().ensureActive()
            if (file.extension == "apk") enrichWithPackageInfo(pm, file) else file
        }.sortedByDescending { it.modifiedMillis }
    }

    private fun enrichWithPackageInfo(
        pm: PackageManager,
        file: FoundPackageFile,
    ): FoundPackageFile {
        val archive = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageArchiveInfo(file.path, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION") pm.getPackageArchiveInfo(file.path, 0)
            }
        }.getOrNull() ?: return file

        val pkgName = archive.packageName ?: return file
        val archiveCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archive.longVersionCode
        } else {
            @Suppress("DEPRECATION") archive.versionCode.toLong()
        }
        val installedCode = runCatching {
            val installed = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.getPackageInfo(pkgName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION") pm.getPackageInfo(pkgName, 0)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                installed.longVersionCode
            } else {
                @Suppress("DEPRECATION") installed.versionCode.toLong()
            }
        }.getOrNull()

        val state = when {
            installedCode == null -> InstallState.NotInstalled
            archiveCode == installedCode -> InstallState.SameVersion
            archiveCode > installedCode -> InstallState.Newer
            else -> InstallState.Older
        }
        return file.copy(
            packageName = pkgName,
            versionCode = archiveCode,
            versionName = archive.versionName,
            installState = state,
        )
    }

    private suspend fun scanRecursive(
        dir: File,
        out: MutableList<FoundPackageFile>,
        depth: Int,
        maxDepth: Int,
    ) {
        currentCoroutineContext().ensureActive()
        if (depth > maxDepth) return
        if (!dir.exists() || !dir.canRead()) return
        val children = runCatching { dir.listFiles() }.getOrNull() ?: return
        for (child in children) {
            currentCoroutineContext().ensureActive()
            if (child.isDirectory) {
                val name = child.name
                // Skip dotfiles, app-scoped dirs (restricted even with MANAGE access), and thumbnails.
                if (name.startsWith(".")) continue
                if (depth == 0 && name == "Android") continue
                scanRecursive(child, out, depth + 1, maxDepth)
            } else {
                val ext = child.extension.lowercase()
                if (ext in SUPPORTED_EXTENSIONS) {
                    out.add(
                        FoundPackageFile(
                            path = child.absolutePath,
                            name = child.name,
                            sizeBytes = child.length(),
                            modifiedMillis = child.lastModified(),
                            extension = ext,
                        )
                    )
                }
            }
        }
    }

}
