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
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import timber.log.Timber
import java.io.File
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

/**
 * Posts and updates a persistent notification for installs that the user backgrounded
 * from [DialogInstallActivity]. Lives at process scope, independent of any activity/VM
 * scope so the install can complete (and be reported) after the dialog activity is
 * destroyed.
 *
 * Observes [SessionDataRepository] (process-wide singleton) — that's the source of
 * truth both ackpine-backed controllers and [controller.ManualInstallController]
 * (targeted-user installs) push to, so this single observer covers all install paths.
 * Doesn't try to talk to ackpine's session API directly because manual targeted
 * installs use UUIDs that ackpine has never seen.
 *
 * Mapping:
 *  - session present in repo, error blank → Installing (update progress notif)
 *  - session present, error non-blank   → Failed  (result notif + untrack)
 *  - session removed from repo          → Succeeded (result notif + untrack)
 *    (User-cancels also remove without setting error; we surface them as success,
 *    since the dialog already showed the cancel — no separate state preserved here.)
 *
 * Notifications:
 *  - One progress notification (NOTIF_ID_PROGRESS) rolls all tracked installs into
 *    a single entry; multi-install case shows count + average progress.
 *  - One result notification per completed install (auto-incrementing id), with
 *    BigTextStyle so the failure reason is readable.
 */
