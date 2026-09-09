package app.pwhs.universalinstaller.presentation.setting.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.Preferences
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.SettingUiState
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.setting.asCommon

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallOptionsBottomSheet(
    uiState: SettingUiState,
    useDhizuku: Boolean,
    onDismiss: () -> Unit,
    onPrivilegedOptionChanged: (SettingViewModel.PrivilegedOption, Boolean) -> Unit,
    onInstallerPackageChanged: (String) -> Unit,
    onShizukuOptionChanged: (Preferences.Key<Boolean>, Boolean) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val privileged = uiState.useShizuku ||
        (uiState.rootSupported && uiState.useRoot) ||
        useDhizuku

    val opts = if (uiState.useRoot && uiState.rootSupported) {
        uiState.rootOptions.asCommon()
    } else {
        uiState.shizukuOptions.asCommon()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
        ) {
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.AdminPanelSettings,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = stringResource(R.string.setting_section_install_options),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = stringResource(R.string.setting_install_options_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (!privileged) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.setting_install_options_unprivileged_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (useDhizuku) {
                Text(
                    text = stringResource(R.string.setting_install_options_dhizuku_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            if (privileged) {
                OptionGroupHeader(stringResource(R.string.setting_shizuku_options_install_group))
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_replace),
                    subtitle = stringResource(R.string.setting_shizuku_replace_sub),
                    checked = opts.replaceExisting,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.ReplaceExisting, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_downgrade),
                    subtitle = stringResource(R.string.setting_shizuku_downgrade_sub),
                    checked = opts.requestDowngrade,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.RequestDowngrade, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_grant_permissions),
                    subtitle = stringResource(R.string.setting_shizuku_grant_permissions_sub),
                    checked = opts.grantAllPermissions,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.GrantAllPermissions, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_allow_test),
                    subtitle = stringResource(R.string.setting_shizuku_allow_test_sub),
                    checked = opts.allowTest,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.AllowTest, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_bypass_sdk),
                    subtitle = stringResource(R.string.setting_shizuku_bypass_sdk_sub),
                    checked = opts.bypassLowTargetSdk,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.BypassLowTargetSdk, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_all_users),
                    subtitle = stringResource(R.string.setting_shizuku_all_users_sub),
                    checked = opts.allUsers,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.AllUsers, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_allow_restricted_permissions),
                    subtitle = stringResource(R.string.setting_shizuku_allow_restricted_permissions_sub),
                    checked = opts.allowRestrictedPermissions,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.AllowRestrictedPermissions, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_dont_kill_app),
                    subtitle = stringResource(R.string.setting_shizuku_dont_kill_app_sub),
                    checked = opts.dontKillApp,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.DontKillApp, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_disable_verification),
                    subtitle = stringResource(R.string.setting_shizuku_disable_verification_sub),
                    checked = opts.disableVerification,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.DisableVerification, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_enable_rollback),
                    subtitle = stringResource(R.string.setting_shizuku_enable_rollback_sub),
                    checked = opts.enableRollback,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.EnableRollback, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_request_update_ownership),
                    subtitle = stringResource(R.string.setting_shizuku_request_update_ownership_sub),
                    checked = opts.requestUpdateOwnership,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.RequestUpdateOwnership, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_dex2oat_optimization),
                    subtitle = stringResource(R.string.setting_dex2oat_optimization_sub),
                    checked = opts.dex2oatOptimization,
                    onCheckedChange = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.Dex2oatOptimization, it) },
                )
            }

            OptionGroupHeader(stringResource(R.string.setting_group_installing))
            InstallSourceItem(
                title = stringResource(R.string.setting_shizuku_set_source),
                subtitle = stringResource(R.string.setting_shizuku_set_source_sub),
                enabled = opts.setInstallSource,
                installerPackageName = opts.installerPackageName,
                onToggle = { onPrivilegedOptionChanged(SettingViewModel.PrivilegedOption.SetInstallSource, it) },
                onInstallerChange = onInstallerPackageChanged,
            )

            // Uninstall flags are genuinely Shizuku-only
            if (uiState.useShizuku) {
                OptionGroupHeader(stringResource(R.string.setting_shizuku_options_uninstall_group))
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_uninstall_keep_data),
                    subtitle = stringResource(R.string.setting_shizuku_uninstall_keep_data_sub),
                    checked = uiState.shizukuOptions.uninstallKeepData,
                    onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_UNINSTALL_KEEP_DATA, it) },
                )
                OptionSwitch(
                    title = stringResource(R.string.setting_shizuku_uninstall_all_users),
                    subtitle = stringResource(R.string.setting_shizuku_uninstall_all_users_sub),
                    checked = uiState.shizukuOptions.uninstallAllUsers,
                    onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_UNINSTALL_ALL_USERS, it) },
                )
            }
        }
    }
}
