package app.pwhs.universalinstaller.presentation.setting.components

import androidx.compose.runtime.Composable
import androidx.datastore.preferences.core.Preferences
import app.pwhs.core.billing.BillingManager
import app.pwhs.universalinstaller.presentation.composable.InstallPriorityBottomSheet
import app.pwhs.universalinstaller.presentation.pro.UpgradeProBottomSheet
import app.pwhs.universalinstaller.presentation.setting.SettingUiState
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.setting.backup.BackupSheetsHost
import app.pwhs.universalinstaller.presentation.setting.backup.BackupViewModel

@Composable
fun SettingSheetsHost(
    backupViewModel: BackupViewModel,
    onPickerReady: (() -> Unit) -> Unit,
    showInstallOptionsSheet: Boolean,
    onDismissInstallOptionsSheet: () -> Unit,
    showInstallPrioritySheet: Boolean,
    onDismissInstallPrioritySheet: () -> Unit,
    showSyncOptionsSheet: Boolean,
    onDismissSyncOptionsSheet: () -> Unit,
    showUpgradeProSheet: Boolean,
    onDismissUpgradeProSheet: () -> Unit,
    billingManager: BillingManager,
    uiState: SettingUiState,
    useDhizuku: Boolean,
    onPrivilegedOptionChanged: (SettingViewModel.PrivilegedOption, Boolean) -> Unit,
    onInstallerPackageChanged: (String) -> Unit,
    onShizukuOptionChanged: (Preferences.Key<Boolean>, Boolean) -> Unit,
    onSyncServerPortChanged: (String) -> Unit,
    onSyncRequirePinChanged: (Boolean) -> Unit,
    onSyncPinCodeChanged: (String) -> Unit,
) {
    BackupSheetsHost(
        viewModel = backupViewModel,
        onPickerReady = onPickerReady,
    )

    if (showInstallOptionsSheet) {
        InstallOptionsBottomSheet(
            uiState = uiState,
            useDhizuku = useDhizuku,
            onDismiss = onDismissInstallOptionsSheet,
            onPrivilegedOptionChanged = onPrivilegedOptionChanged,
            onInstallerPackageChanged = onInstallerPackageChanged,
            onShizukuOptionChanged = onShizukuOptionChanged,
        )
    }

    if (showInstallPrioritySheet) {
        InstallPriorityBottomSheet(
            onDismissRequest = onDismissInstallPrioritySheet,
        )
    }

    if (showSyncOptionsSheet) {
        SyncOptionsBottomSheet(
            syncOptions = uiState.syncOptions,
            onDismiss = onDismissSyncOptionsSheet,
            onSyncServerPortChanged = onSyncServerPortChanged,
            onSyncRequirePinChanged = onSyncRequirePinChanged,
            onSyncPinCodeChanged = onSyncPinCodeChanged,
        )
    }

    if (showUpgradeProSheet) {
        UpgradeProBottomSheet(
            billingManager = billingManager,
            onDismissRequest = onDismissUpgradeProSheet,
            source = app.pwhs.core.telemetry.TelemetryEvents.SOURCE_SETTINGS_BANNER,
        )
    }
}
