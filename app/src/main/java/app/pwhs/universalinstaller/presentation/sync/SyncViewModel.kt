package app.pwhs.universalinstaller.presentation.sync

import android.app.Application
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    val state: StateFlow<SyncState> = SyncManager.state
    val serverUrl: StateFlow<String?> = SyncManager.serverUrl
    val pinCode: StateFlow<String?> = SyncManager.pinCode
    val activeConnections: StateFlow<Int> = SyncManager.activeConnections
    val sharedFiles: StateFlow<List<File>> = SyncManager.sharedFiles
    val activeTransfers: StateFlow<Map<String, TransferProgress>> = SyncManager.activeTransfers

    private val baseDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "Universal Installer"
    )

    private val validExtensions = listOf("apk", "apks", "xapk", "apkm", "zip")

    init {
        refreshSharedFiles()
    }

    fun toggleServer(enabled: Boolean) {
        val intent = Intent(getApplication(), SyncService::class.java)
        if (enabled) {
            getApplication<Application>().startService(intent)
        } else {
            intent.action = "STOP"
            getApplication<Application>().startService(intent)
        }
        // Refresh file list when toggling
        refreshSharedFiles()
    }

    fun refreshSharedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!baseDir.exists()) baseDir.mkdirs()
            val files = baseDir.listFiles()
                ?.filter { it.isFile && it.extension.lowercase() in validExtensions }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
            SyncManager.sharedFiles.value = files
        }
    }

    fun copyFilesToShareFolder(uris: List<android.net.Uri>) {
        val app = getApplication<Application>()

        viewModelScope.launch(Dispatchers.IO) {
            if (!baseDir.exists()) baseDir.mkdirs()

            var successCount = 0
            var skippedCount = 0
            uris.forEach { uri ->
                try {
                    val displayName = getDisplayName(uri)
                    val ext = displayName.substringAfterLast('.', "").lowercase()
                    if (ext !in validExtensions) {
                        skippedCount++
                        return@forEach
                    }
                    val targetFile = File(baseDir, displayName)
                    app.contentResolver.openInputStream(uri)?.use { input ->
                        targetFile.outputStream().use { output ->
                            input.copyTo(output)
                        }
                    }
                    successCount++
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Refresh file list after copying
            refreshSharedFiles()

            withContext(Dispatchers.Main) {
                when {
                    successCount > 0 && skippedCount > 0 ->
                        Toast.makeText(app, "Added $successCount file(s), skipped $skippedCount unsupported", Toast.LENGTH_LONG).show()
                    successCount > 0 ->
                        Toast.makeText(app, "Added $successCount file(s) to Sync Folder", Toast.LENGTH_SHORT).show()
                    skippedCount > 0 ->
                        Toast.makeText(app, "No supported package files selected (apk, apks, xapk, apkm, zip)", Toast.LENGTH_LONG).show()
                    else ->
                        Toast.makeText(app, "Failed to copy files", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    fun deleteSharedFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (file.exists()) file.delete()
                refreshSharedFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun getDisplayName(uri: android.net.Uri): String {
        var name = "shared_file_${System.currentTimeMillis()}.apk"
        val cursor = getApplication<Application>().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = it.getString(index)
                }
            }
        }
        return name
    }
}