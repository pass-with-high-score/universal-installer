package app.pwhs.core.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import app.pwhs.core.domain.ApkFile
import app.pwhs.core.install.BUNDLE_EXTS
import java.io.File

/**
 * Finds installable APK/bundle files on the device by combining MediaStore results
 * with a direct recursive scan of common download directories.
 */
object DownloadsApkScanner {

    private val APK_EXTS = setOf("apk", "apks", "xapk", "apkm", "apk+", "zip")

    fun scan(context: Context): List<ApkFile> {
        val results = mutableMapOf<String, ApkFile>()

        // 1. Scan via MediaStore (Content URIs)
        scanMediaStore(context).forEach { results[it.displayName] = it }

        // 2. Scan direct filesystem (File URIs) - helps if MediaStore is stale
        val scanDirs = listOf(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES), // Common for some sideloading apps
            File(Environment.getExternalStorageDirectory(), "Download"),
            File(Environment.getExternalStorageDirectory(), "Downloader"), // Common for "Downloader" app
        )

        scanDirs.forEach { dir ->
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { file ->
                    if (file.isFile && file.extension.lowercase() in APK_EXTS) {
                        // Avoid duplicates if MediaStore already found it (by name for simplicity on TV)
                        if (!results.containsKey(file.name)) {
                            results[file.name] = ApkFile(
                                uri = Uri.fromFile(file).toString(),
                                displayName = file.name,
                                sizeBytes = file.length(),
                                isBundle = file.extension.lowercase() in BUNDLE_EXTS
                            )
                        }
                    }
                }
            }
        }

        return results.values.sortedBy { it.displayName.lowercase() }
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
