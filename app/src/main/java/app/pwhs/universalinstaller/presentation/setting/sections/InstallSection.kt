package app.pwhs.universalinstaller.presentation.setting.sections

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material3.HorizontalDivider
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
import app.pwhs.universalinstaller.util.DhizukuCompat
import app.pwhs.universalinstaller.util.DhizukuState
import app.pwhs.universalinstaller.presentation.setting.InstallMode
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.SettingUiState
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.presentation.setting.components.CustomAuthorizerCard
import app.pwhs.universalinstaller.presentation.setting.components.InstallModeSelector
import app.pwhs.universalinstaller.presentation.setting.components.OptionGroupHeader
import app.pwhs.universalinstaller.presentation.setting.components.SearchableItem
import app.pwhs.universalinstaller.presentation.setting.components.SwitchPreference
import app.pwhs.universalinstaller.presentation.setting.components.matchesQuery

internal fun LazyListScope.InstallSection(
    q: String,
    installLabels: List<String>,
    uiState: SettingUiState,
    dhizukuState: DhizukuState,
    useDhizuku: Boolean,
    context: Context,
    onInstallModeChanged: (InstallMode) -> Unit,
    onUseDhizukuChanged: (Boolean) -> Unit,
    onRootRetry: () -> Unit,
    onDeleteApkChanged: (Boolean) -> Unit,
    onAutoOpenAfterInstallChanged: (Boolean) -> Unit,
    onDefaultInstallerChanged: (Boolean) -> Unit,
    onCustomAuthorizerCommandChange: (String) -> Unit = {},
    onTestCustomAuthorizerCommand: suspend (String) -> Result<String> = { Result.success("") },
    onOpenInstallOptions: () -> Unit = {},
) {
    if (matchesQuery(q, installLabels)) item {
        SettingsSection(title = stringResource(R.string.setting_section_installation), icon = Icons.Rounded.SettingsApplications) {
            // Group headers only while unfiltered: a header whose items were all
            // searched away is a label over nothing. Same rule the divider below uses.
            if (q.isBlank()) OptionGroupHeader(stringResource(R.string.setting_group_installing))
            SearchableItem(q, stringResource(R.string.setting_install_mode_title), "shizuku dhizuku root default custom microg") {
                val currentMode = InstallMode.from(
                    useShizuku = uiState.useShizuku,
                    useRoot = uiState.useRoot,
                    useDhizuku = useDhizuku,
                    useCustomAuthorizer = uiState.useCustomAuthorizer,
                    useMicroG = uiState.useMicroG,
                )
                InstallModeSelector(
                    currentMode = currentMode,
                    shizukuState = uiState.shizukuState,
                    rootSupported = uiState.rootSupported,
                    rootState = uiState.rootState,
                    dhizukuSupported = DhizukuCompat.isSupported,
                    dhizukuState = dhizukuState,
                    microGSupported = app.pwhs.universalinstaller.util.MicroGCompat.isAvailable(context),
                    onModeChange = onInstallModeChanged,
                )
                if (currentMode == InstallMode.CUSTOM) {
                    CustomAuthorizerCard(
                        command = uiState.customAuthorizerCommand,
                        onCommandChange = onCustomAuthorizerCommandChange,
                        onTestCommand = onTestCustomAuthorizerCommand,
                    )
                }
                if (uiState.rootSupported && uiState.useRoot && uiState.rootState == RootState.DENIED) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_retry_root)) },
                        leadingContent = { Icon(Icons.Rounded.RocketLaunch, null, tint = MaterialTheme.colorScheme.primary) },
                        modifier = Modifier.clickable { onRootRetry() },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            SearchableItem(
                q,
                stringResource(R.string.setting_section_install_options),
                "shizuku root dhizuku downgrade replace permission test bypass source rollback uninstall",
            ) {
                ListItem(
                    headlineContent = {
                        Text(
                            text = stringResource(R.string.setting_section_install_options),
                            style = MaterialTheme.typography.bodyLarge,
                        )
                    },
                    supportingContent = {
                        Text(
                            text = stringResource(R.string.setting_install_options_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    leadingContent = {
                        Icon(
                            imageVector = Icons.Rounded.AdminPanelSettings,
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
                    modifier = Modifier.clickable(onClick = onOpenInstallOptions),
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }

            if (q.isBlank()) OptionGroupHeader(stringResource(R.string.setting_group_after_install))
            SearchableItem(q, stringResource(R.string.setting_delete_apk_title)) {
                SwitchPreference(
                    title = stringResource(R.string.setting_delete_apk_title),
                    checked = uiState.deleteApkAfterInstall,
                    onCheckedChange = onDeleteApkChanged,
                )
            }
            SearchableItem(q, stringResource(R.string.setting_auto_open_title), stringResource(R.string.setting_auto_open_subtitle)) {
                SwitchPreference(
                    title = stringResource(R.string.setting_auto_open_title),
                    subtitle = stringResource(R.string.setting_auto_open_subtitle),
                    checked = uiState.autoOpenAfterInstall,
                    onCheckedChange = onAutoOpenAfterInstallChanged,
                )
            }

            // Everything about how the installer looks — the external-open modes, the
            // card position, auto-confirm, the Download tab — now lives on its own
            // screen. The keyword list keeps this row findable by what moved.
            if (q.isBlank()) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
            }
            SearchableItem(
                q,
                stringResource(R.string.install_ui_screen_title),
                "dialog notification bottom sheet position card download tab appearance",
            ) {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.install_ui_screen_title)) },
                    supportingContent = { Text(stringResource(R.string.install_ui_entry_subtitle)) },
                    leadingContent = {
                        Icon(Icons.Rounded.Wallpaper, null, tint = MaterialTheme.colorScheme.primary)
                    },
                    modifier = Modifier.clickable {
                        context.startActivity(
                            android.content.Intent(
                                context,
                                app.pwhs.universalinstaller.presentation.setting.installui.InstallUiActivity::class.java,
                            )
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                )
            }
            SearchableItem(q, stringResource(R.string.setting_default_installer_title), stringResource(R.string.setting_default_installer_subtitle)) {
                SwitchPreference(
                    title = stringResource(R.string.setting_default_installer_title),
                    subtitle = stringResource(R.string.setting_default_installer_subtitle),
                    checked = uiState.isDefaultInstaller,
                    onCheckedChange = onDefaultInstallerChanged,
                    // Don't gate on shizukuAvailable here — that's true at NO_PERMISSION too,
                    // and the toggle would silently no-op. Require the backend to be actually
                    // ready; tapping the disabled-state hint covers the "needs grant" case.
                    enabled = uiState.shizukuState == ShizukuState.READY ||
                            uiState.rootState == RootState.READY
                )
            }
        }
    }
}
