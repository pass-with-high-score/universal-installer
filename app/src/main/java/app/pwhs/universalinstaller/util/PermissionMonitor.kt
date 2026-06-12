package app.pwhs.universalinstaller.util

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import app.pwhs.universalinstaller.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Polls a permission state while the user is in system Settings, then brings the *current*
 * Activity back to the foreground when the grant is detected.
 *
 * Two return mechanisms are used in tandem because they fail in different conditions:
 *
 * 1. `startActivity` of an [Intent] for the original Activity class with [Intent.FLAG_ACTIVITY_REORDER_TO_FRONT].
 *    This works on stock Android within the BAL (Background Activity Launch) grace window.
 *    REORDER_TO_FRONT preserves the existing Activity instance — important so the user lands
 *    back on the screen they came from instead of a fresh launcher activity.
 *
 * 2. A high-priority notification with a content-intent to the same Activity. Notifications
 *    are launched by the system on user tap, so they bypass BAL entirely. This is the
 *    fallback for OEMs that block background `startActivity` (notably MIUI on Xiaomi).
 */
object PermissionMonitor {
    private const val CHANNEL_ID = "permission_return"
    private const val NOTIF_ID = 7301

    private var job: Job? = null

    /**
     * Start polling for a permission grant.
     *
     * The Activity reference is used only to capture its [Class] and [applicationContext];
     * we do not retain the instance, so this is safe to call from a singleton.
     */
    fun start(activity: Activity, check: () -> Boolean) {
        job?.cancel()
        val appContext = activity.applicationContext
        val activityClass = activity.javaClass
        job = CoroutineScope(Dispatchers.Main).launch {
            Timber.d("PermissionMonitor: started polling for ${activityClass.simpleName}")
            delay(500)
            var attempts = 0
            while (attempts < 600) {
                if (check()) {
                    Timber.d("PermissionMonitor: granted at attempt=$attempts; returning to ${activityClass.simpleName}")
                    bringAppToFront(appContext, activityClass)
                    break
                }
                delay(500)
                attempts++
            }
            if (attempts >= 600) {
                Timber.d("PermissionMonitor: polling timed out (5 min) for ${activityClass.simpleName}")
            }
            job = null
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        // Best-effort dismiss of any leftover return notification.
        runCatching {
            // Use a non-throwing path: only call when channel infra exists.
            NotificationManagerCompat.from(lastContext ?: return).cancel(NOTIF_ID)
        }
    }

    @Volatile
    private var lastContext: Context? = null

    private fun bringAppToFront(context: Context, activityClass: Class<out Activity>) {
        lastContext = context
        val intent = Intent(context, activityClass).apply {
            // REORDER_TO_FRONT keeps the existing Activity instance — no recreate, no
            // "the app restarted" UX. NEW_TASK + SINGLE_TOP make this work as a process-
            // boundary launch.
            addFlags(
                Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }

        var startActivityWorked = true
        try {
            context.startActivity(intent)
            Timber.d("PermissionMonitor: startActivity dispatched")
        } catch (e: Exception) {
            startActivityWorked = false
            Timber.w(e, "PermissionMonitor: startActivity failed (BAL or other)")
        }

        // On Android 12+ BAL block is silent (no exception), and on MIUI/Xiaomi the call is
        // routinely silently dropped even on older versions. Always post a tap-to-return
        // notification as a fallback — it costs nothing if the activity launch did succeed
        // (we cancel the notif on resume), and it's the only reliable path on MIUI.
        postReturnNotification(context, intent, alsoStartedActivity = startActivityWorked)
    }

    private fun postReturnNotification(
        context: Context,
        contentIntent: Intent,
        alsoStartedActivity: Boolean,
    ) {
        val nm = NotificationManagerCompat.from(context)
        if (!nm.areNotificationsEnabled()) {
            Timber.d("PermissionMonitor: notifications disabled, skipping fallback notif")
            return
        }
        ensureChannel(context)

        val pi = PendingIntent.getActivity(
            context,
            NOTIF_ID,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notif = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(context.getString(R.string.permission_return_title))
            .setContentText(context.getString(R.string.permission_return_body))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            // Stays after dismissed if user already in app: harmless because we cancel on stop().
            .setOnlyAlertOnce(true)
            .build()

        try {
            nm.notify(NOTIF_ID, notif)
            Timber.d("PermissionMonitor: posted return notification (startActivityWorked=$alsoStartedActivity)")
        } catch (e: SecurityException) {
            Timber.w(e, "PermissionMonitor: notify() blocked by SecurityException")
        }
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
