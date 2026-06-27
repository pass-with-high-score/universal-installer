package app.pwhs.universalinstaller.presentation.composable

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.InstallMode
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.core.data.local.dataStore
import kotlinx.coroutines.flow.map
import org.koin.androidx.compose.koinViewModel

/**
 * Pill showing the active install backend. Tapping it opens a picker so the user can switch
 * engine (PackageInstaller / Shizuku / Root) right from the Install and Manage screens —
 * the switch reuses [SettingViewModel.setInstallMode], which runs the same Shizuku-permission
 * / root-request ladder as the Settings screen.
 */
@Composable
fun InstallerModeBadge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settingViewModel: SettingViewModel = koinViewModel()
    val settingState by settingViewModel.uiState.collectAsState()

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
    var showPicker by remember { mutableStateOf(false) }

    // Surface the hint events the switcher emits (e.g. "install Shizuku", "permission denied")
    // so the user isn't left wondering why the engine didn't change.
    LaunchedEffect(Unit) {
        settingViewModel.events.collect { stringRes ->
            Toast.makeText(context, context.getString(stringRes), Toast.LENGTH_LONG).show()
        }
    }

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
            .clickable { showPicker = true }
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

    if (showPicker) {
        val current = InstallMode.from(
            useShizuku = mode == Mode.Shizuku,
            useRoot = mode == Mode.Root,
        )
        // Mirror the Settings screen's enable/disable logic so unusable engines are dimmed:
        //  • Root is selectable only when libsu shipped AND su is reachable (READY) or still
        //    resolvable (DENIED/UNKNOWN). UNAVAILABLE/not-rooted → disabled.
        //  • Shizuku is disabled only when it genuinely can't run here (not installed /
        //    unsupported); NOT_RUNNING / NO_PERMISSION stay tappable since picking them kicks
        //    off the start/permission flow, same as Settings.
        val rootSelectable = settingState.rootSupported && (
            settingState.rootState == RootState.READY ||
            settingState.rootState == RootState.DENIED ||
            settingState.rootState == RootState.UNKNOWN
        )
        val shizukuSelectable = settingState.shizukuState != ShizukuState.UNSUPPORTED &&
            settingState.shizukuState != ShizukuState.NOT_INSTALLED
        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.installer_engine_dialog_title)) },
            text = {
                Column {
                    EngineOption(
                        title = stringResource(R.string.installer_mode_package_installer),
                        subtitle = stringResource(R.string.installer_engine_default_desc),
                        selected = current == InstallMode.DEFAULT,
                        enabled = true,
                        onClick = {
                            settingViewModel.setInstallMode(InstallMode.DEFAULT)
                            showPicker = false
                        },
                    )
                    EngineOption(
                        title = stringResource(R.string.installer_mode_shizuku),
                        subtitle = when (settingState.shizukuState) {
                            ShizukuState.NOT_INSTALLED -> stringResource(R.string.setting_shizuku_not_installed)
                            ShizukuState.UNSUPPORTED -> stringResource(R.string.setting_shizuku_unsupported)
                            else -> stringResource(R.string.installer_engine_shizuku_desc)
                        },
                        selected = current == InstallMode.SHIZUKU,
                        enabled = shizukuSelectable,
                        onClick = {
                            settingViewModel.setInstallMode(InstallMode.SHIZUKU)
                            showPicker = false
                        },
                    )
                    EngineOption(
                        title = stringResource(R.string.installer_mode_root),
                        subtitle = if (rootSelectable)
                            stringResource(R.string.installer_engine_root_desc)
                        else
                            stringResource(R.string.installer_engine_root_unsupported),
                        selected = current == InstallMode.ROOT,
                        enabled = rootSelectable,
                        onClick = {
                            settingViewModel.setInstallMode(InstallMode.ROOT)
                            showPicker = false
                        },
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun EngineOption(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            val titleColor = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            Text(text = title, style = MaterialTheme.typography.bodyLarge, color = titleColor)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private enum class Mode { Default, Shizuku, Root }
