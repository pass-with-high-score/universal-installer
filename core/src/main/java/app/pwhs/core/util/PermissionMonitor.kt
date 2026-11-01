package app.pwhs.core.util

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.ActivityCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.pwhs.core.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Polls a permission state while the user is in system Settings, then brings the *current*
 * Activity back to the foreground when the grant is detected.
 * Shared between Mobile and TV.
 */
object PermissionMonitor {
    private const val CHANNEL_ID = "permission_return"
    private const val NOTIF_ID = 7301

    private var job: Job? = null

    fun start(activity: Activity, check: () -> Boolean) {
        job?.cancel()
        val appContext = activity.applicationContext
        val activityClass = activity.javaClass
        job = CoroutineScope(Dispatchers.Main).launch {
            delay(500)
            var attempts = 0
            while (attempts < 600) {
                if (check()) {
                    bringAppToFront(appContext, activityClass)
                    break
                }
                delay(500)
                attempts++
            }
            job = null
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        runCatching {
            NotificationManagerCompat.from(lastContext ?: return).cancel(NOTIF_ID)
        }
    }

    @Volatile
    private var lastContext: Context? = null

    private fun bringAppToFront(context: Context, activityClass: Class<out Activity>) {
        lastContext = context
        val intent = Intent(context, activityClass).apply {
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }

        try {
            context.startActivity(intent)
        } catch (_: Exception) { }

        postReturnNotification(context, intent)
    }

    private fun postReturnNotification(
        context: Context,
        contentIntent: Intent,
    ) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) return
        ensureChannel(context)

        val pi = PendingIntent.getActivity(
            context,
            NOTIF_ID,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_apk_install)
            .setContentTitle(context.getString(R.string.permission_return_title))
            .setContentText(context.getString(R.string.permission_return_body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setOnlyAlertOnce(true)
            .build()

        try {
            if (ActivityCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            ) {
                nm.notify(NOTIF_ID, notif)
            }
        } catch (_: SecurityException) { }
    }

    private fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sys = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (sys.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.permission_return_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.permission_return_channel_desc)
            setShowBadge(false)
        }
        sys.createNotificationChannel(channel)
    }
}
