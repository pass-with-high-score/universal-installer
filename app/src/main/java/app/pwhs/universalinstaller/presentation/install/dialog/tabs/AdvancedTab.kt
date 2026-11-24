package app.pwhs.universalinstaller.presentation.install.dialog.tabs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.presentation.install.AttachedObb
import app.pwhs.universalinstaller.presentation.install.InstallTargetPicker
import app.pwhs.universalinstaller.presentation.install.dialog.components.AbiOverridePicker
import app.pwhs.universalinstaller.presentation.install.dialog.components.AdvancedToggle
import app.pwhs.universalinstaller.presentation.install.dialog.components.InstallerSourcePicker
import app.pwhs.universalinstaller.presentation.install.dialog.components.MenuCard
import app.pwhs.universalinstaller.presentation.install.dialog.components.SplitChipPicker
import app.pwhs.universalinstaller.presentation.install.dialog.components.rememberInstallerLabel
import app.pwhs.universalinstaller.presentation.install.rememberDeviceUserProfiles

internal fun androidx.compose.foundation.lazy.LazyListScope.advancedTab(
    apkInfo: ApkInfo,
    attachedObbFiles: List<AttachedObb>,
    onRemoveObb: (AttachedObb) -> Unit,
    onAttachObb: () -> Unit,
    onToggleSplit: (Int) -> Unit,
    onMergeSplits: (() -> Unit)? = null,
    allUsers: Boolean,
    selectedUserId: Int?,
    abiOverride: String? = null,
    spoofSource: Boolean,
    installerPkg: String,
    rememberForThisApp: Boolean,
    replaceExisting: Boolean,
    allowTest: Boolean,
    requestDowngrade: Boolean,
    grantAllPermissions: Boolean,
    bypassLowTargetSdk: Boolean,
    allowRestrictedPermissions: Boolean = false,
    dontKillApp: Boolean = false,
    disableVerification: Boolean = false,
    enableRollback: Boolean = false,
    requestUpdateOwnership: Boolean = false,
    showAdvancedFlags: Boolean,
    onToggleAllUsers: (Boolean) -> Unit,
    onSelectUserId: (Int?) -> Unit,
    onSelectAbiOverride: (String?) -> Unit = {},
    onToggleSpoofSource: (Boolean) -> Unit,
    onChangeInstallerPkg: (String) -> Unit,
    onSetRemember: (Boolean) -> Unit,
    onToggleReplaceExisting: (Boolean) -> Unit,
    onToggleAllowTest: (Boolean) -> Unit,
    onToggleRequestDowngrade: (Boolean) -> Unit,
    onToggleGrantAllPermissions: (Boolean) -> Unit,
    onToggleBypassLowTargetSdk: (Boolean) -> Unit,
    onToggleAllowRestrictedPermissions: (Boolean) -> Unit = {},
    onToggleDontKillApp: (Boolean) -> Unit = {},
    onToggleDisableVerification: (Boolean) -> Unit = {},
    onToggleEnableRollback: (Boolean) -> Unit = {},
    onToggleRequestUpdateOwnership: (Boolean) -> Unit = {},
) {
    // 1. OBB Files
    if (apkInfo.obbFileNames.isNotEmpty() || attachedObbFiles.isNotEmpty()) {
        item(key = "obb") {
            var expanded by remember { mutableStateOf(true) }
            val obbCount = apkInfo.obbFileNames.size + attachedObbFiles.size
            MenuCard(
                title = stringResource(R.string.dialog_menu_obb),
                description = stringResource(R.string.dialog_menu_obb_desc),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
                badge = "$obbCount",
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    apkInfo.obbFileNames.forEach { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    attachedObbFiles.forEach { obb ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = obb.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onRemoveObb(obb) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Text(
                                    text = "✕",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 1b. Attach OBB button
    item(key = "obb_attach") {
        MenuCard(
            title = stringResource(R.string.dialog_menu_obb_attach),
            description = stringResource(R.string.dialog_menu_obb_attach_desc),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            onClick = onAttachObb,
        )
    }

    // 2. Split APKs
    if (apkInfo.splitEntries.size > 1) {
        item(key = "splits") {
            var expanded by remember { mutableStateOf(true) }
            val selectedCount = apkInfo.splitEntries.count { it.selected }
            val selectedBytes = apkInfo.splitEntries.filter { it.selected }
                .sumOf { it.sizeBytes.coerceAtLeast(0) }
            MenuCard(
                title = stringResource(R.string.dialog_menu_splits),
                description = stringResource(R.string.dialog_menu_splits_desc),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Splitscreen,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
                badge = "$selectedCount / ${apkInfo.splitEntries.size}",
            ) {
                Column {
                    if (onMergeSplits != null) {
                        OutlinedButton(
                            onClick = onMergeSplits,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CallMerge,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.antisplit_action_merge),
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                    }
                    SplitChipPicker(
                        entries = apkInfo.splitEntries,
                        selectedBytes = selectedBytes,
                        onToggle = onToggleSplit,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    )
                }
            }
        }
    }

    // 3. Install target — profile picker
    item(key = "setting_all_users") {
        val profiles = rememberDeviceUserProfiles()
        val allUsersDesc = if (allUsers) {
            stringResource(R.string.dialog_menu_all_users_on)
        } else {
            stringResource(R.string.dialog_menu_all_users_off)
        }
        MenuCard(
            title = stringResource(R.string.dialog_menu_install_target),
            description = allUsersDesc,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Person,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            onClick = { /* expanded is always shown */ },
            expanded = true,
            expandedContent = {
                InstallTargetPicker(
                    profiles = profiles,
                    allUsers = allUsers,
                    selectedUserId = selectedUserId,
                    onSelectAllUsers = onToggleAllUsers,
                    onSelectUserId = onSelectUserId,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                )
            }
        )
    }

    item(key = "setting_spoof_source") {
        val installerLabel = rememberInstallerLabel(installerPkg)
        val description = if (spoofSource) {
            stringResource(R.string.dialog_menu_install_source_on, installerLabel)
        } else {
            stringResource(R.string.dialog_menu_install_source_desc)
        }
        MenuCard(
            title = stringResource(R.string.dialog_menu_install_source),
            description = description,
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Store,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            onClick = { onToggleSpoofSource(!spoofSource) },
            trailingContent = {
                Switch(checked = spoofSource, onCheckedChange = onToggleSpoofSource)
            },
            expanded = spoofSource,
            expandedContent = {
                InstallerSourcePicker(
                    installerPackageName = installerPkg,
                    onInstallerChange = onChangeInstallerPkg,
                    rememberForThisApp = rememberForThisApp,
                    onSetRemember = onSetRemember,
                    canRemember = apkInfo.packageName.isNotBlank(),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        )
    }

    // 4. ABI Override (--abi) & Advanced Install Flags
    if (showAdvancedFlags) {
        item(key = "abi_override") {
            var expanded by remember { mutableStateOf(abiOverride != null) }
            val description = if (abiOverride != null) {
                stringResource(R.string.dialog_menu_abi_override_forced, abiOverride)
            } else {
                stringResource(R.string.dialog_menu_abi_override_auto)
            }
            MenuCard(
                title = stringResource(R.string.dialog_menu_abi_override),
                description = description,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
                badge = abiOverride,
            ) {
                AbiOverridePicker(
                    apkAbis = apkInfo.supportedAbis,
                    selectedAbi = abiOverride,
                    onSelectAbi = onSelectAbiOverride,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }

        item(key = "advanced_flags") {
            var expanded by remember { mutableStateOf(false) }
            MenuCard(
                title = stringResource(R.string.manage_section_advanced),
                description = stringResource(R.string.setting_shizuku_options_install_group),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.AdminPanelSettings,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    AdvancedToggle(
                        title = stringResource(R.string.dialog_menu_replace_existing),
                        description = stringResource(R.string.dialog_menu_replace_existing_desc),
                        checked = replaceExisting,
                        onCheckedChange = onToggleReplaceExisting
                    )
                    AdvancedToggle(
                        title = stringResource(R.string.dialog_menu_allow_test),
                        description = stringResource(R.string.dialog_menu_allow_test_desc),
                        checked = allowTest,
                        onCheckedChange = onToggleAllowTest
                    )
                    AdvancedToggle(
                        title = stringResource(R.string.dialog_menu_bypass_sdk),
                        description = stringResource(R.string.dialog_menu_bypass_sdk_desc),
                        checked = bypassLowTargetSdk,
                        onCheckedChange = onToggleBypassLowTargetSdk
                    )
                    AdvancedToggle(
                        title = stringResource(R.string.dialog_menu_request_downgrade),
                        description = stringResource(R.string.dialog_menu_request_downgrade_desc),
                        checked = requestDowngrade,
                        onCheckedChange = onToggleRequestDowngrade
                    )
                    AdvancedToggle(
                        title = stringResource(R.string.dialog_menu_grant_permissions),
                        description = stringResource(R.string.dialog_menu_grant_permissions_desc),
                        checked = grantAllPermissions,
                        onCheckedChange = onToggleGrantAllPermissions
                    )
                    AdvancedToggle(
                        title = stringResource(R.string.dialog_menu_allow_restricted_permissions),
                        description = stringResource(R.string.dialog_menu_allow_restricted_permissions_desc),
                        checked = allowRestrictedPermissions,
                        onCheckedChange = onToggleAllowRestrictedPermissions
                    )
                    AdvancedToggle(
                        title = stringResource(R.string.dialog_menu_dont_kill_app),
                        description = stringResource(R.string.dialog_menu_dont_kill_app_desc),
                        checked = dontKillApp,
                        onCheckedChange = onToggleDontKillApp
                    )
                    AdvancedToggle(
                        title = stringResource(R.string.dialog_menu_disable_verification),
                        description = stringResource(R.string.dialog_menu_disable_verification_desc),
                        checked = disableVerification,
                        onCheckedChange = onToggleDisableVerification
                    )
                    AdvancedToggle(
                        title = stringResource(R.string.dialog_menu_enable_rollback),
                        description = stringResource(R.string.dialog_menu_enable_rollback_desc),
                        checked = enableRollback,
                        onCheckedChange = onToggleEnableRollback
                    )
                    AdvancedToggle(
                        title = stringResource(R.string.dialog_menu_request_update_ownership),
                        description = stringResource(R.string.dialog_menu_request_update_ownership_desc),
                        checked = requestUpdateOwnership,
                        onCheckedChange = onToggleRequestUpdateOwnership
                    )
                }
            }
        }
    }
}
