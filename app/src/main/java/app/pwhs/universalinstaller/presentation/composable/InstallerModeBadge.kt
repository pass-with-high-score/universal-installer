package app.pwhs.universalinstaller.presentation.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.dataStore
import kotlinx.coroutines.flow.map

@Composable
fun InstallerModeBadge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val modeFlow = remember(context) {
        context.dataStore.data.map { prefs ->
            when {
                prefs[PreferencesKeys.USE_ROOT] == true -> Mode.Root
                prefs[PreferencesKeys.USE_SHIZUKU] == true -> Mode.Shizuku
                else -> Mode.Default
            }
        }
    }
    val mode by modeFlow.collectAsState(initial = Mode.Default)

    val label = when (mode) {
        Mode.Root -> stringResource(R.string.installer_mode_root)
        Mode.Shizuku -> stringResource(R.string.installer_mode_shizuku)
        Mode.Default -> stringResource(R.string.installer_mode_package_installer)
    }
    val icon = when (mode) {
        Mode.Root -> Icons.Rounded.Key
        Mode.Shizuku -> Icons.Rounded.AdminPanelSettings
        Mode.Default -> Icons.Rounded.Android
    }
    val privileged = mode == Mode.Root || mode == Mode.Shizuku
    val container = if (privileged)
        MaterialTheme.colorScheme.primaryContainer
    else
        MaterialTheme.colorScheme.surfaceContainerHigh
    val content = if (privileged)
        MaterialTheme.colorScheme.onPrimaryContainer
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(container)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = content,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = stringResource(R.string.installer_mode_using, label),
            style = MaterialTheme.typography.labelSmall,
            color = content,
            modifier = Modifier.padding(start = 6.dp),
        )
    }
}

private enum class Mode { Default, Shizuku, Root }
