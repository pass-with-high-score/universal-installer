package app.pwhs.universalinstaller.presentation.setting.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.KeyboardArrowUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.BackendIcon
import app.pwhs.universalinstaller.domain.model.InstallBackend
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.SettingUiState
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.presentation.setting.security.util.SystemInstallerManager
import app.pwhs.universalinstaller.util.DhizukuCompat
import app.pwhs.universalinstaller.util.DhizukuState
import app.pwhs.universalinstaller.util.MicroGCompat

@Composable
fun InstallPriorityList(
    uiState: SettingUiState,
    dhizukuState: DhizukuState,
    useDhizuku: Boolean,
    onMovePriority: (fromIndex: Int, toIndex: Int) -> Unit,
    onResetPriority: () -> Unit,
    onToggleBackend: (InstallBackend, Boolean) -> Unit,
    onCustomAuthorizerCommandChange: (String) -> Unit,
    onTestCustomAuthorizerCommand: suspend (String) -> Result<String>,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val priorityList = uiState.backendPriority
    val isSystemFrozen = SystemInstallerManager.isSystemPackageInstallerDisabled(context)
    val microGAvailable = MicroGCompat.isAvailable(context)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.setting_install_priority_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.setting_install_priority_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(
                    onClick = onResetPriority,
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.RotateLeft,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.setting_install_priority_reset),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Priority items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                priorityList.forEachIndexed { index, backend ->
                    val isEnabled = when (backend) {
                        InstallBackend.SHIZUKU -> uiState.useShizuku
                        InstallBackend.DHIZUKU -> useDhizuku
                        InstallBackend.ROOT -> uiState.useRoot
                        InstallBackend.CUSTOM -> uiState.useCustomAuthorizer
                        InstallBackend.MICROG -> uiState.useMicroG
                        InstallBackend.DEFAULT -> !isSystemFrozen
                    }

                    val statusText = resolveStatusText(
                        backend = backend,
                        isEnabled = isEnabled,
                        uiState = uiState,
                        dhizukuState = dhizukuState,
                        microGAvailable = microGAvailable,
                        isSystemFrozen = isSystemFrozen,
                    )

                    PriorityBackendRow(
                        rank = index + 1,
                        backend = backend,
                        isEnabled = isEnabled,
                        statusText = statusText,
                        canMoveUp = index > 0,
                        canMoveDown = index < priorityList.size - 1,
                        onMoveUp = { onMovePriority(index, index - 1) },
                        onMoveDown = { onMovePriority(index, index + 1) },
                        onToggle = { checked -> onToggleBackend(backend, checked) },
                        isDefaultFallback = backend == InstallBackend.DEFAULT,
                    )
                }
            }

            // Custom Authorizer inline configuration if custom is enabled
            AnimatedVisibility(visible = uiState.useCustomAuthorizer) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    CustomAuthorizerCard(
                        command = uiState.customAuthorizerCommand,
                        onCommandChange = onCustomAuthorizerCommandChange,
                        onTestCommand = onTestCustomAuthorizerCommand,
                        modifier = Modifier.padding(0.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PriorityBackendRow(
    rank: Int,
    backend: InstallBackend,
    isEnabled: Boolean,
    statusText: String,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onToggle: (Boolean) -> Unit,
    isDefaultFallback: Boolean,
    modifier: Modifier = Modifier,
) {
    val alpha by animateFloatAsState(
        targetValue = if (isEnabled) 1f else 0.5f,
        label = "PriorityRowAlpha",
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .alpha(alpha),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEnabled) {
                MaterialTheme.colorScheme.surfaceContainerHigh
            } else {
                MaterialTheme.colorScheme.surfaceContainerLowest
            },
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Rank badge
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        if (isEnabled) MaterialTheme.colorScheme.primaryContainer
                        else MaterialTheme.colorScheme.surfaceContainerHighest
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "#$rank",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isEnabled) MaterialTheme.colorScheme.onPrimaryContainer
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Backend icon
            BackendIcon(
                backend = backend,
                modifier = Modifier
                    .size(24.dp)
                    .alpha(if (isEnabled) 1f else 0.38f),
                tint = if (isEnabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(modifier = Modifier.width(10.dp))

            // Title & Status
            Column(
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(backend.titleRes),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            // Up / Down Reorder buttons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                IconButton(
                    onClick = onMoveUp,
                    enabled = canMoveUp,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowUp,
                        contentDescription = stringResource(R.string.setting_install_priority_up),
                        tint = if (canMoveUp) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onMoveDown,
                    enabled = canMoveDown,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.setting_install_priority_down),
                        tint = if (canMoveDown) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f),
                        modifier = Modifier.size(20.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Enable switch
            Switch(
                checked = isEnabled,
                onCheckedChange = if (isDefaultFallback) null else onToggle,
                enabled = !isDefaultFallback,
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun resolveStatusText(
    backend: InstallBackend,
    isEnabled: Boolean,
    uiState: SettingUiState,
    dhizukuState: DhizukuState,
    microGAvailable: Boolean,
    isSystemFrozen: Boolean,
): String {
    if (!isEnabled) {
        return stringResource(R.string.setting_install_priority_unavailable)
    }

    return when (backend) {
        InstallBackend.SHIZUKU -> when (uiState.shizukuState) {
            ShizukuState.READY -> stringResource(R.string.setting_install_priority_ready)
            ShizukuState.NO_PERMISSION -> stringResource(R.string.setting_shizuku_no_permission)
            ShizukuState.NOT_RUNNING -> stringResource(R.string.setting_shizuku_not_running)
            ShizukuState.NOT_INSTALLED -> stringResource(R.string.setting_shizuku_not_installed)
            ShizukuState.UNSUPPORTED -> stringResource(R.string.setting_shizuku_unsupported)
        }
        InstallBackend.DHIZUKU -> when (dhizukuState) {
            DhizukuState.READY -> stringResource(R.string.setting_install_priority_ready)
            DhizukuState.NOT_AUTHORIZED -> stringResource(R.string.setting_dhizuku_no_permission)
            DhizukuState.NOT_RUNNING -> stringResource(R.string.setting_dhizuku_not_running)
            DhizukuState.PROFILE_OWNER_UNSUPPORTED -> stringResource(R.string.setting_dhizuku_profile_owner_unsupported)
            DhizukuState.NOT_INSTALLED -> stringResource(R.string.setting_dhizuku_not_installed)
            DhizukuState.UNSUPPORTED -> stringResource(R.string.setting_dhizuku_unsupported)
        }
        InstallBackend.ROOT -> when {
            !uiState.rootSupported -> stringResource(R.string.installer_engine_root_unsupported)
            uiState.rootState == RootState.READY -> stringResource(R.string.setting_install_priority_ready)
            uiState.rootState == RootState.DENIED -> stringResource(R.string.installer_engine_root_request)
            else -> stringResource(R.string.installer_engine_root_desc)
        }
        InstallBackend.CUSTOM -> {
            if (uiState.customAuthorizerCommand.isNotBlank()) {
                uiState.customAuthorizerCommand
            } else {
                stringResource(R.string.setting_install_mode_custom_sub)
            }
        }
        InstallBackend.MICROG -> {
            if (microGAvailable) stringResource(R.string.setting_install_priority_ready)
            else stringResource(R.string.microg_not_installed)
        }
        InstallBackend.DEFAULT -> {
            if (isSystemFrozen) stringResource(R.string.setting_system_installer_frozen_badge_desc)
            else stringResource(R.string.setting_install_priority_fallback)
        }
    }
}
