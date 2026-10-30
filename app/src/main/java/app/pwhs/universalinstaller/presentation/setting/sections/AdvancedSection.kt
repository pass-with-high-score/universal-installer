package app.pwhs.universalinstaller.presentation.setting.sections

import android.content.ClipboardManager
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.pwhs.core.util.openUriSafely
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.SettingsSection
import app.pwhs.universalinstaller.presentation.setting.SecurityLevel
import app.pwhs.universalinstaller.presentation.setting.components.SecurityLevelSelector
import app.pwhs.universalinstaller.presentation.setting.components.matchesQuery

internal fun LazyListScope.AdvancedSection(
    q: String,
    advancedLabels: List<String>,
    virusTotalApiKey: String,
    onVirusTotalKeyChanged: (String) -> Unit,
    securityLevel: SecurityLevel,
    onSecurityLevelChanged: (SecurityLevel) -> Unit,
) {
    if (matchesQuery(q, advancedLabels)) item {
        val context = LocalContext.current
        val uriHandler = LocalUriHandler.current

        SettingsSection(title = stringResource(R.string.setting_section_advanced), icon = Icons.Rounded.Terminal) {
            OutlinedTextField(
                value = virusTotalApiKey,
                onValueChange = onVirusTotalKeyChanged,
                label = { Text(stringResource(R.string.setting_vt_api_key_title)) },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                leadingIcon = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary) },
                trailingIcon = {
                    IconButton(onClick = {
                        val clipboard = context.getSystemService<ClipboardManager>()
                        val clipText = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                        if (!clipText.isNullOrBlank()) {
                            onVirusTotalKeyChanged(clipText)
                        }
                    }) {
                        Icon(
                            imageVector = Icons.Rounded.ContentPaste,
                            contentDescription = stringResource(R.string.virustotal_setup_paste),
                            modifier = Modifier.size(20.dp),
                        )
                    }
                },
                placeholder = { Text(stringResource(R.string.setting_vt_api_key_placeholder)) },
                supportingText = {
                    Text(stringResource(R.string.virustotal_setup_dialog_desc))
                },
                singleLine = true,
            )
            TextButton(
                onClick = { uriHandler.openUriSafely(context, "https://www.virustotal.com/gui/my-apikey") },
                modifier = Modifier.padding(horizontal = 16.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.virustotal_setup_get_free_key),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            SecurityLevelSelector(
                current = securityLevel,
                onChange = onSecurityLevelChanged,
            )
        }
    }
}
