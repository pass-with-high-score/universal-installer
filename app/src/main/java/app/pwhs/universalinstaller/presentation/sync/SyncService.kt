package app.pwhs.universalinstaller.presentation.sync

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import java.net.Inet4Address
import java.net.NetworkInterface

object SyncManager {
    val state = MutableStateFlow(SyncState.STOPPED)
    val serverUrl = MutableStateFlow<String?>(null)
    val pinCode = MutableStateFlow<String?>("1234")
    val activeConnections = MutableStateFlow(0)
    val sharedFiles = MutableStateFlow<List<java.io.File>>(emptyList())
}

enum class SyncState { STOPPED, STARTING, RUNNING, ERROR }

class SyncService : Service() {
    private var server: ApkHttpServer? = null

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
                    SyncManager.activeConnections.value += delta
                }
                server?.start()
                
                val ip = getLocalIpAddress()
                if (ip != null) {
                    SyncManager.serverUrl.value = "http://$ip:$port"
                    SyncManager.state.value = SyncState.RUNNING
                    refreshSharedFiles()
                    updateNotification("Server running at http://$ip:$port")
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

    private fun createNotification(channelId: String, text: String): Notification {
        return NotificationCompat.Builder(this, channelId)
            .setContentTitle("Universal Installer Sync")
            .setContentText(text)
            // fallback to ic_launcher if there is no other simple icon, or Github icon that we saw earlier
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setOngoing(true)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        server?.stop()
        SyncManager.state.value = SyncState.STOPPED
        SyncManager.serverUrl.value = null
        SyncManager.activeConnections.value = 0
    }

    override fun onBind(intent: Intent?): IBinder? = null

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
