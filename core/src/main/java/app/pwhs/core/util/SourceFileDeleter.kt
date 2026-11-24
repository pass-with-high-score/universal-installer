package app.pwhs.core.util

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.OpenableColumns
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
                if (!file.exists()) {
                    return@runCatching true // Already deleted or doesn't exist
                }
                val deleted = file.delete()
                if (deleted) {
                    MediaScannerConnection.scanFile(context, arrayOf(path), null, null)
                }
                deleted || !file.exists()
            }.onFailure { Log.e(TAG, "Failed to delete source file: $uri", it) }
                .getOrDefault(false)
        }

        // For content:// scheme, check if already non-existent on filesystem
        val resolvedPath = resolveFilePathFromUri(context, uri)
        if (resolvedPath != null && !File(resolvedPath).exists()) {
            return true
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
        val directDeleted = runCatching {
            if (resolvedPath != null) {
                val file = File(resolvedPath)
                if (file.exists()) {
                    if (file.delete()) {
                        runCatching { context.contentResolver.delete(uri, null, null) }
                        MediaScannerConnection.scanFile(context, arrayOf(resolvedPath), null, null)
                        true
                    } else {
                        !file.exists()
                    }
                } else {
                    true
                }
            } else {
                false
            }
        }.onFailure { Log.e(TAG, "Failed to delete resolved file from uri: $uri", it) }
            .getOrDefault(false)
        if (directDeleted) return true

        // Verify if file is actually gone
        return runCatching {
            if (resolvedPath != null) {
                !File(resolvedPath).exists()
            } else {
                context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { false } ?: true
            }
        }.getOrElse { throwable ->
            throwable is java.io.FileNotFoundException
        }
    }

    private const val VERIFY_PREFIX_BYTES = 4096

    private fun resolveFilePathFromUri(context: Context, uri: Uri): String? {
        return runCatching {
            val rawPath = Uri.decode(uri.path) ?: uri.path
            if (rawPath != null) {
                val storageIdx = rawPath.indexOf("/storage/")
                if (storageIdx != -1) {
                    return rawPath.substring(storageIdx)
                }
            }

            if (DocumentsContract.isDocumentUri(context, uri)) {
                val docId = DocumentsContract.getDocumentId(uri)
                when {
                    docId.startsWith("primary:") -> {
                        val relativePath = Uri.decode(docId.substringAfter("primary:"))
                        return File(Environment.getExternalStorageDirectory(), relativePath).absolutePath
                    }
                    docId.startsWith("raw:") -> {
                        return Uri.decode(docId.substringAfter("raw:"))
                    }
                    docId.startsWith("msf:") || docId.startsWith("msd:") -> {
                        val mediaId = docId.substringAfter(':').toLongOrNull()
                        if (mediaId != null) {
                            queryMediaStorePathById(context, mediaId)?.let { return it }
                        }
                    }
                    uri.authority == "com.android.providers.downloads.documents" -> {
                        val downloadId = docId.toLongOrNull()
                        if (downloadId != null) {
                            for (prefix in arrayOf(
                                "content://downloads/public_downloads",
                                "content://downloads/my_downloads",
                                "content://downloads/all_downloads",
                            )) {
                                val downloadUri = ContentUris.withAppendedId(Uri.parse(prefix), downloadId)
                                queryPathColumns(context, downloadUri)?.let { return it }
                            }
                        }
                    }
                }
            }

            queryPathColumns(context, uri)?.let { return it }

            // Android 10–15 DownloadManager notifications (content://downloads/all_downloads/<id>,
            // FileProvider downloads, etc.) often hide _data and RELATIVE_PATH. Resolve via
            // DISPLAY_NAME + SIZE + content prefix verification so we only match the exact file.
            resolveByDisplayNameAndSize(context, uri)?.let { return it }

            null
        }.getOrNull()
    }

    private fun queryPathColumns(context: Context, uri: Uri): String? {
        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(MediaStore.MediaColumns.DATA),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val colIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                    if (colIdx != -1) {
                        val filePath = cursor.getString(colIdx)
                        if (!filePath.isNullOrBlank()) {
                            return filePath
                        }
                    }
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        MediaStore.MediaColumns.DISPLAY_NAME,
                    ),
                    null,
                    null,
                    null,
                )?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val relIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
                        val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                        val relPath = if (relIdx != -1) cursor.getString(relIdx) else null
                        val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
                        if (!relPath.isNullOrBlank() && !name.isNullOrBlank()) {
                            return File(
                                File(Environment.getExternalStorageDirectory(), relPath),
                                name,
                            ).absolutePath
                        }
                    }
                }
            }
        }

        return null
    }

    private fun queryMediaStorePathById(context: Context, mediaId: Long): String? {
        val collections = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            }
            add(MediaStore.Files.getContentUri("external"))
        }
        val selection = "${MediaStore.MediaColumns._ID} = ?"
        val selectionArgs = arrayOf(mediaId.toString())

        for (collection in collections) {
            queryMediaStoreSinglePath(context, collection, selection, selectionArgs)?.let { return it }
        }
        return null
    }

    private fun queryMediaStoreSinglePath(
        context: Context,
        collection: Uri,
        selection: String,
        selectionArgs: Array<String>,
    ): String? {
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
            )
        } else {
            arrayOf(MediaStore.MediaColumns.DATA)
        }
        return runCatching {
            context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (!cursor.moveToFirst()) return@use null
                extractPathFromCursor(cursor)
            }
        }.getOrNull()
    }

    private fun extractPathFromCursor(cursor: android.database.Cursor): String? {
        val dataIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
        if (dataIdx != -1) {
            val data = cursor.getString(dataIdx)
            if (!data.isNullOrBlank()) return data
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val relIdx = cursor.getColumnIndex(MediaStore.MediaColumns.RELATIVE_PATH)
            val nameIdx = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
            val relPath = if (relIdx != -1) cursor.getString(relIdx) else null
            val name = if (nameIdx != -1) cursor.getString(nameIdx) else null
            if (!relPath.isNullOrBlank() && !name.isNullOrBlank()) {
                return File(File(Environment.getExternalStorageDirectory(), relPath), name).absolutePath
            }
        }
        return null
    }

    private fun resolveByDisplayNameAndSize(context: Context, uri: Uri): String? {
        var displayName: String? = null
        var fileSize: Long = -1L

        runCatching {
            context.contentResolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (nameIdx != -1) displayName = cursor.getString(nameIdx)
                    if (sizeIdx != -1 && !cursor.isNull(sizeIdx)) fileSize = cursor.getLong(sizeIdx)
                }
            }
        }

        val safeName = displayName?.trim()?.takeIf {
            it.isNotEmpty() && !it.contains('/') && !it.contains('\\') && !it.contains("..")
        } ?: return null

        val uriPrefix = readUriPrefix(context, uri)
        val candidates = LinkedHashSet<File>()

        // 1. Check the public Downloads directory directly
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val directDownload = File(downloadsDir, safeName)
        if (directDownload.isFile && (fileSize <= 0L || directDownload.length() == fileSize)) {
            candidates.add(directDownload)
        }

        // 2. Query MediaStore (Downloads first, then general Files) by DISPLAY_NAME (+ SIZE)
        val collections = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Downloads.EXTERNAL_CONTENT_URI)
            }
            add(MediaStore.Files.getContentUri("external"))
        }
        val projection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.RELATIVE_PATH,
                MediaStore.MediaColumns.DISPLAY_NAME,
            )
        } else {
            arrayOf(MediaStore.MediaColumns.DATA)
        }
        val selection = if (fileSize > 0L) {
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ? AND ${MediaStore.MediaColumns.SIZE} = ?"
        } else {
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        }
        val selectionArgs = if (fileSize > 0L) {
            arrayOf(safeName, fileSize.toString())
        } else {
            arrayOf(safeName)
        }

        for (collection in collections) {
            runCatching {
                context.contentResolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val path = extractPathFromCursor(cursor) ?: continue
                        val file = File(path)
                        if (file.isFile && (fileSize <= 0L || file.length() == fileSize)) {
                            candidates.add(file)
                        }
                    }
                }
            }
        }

        if (candidates.isEmpty()) return null

        // Prefer a candidate whose leading bytes match the source URI stream
        if (uriPrefix != null) {
            val matched = candidates.filter { file ->
                readFilePrefix(file)?.contentEquals(uriPrefix) == true
            }
            if (matched.size == 1) {
                return matched.first().absolutePath
            }
            // If multiple identical copies exist, prefer the one in Downloads
            if (matched.size > 1) {
                matched.firstOrNull { it.parentFile == downloadsDir }?.let { return it.absolutePath }
            }
            return null
        }

        // If the URI stream can no longer be read, only accept a single unambiguous size-verified match
        return if (candidates.size == 1 && fileSize > 0L) {
            candidates.first().absolutePath
        } else {
            null
        }
    }

    private fun readUriPrefix(context: Context, uri: Uri): ByteArray? = runCatching {
        context.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArray(VERIFY_PREFIX_BYTES)
            var total = 0
            while (total < buf.size) {
                val n = input.read(buf, total, buf.size - total)
                if (n <= 0) break
                total += n
            }
            if (total > 0) buf.copyOf(total) else null
        }
    }.getOrNull()

    private fun readFilePrefix(file: File): ByteArray? = runCatching {
        file.inputStream().use { input ->
            val buf = ByteArray(VERIFY_PREFIX_BYTES)
            var total = 0
            while (total < buf.size) {
                val n = input.read(buf, total, buf.size - total)
                if (n <= 0) break
                total += n
            }
            if (total > 0) buf.copyOf(total) else null
        }
    }.getOrNull()
}
