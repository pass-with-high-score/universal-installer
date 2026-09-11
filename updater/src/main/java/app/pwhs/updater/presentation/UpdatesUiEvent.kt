package app.pwhs.updater.presentation

sealed interface UpdatesUiEvent {
    data class ShowToast(val message: String) : UpdatesUiEvent
}
