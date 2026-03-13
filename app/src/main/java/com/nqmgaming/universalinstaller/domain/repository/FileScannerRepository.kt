package com.nqmgaming.universalinstaller.domain.repository

import android.net.Uri
import kotlinx.coroutines.flow.Flow

data class LocalAppFile(
    val uri: Uri,
    val name: String,
    val size: Long,
    val dateModified: Long,
    val path: String
)

interface FileScannerRepository {
    suspend fun scanFiles(): Flow<List<LocalAppFile>>
}
