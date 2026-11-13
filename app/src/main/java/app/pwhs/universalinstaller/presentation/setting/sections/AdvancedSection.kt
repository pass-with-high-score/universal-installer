package app.pwhs.universalinstaller.presentation.setting.sections

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.SettingsSuggest
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.SettingsSection
import app.pwhs.universalinstaller.presentation.setting.components.SearchableItem
import app.pwhs.universalinstaller.presentation.setting.components.matchesQuery
import app.pwhs.universalinstaller.presentation.setting.virustotal.VirusTotalSettingActivity

internal fun LazyListScope.AdvancedSection(
    q: String,
    advancedLabels: List<String>,
    securityScanDisabled: Boolean,
    onToggleSecurityScan: (Boolean) -> Unit = {},
) {
    if (matchesQuery(q, advancedLabels)) item {
        val context = LocalContext.current
        SettingsSection(
            title = stringResource(R.string.setting_section_advanced),
            icon = Icons.Rounded.SettingsSuggest,
        ) {
            SearchableItem(
                q,
                "Quét bảo mật APK (AppShield / VirusTotal)",
                "appshield virusdetec virustotal scan malware security bao mat ma doc nguy hiem api key fallback",
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = "Quét bảo mật APK",
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = if (securityScanDisabled) {
                                "Đã tắt quét bảo mật"
                            } else {
                                "Cấu hình bộ quét chính, dự phòng và khóa API"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(
                            painter = painterResource(R.drawable.ic_appshield),
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
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, VirusTotalSettingActivity::class.java))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
