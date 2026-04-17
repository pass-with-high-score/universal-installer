package app.pwhs.universalinstaller.data.remote

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.pwhs.universalinstaller.R

class VirusTotalNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        ensureChannel()
    }

    fun notifyHashing(fileName: String): Int {
        if (!canPost()) return -1
        val id = nextId()
        val n = baseBuilder()
            .setContentTitle(context.getString(R.string.vt_notif_hashing))
            .setContentText(fileName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        post(id, n)
        return id
    }

    fun notifyUploading(id: Int, fileName: String, percent: Int) {
        if (!canPost() || id < 0) return
        val n = baseBuilder()
            .setContentTitle(context.getString(R.string.vt_notif_uploading, percent))
            .setContentText(fileName)
            .setProgress(100, percent, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        post(id, n)
    }

    fun notifyQueued(id: Int, fileName: String) {
        if (!canPost() || id < 0) return
        val n = baseBuilder()
            .setContentTitle(context.getString(R.string.vt_notif_queued))
            .setContentText(fileName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        post(id, n)
    }

    fun notifyAnalyzing(id: Int, fileName: String) {
        if (!canPost() || id < 0) return
        val n = baseBuilder()
            .setContentTitle(context.getString(R.string.vt_notif_analyzing))
            .setContentText(fileName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        post(id, n)
    }

    fun notifyResult(id: Int, fileName: String, title: String, text: String) {
        if (!canPost() || id < 0) return
        val n = baseBuilder()
            .setContentTitle(title)
            .setContentText("$fileName · $text")
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        post(id, n)
    }

    fun cancel(id: Int) {
        if (id < 0) return
        manager.cancel(id)
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)

    private fun post(id: Int, notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) return
        manager.notify(id, notification)
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return manager.areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val sys = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (sys.getNotificationChannel(CHANNEL_ID) != null) return
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.vt_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.vt_notif_channel_description)
                setShowBadge(false)
            }
            sys.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "virustotal_scan"
        private var nextId = 3000
        @Synchronized
        private fun nextId(): Int = ++nextId
    }
}
