package app.pwhs.universalinstaller.wearos.presentation.settings

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme

/** A settings entry. [onClick] null makes it read-only, for rows that only report a value. */
@Composable
fun SettingRow(
    title: String,
    summary: String,
    onClick: (() -> Unit)?,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
) {
    TitleCard(
        onClick = onClick ?: {},
        enabled = onClick != null,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        modifier = modifier.fillMaxWidth(),
        transformation = transformation,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@WearPreviewDevices
@Composable
private fun SettingRowPreview() {
    UniversalInstallerTheme { SettingRow("Storage", "Queue 119 MB · 2.4 GB free", {}) }
}

@WearPreviewDevices
@Composable
private fun SettingRowReadOnlyPreview() {
    UniversalInstallerTheme { SettingRow("About", "1.12.0 (1035)", null) }
}
