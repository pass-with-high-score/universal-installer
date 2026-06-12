package app.pwhs.universalinstaller.presentation.manage

import android.app.Application
import android.os.Environment
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.dataStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val extractorOutputPath: String = "",
    val extractorFilenameTemplate: String = "{name}-{version}",
)

class BackupsViewModel(
    private val application: Application,
) : ViewModel() {

    private val dataStore = application.dataStore
    private val _filesState = MutableStateFlow<List<BackupFile>>(emptyList())
    private val _isLoading = MutableStateFlow(true)

    val uiState: StateFlow<BackupsUiState> = combine(
        _filesState,
        _isLoading,
        dataStore.data.map { it[PreferencesKeys.APK_EXTRACTOR_OUTPUT_PATH] ?: "" },
        dataStore.data.map { it[PreferencesKeys.APK_EXTRACTOR_FILENAME_TEMPLATE] ?: "{name}-{version}" }
    ) { files, loading, path, template ->
        BackupsUiState(
            files = files,
            totalBytes = files.sumOf { it.sizeBytes },
            isLoading = loading,
            extractorOutputPath = path,
            extractorFilenameTemplate = template
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), BackupsUiState())

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
            _isLoading.value = true
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
            _filesState.value = files
            _isLoading.value = false
        }
    }

    fun setExtractorOutputPath(path: String) {
        viewModelScope.launch {
            if (path.startsWith("content://")) {
                runCatching {
                    application.contentResolver.takePersistableUriPermission(
                        android.net.Uri.parse(path),
                        android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
            }
            dataStore.edit { prefs -> prefs[PreferencesKeys.APK_EXTRACTOR_OUTPUT_PATH] = path }
        }
    }

    fun setExtractorFilenameTemplate(template: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.APK_EXTRACTOR_FILENAME_TEMPLATE] = template }
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
                _filesState.value.forEach { runCatching { it.file.delete() } }
            }
            refresh()
        }
    }

    private companion object {
        val apkExtensions = setOf("apk", "apks", "xapk", "apkm")
        val splitExtensions = setOf("apks", "xapk", "apkm")
    }
}
