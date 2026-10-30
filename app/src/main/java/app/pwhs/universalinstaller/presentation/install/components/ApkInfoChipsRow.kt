package app.pwhs.universalinstaller.presentation.install.components

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.core.util.openUriSafely
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.presentation.setting.SettingActivity

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ApkInfoChipsRow(
    apkInfo: ApkInfo,
    isExpanded: Boolean,
    isDowngrade: Boolean,
    onCheckVirusTotal: () -> Unit,
    onShowTrackers: () -> Unit,
    onExpand: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onOpenDetail: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current

    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        if (isDowngrade) {
            InfoChip(
                label = stringResource(R.string.dialog_chip_downgrade),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (apkInfo.isAbiIncompatible) {
            InfoChip(
                label = stringResource(R.string.dialog_chip_abi_incompatible),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (apkInfo.isAndroidAutoSupported) {
            InfoChip(
                label = stringResource(R.string.aa_compatibility_ok),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (apkInfo.isRootRequested) {
            InfoChip(
                label = stringResource(R.string.apk_info_root_requested),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                },
                containerColor = MaterialTheme.colorScheme.errorContainer,
                contentColor = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
        if (apkInfo.isShizukuRequested) {
            InfoChip(
                label = stringResource(R.string.apk_info_shizuku_requested),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Terminal,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.secondary,
                    )
                },
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
        apkInfo.vtResult?.let { vt ->
            VtStatusChip(
                vt = vt,
                onClick = onCheckVirusTotal,
                onOpenWeb = onOpenDetail ?: {
                    if (apkInfo.sha256.isNotBlank()) {
                        uriHandler.openUriSafely(context, "https://www.virustotal.com/gui/file/${apkInfo.sha256}/detection")
                    }
                },
                onOpenSettings = onOpenSettings,
            )
        }
        if (apkInfo.isScanningTrackers) {
            InfoChip(
                label = stringResource(R.string.dialog_chip_trackers_scanning),
                leadingIcon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        } else if (apkInfo.trackers.isNotEmpty()) {
            InfoChip(
                label = stringResource(R.string.dialog_chip_trackers, apkInfo.trackers.size),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Shield,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                },
                containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                onClick = onShowTrackers,
            )
        }
        if (apkInfo.fileSizeBytes > 0) {
            InfoChip(
                label = Formatter.formatShortFileSize(context, apkInfo.fileSizeBytes),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Storage,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = onExpand,
            )
        }
        if (apkInfo.targetSdkVersion > 0) {
            InfoChip(
                label = "API ${apkInfo.targetSdkVersion}",
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = onExpand,
            )
        }
        if (!apkInfo.isAbiIncompatible) {
            val abiLabel = when {
                apkInfo.supportedAbis.isEmpty() -> "Universal"
                apkInfo.supportedAbis.size == 1 -> apkInfo.supportedAbis.first()
                else -> "${apkInfo.supportedAbis.first()} +${apkInfo.supportedAbis.size - 1}"
            }
            InfoChip(
                label = abiLabel,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = onExpand,
            )
        }
        if (apkInfo.permissions.isNotEmpty()) {
            InfoChip(
                label = stringResource(R.string.apk_info_section_permissions, apkInfo.permissions.size),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Key,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                onClick = onExpand,
            )
        }
        if (isExpanded && apkInfo.versionName.isNotBlank()) {
            InfoChip(
                label = stringResource(R.string.apk_info_version_chip, apkInfo.versionName),
            )
        }
        if (apkInfo.splitCount > 1) {
            InfoChip(
                label = stringResource(R.string.apk_info_splits_count, apkInfo.splitCount),
                onClick = onExpand,
            )
        }
        if (apkInfo.obbFileNames.isNotEmpty()) {
            InfoChip(
                label = "OBB: ${apkInfo.obbFileNames.size}",
                onClick = onExpand,
            )
        }
    }
}
