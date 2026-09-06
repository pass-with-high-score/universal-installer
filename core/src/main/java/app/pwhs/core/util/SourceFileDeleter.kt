package app.pwhs.core.util

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

object SourceFileDeleter {
    private const val TAG = "SourceFileDeleter"

    fun deleteSourceFile(context: Context, uri: Uri): Boolean {
        // 1. Direct file:// scheme
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val path = uri.path
            if (path == null) {
                Log.e(TAG, "No path on file uri: $uri")
                return false
            }
            return runCatching {
                val file = File(path)
                val deleted = file.exists() && file.delete()
                if (deleted) {
                    MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                }
                deleted
            }.onFailure { Log.e(TAG, "Failed to delete source file: $uri", it) }
                .getOrDefault(false)
        }

        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri, Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )
        }

        // 2. DocumentsContract.deleteDocument
        val docDeleted = runCatching {
            if (DocumentsContract.isDocumentUri(context, uri)) {
                DocumentsContract.deleteDocument(context.contentResolver, uri)
            } else {
                false
            }
        }.getOrDefault(false)
        if (docDeleted) return true

        // 3. DocumentFile delete
        val docFileDeleted = runCatching {
            DocumentFile.fromSingleUri(context, uri)?.delete() == true
        }.getOrDefault(false)
        if (docFileDeleted) return true

        // 4. ContentResolver.delete
        val crDeleted = runCatching {
            context.contentResolver.delete(uri, null, null) > 0
        }.getOrDefault(false)
        if (crDeleted) return true

        // 5. Direct path resolution fallback (e.g. MediaStore DATA column, storage path, MANAGE_EXTERNAL_STORAGE)
        return runCatching {
            val resolvedPath = resolveFilePathFromUri(context, uri)
            if (resolvedPath != null) {
                val file = File(resolvedPath)
                if (file.exists() && file.delete()) {
                    runCatching { context.contentResolver.delete(uri, null, null) }
                    MediaScannerConnection.scanFile(context, arrayOf(resolvedPath), null, null)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        }.onFailure { Log.e(TAG, "Failed to delete resolved file from uri: $uri", it) }
            .getOrDefault(false)
    }

    private fun resolveFilePathFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            val rawPath = uri.path
            if (rawPath != null) {
                val storageIdx = rawPath.indexOf("/storage/")
                if (storageIdx != -1) {
                    val candidate = rawPath.substring(storageIdx)
                    if (File(candidate).exists()) return candidate
                }
            }

            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                if (docId.startsWith("primary:")) {
                    val relativePath = docId.substringAfter("primary:")
                    val file = File(Environment.getExternalStorageDirectory(), relativePath)
                    if (file.exists()) return file.absolutePath
                }
            }

            val projection = arrayOf(MediaStore.MediaColumns.DATA)
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (colIdx != -1) {
                        val filePath = cursor.getString(colIdx)
                        if (!filePath.isNullOrBlank() && File(filePath).exists()) {
                            return filePath
                        }
                    }
                }
            }
            null
        }.getOrNull()
    }
}
