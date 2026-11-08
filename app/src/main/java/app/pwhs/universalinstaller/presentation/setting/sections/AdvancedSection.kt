package app.pwhs.universalinstaller.presentation.setting.sections

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
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
    securityScanDisabled: Boolean,
    onToggleSecurityScan: (Boolean) -> Unit,
) {
    if (matchesQuery(q, advancedLabels)) item {
        SettingsSection(
            title = stringResource(R.string.setting_section_advanced),
            icon = Icons.Rounded.Security,
        ) {
            SearchableItem(
                q,
                "Quét bảo mật APK (VirusDetec)",
                "virusdetec scan malware security bao mat ma doc nguy hiem",
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Quét bảo mật APK (VirusDetec)",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (securityScanDisabled) {
                                "Đã tắt quét tự động"
                            } else {
                                "Tự động phân tích chữ ký, mã độc và hành vi bất thường"
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
                        Switch(
                            checked = !securityScanDisabled,
                            onCheckedChange = { onToggleSecurityScan(!it) },
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
