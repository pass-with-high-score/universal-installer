package app.pwhs.universalinstaller.presentation.install.dialog

import android.text.format.Formatter
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import app.pwhs.universalinstaller.presentation.install.dialog.components.DialogPill
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.presentation.composable.InstallerModeBadge
import app.pwhs.universalinstaller.presentation.install.components.AbiIncompatibleBanner
import app.pwhs.universalinstaller.presentation.install.dialog.AbiIncompatibleDialog

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DialogPrepareContent(
    apkInfo: ApkInfo,
    installedVersionName: String? = null,
    installedVersionCode: Long? = null,
    onInstall: () -> Unit,
    onMenu: () -> Unit,
    onCancel: () -> Unit,
    onUnblock: (String) -> Unit = {},
    onCheckVirusTotal: () -> Unit = {},
    showKeepApkOption: Boolean = false,
    keepApk: Boolean = false,
    onKeepApkChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    var showTrackersDialog by remember { mutableStateOf(false) }
    var showAbiWarningDialog by remember { mutableStateOf(false) }
    var showVtSetupDialog by remember { mutableStateOf(false) }
    var pendingInstallAfterVt by remember { mutableStateOf(false) }
    val storedPrefs by context.dataStore.data.collectAsState(initial = null)
    val hasVtKey = !storedPrefs?.get(SharedPrefsKeys.VIRUSTOTAL_API_KEY).isNullOrBlank()
    val dontAskAgain = storedPrefs?.get(SharedPrefsKeys.VIRUSTOTAL_DONT_ASK_AGAIN) ?: false
    val isUpdate = installedVersionCode != null && installedVersionCode > 0
    val isDowngrade = isUpdate && apkInfo.versionCode < installedVersionCode

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Version Info ──
        AnimatedContent(
            targetState = Triple(isUpdate, isDowngrade, apkInfo.versionName),
            transitionSpec = {
                fadeIn(tween(200)) togetherWith fadeOut(tween(200))
            },
            label = "VersionAnimation",
        ) { (update, downgrade, newVersion) ->
            when {
                downgrade -> {
                    Text(
                        text = stringResource(
                            R.string.dialog_version_downgrade,
                            installedVersionName ?: "?",
                            newVersion,
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                    )
                }
                update -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center,
                    ) {
                        Text(
                            text = installedVersionName ?: "?",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(horizontal = 4.dp)
                                .size(16.dp),
                        )
                        Text(
                            text = newVersion,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                else -> {
                    Text(
                        text = "${newVersion} (${apkInfo.versionCode})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }

        // ── Size ──
        val sizeText = Formatter.formatFileSize(context, apkInfo.fileSizeBytes)
        if (apkInfo.fileSizeBytes > 0) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = sizeText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // ── Warning Chips ──
        val hasChips = isDowngrade || apkInfo.splitCount > 1 || apkInfo.obbFileNames.isNotEmpty() || apkInfo.isAndroidAutoSupported || apkInfo.isRootRequested || apkInfo.isShizukuRequested
        AnimatedVisibility(visible = hasChips) {
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .animateContentSize(animationSpec = DialogMotion.ContentSpring),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (apkInfo.isAndroidAutoSupported) {
                    DialogPill(
                        label = stringResource(R.string.aa_compatibility_ok),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.DirectionsCar,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        onClick = onMenu,
                    )
                }
                if (apkInfo.isRootRequested) {
                    DialogPill(
                        label = stringResource(R.string.apk_info_root_requested),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        onClick = onMenu,
                    )
                }
                if (apkInfo.isShizukuRequested) {
                    DialogPill(
                        label = stringResource(R.string.apk_info_shizuku_requested),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Terminal,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.secondary,
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        onClick = onMenu,
                    )
                }
                if (isDowngrade) {
                    DialogPill(
                        label = stringResource(R.string.dialog_chip_downgrade),
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Warning,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        },
                        containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
                if (apkInfo.splitCount > 1) {
                    DialogPill(
                        label = stringResource(R.string.dialog_chip_split_apk),
                    )
                }
                if (apkInfo.obbFileNames.isNotEmpty()) {
                    DialogPill(
                        label = stringResource(R.string.dialog_chip_has_obb),
                    )
                }
            }
        }

        // ── Install engine (tap to switch) & VirusTotal status / scan prompt & Trackers ──
        Spacer(modifier = Modifier.height(12.dp))
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            InstallerModeBadge()
            DialogPill(
                label = stringResource(R.string.dialog_menu_details),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                },
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                onClick = onMenu,
            )
            if (apkInfo.isAbiIncompatible) {
                DialogPill(
                    label = stringResource(R.string.dialog_chip_abi_incompatible),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onClick = { showAbiWarningDialog = true },
                )
            }
            VirusTotalInstallPrompt(
                apkInfo = apkInfo,
                onScan = {
                    if (!hasVtKey) {
                        showVtSetupDialog = true
                    } else {
                        onCheckVirusTotal()
                    }
                },
            )
            if (apkInfo.isScanningTrackers) {
                DialogPill(
                    label = stringResource(R.string.dialog_chip_trackers_scanning),
                    leadingIcon = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(12.dp),
                            strokeWidth = 1.5.dp,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            } else if (apkInfo.trackers.isNotEmpty()) {
                DialogPill(
                    label = stringResource(R.string.dialog_chip_trackers, apkInfo.trackers.size),
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                    onClick = { showTrackersDialog = true },
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // ── Buttons: [Menu] [Install] ──
        if (apkInfo.isAbiIncompatible) {
            AbiIncompatibleBanner(
                apkAbis = apkInfo.supportedAbis,
                onClick = { showAbiWarningDialog = true },
                modifier = Modifier.padding(bottom = 12.dp),
            )
        }
        if (apkInfo.isBlocked) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Block,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.install_blocked_banner),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { onUnblock(apkInfo.packageName) }) {
                        Text(
                            text = stringResource(R.string.install_blocked_unblock),
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        }

        if (showKeepApkOption) {
            DialogKeepApkOption(
                checked = keepApk,
                onCheckedChange = onKeepApkChanged,
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            // Menu button
            OutlinedButton(
                onClick = onMenu,
                modifier = Modifier.weight(1f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Menu,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.dialog_menu_btn),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }

            // Install/Update/Downgrade button
            Button(
                onClick = {
                    if (apkInfo.isAbiIncompatible) {
                        showAbiWarningDialog = true
                    } else if (!hasVtKey && !dontAskAgain) {
                        pendingInstallAfterVt = true
                        showVtSetupDialog = true
                    } else {
                        onInstall()
                    }
                },
                modifier = Modifier.weight(1f),
                enabled = !apkInfo.isBlocked,
                colors = if (isDowngrade) {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                } else {
                    ButtonDefaults.buttonColors()
                },
            ) {
                Text(
                    text = when {
                        isDowngrade -> stringResource(R.string.dialog_downgrade_btn)
                        isUpdate -> stringResource(R.string.dialog_update_btn)
                        else -> stringResource(R.string.dialog_install_btn)
                    },
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
        }

        // Cancel
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = onCancel,
            modifier = Modifier.fillMaxWidth(),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
        ) {
            Text(stringResource(R.string.dialog_cancel_btn))
        }

        if (showTrackersDialog && apkInfo.trackers.isNotEmpty()) {
            TrackersDetailDialog(
                trackers = apkInfo.trackers,
                onDismiss = { showTrackersDialog = false },
            )
        }

        if (showAbiWarningDialog) {
            AbiIncompatibleDialog(
                apkAbis = apkInfo.supportedAbis,
                onDismiss = { showAbiWarningDialog = false },
                onInstallAnyway = {
                    showAbiWarningDialog = false
                    if (!hasVtKey && !dontAskAgain) {
                        pendingInstallAfterVt = true
                        showVtSetupDialog = true
                    } else {
                        onInstall()
                    }
                },
            )
        }

        if (showVtSetupDialog) {
            VirusTotalSetupDialog(
                onDismiss = {
                    showVtSetupDialog = false
                    pendingInstallAfterVt = false
                },
                onSaveAndScan = {
                    onCheckVirusTotal()
                    pendingInstallAfterVt = false
                },
                onProceedInstall = if (pendingInstallAfterVt) onInstall else null,
            )
        }
    }
}
