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
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject

/**
 * Receives packages the phone streams over a Wearable channel.
 *
 * Path contract shared with the phone's `WearApkSender`:
 * `/apk-transfer/<expectedBytes>/<fileName>`. The size arrives up front so the free-space check
 * can run before a single byte is written.
 */
class WearReceiverService : WearableListenerService() {

    private val repository: WearApkRepository by inject()

    override fun onChannelOpened(channel: ChannelClient.Channel) {
        val payload = channel.path.removePrefix(CHANNEL_PATH_PREFIX)
        if (payload == channel.path) return

        val expectedBytes = payload.substringBefore('/').toLongOrNull() ?: 0L
        val fileName = payload.substringAfter('/', "")
        if (fileName.isEmpty()) {
            Log.e(TAG, "Malformed channel path: ${channel.path}")
            return
        }

        Log.d(TAG, "Receiving $fileName ($expectedBytes bytes)")
        WearReceiveScope.launch { receive(channel, fileName, expectedBytes) }
    }

    private suspend fun receive(
        channel: ChannelClient.Channel,
        fileName: String,
        expectedBytes: Long,
    ) = withContext(Dispatchers.IO) {
        val context = applicationContext
        val channelClient = Wearable.getChannelClient(context)
        val wakeLock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)

        // The watch sleeps long before a Bluetooth transfer of an APK finishes.
        wakeLock.acquire(WAKE_LOCK_TIMEOUT_MS)
        try {
            if (expectedBytes > 0 && !StorageUtil.hasSufficientStorage(expectedBytes)) {
                Log.e(TAG, "Not enough free space for $fileName ($expectedBytes bytes)")
                return@withContext
            }

            val target = repository.createTempApkFile(fileName)
            val written = runCatching {
                Tasks.await(channelClient.getInputStream(channel)).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
                target.length()
            }.getOrElse { e ->
                Log.e(TAG, "Transfer failed: ${e.message}", e)
                target.delete()
                return@withContext
            }

            if (expectedBytes > 0 && written != expectedBytes) {
                Log.e(TAG, "Truncated transfer: got $written of $expectedBytes bytes")
                target.delete()
                return@withContext
            }

            val apkInfo = repository.addApk(target)
            if (apkInfo == null) {
                Log.e(TAG, "Could not parse package info from ${target.name}")
                target.delete()
                return@withContext
            }

            Log.d(TAG, "Received ${apkInfo.appName} ($written bytes)")
            postNotification(context, apkInfo)
        } finally {
            runCatching { Tasks.await(channelClient.close(channel)) }
            if (wakeLock.isHeld) wakeLock.release()
        }
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

    companion object {
        private const val TAG = "WearReceiverService"
        private const val CHANNEL_ID = "wear_apk_received"
        private const val WAKE_LOCK_TAG = "UniversalInstaller:WearReceiver"
        private const val WAKE_LOCK_TIMEOUT_MS = 10 * 60 * 1000L
        const val CHANNEL_PATH_PREFIX = "/apk-transfer/"
    }
}
