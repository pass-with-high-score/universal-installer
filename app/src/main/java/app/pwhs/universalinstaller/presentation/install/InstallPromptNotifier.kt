package app.pwhs.universalinstaller.presentation.install

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import app.pwhs.universalinstaller.R
import timber.log.Timber
import java.io.File

/**
 * The confirm-from-notification half of [app.pwhs.universalinstaller.domain.model.InstallMode].
 *
 * Separate from [InstallProgressNotifier], which reports an install already under way. This one
 * asks a question, so it needs action buttons that mean something:
 *
 *  - **Install** starts [DialogInstallActivity] with [DialogInstallActivity.EXTRA_PENDING_ID].
 *    A notification action is allowed to start an activity (it counts as user-initiated, so the
 *    background-activity-start restrictions do not apply), and going through the activity means
 *    the install reuses every gate the dialog path already has — blacklist, profiles, risk
 *    confirmation — instead of a second copy of that logic living here.
 *  - **Cancel** is a broadcast to [InstallActionReceiver], which needs no window at all.
 *
 * The channel is deliberately its own: this is the only install notification that expects an
 * answer, so it takes HIGH importance and arrives as a heads-up, while progress stays quiet on LOW.
 */
class InstallPromptNotifier(
    private val context: Context,
) {
    private val nm = NotificationManagerCompat.from(context)

    init {
        ensureChannel()
    }

    /**
     * Ask the user whether to install [entry].
     *
     * @return false when the prompt could not be posted, which on Android 13+ usually means
     *   POST_NOTIFICATIONS was denied. Callers must fall back to showing the dialog — a silently
     *   dropped prompt would strand the install with nothing on screen.
     */
    fun prompt(entry: PendingInstallStore.Entry): Boolean {
        if (!canPost()) {
            Timber.w("Cannot post install prompt for ${entry.packageName} — notifications blocked")
            return false
        }

        val title = entry.appName.ifBlank { entry.fileName }
        val body = if (entry.isDowngrade) {
            context.getString(R.string.install_prompt_notif_downgrade)
        } else {
            context.getString(R.string.install_prompt_notif_text)
        }

        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_no_gradient)
            .setContentTitle(title)
            .setContentText(body)
            // Keep compact (no BigTextStyle / CATEGORY_RECOMMENDATION) and mark ongoing so
            // Android & One UI place the prompt at the top of the shade with Install/Cancel
            // actions immediately visible instead of collapsing them.
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setAutoCancel(false)
            // On Android 14+ ongoing notifications can still be swiped away; treat swipe as Cancel.
            .setDeleteIntent(cancelIntent(entry.id))
            .setContentIntent(openDialogIntent(entry.id))
            .addAction(0, context.getString(R.string.txt_install), installIntent(entry.id))
            .addAction(0, context.getString(R.string.cancel), cancelIntent(entry.id))

        entry.iconPath?.let { path ->
            runCatching { BitmapFactory.decodeFile(path) }.getOrNull()?.let(builder::setLargeIcon)
        }

        return runCatching {
            nm.notify(notificationId(entry.id), builder.build())
            true
        }.getOrElse { e ->
            Timber.e(e, "Failed to post install prompt")
            false
        }
    }

    /**
     * Whether a prompt can be posted at all. Checked before the caller commits to the headless
     * path, since after that there is no window left to fall back to.
     */
    fun canPost(): Boolean {
        if (!nm.areNotificationsEnabled()) return false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
        return ContextCompat.checkSelfPermission(
            context, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun cancel(pendingId: String) = nm.cancel(notificationId(pendingId))

    /** Stable id per pending install so two queued prompts don't collapse into one. */
    fun notificationId(pendingId: String): Int =
        NOTIF_ID_BASE + (pendingId.hashCode() and 0xFFFF)

    private fun installIntent(pendingId: String): PendingIntent {
        val intent = Intent(context, DialogInstallActivity::class.java)
            .putExtra(DialogInstallActivity.EXTRA_PENDING_ID, pendingId)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            PendingInstallStore.requestCode(pendingId, "install"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /** Tapping the body opens the full dialog instead of committing — the "let me look" path. */
    private fun openDialogIntent(pendingId: String): PendingIntent {
        val intent = Intent(context, DialogInstallActivity::class.java)
            .putExtra(DialogInstallActivity.EXTRA_PENDING_ID, pendingId)
            .putExtra(DialogInstallActivity.EXTRA_PENDING_SHOW_UI, true)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return PendingIntent.getActivity(
            context,
            PendingInstallStore.requestCode(pendingId, "open"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun cancelIntent(pendingId: String): PendingIntent {
        val intent = Intent(context, InstallActionReceiver::class.java)
            .setAction(InstallActionReceiver.ACTION_CANCEL)
            .putExtra(InstallActionReceiver.EXTRA_PENDING_ID, pendingId)
        return PendingIntent.getBroadcast(
            context,
            PendingInstallStore.requestCode(pendingId, "cancel"),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.install_prompt_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.install_prompt_channel_desc)
            setShowBadge(true)
        }
        context.getSystemService(NotificationManager::class.java)
            ?.createNotificationChannel(channel)
    }

    private companion object {
        /**
         * Versioned: Android ignores importance changes to a channel that already exists, so the
         * v1 channel would stay on DEFAULT for anyone who had already received a prompt.
         */
        const val CHANNEL_ID = "install_prompt_v2"
        const val NOTIF_ID_BASE = 43000

    }
}
