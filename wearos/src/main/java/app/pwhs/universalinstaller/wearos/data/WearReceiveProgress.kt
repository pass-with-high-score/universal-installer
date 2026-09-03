package app.pwhs.universalinstaller.wearos.data

import android.graphics.Bitmap
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Process-wide bridge between [WearReceiverService], which owns the transfer, and the home screen,
 * which only renders it. Mirrors the phone's `WearTransferState`.
 */
object WearReceiveProgress {

    private val _state = MutableStateFlow<WearReceiveState>(WearReceiveState.Idle)
    val state: StateFlow<WearReceiveState> = _state.asStateFlow()

    // The icon message and the channel open race each other, so whichever lands first parks its
    // half here and the other picks it up.
    private var pendingIcon: Pair<String, Bitmap>? = null

    fun update(next: WearReceiveState) {
        _state.value = when {
            next !is WearReceiveState.Receiving -> next
            next.icon != null -> next
            else -> next.copy(icon = pendingIcon?.takeIf { it.first == next.fileName }?.second)
        }
    }

    fun setIcon(fileName: String, icon: Bitmap) {
        pendingIcon = fileName to icon
        _state.update { current ->
            if (current is WearReceiveState.Receiving && current.fileName == fileName) {
                current.copy(icon = icon)
            } else {
                current
            }
        }
    }

    fun reset() {
        pendingIcon = null
        _state.value = WearReceiveState.Idle
    }
}
