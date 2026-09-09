package app.pwhs.universalinstaller.presentation.composable

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
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
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.InstallMode
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.presentation.setting.security.util.SystemInstallerManager
import app.pwhs.universalinstaller.util.DhizukuCompat
import app.pwhs.universalinstaller.util.DhizukuState
import app.pwhs.universalinstaller.util.MicroGCompat
import org.koin.androidx.compose.koinViewModel

/**
 * Pill showing the active install backend. Tapping it opens a picker so the user can switch
 * engine (PackageInstaller / Shizuku / Dhizuku / Root) right from the Install and Manage screens —
 * the switch reuses [SettingViewModel.setInstallMode], which runs the same Shizuku-permission
 * / root-request ladder as the Settings screen.
 */
@Composable
fun InstallerModeBadge(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val settingViewModel: SettingViewModel = koinViewModel()
    val settingState by settingViewModel.uiState.collectAsState()
    val useDhizuku by settingViewModel.useDhizuku.collectAsState()
    val dhizukuState by settingViewModel.dhizukuState.collectAsState()

    var isSystemInstallerFrozen by remember {
        mutableStateOf(SystemInstallerManager.isSystemPackageInstallerDisabled(context))
    }
    var canInstallPackages by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else true
        )
    }

    LifecycleResumeEffect(Unit) {
        isSystemInstallerFrozen = SystemInstallerManager.isSystemPackageInstallerDisabled(context)
        canInstallPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
        onPauseOrDispose {}
    }

    val microGAvailable = remember(context) { MicroGCompat.isAvailable(context) }

    val configuredMode = remember(
        settingState.useShizuku,
        settingState.useRoot,
        useDhizuku,
        settingState.useCustomAuthorizer,
        settingState.useMicroG,
    ) {
        InstallMode.from(
            useShizuku = settingState.useShizuku,
            useRoot = settingState.useRoot,
            useDhizuku = useDhizuku,
            useCustomAuthorizer = settingState.useCustomAuthorizer,
            useMicroG = settingState.useMicroG,
        )
    }

    val effectiveMode = remember(
        configuredMode,
        settingState.shizukuState,
        settingState.rootState,
        dhizukuState,
        microGAvailable,
        isSystemInstallerFrozen,
    ) {
        InstallMode.resolveEffective(
            configuredMode = configuredMode,
            shizukuState = settingState.shizukuState,
            rootState = settingState.rootState,
            dhizukuState = dhizukuState,
            isMicroGAvailable = microGAvailable,
            isSystemInstallerFrozen = isSystemInstallerFrozen,
        )
    }

    var showPicker by remember { mutableStateOf(false) }

    // Surface the hint events the switcher emits (e.g. "install Shizuku", "permission denied")
    // so the user isn't left wondering why the engine didn't change.
    LaunchedEffect(Unit) {
        settingViewModel.events.collect { stringRes ->
            Toast.makeText(context, context.getString(stringRes), Toast.LENGTH_LONG).show()
        }
    }

    val label = when (effectiveMode) {
        InstallMode.MICROG -> stringResource(R.string.installer_mode_microg)
        InstallMode.CUSTOM -> stringResource(R.string.installer_mode_custom)
        InstallMode.ROOT -> stringResource(R.string.installer_mode_root)
        InstallMode.SHIZUKU -> stringResource(R.string.installer_mode_shizuku)
        InstallMode.DHIZUKU -> stringResource(R.string.installer_mode_dhizuku)
        InstallMode.DEFAULT -> stringResource(R.string.installer_mode_package_installer)
    }
    val icon = when (effectiveMode) {
        InstallMode.MICROG -> Icons.Rounded.CloudDownload
        InstallMode.CUSTOM -> Icons.Rounded.Terminal
        InstallMode.ROOT -> Icons.Rounded.Key
        InstallMode.SHIZUKU -> Icons.Rounded.AdminPanelSettings
        InstallMode.DHIZUKU -> Icons.Rounded.Shield
        InstallMode.DEFAULT -> Icons.Rounded.Android
    }
    val privileged = effectiveMode != InstallMode.DEFAULT
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
        val rootReady = settingState.rootState == RootState.READY || effectiveMode == InstallMode.ROOT
        val rootDimmed = effectiveMode != InstallMode.ROOT && (
            settingState.rootState == RootState.NOT_ROOTED ||
            settingState.rootState == RootState.UNAVAILABLE
        )
        val shizukuSelectable = settingState.shizukuState != ShizukuState.UNSUPPORTED &&
            settingState.shizukuState != ShizukuState.NOT_INSTALLED

        val dhizukuSupported = DhizukuCompat.isSupported
        val dhizukuReady = dhizukuState == DhizukuState.READY || effectiveMode == InstallMode.DHIZUKU
        val dhizukuSelectable = dhizukuSupported &&
            dhizukuState != DhizukuState.UNSUPPORTED &&
            dhizukuState != DhizukuState.NOT_INSTALLED &&
            dhizukuState != DhizukuState.PROFILE_OWNER_UNSUPPORTED
        val dhizukuDimmed = effectiveMode != InstallMode.DHIZUKU && !dhizukuReady

        AlertDialog(
            onDismissRequest = { showPicker = false },
            title = { Text(stringResource(R.string.installer_engine_dialog_title)) },
            text = {
                Column {
                    EngineOption(
                        title = stringResource(R.string.installer_mode_package_installer),
                        subtitle = if (isSystemInstallerFrozen) {
                            stringResource(R.string.setting_system_installer_frozen_badge_desc)
                        } else if (!canInstallPackages) {
                            stringResource(R.string.permission_install_prompt_required)
                        } else {
                            stringResource(R.string.installer_engine_default_desc)
                        },
                        selected = configuredMode == InstallMode.DEFAULT,
                        enabled = !isSystemInstallerFrozen,
                        dimmed = isSystemInstallerFrozen,
                        onClick = {
                            if (isSystemInstallerFrozen) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.setting_system_installer_frozen_cannot_select),
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else if (!canInstallPackages) {
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.permission_install_prompt_required),
                                    Toast.LENGTH_LONG
                                ).show()
                                runCatching {
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                        val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                            data = Uri.parse("package:${context.packageName}")
                                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                        }
                                        context.startActivity(intent)
                                    }
                                }
                                settingViewModel.setInstallMode(InstallMode.DEFAULT)
                                showPicker = false
                            } else {
                                settingViewModel.setInstallMode(InstallMode.DEFAULT)
                                showPicker = false
                            }
                        },
                    )
                    EngineOption(
                        title = stringResource(R.string.installer_mode_shizuku),
                        subtitle = when (settingState.shizukuState) {
                            ShizukuState.NOT_INSTALLED -> stringResource(R.string.setting_shizuku_not_installed)
                            ShizukuState.UNSUPPORTED -> stringResource(R.string.setting_shizuku_unsupported)
                            else -> stringResource(R.string.installer_engine_shizuku_desc)
                        },
                        selected = configuredMode == InstallMode.SHIZUKU,
                        enabled = shizukuSelectable,
                        onClick = {
                            settingViewModel.setInstallMode(InstallMode.SHIZUKU)
                            showPicker = false
                        },
                    )
                    if (dhizukuSupported) {
                        EngineOption(
                            title = stringResource(R.string.installer_mode_dhizuku),
                            subtitle = when (dhizukuState) {
                                DhizukuState.UNSUPPORTED -> stringResource(R.string.setting_dhizuku_unsupported)
                                DhizukuState.NOT_INSTALLED -> stringResource(R.string.setting_dhizuku_not_installed)
                                DhizukuState.NOT_RUNNING -> stringResource(R.string.setting_dhizuku_not_running)
                                DhizukuState.PROFILE_OWNER_UNSUPPORTED -> stringResource(R.string.setting_dhizuku_profile_owner_unsupported)
                                DhizukuState.NOT_AUTHORIZED -> stringResource(R.string.setting_dhizuku_no_permission)
                                else -> stringResource(R.string.installer_engine_dhizuku_desc)
                            },
                            selected = configuredMode == InstallMode.DHIZUKU,
                            enabled = dhizukuSelectable,
                            dimmed = dhizukuDimmed,
                            onClick = {
                                settingViewModel.setInstallMode(InstallMode.DHIZUKU)
                                showPicker = false
                            },
                        )
                    }
                    EngineOption(
                        title = stringResource(R.string.installer_mode_root),
                        subtitle = when {
                            !settingState.rootSupported ->
                                stringResource(R.string.installer_engine_root_unsupported)
                            rootReady -> stringResource(R.string.installer_engine_root_desc)
                            else -> stringResource(R.string.installer_engine_root_request)
                        },
                        selected = configuredMode == InstallMode.ROOT,
                        enabled = settingState.rootSupported,
                        dimmed = rootDimmed,
                        onClick = {
                            settingViewModel.setInstallMode(InstallMode.ROOT)
                            showPicker = false
                        },
                    )
                    EngineOption(
                        title = stringResource(R.string.installer_mode_custom),
                        subtitle = stringResource(R.string.installer_engine_custom_desc),
                        selected = configuredMode == InstallMode.CUSTOM,
                        enabled = true,
                        onClick = {
                            settingViewModel.setInstallMode(InstallMode.CUSTOM)
                            showPicker = false
                        },
                    )
                    EngineOption(
                        title = stringResource(R.string.installer_mode_microg),
                        subtitle = if (microGAvailable) stringResource(R.string.installer_mode_microg_desc)
                            else stringResource(R.string.microg_not_installed),
                        selected = configuredMode == InstallMode.MICROG,
                        enabled = microGAvailable,
                        onClick = {
                            settingViewModel.setInstallMode(InstallMode.MICROG)
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
    // Greyed-out look without blocking the click — used for engines that aren't ready yet
    // but can still be tapped to start their permission/request flow (e.g. Root).
    dimmed: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, enabled = enabled, onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick, enabled = enabled)
        Column(modifier = Modifier.padding(start = 8.dp)) {
            val titleColor = if (enabled && !dimmed) MaterialTheme.colorScheme.onSurface
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
