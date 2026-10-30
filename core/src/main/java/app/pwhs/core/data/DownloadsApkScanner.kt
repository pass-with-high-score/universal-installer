package app.pwhs.core.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import app.pwhs.core.domain.ApkFile
import app.pwhs.core.install.BUNDLE_EXTS
import java.io.File

/**
 * Finds installable APK/bundle files on the device by combining MediaStore results
 * with a direct recursive scan of common download directories and external drives (USB/OTG).
 */
object DownloadsApkScanner {

    private val APK_EXTS = setOf("apk", "apks", "xapk", "apkm", "apk+", "zip")

    fun scan(context: Context): List<ApkFile> {
        val results = mutableMapOf<String, ApkFile>()

        // 1. Scan via MediaStore (Content URIs)
        scanMediaStore(context).forEach { results[it.displayName] = it }

        // 2. Scan direct internal filesystem (File URIs) - helps if MediaStore is stale
        val scanDirs = mutableListOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), // Common for some sideloading apps
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "Downloader"), // Common for "Downloader" app
        )

        // 3. Scan external storage drives (USB drives, SD cards, OTG)
        scanDirs.addAll(findExternalStorageDirs(context))

        scanDirs.distinctBy { it.absolutePath }.forEach { dir ->
            scanDirectory(dir, results, maxDepth = 2)
        }

        return results.values.sortedBy { it.displayName.lowercase() }
    }

    private fun findExternalStorageDirs(context: Context): List<File> {
        val externalDirs = mutableListOf<File>()

        // Approach A: StorageManager StorageVolumes (Android 7+)
        runCatching {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                sm.storageVolumes.forEach { volume ->
                    if (volume.isRemovable) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            volume.directory?.let { externalDirs.add(it) }
                        }
                    }
                }
            }
        }

        // Approach B: ContextCompat.getExternalFilesDirs / ContextCompat.getExternalCacheDirs (Android 4.4+)
        runCatching {
            val appDirs = ContextCompat.getExternalFilesDirs(context, null)
            appDirs.filterNotNull().forEach { file ->
                // Example: /storage/ABCD-1234/Android/data/... -> root is /storage/ABCD-1234
                val path = file.absolutePath
                val androidIndex = path.indexOf("/Android/")
                if (androidIndex != -1) {
                    val root = File(path.substring(0, androidIndex))
                    if (root.exists() && root.canRead()) {
                        externalDirs.add(root)
                    }
                }
            }
        }

        // Approach C: Common mount points fallback (Android TV / Box / OTG)
        listOf("/storage", "/mnt/media_rw", "/mnt/usbhost").forEach { mountPoint ->
            val dir = File(mountPoint)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { sub ->
                    if (sub.isDirectory && sub.canRead() && sub.name != "emulated" && sub.name != "self") {
                        externalDirs.add(sub)
                    }
                }
            }
        }

        return externalDirs
    }

    private fun scanDirectory(dir: File, results: MutableMap<String, ApkFile>, maxDepth: Int, currentDepth: Int = 0) {
        if (!dir.exists() || !dir.isDirectory || !dir.canRead() || currentDepth > maxDepth) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in APK_EXTS) {
                    if (!results.containsKey(file.name)) {
                        results[file.name] = ApkFile(
                            uri = Uri.fromFile(file).toString(),
                            displayName = file.name,
                            sizeBytes = file.length(),
                            isBundle = ext in BUNDLE_EXTS
                        )
                    }
                }
            } else if (file.isDirectory && currentDepth < maxDepth) {
                // Don't recurse into hidden folders, Android data dirs, etc.
                val name = file.name
                if (!name.startsWith(".") && !name.equals("Android", ignoreCase = true)) {
                    scanDirectory(file, results, maxDepth, currentDepth + 1)
                }
            }
        }
    }

    private fun scanMediaStore(context: Context): List<ApkFile> {
        val collection = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.SIZE,
        )
        val nameCol = MediaStore.Files.FileColumns.DISPLAY_NAME
        val selection = APK_EXTS.joinToString(" OR ") { "$nameCol LIKE '%.$it'" }

        val out = mutableListOf<ApkFile>()
        runCatching {
            context.contentResolver.query(collection, projection, selection, null, "$nameCol ASC")
                ?.use { c ->
                    val idIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val sizeIdx = c.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                    while (c.moveToNext()) {
                        val name = c.getString(nameIdx) ?: continue
                        val uri = ContentUris.withAppendedId(collection, c.getLong(idIdx))
                        out += ApkFile(
                            uri = uri.toString(),
                            displayName = name,
                            sizeBytes = c.getLong(sizeIdx),
                            isBundle = name.substringAfterLast('.', "").lowercase() in BUNDLE_EXTS,
                        )
                    }
                }
        }
        return out
    }
}
