package app.pwhs.universalinstaller.presentation.setting.sections

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Security
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
import app.pwhs.universalinstaller.presentation.setting.components.SearchableItem
import app.pwhs.universalinstaller.presentation.setting.components.matchesQuery

internal fun LazyListScope.AdvancedSection(
    q: String,
    advancedLabels: List<String>,
    virusTotalDisabled: Boolean,
    onVirusTotalClick: () -> Unit,
) {
    if (matchesQuery(q, advancedLabels)) item {
        SettingsSection(
            title = stringResource(R.string.setting_section_advanced),
            icon = Icons.Rounded.Security,
        ) {
            SearchableItem(
                q,
                stringResource(app.pwhs.core.R.string.setting_virustotal_title),
                "virustotal scan malware security cloud key api strict",
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(app.pwhs.core.R.string.setting_virustotal_title),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (virusTotalDisabled) {
                                stringResource(app.pwhs.core.R.string.setting_virustotal_status_disabled)
                            } else {
                                stringResource(app.pwhs.core.R.string.setting_virustotal_status_enabled)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                    },
                    trailingContent = {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                        )
                    },
                    modifier = Modifier.clickable(onClick = onVirusTotalClick),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
