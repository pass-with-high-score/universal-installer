package app.pwhs.universalinstaller.presentation.install.wear

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Streams an APK to the paired Wear OS watch over a Wearable [com.google.android.gms.wearable.ChannelClient]
 * channel.
 */
object WearApkSender {

    private const val TAG = "WearApkSender"

    /** Declared by the watch in `wearos/src/main/res/values/wear.xml`. */
    const val CAPABILITY_APK_RECEIVER = "apk_receiver"

    /**
     * Channel path contract shared with the watch's `WearReceiverService`:
     * `/apk-transfer/<expectedBytes>/<safeFileName>`. Changing either side alone breaks transfers.
     */
    const val CHANNEL_PATH_PREFIX = "/apk-transfer/"

    private const val BUFFER_SIZE = 8192

    sealed interface SendResult {
        data object Success : SendResult
        data object NoWatchFound : SendResult
        data class Unsupported(val reason: String) : SendResult
        data class Error(val message: String) : SendResult
    }

    /** True when a watch that actually has this app installed is reachable. */
    suspend fun isWatchAvailable(context: Context): Boolean = withContext(Dispatchers.IO) {
        findReceiverNode(context) != null
    }

    /**
     * Streams the APK at [apkUri] to the nearest watch running this app.
     *
     * [fileName] must keep its original extension — the watch decides whether the payload is a
     * split bundle from it. [onProgress] is called on the IO thread, at most once per percent.
     */
    suspend fun send(
        context: Context,
        apkUri: Uri,
        fileName: String,
        onProgress: ((Float) -> Unit)? = null,
    ): SendResult = withContext(Dispatchers.IO) {
        val targetNode = findReceiverNode(context) ?: return@withContext SendResult.NoWatchFound
        Log.d(TAG, "Sending to node: ${targetNode.displayName} (${targetNode.id})")

        val totalBytes = readSize(context, apkUri)
        val channelPath = CHANNEL_PATH_PREFIX + totalBytes + "/" + sanitize(fileName)

        val channelClient = Wearable.getChannelClient(context)
        val channel = runCatching {
            Tasks.await(channelClient.openChannel(targetNode.id, channelPath))
        }.getOrElse { e ->
            Log.e(TAG, "Failed to open channel: ${e.message}", e)
            return@withContext SendResult.Error("Failed to open channel: ${e.message}")
        }

        try {
            runCatching {
                val input = context.contentResolver.openInputStream(apkUri)
                    ?: error("Cannot read APK file")
                Tasks.await(channelClient.getOutputStream(channel)).use { out ->
                    input.use { copyWithProgress(it, out, totalBytes, onProgress) }
                }
            }.fold(
                onSuccess = { SendResult.Success },
                onFailure = { e ->
                    Log.e(TAG, "Send failed: ${e.message}", e)
                    SendResult.Error(e.message ?: "Transfer failed")
                },
            )
        } finally {
            runCatching { Tasks.await(channelClient.close(channel)) }
        }
    }

    private fun findReceiverNode(context: Context): Node? = runCatching {
        val info = Tasks.await(
            Wearable.getCapabilityClient(context)
                .getCapability(CAPABILITY_APK_RECEIVER, CapabilityClient.FILTER_REACHABLE)
        )
        info.nodes.firstOrNull { it.isNearby } ?: info.nodes.firstOrNull()
    }.getOrElse { e ->
        Log.e(TAG, "Capability lookup failed: ${e.message}", e)
        null
    }

    private fun readSize(context: Context, uri: Uri): Long = runCatching {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L
    }.getOrDefault(0L).coerceAtLeast(0L)

    private fun copyWithProgress(
        input: java.io.InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        onProgress: ((Float) -> Unit)?,
    ) {
        val buffer = ByteArray(BUFFER_SIZE)
        var sent = 0L
        var lastPercent = -1
        var read = input.read(buffer)
        while (read != -1) {
            output.write(buffer, 0, read)
            sent += read
            if (onProgress != null && totalBytes > 0) {
                val percent = ((sent * 100) / totalBytes).toInt()
                if (percent != lastPercent) {
                    lastPercent = percent
                    onProgress(sent.toFloat() / totalBytes)
                }
            }
            read = input.read(buffer)
        }
        output.flush()
    }

    private fun sanitize(fileName: String) = fileName.replace(Regex("[^a-zA-Z0-9._-]"), "_")
}
