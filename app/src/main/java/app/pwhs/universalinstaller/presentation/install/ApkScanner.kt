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

data class FoundPackageFile(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val extension: String,
)

object ApkScanner {

    private val SUPPORTED_EXTENSIONS = setOf("apk", "apks", "xapk", "apkm")

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
     */
    suspend fun scan(): List<FoundPackageFile> = withContext(Dispatchers.IO) {
        val root = Environment.getExternalStorageDirectory() ?: return@withContext emptyList()
        val results = mutableListOf<FoundPackageFile>()
        scanRecursive(root, results, depth = 0, maxDepth = 10)
        results.sortedByDescending { it.modifiedMillis }
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
