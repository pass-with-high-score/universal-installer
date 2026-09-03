package app.pwhs.universalinstaller.wearos.data

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.pwhs.universalinstaller.wearos.R

/**
 * Keeps the process alive for the duration of a transfer, and nothing else.
 *
 * It deliberately does not own the channel. A `ChannelClient.Channel` parcels, but the copy that
 * comes back out of an Intent is not the live one — handing it to another service opened an input
 * stream that never delivered a byte while the phone wrote into nothing. The read therefore stays
 * in the listener callback the channel arrives on; this service only exists so a low-memory kill
 * cannot throw away a half-written APK and make the whole Bluetooth transfer run again.
 */
class WearReceiveService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
        if (intent?.action == ACTION_STOP) stopSelf()
        return START_NOT_STICKY
    }

    private fun notification(): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.receive_channel_name),
                    NotificationManager.IMPORTANCE_LOW,
                )
            )
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.receive_channel_name))
            .setSmallIcon(R.drawable.ic_notification)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val TAG = "WearReceiveService"
        private const val CHANNEL_ID = "wear_apk_receiving"
        private const val NOTIFICATION_ID = 2201
        private const val ACTION_STOP = "app.pwhs.universalinstaller.wearos.RECEIVE_STOP"

        /**
         * Android 12 forbids starting a foreground service from the background and a channel event
         * arrives exactly there — in practice GMS's bind grants a short allowlist, but a refusal
         * only costs the process-alive guarantee, not the transfer.
         */
        fun start(context: Context) {
            runCatching {
                ContextCompat.startForegroundService(
                    context, Intent(context, WearReceiveService::class.java)
                )
            }.onFailure { Log.w(TAG, "Foreground start refused: ${it.message}") }
        }

        fun stop(context: Context) {
            runCatching {
                context.stopService(Intent(context, WearReceiveService::class.java))
            }
        }
    }
}
