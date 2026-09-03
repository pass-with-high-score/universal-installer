package app.pwhs.universalinstaller.wearos.presentation.detail

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.pwhs.universalinstaller.wearos.R

/** The single action offered for a state, or null when the state is waiting on the system. */
internal enum class Action(val labelRes: Int) {
    INSTALL(R.string.install),
    INSTALL_ANYWAY(R.string.install_anyway),
    RETRY(R.string.retry),
    OPEN_SETTINGS(R.string.unknown_sources_open_settings),
}

internal fun InstallState.action(): Action? = when (this) {
    is InstallState.Idle -> Action.INSTALL
    is InstallState.Incompatible -> Action.INSTALL_ANYWAY
    is InstallState.Failed -> Action.RETRY
    is InstallState.NeedsUnknownSources -> Action.OPEN_SETTINGS
    else -> null
}

/** Idle says nothing: the header already shows everything there is to know about the package. */
internal fun InstallState.hasStatus(): Boolean = when (this) {
    is InstallState.Installing, is InstallState.Failed,
    is InstallState.NeedsUnknownSources, is InstallState.Incompatible -> true

    else -> false
}

@Composable
internal fun InstallState.statusText(): String = when (this) {
    is InstallState.Installing -> stringResource(R.string.installing)
    is InstallState.Failed -> "${stringResource(R.string.install_failed)}\n$message"
    is InstallState.NeedsUnknownSources -> stringResource(R.string.unknown_sources_msg)
    is InstallState.Incompatible -> reason
    else -> ""
}
