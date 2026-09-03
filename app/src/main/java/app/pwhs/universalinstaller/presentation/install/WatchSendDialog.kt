package app.pwhs.universalinstaller.presentation.install

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme

@Composable
fun WatchSendDialog(
    state: WatchSendState,
    onDismiss: () -> Unit,
    onCancel: () -> Unit = onDismiss,
    onConfirmSend: () -> Unit = {},
) {
    if (state is WatchSendState.Idle) return

    AlertDialog(
        // An in-flight transfer must not be dismissed by tapping outside.
        onDismissRequest = { if (state !is WatchSendState.Sending) onDismiss() },
        icon = {
            Icon(
                imageVector = state.icon(),
                contentDescription = null,
                tint = state.iconTint(),
                modifier = Modifier.size(32.dp),
            )
        },
        title = {
            Text(
                text = stringResource(state.titleRes()),
                style = MaterialTheme.typography.headlineSmall,
            )
        },
        text = { WatchSendBody(state) },
        confirmButton = {
            when {
                state is WatchSendState.ConfirmNotWatchApp ->
                    Button(onClick = onConfirmSend) {
                        Text(stringResource(R.string.watch_send_anyway))
                    }

                state.isTerminal() ->
                    Button(onClick = onDismiss) { Text(stringResource(android.R.string.ok)) }
            }
        },
        dismissButton = {
            if (!state.isTerminal()) {
                TextButton(onClick = onCancel) { Text(stringResource(R.string.cancel)) }
            }
        },
    )
}

@Composable
private fun WatchSendBody(state: WatchSendState) {
    when (state) {
        is WatchSendState.CheckingWatch -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp))
            Text(
                text = stringResource(R.string.watch_send_connecting),
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        is WatchSendState.Sending -> Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.watch_send_sending, (state.progress * 100).toInt()),
                style = MaterialTheme.typography.bodyMedium,
            )
            LinearProgressIndicator(
                progress = { state.progress },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        else -> Text(
            text = state.message(),
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun WatchSendState.message(): String = when (this) {
    is WatchSendState.Success -> stringResource(R.string.watch_send_success_msg)
    is WatchSendState.ConfirmNotWatchApp ->
        stringResource(R.string.watch_send_not_watch_app_msg, fileName)
    is WatchSendState.NoWatch -> stringResource(R.string.watch_send_no_watch_msg)
    is WatchSendState.Unsupported -> reason
    is WatchSendState.Error -> message
    else -> ""
}

private fun WatchSendState.titleRes(): Int = when (this) {
    is WatchSendState.Success -> R.string.watch_send_success_title
    is WatchSendState.ConfirmNotWatchApp -> R.string.watch_send_not_watch_app_title
    is WatchSendState.NoWatch -> R.string.watch_send_no_watch_title
    is WatchSendState.Unsupported -> R.string.watch_send_unsupported_title
    is WatchSendState.Error -> R.string.watch_send_error_title
    else -> R.string.watch_send_title
}

private fun WatchSendState.icon(): ImageVector = when (this) {
    is WatchSendState.Success -> Icons.Rounded.CheckCircle
    is WatchSendState.ConfirmNotWatchApp -> Icons.AutoMirrored.Rounded.HelpOutline
    is WatchSendState.Unsupported, is WatchSendState.Error -> Icons.Rounded.ErrorOutline
    else -> Icons.Rounded.Watch
}

@Composable
private fun WatchSendState.iconTint(): Color = when (this) {
    is WatchSendState.NoWatch, is WatchSendState.Unsupported, is WatchSendState.Error ->
        MaterialTheme.colorScheme.error

    else -> MaterialTheme.colorScheme.primary
}

private fun WatchSendState.isTerminal(): Boolean = when (this) {
    is WatchSendState.CheckingWatch,
    is WatchSendState.Sending,
    is WatchSendState.ConfirmNotWatchApp -> false

    else -> true
}

@Preview
@Composable
private fun WatchSendDialogCheckingPreview() {
    UniversalInstallerTheme { WatchSendDialog(WatchSendState.CheckingWatch, onDismiss = {}) }
}

@Preview
@Composable
private fun WatchSendDialogSendingPreview() {
    UniversalInstallerTheme { WatchSendDialog(WatchSendState.Sending(0.42f), onDismiss = {}) }
}

@Preview
@Composable
private fun WatchSendDialogSuccessPreview() {
    UniversalInstallerTheme { WatchSendDialog(WatchSendState.Success, onDismiss = {}) }
}

@Preview
@Composable
private fun WatchSendDialogNoWatchPreview() {
    UniversalInstallerTheme { WatchSendDialog(WatchSendState.NoWatch, onDismiss = {}) }
}

@Preview
@Composable
private fun WatchSendDialogUnsupportedPreview() {
    UniversalInstallerTheme {
        WatchSendDialog(WatchSendState.Unsupported("This is a set of separate split APKs."), onDismiss = {})
    }
}

@Preview
@Composable
private fun WatchSendDialogConfirmNotWatchAppPreview() {
    UniversalInstallerTheme {
        WatchSendDialog(
            state = WatchSendState.ConfirmNotWatchApp(Uri.EMPTY, "Instagram.apk"),
            onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun WatchSendDialogErrorPreview() {
    UniversalInstallerTheme { WatchSendDialog(WatchSendState.Error("Transfer failed"), onDismiss = {}) }
}
