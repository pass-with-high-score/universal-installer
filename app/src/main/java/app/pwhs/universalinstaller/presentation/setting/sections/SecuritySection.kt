package app.pwhs.universalinstaller.presentation.setting.sections

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
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
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.SettingsSection
import app.pwhs.universalinstaller.presentation.setting.components.SearchableItem
import app.pwhs.universalinstaller.presentation.setting.components.matchesQuery
import app.pwhs.universalinstaller.presentation.setting.security.SecurityActivity

internal fun LazyListScope.SecuritySection(
    q: String,
    securityLabels: List<String>,
    context: Context,
) {
    if (matchesQuery(q, securityLabels)) item {
        SettingsSection(
            title = stringResource(R.string.setting_section_security),
            icon = Icons.Rounded.Security,
        ) {
            SearchableItem(
                q,
                stringResource(R.string.setting_security_screen_title),
                "security lock pin biometric password fingerprint blacklist parental control",
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.setting_security_screen_title)) },
                    supportingContent = { Text(stringResource(R.string.setting_security_entry_subtitle)) },
                    leadingContent = {
                        Icon(Icons.Rounded.Security, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    trailingContent = {
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, SecurityActivity::class.java))
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
        }
    }
}