class InstallProgressNotifier(
    private val context: Context,
    @Suppress("unused") private val packageInstaller: ru.solrudev.ackpine.installer.PackageInstaller,
    private val sessionDataRepository: SessionDataRepository,
) {
    private data class TrackedInstall(
        val sessionId: UUID,
        val packageName: String,
        val appName: String,
        val iconPath: String?,
        var progress: Int = 0,
        var indeterminate: Boolean = true,
        var sawInList: Boolean = false,
    )

    private val tracked = ConcurrentHashMap<UUID, TrackedInstall>()
    private val nm = NotificationManagerCompat.from(context)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var observerJob: Job? = null

    init {
        ensureChannel()
    }

    fun track(sessionId: UUID, packageName: String, appName: String, iconPath: String?) {
        if (tracked.containsKey(sessionId)) return
        tracked[sessionId] = TrackedInstall(sessionId, packageName, appName, iconPath)
        ensureObserverRunning()
        refreshProgressNotification()
    }

    fun untrack(sessionId: UUID) {
        if (tracked.remove(sessionId) == null) return
        if (tracked.isEmpty()) {
            nm.cancel(NOTIF_ID_PROGRESS)
            observerJob?.cancel()
            observerJob = null
        } else {
            refreshProgressNotification()
        }
    }

    /**
     * Single observer multiplexed across all tracked sessions. Started lazily on the
     * first [track] call and cancelled when the last one untracks, so we don't burn
     * a coroutine when nothing is in flight.
     */
    private fun ensureObserverRunning() {
        if (observerJob?.isActive == true) return
        observerJob = scope.launch {
            combine(
                sessionDataRepository.sessions,
                sessionDataRepository.sessionsProgress,
            ) { sessions, progress -> sessions to progress }
                .onEach { (sessions, progressList) ->
                    val snapshot = tracked.values.toList()
                    for (entry in snapshot) {
                        val sd = sessions.find { it.id == entry.sessionId }
                        if (sd != null) {
                            entry.sawInList = true
                            val sp = progressList.find { it.id == entry.sessionId }
                            if (sp != null && sp.progressMax > 0) {
                                entry.progress = (sp.currentProgress * 100 / sp.progressMax)
                                    .coerceIn(0, 100)
                                entry.indeterminate = false
                            }
                            val errMsg = sd.error.resolve(context)
                            if (errMsg.isNotBlank()) {
                                finishTracked(entry, success = false, errorText = errMsg)
                            }
                        } else if (entry.sawInList) {
                            // Was in the repo, now gone — controller removed it on success
                            // (or user cancelled, which we collapse into success).
                            finishTracked(entry, success = true, errorText = null)
                        }
                        // If !sawInList AND not in list yet, the install hasn't been registered
                        // by the controller yet (it's a brief window between dialog dismiss and
                        // controller.install() suspending past addSessionData). Keep waiting.
                    }
                    refreshProgressNotification()
                }
                .launchIn(this)
        }
    }

    private fun finishTracked(entry: TrackedInstall, success: Boolean, errorText: String?) {
        if (tracked.remove(entry.sessionId) == null) return
        postResultNotification(entry, success, errorText)
        if (tracked.isEmpty()) {
            nm.cancel(NOTIF_ID_PROGRESS)
            observerJob?.cancel()
            observerJob = null
        }
    }

    private fun refreshProgressNotification() {
        if (!canPost()) return
        val active = tracked.values.toList()
        if (active.isEmpty()) {
            nm.cancel(NOTIF_ID_PROGRESS)
            return
        }
        val newest = active.last()
        val args = if (active.size == 1) {
            val a = active.first()
            ProgressArgs(
                title = context.getString(R.string.install_notif_progress_title_single, a.appName.ifBlank { a.packageName }),
                text = context.getString(R.string.install_notif_progress_text, a.progress),
                max = 100,
                progress = a.progress,
                indeterminate = a.indeterminate,
            )
        } else {
            val avg = active.map { it.progress }.average().toInt()
            ProgressArgs(
                title = context.getString(R.string.install_notif_progress_title_multi, active.size),
                text = active.joinToString(", ") { it.appName.ifBlank { it.packageName } },
                max = 100,
                progress = avg,
                indeterminate = active.any { it.indeterminate },
            )
        }
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_no_gradient)
            .setContentTitle(args.title)
            .setContentText(args.text)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(args.max, args.progress, args.indeterminate)
            .setContentIntent(buildOpenAppIntent(newest.sessionId))
        loadIconBitmap(newest.iconPath)?.let { builder.setLargeIcon(it) }
        nm.notify(NOTIF_ID_PROGRESS, builder.build())
    }

    private fun postResultNotification(entry: TrackedInstall, success: Boolean, errorText: String?) {
        if (!canPost()) return
        val title = if (success) {
            context.getString(R.string.install_notif_result_success_title)
        } else {
            context.getString(R.string.install_notif_result_failed_title)
        }
        val appLabel = entry.appName.ifBlank { entry.packageName }
        val text = if (success) appLabel else errorText?.takeIf { it.isNotBlank() }?.let { "$appLabel · $it" } ?: appLabel
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.logo_no_gradient)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(buildResultIntent(entry, success))
        loadIconBitmap(entry.iconPath)?.let { builder.setLargeIcon(it) }
        nm.notify(nextResultId(), builder.build())
    }

    private fun buildOpenAppIntent(sessionId: UUID): PendingIntent {
        // Reopen the full install screen — it already shows the session list with progress
        // for every active install. Restoring the dialog state from a sessionId alone is
        // tricky (no URI to re-parse), so we route to InstallActivity instead.
        val intent = Intent(context, InstallActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            context,
            sessionId.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun buildResultIntent(entry: TrackedInstall, success: Boolean): PendingIntent {
        // On Success, try to launch the installed app directly. Fall back to reopening the app.
        if (success && entry.packageName.isNotBlank()) {
            val launch = runCatching { context.packageManager.getLaunchIntentForPackage(entry.packageName) }
                .getOrNull()
            if (launch != null) {
                launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                return PendingIntent.getActivity(
                    context,
                    entry.sessionId.hashCode() xor 0x5151,
                    launch,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        }
        return buildOpenAppIntent(entry.sessionId)
    }

    private fun loadIconBitmap(iconPath: String?): android.graphics.Bitmap? {
        if (iconPath.isNullOrBlank()) return null
        return runCatching {
            if (!File(iconPath).exists()) return@runCatching null
            BitmapFactory.decodeFile(iconPath)
        }.getOrNull()
    }

    private fun canPost(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return nm.areNotificationsEnabled()
    }

    private fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val sys = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (sys.getNotificationChannel(CHANNEL_ID) != null) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.install_notif_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = context.getString(R.string.install_notif_channel_description)
            setShowBadge(false)
        }
        sys.createNotificationChannel(channel)
    }

    private data class ProgressArgs(
        val title: String,
        val text: String,
        val max: Int,
        val progress: Int,
        val indeterminate: Boolean,
    )

    companion object {
        private const val CHANNEL_ID = "install_progress"
        private const val NOTIF_ID_PROGRESS = 5000
        private val resultIdSeq = AtomicInteger(5001)
        private fun nextResultId(): Int = resultIdSeq.incrementAndGet()
    }
}
