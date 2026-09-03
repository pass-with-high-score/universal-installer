package app.pwhs.universalinstaller.wearos.data

/** What the watch is doing with an incoming package, as far as the UI needs to know. */
sealed interface WearReceiveState {
    data object Idle : WearReceiveState

    data class Receiving(val fileName: String, val bytes: Long, val expectedBytes: Long) :
        WearReceiveState {
        val progress: Float?
            get() = if (expectedBytes > 0) (bytes.toFloat() / expectedBytes).coerceIn(0f, 1f) else null
    }

    data class Failed(val fileName: String) : WearReceiveState
}
