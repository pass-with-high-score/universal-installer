package app.pwhs.universalinstaller.wearos.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bridge between [WearReceiverService], which owns the transfer, and the home screen,
 * which only renders it. Mirrors the phone's `WearTransferState`.
 */
object WearReceiveProgress {

    private val _state = MutableStateFlow<WearReceiveState>(WearReceiveState.Idle)
    val state: StateFlow<WearReceiveState> = _state.asStateFlow()

    fun update(next: WearReceiveState) {
        _state.value = next
    }

    fun reset() {
        _state.value = WearReceiveState.Idle
    }
}
