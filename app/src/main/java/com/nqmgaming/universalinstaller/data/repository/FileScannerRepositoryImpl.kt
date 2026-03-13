package com.nqmgaming.universalinstaller.data.repository

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.nqmgaming.universalinstaller.domain.repository.FileScannerRepository
import com.nqmgaming.universalinstaller.domain.repository.LocalAppFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import timber.log.Timber

class FileScannerRepositoryImpl(
    private val context: Context
) : FileScannerRepository {

    override suspend fun scanFiles(): Flow<List<LocalAppFile>> = flow {
        val files = mutableListOf<LocalAppFile>()
        
        try {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.SIZE,
                MediaStore.Files.FileColumns.DATE_MODIFIED,
                MediaStore.Files.FileColumns.DATA,
                MediaStore.Files.FileColumns.MIME_TYPE
            )

            // Try to filter directly by common extensions
            val selection = "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apk' OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apks' OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.xapk' OR " +
                    "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE '%.apkm'"

            val sortOrder = "${MediaStore.Files.FileColumns.DATE_MODIFIED} DESC"

            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                null,
                sortOrder
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.SIZE)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATA)

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idColumn)
                    val name = cursor.getString(nameColumn)
                    val size = cursor.getLong(sizeColumn)
                    val dateModified = cursor.getLong(dateModifiedColumn)
                    val path = cursor.getString(dataColumn)

                    val contentUri: Uri = Uri.withAppendedPath(
                        MediaStore.Files.getContentUri("external"),
                        id.toString()
                    )

                    files.add(
                        LocalAppFile(
                            uri = contentUri,
                            name = name,
                            size = size,
                            dateModified = dateModified,
                            path = path
                        )
                    )
                }
            }
            emit(files)
        } catch (e: Exception) {
            Timber.e(e, "Error scanning files via MediaStore")
            emit(emptyList()) // Provide empty list instead of crashing
        }
    }.flowOn(Dispatchers.IO)
}
