package app.pwhs.universalinstaller.presentation.install.wear

import app.pwhs.universalinstaller.presentation.install.WatchSendState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Process-wide bridge between [WearTransferService], which owns the transfer, and the install
 * screen, which only renders it. Mirrors `SyncManager` so a transfer outlives the ViewModel.
 */
object WearTransferState {

    private val _state = MutableStateFlow<WatchSendState>(WatchSendState.Idle)
    val state: StateFlow<WatchSendState> = _state.asStateFlow()

    fun update(next: WatchSendState) {
        _state.value = next
    }

    fun reset() {
        _state.value = WatchSendState.Idle
    }
}
