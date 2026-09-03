package app.pwhs.universalinstaller.wearos.data

import android.graphics.Bitmap

/** What the watch is doing with an incoming package, as far as the UI needs to know. */
sealed interface WearReceiveState {
    data object Idle : WearReceiveState

    /**
     * [icon] arrives on a separate message ahead of the payload — the file being written is a
     * partial archive, so nothing can be parsed out of it until the transfer finishes.
     */
    data class Receiving(
        val fileName: String,
        val bytes: Long,
        val expectedBytes: Long,
        val icon: Bitmap? = null,
    ) : WearReceiveState {
        val progress: Float?
            get() = if (expectedBytes > 0) (bytes.toFloat() / expectedBytes).coerceIn(0f, 1f) else null
    }

    data class Failed(val fileName: String) : WearReceiveState
}
