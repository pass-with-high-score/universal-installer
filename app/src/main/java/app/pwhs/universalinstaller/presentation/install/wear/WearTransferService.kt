package app.pwhs.universalinstaller.presentation.install.wear

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.content.IntentCompat
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.WatchSendState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns an APK transfer to the watch. A transfer over Bluetooth outlives the install screen, so it
 * cannot live in a ViewModel scope.
 */
class WearTransferService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var transferJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Android gives 5 seconds from startForegroundService() to this call.
        startForeground(notification(getString(R.string.watch_send_connecting)))

        when (intent?.action) {
            ACTION_CANCEL -> {
                transferJob?.cancel()
                WearTransferState.update(WatchSendState.Idle)
                stopSelf()
            }

            ACTION_SEND -> {
                val uri = IntentCompat.getParcelableExtra(intent, EXTRA_URI, Uri::class.java)
                val fileName = intent.getStringExtra(EXTRA_FILE_NAME)
                if (uri == null || fileName == null || transferJob?.isActive == true) {
                    if (transferJob?.isActive != true) stopSelf()
                    return START_NOT_STICKY
                }
                transferJob = scope.launch { runTransfer(uri, fileName) }
            }

            else -> stopSelf()
        }
        return START_NOT_STICKY
    }

    private suspend fun runTransfer(uri: Uri, fileName: String) {
        WearTransferState.update(WatchSendState.CheckingWatch)
        // Parsing the icon reads the whole archive, so it runs beside the transfer rather than
        // delaying it; the watch shows a placeholder until the icon lands.
        scope.launch { WearApkSender.sendIcon(applicationContext, uri, fileName) }
        val result = WearApkSender.send(
            context = applicationContext,
            apkUri = uri,
            fileName = fileName,
            onProgress = { progress ->
                WearTransferState.update(WatchSendState.Sending(progress))
                updateNotification(progress)
            },
        )
        if (!scope.isActive) return
        WearTransferState.update(
            when (result) {
                is WearApkSender.SendResult.Success -> WatchSendState.Success
                is WearApkSender.SendResult.NoWatchFound -> WatchSendState.NoWatch
                is WearApkSender.SendResult.Unsupported -> WatchSendState.Unsupported(result.reason)
                is WearApkSender.SendResult.Error -> WatchSendState.Error(result.message)
            }
        )
        stopSelf()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }

    private fun startForeground(notification: Notification) {
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            } else {
                0
            },
        )
    }

    private fun updateNotification(progress: Float) {
        val percent = (progress * 100).toInt().coerceIn(0, 100)
        val text = getString(R.string.watch_send_sending, percent)
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, notification(text, percent))
    }

    private fun notification(text: String, progress: Int = -1): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            if (manager.getNotificationChannel(CHANNEL_ID) == null) {
                manager.createNotificationChannel(
                    NotificationChannel(
                        CHANNEL_ID,
                        getString(R.string.watch_send_channel_name),
                        NotificationManager.IMPORTANCE_LOW,
                    )
                )
            }
        }

        val cancelIntent = Intent(this, WearTransferService::class.java).setAction(ACTION_CANCEL)
        val cancelPending = PendingIntent.getService(
            this, 0, cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.watch_send_title))
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                getString(R.string.cancel),
                cancelPending,
            )
            .apply { if (progress in 0..100) setProgress(100, progress, false) }
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "watch_send"
        private const val NOTIFICATION_ID = 1102
        private const val ACTION_SEND = "app.pwhs.universalinstaller.WATCH_SEND"
        private const val ACTION_CANCEL = "app.pwhs.universalinstaller.WATCH_SEND_CANCEL"
        private const val EXTRA_URI = "uri"
        private const val EXTRA_FILE_NAME = "file_name"

        fun start(context: Context, uri: Uri, fileName: String) {
            val intent = Intent(context, WearTransferService::class.java)
                .setAction(ACTION_SEND)
                .putExtra(EXTRA_URI, uri)
                .putExtra(EXTRA_FILE_NAME, fileName)
            ContextCompat.startForegroundService(context, intent)
        }

        fun cancel(context: Context) {
            val intent = Intent(context, WearTransferService::class.java).setAction(ACTION_CANCEL)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
