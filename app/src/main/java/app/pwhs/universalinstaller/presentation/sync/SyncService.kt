package app.pwhs.universalinstaller.presentation.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface

data class TransferProgress(
    val fileName: String,
    val bytesTransferred: Long,
    val totalBytes: Long,
) {
    val percentage: Int
        get() = if (totalBytes > 0) ((bytesTransferred * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
}

object SyncManager {
    val state = MutableStateFlow(SyncState.STOPPED)
    val serverUrl = MutableStateFlow<String?>(null)
    val pinCode = MutableStateFlow<String?>("1234")
    val activeConnections = MutableStateFlow(0)
    val sharedFiles = MutableStateFlow<List<java.io.File>>(emptyList())
    val activeTransfers = MutableStateFlow<Map<String, TransferProgress>>(emptyMap())

    private val connectionCounter = java.util.concurrent.atomic.AtomicInteger(0)
    private val transferIdCounter = java.util.concurrent.atomic.AtomicLong(0)

    fun nextTransferId(): String = "transfer_${transferIdCounter.incrementAndGet()}"

    fun incrementConnections() {
        val newVal = connectionCounter.incrementAndGet()
        activeConnections.update { newVal }
    }

    fun decrementConnections() {
        val newVal = connectionCounter.decrementAndGet().coerceAtLeast(0)
        activeConnections.update { newVal }
    }

    fun resetConnections() {
        connectionCounter.set(0)
        activeConnections.update { 0 }
        activeTransfers.value = emptyMap()
    }

    fun updateProgress(transferId: String, fileName: String, bytesTransferred: Long, totalBytes: Long) {
        activeTransfers.update { map ->
            map + (transferId to TransferProgress(fileName, bytesTransferred, totalBytes))
        }
    }

    fun removeTransfer(transferId: String) {
        activeTransfers.update { map -> map - transferId }
    }
}

enum class SyncState { STOPPED, STARTING, RUNNING, ERROR }

class SyncService : Service() {
    private var server: ApkHttpServer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var serverUrlCache: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") {
            stopSelf()
            return START_NOT_STICKY
        }

        SyncManager.state.value = SyncState.STARTING
        startForegroundService()
        
        runBlocking {
            val prefs = applicationContext.dataStore.data.first()
            val requirePin = prefs[PreferencesKeys.SYNC_REQUIRE_PIN] ?: true
            var pin = prefs[PreferencesKeys.SYNC_PIN_CODE] ?: ""
            if (pin.isEmpty()) {
                pin = "1234"
            }
            val portStr = prefs[PreferencesKeys.SYNC_SERVER_PORT] ?: "8080"
            val port = portStr.toIntOrNull() ?: 8080
            
            SyncManager.pinCode.value = if (requirePin) pin else null

            try {
                // If there's an existing server running, stop it
                server?.stop()

                server = ApkHttpServer(this@SyncService, port, requirePin, pin) { delta ->
                    if (delta > 0) SyncManager.incrementConnections()
                    else SyncManager.decrementConnections()
                }
                server?.start()
                
                val ip = getLocalIpAddress()
                if (ip != null) {
                    serverUrlCache = "http://$ip:$port"
                    SyncManager.serverUrl.value = serverUrlCache
                    SyncManager.state.value = SyncState.RUNNING
                    refreshSharedFiles()
                    updateNotification("Server running at http://$ip:$port")
                    startProgressObserver()
                } else {
                    SyncManager.state.value = SyncState.ERROR
                    stopSelf()
                }
            } catch (e: Exception) {
                SyncManager.state.value = SyncState.ERROR
                stopSelf()
            }
        }

        return START_STICKY
    }

    private fun startForegroundService() {
        val channelId = "sync_service_channel"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Sync Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        val notification = createNotification(channelId, "Server starting...")
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this, 
                1001, 
                notification, 
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0
            )
        } else {
            startForeground(1001, notification)
        }
    }

    private fun updateNotification(text: String) {
        val channelId = "sync_service_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(1001, createNotification(channelId, text))
    }

    private fun createNotification(
        channelId: String,
        text: String,
        progress: Int = -1,
    ): Notification {
        val stopIntent = Intent(this, SyncService::class.java).apply { action = "STOP" }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentTitle("Universal Installer Sync")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "Stop",
                stopPendingIntent
            )

        if (progress in 0..100) {
            builder.setProgress(100, progress, false)
                .setSubText("$progress%")
        }

        return builder.build()
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        server?.stop()
        SyncManager.state.value = SyncState.STOPPED
        SyncManager.serverUrl.value = null
        SyncManager.resetConnections()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startProgressObserver() {
        serviceScope.launch {
            SyncManager.activeTransfers.collectLatest { transfers ->
                val channelId = "sync_service_channel"
                val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (transfers.isNotEmpty()) {
                    val totalBytes = transfers.values.sumOf { it.totalBytes }
                    val transferredBytes = transfers.values.sumOf { it.bytesTransferred }
                    val overallPercent = if (totalBytes > 0) ((transferredBytes * 100) / totalBytes).toInt().coerceIn(0, 100) else 0
                    val text = if (transfers.size == 1) {
                        "Sending: ${transfers.values.first().fileName}"
                    } else {
                        "Sending ${transfers.size} files"
                    }
                    val notification = createNotification(channelId, text, overallPercent)
                    manager.notify(1001, notification)
                } else if (serverUrlCache != null) {
                    val notification = createNotification(channelId, "Server running at $serverUrlCache")
                    manager.notify(1001, notification)
                }
            }
        }
    }

    private val validExtensions = listOf("apk", "apks", "xapk", "apkm", "zip")

    private fun refreshSharedFiles() {
        val baseDir = java.io.File(
            android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS),
            "Universal Installer"
        )
        val files = baseDir.listFiles()
            ?.filter { it.isFile && it.extension.lowercase() in validExtensions }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        SyncManager.sharedFiles.value = files
    }

    private fun getLocalIpAddress(): String? {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val inetAddress = addresses.nextElement()
                    if (!inetAddress.isLoopbackAddress && inetAddress is Inet4Address) {
                        return inetAddress.hostAddress
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }
}
