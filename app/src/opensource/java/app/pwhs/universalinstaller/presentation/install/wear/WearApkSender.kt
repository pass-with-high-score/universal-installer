package app.pwhs.universalinstaller.presentation.install.wear

import android.content.Context
import android.net.Uri

/**
 * The open-source build ships no Google Play Services libraries, so Wear OS transfer over the
 * Wearable Data Layer is unavailable.
 *
 * The `play` source set defines the real implementation using Google Play Services Wearable.
 * Keep public signatures identical so shared presentation code compiles unchanged on both flavors.
 */
object WearApkSender {

    const val isAvailable = false

    sealed interface SendResult {
        data object Success : SendResult
        data object NoWatchFound : SendResult
        data class Unsupported(val reason: String) : SendResult
        data class Error(val message: String) : SendResult
    }

    suspend fun connectedWatchName(context: Context): String? = null

    suspend fun send(
        context: Context,
        apkUri: Uri,
        fileName: String,
        onProgress: ((WearSendProgress) -> Unit)? = null,
    ): SendResult = SendResult.NoWatchFound

    @Suppress("UnusedParameter")
    suspend fun sendIcon(context: Context, apkUri: Uri, fileName: String) = Unit
}
