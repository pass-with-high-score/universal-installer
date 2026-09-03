package app.pwhs.universalinstaller.presentation.install.wear

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.Log
import androidx.core.graphics.scale
import app.pwhs.core.data.ApkMetadataReader
import app.pwhs.core.install.isBundleFileName
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.ChannelClient
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

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

    /** Icon channel, paired with [CHANNEL_PATH_PREFIX] by file name. */
    const val META_PATH_PREFIX = "/apk-meta/"

    private const val BUFFER_SIZE = 8192

    /** Opening a channel talks to a watch over Bluetooth; slow is normal, forever is not. */
    private const val OPEN_TIMEOUT_SECONDS = 30L
    private const val CLOSE_TIMEOUT_SECONDS = 10L
    private const val STALL_TIMEOUT_MS = 60_000L
    private const val STALL_CHECK_MS = 5_000L
    private const val ICON_PX = 96

    sealed interface SendResult {
        data object Success : SendResult
        data object NoWatchFound : SendResult
        data class Unsupported(val reason: String) : SendResult
        data class Error(val message: String) : SendResult
    }

    /** Display name of a reachable watch that has this app installed, or null when there is none. */
    suspend fun connectedWatchName(context: Context): String? = withContext(Dispatchers.IO) {
        findReceiverNode(context)?.displayName
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
            Tasks.await(
                channelClient.openChannel(targetNode.id, channelPath),
                OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS,
            )
        }.getOrElse { e ->
            Log.e(TAG, "Failed to open channel: ${e.message}", e)
            return@withContext SendResult.Error("Failed to open channel: ${e.message}")
        }

        try {
            runCatching {
                val input = context.contentResolver.openInputStream(apkUri)
                    ?: error("Cannot read APK file")
                val out = Tasks.await(
                    channelClient.getOutputStream(channel),
                    OPEN_TIMEOUT_SECONDS, TimeUnit.SECONDS,
                )
                out.use { stream ->
                    input.use { copyWatched(channelClient, channel, it, stream, totalBytes, onProgress) }
                }
            }.fold(
                onSuccess = { SendResult.Success },
                onFailure = { e ->
                    Log.e(TAG, "Send failed: ${e.message}", e)
                    SendResult.Error(e.message ?: "Transfer failed")
                },
            )
        } finally {
            runCatching { Tasks.await(channelClient.close(channel), CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
        }
    }

    /**
     * Runs the copy with a watchdog. A blocked `write` cannot be interrupted by cancelling the
     * coroutine — the only thing that frees it is closing the channel underneath, which is what the
     * watchdog does once the byte count has not moved for [STALL_TIMEOUT_MS]. Without this a watch
     * that stops reading leaves the sender parked on write() for good.
     */
    private suspend fun copyWatched(
        channelClient: ChannelClient,
        channel: ChannelClient.Channel,
        input: java.io.InputStream,
        output: java.io.OutputStream,
        totalBytes: Long,
        onProgress: ((Float) -> Unit)?,
    ) = coroutineScope {
        val lastMovement = AtomicLong(System.currentTimeMillis())
        val watchdog = launch {
            while (isActive) {
                delay(STALL_CHECK_MS)
                if (System.currentTimeMillis() - lastMovement.get() > STALL_TIMEOUT_MS) {
                    Log.e(TAG, "No bytes moved for ${STALL_TIMEOUT_MS}ms — closing the channel")
                    runCatching { Tasks.await(channelClient.close(channel), CLOSE_TIMEOUT_SECONDS, TimeUnit.SECONDS) }
                    return@launch
                }
            }
        }
        try {
            copyWithProgress(input, output, totalBytes) { progress ->
                lastMovement.set(System.currentTimeMillis())
                onProgress?.invoke(progress)
            }
        } finally {
            watchdog.cancel()
        }
    }

    /**
     * Sends the package's launcher icon ahead of the payload, so the watch can show what is
     * arriving — it cannot read the icon itself until the archive is complete. Best effort: a
     * transfer with no icon is still a fine transfer, so every failure here is swallowed.
     */
    suspend fun sendIcon(context: Context, apkUri: Uri, fileName: String) =
        withContext(Dispatchers.IO) {
            runCatching {
                val node = findReceiverNode(context) ?: return@withContext
                val metadata = ApkMetadataReader(context)
                    .readMetadata(apkUri, fileName.isBundleFileName()) ?: return@withContext
                val icon = metadata.icon ?: return@withContext
                val bytes = ByteArrayOutputStream().use { out ->
                    icon.scale(ICON_PX, ICON_PX).compress(Bitmap.CompressFormat.PNG, 100, out)
                    out.toByteArray()
                }
                Tasks.await(
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, META_PATH_PREFIX + sanitize(fileName), bytes)
                )
            }.onFailure { Log.d(TAG, "Icon not sent: ${it.message}") }
            Unit
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

    private suspend fun copyWithProgress(
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
            // Lets a cancel take effect within one chunk instead of at the end of the file.
            currentCoroutineContext().ensureActive()
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
