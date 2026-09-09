package app.pwhs.universalinstaller.presentation.setting.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.InstallMode
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.presentation.setting.security.util.SystemInstallerManager
import app.pwhs.universalinstaller.util.DhizukuState

/**
 * Picker for the global install backend.
 *
 * Root option disappears when the build has no libsu (store flavor).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun InstallModeSelector(
    currentMode: InstallMode,
    shizukuState: ShizukuState,
    rootSupported: Boolean,
    rootState: RootState,
    dhizukuSupported: Boolean = true,
    dhizukuState: DhizukuState = DhizukuState.NOT_INSTALLED,
    microGSupported: Boolean = true,
    onModeChange: (InstallMode) -> Unit,
) {
    val context = LocalContext.current
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

    val effectiveMode = remember(currentMode, shizukuState, rootState, dhizukuState, microGSupported, isSystemInstallerFrozen) {
        InstallMode.resolveEffective(
            configuredMode = currentMode,
            shizukuState = shizukuState,
            rootState = rootState,
            dhizukuState = dhizukuState,
            isMicroGAvailable = microGSupported,
            isSystemInstallerFrozen = isSystemInstallerFrozen,
        )
    }

    val options: List<InstallMode> = remember(rootSupported, dhizukuSupported) {
        buildList {
            add(InstallMode.DEFAULT)
            add(InstallMode.SHIZUKU)
            if (dhizukuSupported) add(InstallMode.DHIZUKU)
            if (rootSupported) add(InstallMode.ROOT)
            add(InstallMode.CUSTOM)
            add(InstallMode.MICROG)
        }
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.setting_install_mode_title),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.padding(bottom = 8.dp),
        )

        val rootDimmed = currentMode != InstallMode.ROOT &&
            (rootState == RootState.NOT_ROOTED || rootState == RootState.UNAVAILABLE)
        val dhizukuDimmed = currentMode != InstallMode.DHIZUKU &&
            (dhizukuState == DhizukuState.NOT_INSTALLED || dhizukuState == DhizukuState.UNSUPPORTED || dhizukuState == DhizukuState.PROFILE_OWNER_UNSUPPORTED)
        val microGDimmed = currentMode != InstallMode.MICROG && !microGSupported

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { mode ->
                val dim = (mode == InstallMode.ROOT && rootDimmed) ||
                    (mode == InstallMode.DHIZUKU && dhizukuDimmed) ||
                    (mode == InstallMode.MICROG && microGDimmed) ||
                    (mode == InstallMode.DEFAULT && isSystemInstallerFrozen)
                val selected = mode == currentMode
                FilterChip(
                    selected = selected,
                    onClick = {
                        if (mode == InstallMode.DEFAULT && isSystemInstallerFrozen) {
                            Toast.makeText(
                                context,
                                context.getString(R.string.setting_system_installer_frozen_cannot_select),
                                Toast.LENGTH_SHORT
                            ).show()
                        } else if (mode == InstallMode.DEFAULT && !canInstallPackages) {
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
                            onModeChange(InstallMode.DEFAULT)
                        } else if (mode != currentMode || (mode == InstallMode.DHIZUKU && dhizukuState == DhizukuState.NOT_AUTHORIZED)) {
                            onModeChange(mode)
                        }
                    },
                    label = {
                        Text(
                            text = when (mode) {
                                InstallMode.DEFAULT -> stringResource(R.string.setting_install_mode_default)
                                InstallMode.SHIZUKU -> stringResource(R.string.setting_install_mode_shizuku)
                                InstallMode.DHIZUKU -> stringResource(R.string.setting_install_mode_dhizuku)
                                InstallMode.ROOT -> stringResource(R.string.setting_install_mode_root)
                                InstallMode.CUSTOM -> stringResource(R.string.setting_install_mode_custom)
                                InstallMode.MICROG -> stringResource(R.string.installer_mode_microg)
                            },
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = when (mode) {
                                InstallMode.DEFAULT -> Icons.Rounded.Android
                                InstallMode.SHIZUKU -> Icons.Rounded.Key
                                InstallMode.DHIZUKU -> Icons.Rounded.AdminPanelSettings
                                InstallMode.ROOT -> Icons.Rounded.Shield
                                InstallMode.CUSTOM -> Icons.Rounded.Terminal
                                InstallMode.MICROG -> Icons.Rounded.CloudDownload
                            },
                            contentDescription = null,
                            modifier = Modifier.size(FilterChipDefaults.IconSize),
                        )
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                    modifier = if (dim) Modifier.alpha(0.38f) else Modifier,
                )
            }
        }
        val statusText = when (currentMode) {
            InstallMode.DEFAULT -> if (isSystemInstallerFrozen) {
                stringResource(R.string.system_installer_frozen_warning)
            } else if (!canInstallPackages) {
                stringResource(R.string.permission_install_prompt_required)
            } else {
                stringResource(R.string.setting_install_mode_default_sub)
            }
            InstallMode.SHIZUKU -> when (shizukuState) {
                ShizukuState.NOT_INSTALLED -> stringResource(R.string.setting_shizuku_not_installed)
                ShizukuState.NOT_RUNNING -> stringResource(R.string.setting_shizuku_not_running)
                ShizukuState.UNSUPPORTED -> stringResource(R.string.setting_shizuku_unsupported)
                ShizukuState.NO_PERMISSION -> stringResource(R.string.setting_shizuku_no_permission)
                ShizukuState.READY -> stringResource(R.string.setting_shizuku_ready)
            }
            InstallMode.DHIZUKU -> when (dhizukuState) {
                DhizukuState.UNSUPPORTED -> stringResource(R.string.setting_dhizuku_unsupported)
                DhizukuState.NOT_INSTALLED -> stringResource(R.string.setting_dhizuku_not_installed)
                DhizukuState.NOT_RUNNING -> stringResource(R.string.setting_dhizuku_not_running)
                DhizukuState.PROFILE_OWNER_UNSUPPORTED -> stringResource(R.string.setting_dhizuku_profile_owner_unsupported)
                DhizukuState.NOT_AUTHORIZED -> stringResource(R.string.setting_dhizuku_no_permission)
                DhizukuState.READY -> stringResource(R.string.setting_dhizuku_ready)
            }
            InstallMode.ROOT -> when (rootState) {
                RootState.UNAVAILABLE -> "Unavailable"
                RootState.UNKNOWN -> "Checking..."
                RootState.DENIED -> "Denied"
                RootState.READY -> "Ready"
                else -> "Not Rooted"
            }
            InstallMode.CUSTOM -> stringResource(R.string.setting_install_mode_custom_sub)
            InstallMode.MICROG -> if (microGSupported) {
                stringResource(R.string.installer_mode_microg_desc)
            } else {
                stringResource(R.string.microg_not_installed)
            }
        }
        val canRequestPermission = currentMode == InstallMode.DHIZUKU && dhizukuState == DhizukuState.NOT_AUTHORIZED
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = if (canRequestPermission || (currentMode == InstallMode.DEFAULT && !canInstallPackages)) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(top = 8.dp)
                .then(
                    if (canRequestPermission) Modifier.clickable { onModeChange(InstallMode.DHIZUKU) }
                    else if (effectiveMode == InstallMode.DEFAULT && !canInstallPackages) Modifier.clickable {
                        runCatching {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                                context.startActivity(intent)
                            }
                        }
                    }
                    else Modifier
                ),
        )
    }
}
