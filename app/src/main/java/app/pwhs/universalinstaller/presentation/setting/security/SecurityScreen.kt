package app.pwhs.universalinstaller.presentation.setting.security

import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.LockOpen
import androidx.compose.material.icons.rounded.LockReset
import androidx.compose.material.icons.rounded.Password
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.SettingsSection
import app.pwhs.universalinstaller.presentation.setting.blacklist.BlacklistActivity
import app.pwhs.universalinstaller.presentation.setting.components.SwitchPreference
import app.pwhs.universalinstaller.presentation.setting.security.components.ParentalGuidanceCard
import app.pwhs.universalinstaller.presentation.setting.security.components.PinDialog
import app.pwhs.universalinstaller.presentation.setting.security.components.PinDialogMode
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SecurityViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

    // Dialog state
    var activePinDialog by remember { mutableStateOf<PinDialogMode?>(null) }
    var showRevokeConfirmDialog by remember { mutableStateOf(false) }

    // If settings are protected by PIN and not yet unlocked in this session
    val isLockedOut = uiState.hasPinSet && uiState.pinLockProtectSettings && !uiState.isSettingsUnlocked

    if (isLockedOut) {
        PinDialog(
            mode = PinDialogMode.Verify(
                titleRes = R.string.pin_dialog_title_verify,
                descRes = R.string.pin_dialog_desc_settings,
            ),
            onDismiss = onBack,
            onVerifyPin = viewModel::verifyPin,
            onSuccess = viewModel::unlockSettings,
        )
    }

    // Interactive PIN Dialogs for user actions
    activePinDialog?.let { mode ->
        PinDialog(
            mode = mode,
            onDismiss = { activePinDialog = null },
            onVerifyPin = viewModel::verifyPin,
            onPinConfirmed = { pin ->
                when (mode) {
                    PinDialogMode.Setup -> {
                        viewModel.setupPin(pin)
                        Toast.makeText(context, context.getString(R.string.pin_dialog_success_set), Toast.LENGTH_SHORT).show()
                    }
                    PinDialogMode.Change -> {
                        viewModel.changePin(pin)
                        Toast.makeText(context, context.getString(R.string.pin_dialog_success_changed), Toast.LENGTH_SHORT).show()
                    }
                    else -> Unit
                }
                activePinDialog = null
            },
            onSuccess = {
                if (mode is PinDialogMode.Verify && mode.descRes == R.string.pin_dialog_desc_verify_current) {
                    // Disable PIN verified
                    viewModel.disablePin()
                    Toast.makeText(context, context.getString(R.string.pin_dialog_success_disabled), Toast.LENGTH_SHORT).show()
                }
                activePinDialog = null
            },
        )
    }

    if (showRevokeConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showRevokeConfirmDialog = false },
            title = { Text(stringResource(R.string.setting_revoke_other_installers_dialog_title)) },
            text = { Text(stringResource(R.string.setting_revoke_other_installers_dialog_desc)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showRevokeConfirmDialog = false
                        viewModel.revokeOtherAppsInstallPermissions { count ->
                            val msg = if (count > 0) {
                                context.getString(R.string.setting_revoke_other_installers_toast_success, count)
                            } else {
                                context.getString(R.string.setting_revoke_other_installers_toast_none)
                            }
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRevokeConfirmDialog = false }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.setting_security_screen_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(android.R.string.cancel),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        ) {
            // ── Option 3: Parental Guidance Card ───────────────────────
            item {
                ParentalGuidanceCard(modifier = Modifier.padding(bottom = 16.dp))
            }

            // ── PIN Lock Group (Parental Control) ──────────────────────
            item {
                SettingsSection(
                    title = stringResource(R.string.setting_security_group_pin),
                    icon = Icons.Rounded.Password,
                ) {
                    if (!uiState.hasPinSet) {
                        // Setup PIN Entry
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.setting_pin_lock_setup)) },
                            supportingContent = { Text(stringResource(R.string.setting_pin_lock_status_disabled)) },
                            leadingContent = {
                                Icon(Icons.Rounded.LockOpen, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                            },
                            modifier = Modifier.clickable {
                                activePinDialog = PinDialogMode.Setup
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    } else {
                        // PIN Active Status
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.setting_pin_lock_status_enabled)) },
                            supportingContent = { Text(stringResource(R.string.setting_pin_lock_change)) },
                            leadingContent = {
                                Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingContent = {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                            },
                            modifier = Modifier.clickable {
                                activePinDialog = PinDialogMode.Change
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

                        // Switches for enforcement
                        SwitchPreference(
                            title = stringResource(R.string.setting_pin_lock_install_title),
                            subtitle = stringResource(R.string.setting_pin_lock_install_subtitle),
                            checked = uiState.pinLockInstall,
                            onCheckedChange = viewModel::setPinLockInstall,
                        )

                        SwitchPreference(
                            title = stringResource(R.string.setting_pin_lock_uninstall_title),
                            subtitle = stringResource(R.string.setting_pin_lock_uninstall_subtitle),
                            checked = uiState.pinLockUninstall,
                            onCheckedChange = viewModel::setPinLockUninstall,
                        )

                        SwitchPreference(
                            title = stringResource(R.string.setting_pin_lock_app_open_title),
                            subtitle = stringResource(R.string.setting_pin_lock_app_open_subtitle),
                            checked = uiState.pinLockAppOpen,
                            onCheckedChange = viewModel::setPinLockAppOpen,
                        )

                        SwitchPreference(
                            title = stringResource(R.string.setting_pin_lock_protect_settings_title),
                            subtitle = stringResource(R.string.setting_pin_lock_protect_settings_subtitle),
                            checked = uiState.pinLockProtectSettings,
                            onCheckedChange = viewModel::setPinLockProtectSettings,
                        )

                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

                        // Disable PIN Action
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(R.string.setting_pin_lock_disable),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            },
                            leadingContent = {
                                Icon(Icons.Rounded.LockReset, null, tint = MaterialTheme.colorScheme.error)
                            },
                            modifier = Modifier.clickable {
                                activePinDialog = PinDialogMode.Verify(
                                    titleRes = R.string.pin_dialog_title_verify,
                                    descRes = R.string.pin_dialog_desc_verify_current,
                                )
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }
            }

            // ── Biometric Authentication Group ────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(
                    title = stringResource(R.string.setting_security_group_biometric),
                    icon = Icons.Rounded.Fingerprint,
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.setting_lock_install_title),
                        subtitle = stringResource(R.string.setting_lock_install_subtitle),
                        checked = uiState.biometricLockInstall,
                        onCheckedChange = viewModel::setBiometricLockInstall,
                        enabled = uiState.biometricEnrolmentAvailable,
                    )

                    SwitchPreference(
                        title = stringResource(R.string.setting_lock_uninstall_title),
                        subtitle = stringResource(R.string.setting_lock_uninstall_subtitle),
                        checked = uiState.biometricLockUninstall,
                        onCheckedChange = viewModel::setBiometricLockUninstall,
                        enabled = uiState.biometricEnrolmentAvailable,
                    )

                    if (!uiState.biometricEnrolmentAvailable) {
                        Text(
                            text = stringResource(R.string.setting_biometric_unavailable),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }

            // ── Package Restrictions Group ────────────────────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(
                    title = stringResource(R.string.setting_security_group_restrictions),
                    icon = Icons.Rounded.Block,
                ) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_blacklist_title)) },
                        supportingContent = {
                            Text(
                                if (uiState.blacklistCount == 0) stringResource(R.string.setting_blacklist_empty)
                                else stringResource(R.string.setting_blacklist_count, uiState.blacklistCount),
                            )
                        },
                        leadingContent = {
                            Icon(Icons.Rounded.Block, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        trailingContent = {
                            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                        },
                        modifier = Modifier.clickable {
                            context.startActivity(Intent(context, BlacklistActivity::class.java))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            // ── Advanced Protection Group (Shizuku / Root) ────────────
            item {
                Spacer(modifier = Modifier.height(16.dp))
                SettingsSection(
                    title = stringResource(R.string.setting_security_group_privileged),
                    icon = Icons.Rounded.AdminPanelSettings,
                ) {
                    SwitchPreference(
                        title = stringResource(R.string.setting_disable_system_installer_title),
                        subtitle = stringResource(R.string.setting_disable_system_installer_subtitle),
                        checked = uiState.isSystemInstallerDisabled,
                        onCheckedChange = { disable ->
                            viewModel.toggleSystemInstaller(disable) { success ->
                                if (success) {
                                    val msg = if (disable) {
                                        context.getString(R.string.setting_disable_system_installer_toast_disabled)
                                    } else {
                                        context.getString(R.string.setting_disable_system_installer_toast_enabled)
                                    }
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, context.getString(R.string.setting_disable_system_installer_toast_failed), Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        enabled = uiState.isPrivilegeAvailable,
                    )

                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)

                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_revoke_other_installers_title)) },
                        supportingContent = { Text(stringResource(R.string.setting_revoke_other_installers_subtitle)) },
                        leadingContent = {
                            Icon(Icons.Rounded.DeleteSweep, null, tint = MaterialTheme.colorScheme.primary)
                        },
                        modifier = Modifier.clickable(enabled = uiState.isPrivilegeAvailable) {
                            showRevokeConfirmDialog = true
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )

                    if (!uiState.isPrivilegeAvailable) {
                        Text(
                            text = stringResource(R.string.setting_security_privileged_not_running),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            }
        }
    }
}
