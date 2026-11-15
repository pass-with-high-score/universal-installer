package app.pwhs.universalinstaller.presentation.sync

import android.app.Application
import android.content.Context
import android.content.Intent
import android.os.Environment
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.install.DialogInstallActivity
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.SyncOptions
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

data class SyncUiState(
    // Server status & Wi-Fi LAN
    val state: SyncState = SyncState.STOPPED,
    val serverUrl: String? = null,
    val pinCode: String? = null,
    val activeConnections: Int = 0,
    val activeTransfers: Map<String, TransferProgress> = emptyMap(),
    val syncOptions: SyncOptions = SyncOptions(),

    // Tab 1: Share / Send files
    val sharedFiles: List<File> = emptyList(),

    // Tab 2: Received packages
    val receivedFiles: List<File> = emptyList(),
)

class SyncViewModel(application: Application) : AndroidViewModel(application) {
    private val dataStore = application.dataStore

    private val baseDir = File(
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
        "UniversalInstaller",
    )
    private val receivedDir = File(baseDir, "received")

    private val validExtensions = listOf("apk", "apks", "xapk", "apkm", "zip", "apk+")

    private val _recentlyReceived = MutableStateFlow<File?>(null)
    val recentlyReceived: StateFlow<File?> = _recentlyReceived.asStateFlow()

    private val _receivedFiles = MutableStateFlow<List<File>>(emptyList())

    private val serverSubState = combine(
        SyncManager.state,
        SyncManager.serverUrl,
        SyncManager.pinCode,
        SyncManager.activeConnections,
        SyncManager.sharedFiles,
    ) { state, serverUrl, pinCode, activeConns, sharedFiles ->
        ServerCoreState(state, serverUrl, pinCode, activeConns, sharedFiles)
    }

    val uiState: StateFlow<SyncUiState> = combine(
        serverSubState,
        SyncManager.activeTransfers,
        dataStore.data.map { prefs ->
            SyncOptions(
                requirePin = prefs[PreferencesKeys.SYNC_REQUIRE_PIN] ?: true,
                pinCode = prefs[PreferencesKeys.SYNC_PIN_CODE] ?: "",
                serverPort = prefs[PreferencesKeys.SYNC_SERVER_PORT] ?: "8080"
            )
        },
        _receivedFiles,
    ) { server, transfers, syncOptions, receivedFiles ->
        SyncUiState(
            state = server.state,
            serverUrl = server.serverUrl,
            pinCode = server.pinCode,
            activeConnections = server.activeConnections,
            sharedFiles = server.sharedFiles,
            activeTransfers = transfers,
            syncOptions = syncOptions,
            receivedFiles = receivedFiles,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SyncUiState())

    init {
        refreshSharedFiles()
        refreshReceivedFiles()

        // Listen for files received via ApkHttpServer (Web upload)
        viewModelScope.launch {
            SyncManager.fileReceived.collect { file ->
                refreshReceivedFiles()
                _recentlyReceived.value = file
            }
        }
    }

    fun toggleServer(enabled: Boolean) {
        val intent = Intent(getApplication(), SyncService::class.java)
        if (enabled) {
            Telemetry.feature(TelemetryEvents.FEATURE_LAN_SHARE)
            try {
                ContextCompat.startForegroundService(getApplication(), intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to start SyncService as foreground service")
                SyncManager.state.value = SyncState.ERROR
            }
        } else {
            intent.action = "STOP"
            try {
                getApplication<Application>().startService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Failed to stop SyncService")
            }
        }
        refreshSharedFiles()
    }

    fun setSyncRequirePin(enabled: Boolean) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.SYNC_REQUIRE_PIN] = enabled }
        }
    }

    fun setSyncPinCode(code: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.SYNC_PIN_CODE] = code }
        }
    }

    fun setSyncServerPort(port: String) {
        viewModelScope.launch {
            dataStore.edit { prefs -> prefs[PreferencesKeys.SYNC_SERVER_PORT] = port }
        }
    }

    fun refreshSharedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!baseDir.exists()) baseDir.mkdirs()
            val files = baseDir.listFiles()
                ?.filter { it.isFile && (it.extension.lowercase() in validExtensions || it.name.endsWith(".apk+", true)) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
            SyncManager.sharedFiles.value = files
        }
    }

    fun refreshReceivedFiles() {
        viewModelScope.launch(Dispatchers.IO) {
            if (!receivedDir.exists()) receivedDir.mkdirs()
            val files = receivedDir.listFiles()
                ?.filter { it.isFile && (it.extension.lowercase() in validExtensions || it.name.endsWith(".apk+", true)) }
                ?.sortedByDescending { it.lastModified() }
                ?: emptyList()
            _receivedFiles.value = files
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
                    if (ext !in validExtensions && !displayName.endsWith(".apk+", true)) {
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

            refreshSharedFiles()

            withContext(Dispatchers.Main) {
                when {
                    successCount > 0 && skippedCount > 0 ->
                        Toast.makeText(app, "Added $successCount file(s), skipped $skippedCount unsupported", Toast.LENGTH_LONG).show()
                    successCount > 0 ->
                        Toast.makeText(app, "Added $successCount file(s) to Sync Folder", Toast.LENGTH_SHORT).show()
                    skippedCount > 0 ->
                        Toast.makeText(app, "No supported package files selected (apk, apk+, apks, xapk, apkm, zip)", Toast.LENGTH_LONG).show()
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

    fun deleteReceivedFile(file: File) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (file.exists()) file.delete()
                refreshReceivedFiles()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun installApk(context: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(context, DialogInstallActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Timber.e(e, "Failed to install ${file.name}")
            Toast.makeText(context, "Error launching installer: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    fun clearRecentlyReceived() {
        _recentlyReceived.value = null
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

private data class ServerCoreState(
    val state: SyncState,
    val serverUrl: String?,
    val pinCode: String?,
    val activeConnections: Int,
    val sharedFiles: List<File>,
)
