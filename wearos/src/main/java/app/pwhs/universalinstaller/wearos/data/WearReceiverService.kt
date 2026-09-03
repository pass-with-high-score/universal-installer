package app.pwhs.universalinstaller.wearos.data

import android.graphics.BitmapFactory
import android.util.Log
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.WearableListenerService
import kotlinx.coroutines.launch
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

    /** The phone sends the launcher icon ahead of the payload; the payload itself is unparsable
     *  until it is complete, so this is the only way the transfer can show what is arriving. */
    override fun onMessageReceived(event: MessageEvent) {
        val fileName = event.path.removePrefix(META_PATH_PREFIX)
        if (fileName == event.path || fileName.isEmpty()) return
        val icon = runCatching {
            BitmapFactory.decodeByteArray(event.data, 0, event.data.size)
        }.getOrNull() ?: return
        WearReceiveProgress.setIcon(fileName, icon)
    }

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
        WearReceiveProgress.update(WearReceiveState.Receiving(fileName, 0L, expectedBytes))
        // The read has to happen here, on the channel this callback was handed. The service only
        // keeps the process alive around it.
        WearReceiveService.start(applicationContext)
        WearReceiveScope.launch {
            try {
                WearApkReceiver.receive(applicationContext, repository, channel, fileName, expectedBytes)
            } finally {
                WearReceiveService.stop(applicationContext)
            }
        }
    }

    companion object {
        private const val TAG = "WearReceiverService"
        const val CHANNEL_PATH_PREFIX = "/apk-transfer/"
        const val META_PATH_PREFIX = "/apk-meta/"
    }
}
