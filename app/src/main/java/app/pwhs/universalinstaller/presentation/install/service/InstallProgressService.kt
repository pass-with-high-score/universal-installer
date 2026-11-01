package app.pwhs.universalinstaller.presentation.install.service

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import app.pwhs.universalinstaller.presentation.install.InstallProgressNotifier
import org.koin.android.ext.android.inject
import timber.log.Timber

/**
 * Foreground service that keeps the application process alive while one or more
 * package installations are in progress.
 *
 * Prevents aggressive OEM task killers (such as RedMagic OS, MIUI/HyperOS, etc.)
 * and standard Android task cleanup from terminating active installation sessions
 * when the user swipes the app away in the Recent Apps screen.
 */
class InstallProgressService : Service() {

    private val installNotifier: InstallProgressNotifier by inject()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForegroundService()
            return START_NOT_STICKY
        }

        val notification = installNotifier.buildProgressNotification()
        if (notification != null) {
            startForegroundWithNotification(notification)
        } else {
            stopForegroundService()
            return START_NOT_STICKY
        }

        return START_NOT_STICKY
    }

    private fun startForegroundWithNotification(notification: Notification) {
        val fgsType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        try {
            ServiceCompat.startForeground(
                this,
                InstallProgressNotifier.NOTIF_ID_PROGRESS,
                notification,
                fgsType,
            )
        } catch (e: Exception) {
            Timber.e(e, "Failed to start InstallProgressService in foreground")
        }
    }

    private fun stopForegroundService() {
        try {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Timber.w(e, "Failed to stop foreground for InstallProgressService")
        }
        stopSelf()
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Timber.i("InstallProgressService: Task removed from Recents, keeping service alive for ongoing install(s)")
    }

    companion object {
        const val ACTION_START = "app.pwhs.universalinstaller.action.START_INSTALL_SERVICE"
        const val ACTION_STOP = "app.pwhs.universalinstaller.action.STOP_INSTALL_SERVICE"

        fun start(context: Context) {
            val intent = Intent(context, InstallProgressService::class.java).apply {
                action = ACTION_START
            }
            try {
                ContextCompat.startForegroundService(context, intent)
            } catch (e: Exception) {
                Timber.e(e, "Cannot start InstallProgressService as foreground service")
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, InstallProgressService::class.java).apply {
                action = ACTION_STOP
            }
            try {
                context.startService(intent)
            } catch (e: Exception) {
                Timber.e(e, "Cannot stop InstallProgressService")
            }
        }
    }
}
