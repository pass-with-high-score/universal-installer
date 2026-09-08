package app.pwhs.universalinstaller.presentation.setting.sections

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.SettingsSection
import app.pwhs.universalinstaller.presentation.setting.SyncOptions
import app.pwhs.universalinstaller.presentation.setting.components.matchesQuery
import app.pwhs.universalinstaller.presentation.sync.SyncActivity

internal fun LazyListScope.SyncSection(
    q: String,
    syncLabels: List<String>,
    context: Context,
    syncOptions: SyncOptions,
    onOpenSyncOptions: () -> Unit,
) {
    if (matchesQuery(q, syncLabels)) item {
        SettingsSection(
            title = stringResource(R.string.setting_section_sync_short),
            icon = Icons.Rounded.WifiTethering,
        ) {
            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.setting_sync_control_panel),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                supportingContent = {
                    Text(
                        stringResource(R.string.sync_hero_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Rounded.WifiTethering,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable {
                    context.startActivity(Intent(context, SyncActivity::class.java))
                },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            ListItem(
                headlineContent = {
                    Text(
                        stringResource(R.string.sync_server_settings),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                supportingContent = {
                    Text(
                        text = "${stringResource(R.string.sync_port)}: ${syncOptions.serverPort} • " +
                            if (syncOptions.requirePin) stringResource(R.string.sync_require_pin)
                            else stringResource(R.string.sync_require_pin_off),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                leadingContent = {
                    Icon(
                        imageVector = Icons.Rounded.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp),
                    )
                },
                trailingContent = {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                modifier = Modifier.clickable(onClick = onOpenSyncOptions),
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )
        }
    }
}
