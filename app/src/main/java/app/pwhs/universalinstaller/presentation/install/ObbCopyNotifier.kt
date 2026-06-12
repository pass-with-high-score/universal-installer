package app.pwhs.universalinstaller.presentation.install

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.getSystemService
import app.pwhs.universalinstaller.R

/**
 * Builds progress / completion / failure notifications for the OBB copy worker. Uses a
 * dedicated low-importance channel so it doesn't trigger heads-up for a routine file copy.
 */
object ObbCopyNotifier {

    const val CHANNEL_ID = "obb_copy"
    const val FOREGROUND_ID = 4200

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService<NotificationManager>() ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.obb_notif_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = context.getString(R.string.obb_notif_channel_description)
                setShowBadge(false)
            }
        )
    }

    fun buildProgress(
        context: Context,
        appName: String,
        bytesCopied: Long,
        totalBytes: Long,
    ): Notification {
        ensureChannel(context)
        val percent = if (totalBytes > 0) {
            ((bytesCopied * 100L) / totalBytes).toInt().coerceIn(0, 100)
        } else 0
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(context.getString(R.string.obb_notif_running_title, appName))
            .setContentText(context.getString(R.string.obb_notif_running_text, percent))
            .setProgress(100, percent, totalBytes <= 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildDone(context: Context, appName: String, fileCount: Int): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(context.getString(R.string.obb_notif_done_title, appName))
            .setContentText(context.getString(R.string.obb_notif_done_text, fileCount))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    fun buildFailed(context: Context, appName: String, reason: String): Notification {
        ensureChannel(context)
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle(context.getString(R.string.obb_notif_failed_title, appName))
            .setContentText(reason)
            .setStyle(NotificationCompat.BigTextStyle().bigText(reason))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
    }
}
