package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pwhs.core.billing.BillingManager
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.presentation.pro.UpgradeProBottomSheet
import org.koin.compose.koinInject

@Composable
fun DialogPrepareModals(
    apkInfo: ApkInfo,
    showTrackersDialog: Boolean,
    onDismissTrackersDialog: () -> Unit,
    showAbiWarningDialog: Boolean,
    onDismissAbiWarningDialog: () -> Unit,
    onInstallAnyway: () -> Unit,
    showVtSetupDialog: Boolean,
    onDismissVtSetupDialog: () -> Unit,
    onSaveAndScanVt: () -> Unit,
    onProceedInstallVt: (() -> Unit)?,
    billingManager: BillingManager = koinInject(),
) {
    var showUpgradeProSheet by remember { mutableStateOf(false) }

    if (showTrackersDialog && apkInfo.trackers.isNotEmpty()) {
        TrackersDetailDialog(
            trackers = apkInfo.trackers,
            onDismiss = onDismissTrackersDialog,
        )
    }

    if (showAbiWarningDialog) {
        AbiIncompatibleDialog(
            apkAbis = apkInfo.supportedAbis,
            onDismiss = onDismissAbiWarningDialog,
            onInstallAnyway = onInstallAnyway,
        )
    }

    if (showVtSetupDialog) {
        VirusTotalSetupDialog(
            onDismiss = onDismissVtSetupDialog,
            onSaveAndScan = { onSaveAndScanVt() },
            onProceedInstall = onProceedInstallVt,
            onUpgradeProClick = {
                showUpgradeProSheet = true
            },
        )
    }

    if (showUpgradeProSheet) {
        UpgradeProBottomSheet(
            billingManager = billingManager,
            onDismissRequest = { showUpgradeProSheet = false },
        )
    }
}
