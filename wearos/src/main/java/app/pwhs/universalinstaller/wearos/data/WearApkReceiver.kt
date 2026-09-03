package app.pwhs.universalinstaller.wearos.data

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import app.pwhs.core.util.StorageUtil
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.presentation.MainActivity
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Writes an incoming package to the queue. Lives apart from the services that call it because two
 * of them do: a foreground service when the platform allows one to start, and the listener's own
 * scope when it does not.
 */
object WearApkReceiver {

    private const val TAG = "WearApkReceiver"
    private const val CHANNEL_ID = "wear_apk_received"
    private const val BUFFER_SIZE = 8192
    private const val WAKE_LOCK_TAG = "UniversalInstaller:WearReceiver"
    private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L

    suspend fun receive(
        context: Context,
        repository: WearApkRepository,
        channel: ChannelClient.Channel,
        fileName: String,
        expectedBytes: Long,
    ) = withContext(Dispatchers.IO) {
        val channelClient = Wearable.getChannelClient(context)
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)

        // The watch sleeps long before a Bluetooth transfer of an APK finishes.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        try {
            if (expectedBytes > 0 && !StorageUtil.hasSufficientStorage(expectedBytes)) {
                Log.e(TAG, "Not enough free space for $fileName ($expectedBytes bytes)")
                WearReceiveProgress.update(WearReceiveState.Failed(fileName))
                return@withContext
            }

            val target = repository.createTempApkFile(fileName)
            val written = runCatching {
                Tasks.await(channelClient.getInputStream(channel)).use { input ->
                    target.outputStream().use { out -> copyReporting(input, out, fileName, expectedBytes) }
                }
                target.length()
            }.getOrElse { e ->
                Log.e(TAG, "Transfer failed: ${e.message}", e)
                target.delete()
                WearReceiveProgress.update(WearReceiveState.Failed(fileName))
                return@withContext
            }

            if (expectedBytes > 0 && written != expectedBytes) {
                Log.e(TAG, "Truncated transfer: got $written of $expectedBytes bytes")
                target.delete()
                WearReceiveProgress.update(WearReceiveState.Failed(fileName))
                return@withContext
            }

            val apkInfo = repository.addApk(target)
            if (apkInfo == null) {
                Log.e(TAG, "Could not parse package info from ${target.name}")
                target.delete()
                WearReceiveProgress.update(WearReceiveState.Failed(fileName))
                return@withContext
            }

            Log.d(TAG, "Received ${apkInfo.appName} ($written bytes)")
            WearReceiveProgress.reset()
            postNotification(context, apkInfo)
        } finally {
            runCatching { Tasks.await(channelClient.close(channel)) }
            if (wakeLock.isHeld) wakeLock.release()
        }
    }

    private fun copyReporting(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        fileName: String,
        expectedBytes: Long,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var received = 0L
        var lastPercent = -1
        var read = input.read(buffer)
        while (read != -1) {
            output.write(buffer, 0, read)
            received += read
            val percent = if (expectedBytes > 0) ((received * 100) / expectedBytes).toInt() else -1
            if (percent != lastPercent) {
                lastPercent = percent
                WearReceiveProgress.update(
                    WearReceiveState.Receiving(fileName, received, expectedBytes)
                )
            }
            read = input.read(buffer)
        }
        output.flush()
    }

    private fun postNotification(context: Context, apkInfo: WearApkInfo) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "APK Received", NotificationManager.IMPORTANCE_DEFAULT)
        )

        val requestCode = apkInfo.id.hashCode()
        val intent = Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            .putExtra(MainActivity.EXTRA_APK_ID, apkInfo.id)
        val pi = PendingIntent.getActivity(
            context, requestCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(context.getString(R.string.apk_received_title))
            .setContentText(apkInfo.appName)
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(requestCode, notification)
    }
}
