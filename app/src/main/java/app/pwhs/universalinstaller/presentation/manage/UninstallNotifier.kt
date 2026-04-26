package app.pwhs.universalinstaller.presentation.manage

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

class UninstallNotifier(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    init {
        ensureChannel()
    }

    fun notifySingleStart(appName: String): Int {
        if (!canPost()) return -1
        val id = nextId()
        val n = baseBuilder()
            .setContentTitle(context.getString(R.string.uninstall_notif_single_title))
            .setContentText(appName)
            .setProgress(0, 0, true)
            .setOngoing(true)
            .build()
        post(id, n)
        return id
    }

    fun notifySingleResult(id: Int, appName: String, success: Boolean) {
        if (!canPost() || id < 0) return
        val n = baseBuilder()
            .setContentTitle(
                context.getString(
                    if (success) R.string.uninstall_notif_single_success
                    else R.string.uninstall_notif_single_failed
                )
            )
            .setContentText(appName)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        post(id, n)
    }

    fun notifyBatchStart(total: Int): Int {
        if (!canPost()) return -1
        val id = nextId()
        val n = baseBuilder()
            .setContentTitle(context.getString(R.string.uninstall_notif_batch_title, total))
            .setContentText(context.getString(R.string.uninstall_notif_batch_preparing))
            .setProgress(total, 0, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        post(id, n)
        return id
    }

    fun notifyBatchProgress(id: Int, completed: Int, total: Int, currentAppName: String?) {
        if (!canPost() || id < 0) return
        // Title uses 1-indexed position of the app currently being processed;
        // progress bar uses `completed` (items actually finished).
        val position = (completed + 1).coerceAtMost(total)
        val n = baseBuilder()
            .setContentTitle(context.getString(R.string.uninstall_notif_batch_progress_title, position, total))
            .setContentText(currentAppName ?: "")
            .setProgress(total, completed, false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
        post(id, n)
    }

    fun notifyBatchDone(id: Int, successful: Int, failed: Int) {
        if (!canPost() || id < 0) return
        val hasFailures = failed > 0
        val title = if (hasFailures)
            context.getString(R.string.uninstall_notif_batch_done_with_errors)
        else
            context.getString(R.string.uninstall_notif_batch_done)
        val text = context.getString(R.string.uninstall_notif_batch_done_text, successful, failed)
        val n = baseBuilder()
            .setContentTitle(title)
            .setContentText(text)
            .setProgress(0, 0, false)
            .setOngoing(false)
            .setAutoCancel(true)
            .build()
        post(id, n)
    }

    private fun baseBuilder(): NotificationCompat.Builder =
        NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_no_gradient)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setSilent(true)

    private fun post(id: Int, notification: Notification) {
        // Explicit permission check directly above the notify call — lint recognises this
        // pattern and no longer requires @SuppressLint("MissingPermission").
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
                context.getString(R.string.uninstall_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = context.getString(R.string.uninstall_notif_channel_description)
                setShowBadge(false)
            }
            sys.createNotificationChannel(channel)
        }
    }

    companion object {
        private const val CHANNEL_ID = "uninstall_progress"
        private var nextId = 2000
        @Synchronized
        private fun nextId(): Int = ++nextId
    }
}
