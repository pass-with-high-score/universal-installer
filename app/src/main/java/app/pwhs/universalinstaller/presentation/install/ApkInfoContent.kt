package app.pwhs.universalinstaller.presentation.install

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.InstallerProfile
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.presentation.composable.InstallerModeBadge
import app.pwhs.universalinstaller.presentation.install.components.AbisCard
import app.pwhs.universalinstaller.presentation.install.components.ApkInfoFooter
import app.pwhs.universalinstaller.presentation.install.components.DetailsCard
import app.pwhs.universalinstaller.presentation.install.components.InfoChip
import app.pwhs.universalinstaller.presentation.install.components.InstallBlockedBanner
import app.pwhs.universalinstaller.presentation.install.components.InstallTargetCard
import app.pwhs.universalinstaller.presentation.install.dialog.TrackersDetailDialog
import app.pwhs.universalinstaller.presentation.install.components.ObbAttachCard
import app.pwhs.universalinstaller.presentation.install.components.PermissionsCard
import app.pwhs.universalinstaller.presentation.install.components.ProfilePickerCard
import app.pwhs.universalinstaller.presentation.install.components.SplitsCard
import app.pwhs.universalinstaller.presentation.install.components.VirusTotalCard
import app.pwhs.universalinstaller.presentation.install.components.VtStatusChip
import app.pwhs.universalinstaller.presentation.install.dialog.DialogKeepApkOption
import app.pwhs.universalinstaller.presentation.setting.SettingActivity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private const val APK_SHEET_SCROLL_FRACTION = 0.65f

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApkInfoContent(
    apkInfo: ApkInfo,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onCheckVirusTotal: () -> Unit = {},
    attachedObbFiles: List<AttachedObb> = emptyList(),
    onAttachObb: () -> Unit = {},
    onRemoveObb: (AttachedObb) -> Unit = {},
    onToggleSplit: (Int) -> Unit = {},
    confirmText: String? = null,
    cancelText: String? = null,
    profiles: List<InstallerProfile> = emptyList(),
    appProfileMapping: Map<String, String> = emptyMap(),
    allUsers: Boolean = false,
    selectedUserId: Int? = null,
    onProfileSelected: (InstallerProfile?) -> Unit = {},
    onMappingChanged: (String, String?) -> Unit = { _, _ -> },
    onToggleAllUsers: (Boolean) -> Unit = {},
    onSelectUserId: (Int?) -> Unit = {},
    startCompact: Boolean = true,
    onUnblock: (String) -> Unit = {},
    strictSecurity: Boolean = false,
    showKeepApkOption: Boolean = false,
    keepApk: Boolean = false,
    onKeepApkChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val currentMappingProfileId = appProfileMapping[apkInfo.packageName]
    var isExpanded by rememberSaveable { mutableStateOf(!startCompact) }
    var showTrackersDialog by rememberSaveable { mutableStateOf(false) }

    val iconBitmap by produceState<ImageBitmap?>(
        initialValue = null,
        key1 = apkInfo.icon,
    ) {
        value = withContext(Dispatchers.IO) {
            apkInfo.icon?.toBitmap(128, 128)?.asImageBitmap()
        }
    }

    val isDowngrade = apkInfo.installedVersionCode != null &&
        apkInfo.installedVersionCode > 0 &&
        apkInfo.versionCode < apkInfo.installedVersionCode

    val scrollCap = (LocalConfiguration.current.screenHeightDp * APK_SHEET_SCROLL_FRACTION).dp
    Column(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isExpanded) Modifier.heightIn(max = scrollCap) else Modifier)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val icon = iconBitmap
            if (isExpanded) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = apkInfo.appName,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(MaterialTheme.shapes.large),
                    )
                    Spacer(Modifier.height(12.dp))
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    )
                    Spacer(Modifier.height(12.dp))
                }
                Text(
                    text = apkInfo.appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = apkInfo.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Image(
                            bitmap = icon,
                            contentDescription = apkInfo.appName,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(MaterialTheme.shapes.medium),
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Android,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = apkInfo.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "v${apkInfo.versionName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            InstallerModeBadge()
            Spacer(Modifier.height(16.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
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
                        onOpenWeb = {
                            if (apkInfo.sha256.isNotBlank()) {
                                uriHandler.openUri("https://www.virustotal.com/gui/file/${apkInfo.sha256}/detection")
                            }
                        },
                        onOpenSettings = {
                            context.startActivity(
                                Intent(context, SettingActivity::class.java),
                            )
                        },
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
                        onClick = { showTrackersDialog = true },
                    )
                }
                if (isExpanded) {
                    if (apkInfo.versionName.isNotBlank()) {
                        InfoChip(
                            label = stringResource(R.string.apk_info_version_chip, apkInfo.versionName),
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
                                )
                            },
                        )
                    }
                } else {
                    if (apkInfo.fileSizeBytes > 0) {
                        InfoChip(
                            label = Formatter.formatShortFileSize(context, apkInfo.fileSizeBytes),
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Storage,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp),
                                )
                            },
                        )
                    }
                }
                if (apkInfo.splitCount > 1) {
                    InfoChip(
                        label = stringResource(R.string.apk_info_splits_count, apkInfo.splitCount),
                    )
                }
                if (apkInfo.obbFileNames.isNotEmpty()) {
                    InfoChip(label = "OBB: ${apkInfo.obbFileNames.size}")
                }
            }

            if (isExpanded) {
                Spacer(Modifier.height(16.dp))

                InstallTargetCard(
                    allUsers = allUsers,
                    selectedUserId = selectedUserId,
                    onToggleAllUsers = onToggleAllUsers,
                    onSelectUserId = onSelectUserId,
                )

                Spacer(Modifier.height(16.dp))

                ProfilePickerCard(
                    profiles = profiles,
                    currentMappingProfileId = currentMappingProfileId,
                    onProfileSelected = onProfileSelected,
                    onMappingToggle = { profileId ->
                        onMappingChanged(apkInfo.packageName, profileId)
                    },
                )

                Spacer(Modifier.height(16.dp))
                ObbAttachCard(
                    attached = attachedObbFiles,
                    onAttach = onAttachObb,
                    onRemove = onRemoveObb,
                )
                Spacer(Modifier.height(16.dp))
                DetailsCard(apkInfo = apkInfo)
                if (apkInfo.splitEntries.size > 1) {
                    Spacer(Modifier.height(16.dp))
                    SplitsCard(splits = apkInfo.splitEntries, onToggle = onToggleSplit)
                }
                if (apkInfo.supportedAbis.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    AbisCard(abis = apkInfo.supportedAbis)
                }
                Spacer(Modifier.height(16.dp))
                VirusTotalCard(
                    vt = apkInfo.vtResult,
                    fileSizeBytes = apkInfo.fileSizeBytes,
                    sha256 = apkInfo.sha256,
                    onCheck = onCheckVirusTotal,
                    onOpenSettings = {
                        context.startActivity(
                            Intent(context, SettingActivity::class.java),
                        )
                    },
                    onGetKey = {
                        uriHandler.openUri("https://www.virustotal.com/gui/my-apikey")
                    },
                    onOpenLink = {
                        if (apkInfo.vtResult?.status in setOf(VtStatus.CLEAN, VtStatus.MALICIOUS, VtStatus.SUSPICIOUS) &&
                            apkInfo.sha256.isNotBlank()
                        ) {
                            uriHandler.openUri("https://www.virustotal.com/gui/file/${apkInfo.sha256}/detection")
                        }
                    },
                )
                if (apkInfo.permissions.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    PermissionsCard(permissions = apkInfo.permissions)
                }
            }

            Spacer(Modifier.height(16.dp))
        }

        if (apkInfo.isBlocked) {
            InstallBlockedBanner(
                packageName = apkInfo.packageName,
                onUnblock = onUnblock,
            )
        }

        if (showKeepApkOption) {
            DialogKeepApkOption(
                checked = keepApk,
                onCheckedChange = onKeepApkChanged,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
        }

        ApkInfoFooter(
            apkInfo = apkInfo,
            isExpanded = isExpanded,
            startCompact = startCompact,
            strictSecurity = strictSecurity,
            confirmText = confirmText,
            cancelText = cancelText,
            onExpand = { isExpanded = true },
            onCollapse = { isExpanded = false },
            onInstall = onInstall,
            onCancel = onCancel,
            onCheckVirusTotal = {
                if (!isExpanded) isExpanded = true
                onCheckVirusTotal()
            },
        )

        if (showTrackersDialog && apkInfo.trackers.isNotEmpty()) {
            TrackersDetailDialog(
                trackers = apkInfo.trackers,
                onDismiss = { showTrackersDialog = false },
            )
        }
    }
}
