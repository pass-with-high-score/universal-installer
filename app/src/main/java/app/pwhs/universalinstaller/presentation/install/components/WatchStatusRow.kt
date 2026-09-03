package app.pwhs.universalinstaller.presentation.install.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WatchOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme

/** Which watch a transfer would go to, or why none is reachable. */
@Composable
internal fun WatchStatusRow(
    watchName: String?,
    isLooking: Boolean,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = watchName != null
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (connected) MaterialTheme.colorScheme.primaryContainer
                else MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (isLooking) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            } else {
                Icon(
                    imageVector = if (connected) Icons.Rounded.Watch else Icons.Rounded.WatchOff,
                    contentDescription = null,
                    tint = if (connected) MaterialTheme.colorScheme.onPrimaryContainer
                           else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = watchName ?: stringResource(R.string.watch_status_none),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        if (connected) R.string.watch_status_ready else R.string.watch_status_none_hint
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!connected && !isLooking) {
                TextButton(onClick = onRetry) { Text(stringResource(R.string.watch_status_retry)) }
            }
        }
    }
}

@Preview
@Composable
private fun WatchStatusRowConnectedPreview() {
    UniversalInstallerTheme {
        WatchStatusRow(watchName = "Pixel Watch 3", isLooking = false, onRetry = {})
    }
}

@Preview
@Composable
private fun WatchStatusRowNoWatchPreview() {
    UniversalInstallerTheme {
        WatchStatusRow(watchName = null, isLooking = false, onRetry = {})
    }
}

@Preview
@Composable
private fun WatchStatusRowLookingPreview() {
    UniversalInstallerTheme {
        WatchStatusRow(watchName = null, isLooking = true, onRetry = {})
    }
}
