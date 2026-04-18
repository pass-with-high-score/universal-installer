package app.pwhs.universalinstaller.presentation.install

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.text.format.Formatter
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import app.pwhs.universalinstaller.R

/**
 * Progress notification while downloading a package from a URL. Tied to the ViewModel's
 * download job — dismissed on completion / error / cancel.
 */
class DownloadNotifier(private val context: Context) {

    private val nm: NotificationManager? = context.getSystemService()
    private val notificationId = 4400

    // Per-second throttle: spamming notify() on every 64 KB chunk can backlog the
    // NotificationManager binder and cause the trailing "done" notification to be
    // overtaken by a late "progress" call from the IO thread.
    @Volatile private var lastProgressMs = 0L

    init { ensureChannel() }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = nm ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.download_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.download_notif_channel_description)
                setShowBadge(false)
            }
        )
    }

    fun notifyProgress(fileName: String, bytesRead: Long, totalBytes: Long) {
        val nm = nm ?: return
        val now = android.os.SystemClock.elapsedRealtime()
        // Always let the first (0 B) and last (complete) updates through; throttle the middle.
        val isEdge = bytesRead == 0L || (totalBytes > 0 && bytesRead >= totalBytes)
        if (!isEdge && now - lastProgressMs < 500L) return
        lastProgressMs = now
        val indeterminate = totalBytes <= 0L
        val percent = if (!indeterminate) {
            ((bytesRead * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else 0
        val text = if (indeterminate) {
            context.getString(
                R.string.download_notif_running_text_unknown,
                Formatter.formatShortFileSize(context, bytesRead),
            )
        } else {
            context.getString(
                R.string.download_notif_running_text,
                Formatter.formatShortFileSize(context, bytesRead),
                Formatter.formatShortFileSize(context, totalBytes),
                percent,
            )
        }
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.download_notif_running_title, fileName))
            .setContentText(text)
            .setProgress(100, percent, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
        nm.notify(notificationId, notif)
    }

    fun notifyDone(fileName: String) {
        val nm = nm ?: return
        // Park the throttle far in the future so no stray progress call (e.g. from a
        // delayed IO thread flush) can overwrite the Done notification we're about to post.
        lastProgressMs = Long.MAX_VALUE / 2
        // Post under a FRESH id. The ongoing progress notification is explicitly cancelled
        // afterwards — avoids MIUI's occasional refusal to downgrade an ongoing notification.
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.download_notif_done_title))
            .setContentText(context.getString(R.string.download_notif_done_text, fileName))
            .setAutoCancel(true)
            .setOngoing(false)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.cancel(notificationId)
        nm.notify(DONE_ID, notif)
    }

    fun notifyFailed(reason: String) {
        val nm = nm ?: return
        lastProgressMs = Long.MAX_VALUE / 2
        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.download_notif_failed_title))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setAutoCancel(true)
            .setOngoing(false)
            .setWhen(System.currentTimeMillis())
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        nm.cancel(notificationId)
        nm.notify(FAILED_ID, notif)
    }

    fun cancel() {
        nm?.cancel(notificationId)
        nm?.cancel(DONE_ID)
        nm?.cancel(FAILED_ID)
    }

    companion object {
        const val CHANNEL_ID = "package_download"
        private const val DONE_ID = 4401
        private const val FAILED_ID = 4402
    }
}
