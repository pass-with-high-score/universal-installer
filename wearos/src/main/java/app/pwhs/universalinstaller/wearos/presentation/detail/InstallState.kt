package app.pwhs.universalinstaller.wearos.presentation.detail

sealed interface InstallState {
    data object Idle : InstallState
    data class Installing(val progress: Float?) : InstallState
    data object Success : InstallState
    data class Failed(val message: String) : InstallState

    /** "Install unknown apps" is off, or the watch hides the toggle entirely. */
    data object NeedsUnknownSources : InstallState

    /** The package is unlikely to run on this watch; the user may override. */
    data class Incompatible(val reason: String) : InstallState
}
