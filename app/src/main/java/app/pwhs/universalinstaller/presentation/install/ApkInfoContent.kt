package app.pwhs.universalinstaller.presentation.install

import android.content.Intent
import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
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
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.openUriSafely
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
import app.pwhs.universalinstaller.presentation.install.components.AbiIncompatibleBanner
import app.pwhs.universalinstaller.presentation.install.components.AbisCard
import app.pwhs.universalinstaller.presentation.install.components.ApkInfoHeader
import app.pwhs.universalinstaller.presentation.install.components.ApkInfoChipsRow
import app.pwhs.universalinstaller.presentation.install.components.ApkInfoFooter
import app.pwhs.universalinstaller.presentation.install.components.DetailsCard
import app.pwhs.universalinstaller.presentation.install.components.InfoChip
import app.pwhs.universalinstaller.presentation.install.components.InstallBlockedBanner
import app.pwhs.universalinstaller.presentation.install.components.InstallTargetCard
import app.pwhs.universalinstaller.presentation.install.dialog.AbiIncompatibleDialog
import app.pwhs.universalinstaller.presentation.install.dialog.TrackersDetailDialog
import app.pwhs.universalinstaller.presentation.install.components.ObbAttachCard
import app.pwhs.universalinstaller.presentation.install.components.PermissionsCard
import app.pwhs.universalinstaller.presentation.install.components.ProfilePickerCard
import app.pwhs.universalinstaller.presentation.install.components.SplitsCard
import app.pwhs.universalinstaller.presentation.install.components.VirusTotalCard
import app.pwhs.universalinstaller.presentation.install.components.VtStatusChip
import app.pwhs.universalinstaller.presentation.install.dialog.DialogKeepApkOption
import app.pwhs.universalinstaller.presentation.install.dialog.InstallSupportBottomSheet
import app.pwhs.universalinstaller.presentation.install.dialog.VirusTotalDetailSheet
import app.pwhs.universalinstaller.presentation.install.dialog.VirusTotalSetupDialog
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
    onMergeSplits: (() -> Unit)? = null,
    installError: String? = null,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val currentMappingProfileId = appProfileMapping[apkInfo.packageName]
    var isExpanded by rememberSaveable { mutableStateOf(!startCompact) }
    var showTrackersDialog by rememberSaveable { mutableStateOf(false) }
    var showAbiWarningDialog by rememberSaveable { mutableStateOf(false) }
    var showSupportSheet by rememberSaveable { mutableStateOf(false) }
    var showVtSetupDialog by rememberSaveable { mutableStateOf(false) }
    var showVtDetailSheet by rememberSaveable { mutableStateOf(false) }
    var pendingInstallAfterVt by rememberSaveable { mutableStateOf(false) }

    val storedPrefs by context.dataStore.data.collectAsState(initial = null)
    val hasVtKey = !storedPrefs?.get(SharedPrefsKeys.VIRUSTOTAL_API_KEY).isNullOrBlank()
    val dontAskAgain = storedPrefs?.get(SharedPrefsKeys.VIRUSTOTAL_DONT_ASK_AGAIN) ?: false

    androidx.compose.runtime.LaunchedEffect(installError) {
        if (!installError.isNullOrBlank()) {
            isExpanded = true
        }
    }

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
            ApkInfoHeader(
                apkInfo = apkInfo,
                icon = iconBitmap,
                isExpanded = isExpanded,
            )

            Spacer(Modifier.height(16.dp))
            InstallerModeBadge()
            Spacer(Modifier.height(16.dp))

            ApkInfoChipsRow(
                apkInfo = apkInfo,
                isExpanded = isExpanded,
                isDowngrade = isDowngrade,
                onCheckVirusTotal = onCheckVirusTotal,
                onShowTrackers = { showTrackersDialog = true },
                onExpand = { isExpanded = true },
                onOpenSettings = { showVtSetupDialog = true },
                onOpenDetail = { showVtDetailSheet = true },
                modifier = Modifier.fillMaxWidth(),
            )

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
                    SplitsCard(
                        splits = apkInfo.splitEntries,
                        onToggle = onToggleSplit,
                        onMergeSplits = onMergeSplits,
                    )
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
                    onOpenSettings = { showVtSetupDialog = true },
                    onGetKey = {
                        uriHandler.openUriSafely(context, "https://www.virustotal.com/gui/my-apikey")
                    },
                    onOpenLink = {
                        if (apkInfo.vtResult?.status in setOf(VtStatus.CLEAN, VtStatus.MALICIOUS, VtStatus.SUSPICIOUS)) {
                            showVtDetailSheet = true
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

        if (apkInfo.isAbiIncompatible) {
            AbiIncompatibleBanner(
                apkAbis = apkInfo.supportedAbis,
                onClick = { showAbiWarningDialog = true },
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }

        if (apkInfo.isBlocked) {
            InstallBlockedBanner(
                packageName = apkInfo.packageName,
                onUnblock = onUnblock,
            )
        }

        if (!installError.isNullOrBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.errorContainer,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 6.dp),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = installError,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    TextButton(
                        onClick = { showSupportSheet = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SupportAgent,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.dialog_failed_get_support),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
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
            onInstall = {
                if (apkInfo.isAbiIncompatible) {
                    showAbiWarningDialog = true
                } else if (!hasVtKey && !dontAskAgain) {
                    pendingInstallAfterVt = true
                    showVtSetupDialog = true
                } else {
                    onInstall()
                }
            },
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

        if (showSupportSheet && !installError.isNullOrBlank()) {
            val target = remember(apkInfo) {
                DialogTarget(
                    sessionId = java.util.UUID.randomUUID(),
                    packageName = apkInfo.packageName,
                    appName = apkInfo.appName,
                    iconPath = null,
                    apkUri = null,
                )
            }
            InstallSupportBottomSheet(
                target = target,
                errorMessage = installError,
                onDismiss = { showSupportSheet = false },
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

        if (showVtDetailSheet && apkInfo.vtResult != null) {
            VirusTotalDetailSheet(
                vt = apkInfo.vtResult,
                apkInfo = apkInfo,
                onDismiss = { showVtDetailSheet = false },
            )
        }
    }
}
