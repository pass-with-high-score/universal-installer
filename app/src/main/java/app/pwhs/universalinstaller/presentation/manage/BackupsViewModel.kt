package app.pwhs.universalinstaller.presentation.manage

import android.app.Application
import android.os.Environment
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

data class BackupFile(
    val file: File,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isSplitBundle: Boolean,
)

data class BackupsUiState(
    val files: List<BackupFile> = emptyList(),
    val totalBytes: Long = 0L,
    val isLoading: Boolean = true,
)

class BackupsViewModel(
    private val application: Application,
) : ViewModel() {

    private val _uiState = MutableStateFlow(BackupsUiState())
    val uiState: StateFlow<BackupsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    val backupsDir: File
        get() = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "UniversalInstaller/Extracted",
        )

    fun refresh() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val files = withContext(Dispatchers.IO) {
                val dir = backupsDir
                if (!dir.exists()) return@withContext emptyList()
                dir.listFiles()
                    ?.filter { it.isFile && it.extension.lowercase() in apkExtensions }
                    ?.sortedByDescending { it.lastModified() }
                    ?.map {
                        BackupFile(
                            file = it,
                            name = it.name,
                            sizeBytes = it.length(),
                            lastModified = it.lastModified(),
                            isSplitBundle = it.extension.lowercase() in splitExtensions,
                        )
                    }
                    ?: emptyList()
            }
            _uiState.value = BackupsUiState(
                files = files,
                totalBytes = files.sumOf { it.sizeBytes },
                isLoading = false,
            )
        }
    }

    fun delete(backup: BackupFile) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) { runCatching { backup.file.delete() } }
            refresh()
        }
    }

    fun deleteAll() {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                _uiState.value.files.forEach { runCatching { it.file.delete() } }
            }
            refresh()
        }
    }

    private companion object {
        val apkExtensions = setOf("apk", "apks", "xapk", "apkm")
        val splitExtensions = setOf("apks", "xapk", "apkm")
    }
}
