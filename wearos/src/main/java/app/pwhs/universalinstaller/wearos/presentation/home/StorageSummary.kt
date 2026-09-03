package app.pwhs.universalinstaller.wearos.presentation.home

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.presentation.component.ThinProgressBar
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme

/**
 * What the queue costs, next to what the watch has left. A sideloaded package can be a sizeable
 * fraction of a watch's storage, so this belongs where the queue is, not only in Settings.
 */
@Composable
fun StorageSummary(
    queueBytes: Long,
    freeBytes: Long,
    usedFraction: Float,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = stringResource(
                R.string.settings_storage_summary,
                Formatter.formatShortFileSize(context, queueBytes),
                Formatter.formatShortFileSize(context, freeBytes),
            ),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        ThinProgressBar(
            progress = usedFraction,
            modifier = Modifier.fillMaxWidth(),
            // Storage is a gauge, not the app's own progress; the muted tone keeps the queue's
            // orange meaning "something is happening".
            color = MaterialTheme.colorScheme.secondary,
        )
    }
}

@WearPreviewDevices
@Composable
private fun StorageSummaryPreview() {
    UniversalInstallerTheme {
        StorageSummary(queueBytes = 160_000_000L, freeBytes = 5_300_000_000L, usedFraction = 0.34f)
    }
}

@WearPreviewDevices
@Composable
private fun StorageSummaryFullPreview() {
    UniversalInstallerTheme {
        StorageSummary(queueBytes = 0L, freeBytes = 120_000_000L, usedFraction = 0.96f)
    }
}
