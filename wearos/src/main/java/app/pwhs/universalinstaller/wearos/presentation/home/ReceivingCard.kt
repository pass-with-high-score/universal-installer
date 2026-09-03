package app.pwhs.universalinstaller.wearos.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.LinearProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.data.WearReceiveState
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme

/** Live view of the transfer the phone is streaming, so an empty list is never a mystery. */
@Composable
fun ReceivingCard(state: WearReceiveState, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state is WearReceiveState.Receiving) {
                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
            }
            Text(
                text = when (state) {
                    is WearReceiveState.Receiving ->
                        stringResource(R.string.receiving, state.fileName)

                    is WearReceiveState.Failed -> stringResource(R.string.receive_failed)
                    WearReceiveState.Idle -> ""
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state is WearReceiveState.Failed) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val progress = (state as? WearReceiveState.Receiving)?.progress
        if (progress != null) {
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
        }
    }
}

@WearPreviewDevices
@Composable
private fun ReceivingCardProgressPreview() {
    UniversalInstallerTheme {
        ReceivingCard(WearReceiveState.Receiving("watchface.apk", 4_000_000, 12_000_000))
    }
}

@WearPreviewDevices
@Composable
private fun ReceivingCardFailedPreview() {
    UniversalInstallerTheme { ReceivingCard(WearReceiveState.Failed("watchface.apk")) }
}
