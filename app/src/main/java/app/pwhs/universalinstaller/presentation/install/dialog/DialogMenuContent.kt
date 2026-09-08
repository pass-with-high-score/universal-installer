package app.pwhs.universalinstaller.presentation.install.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.scale
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Work
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import app.pwhs.universalinstaller.presentation.install.dialog.components.DialogMenuTabRow
import app.pwhs.universalinstaller.presentation.install.dialog.components.DialogTabItem
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SplitEntry
import app.pwhs.universalinstaller.domain.model.SplitType
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.presentation.install.AttachedObb
import app.pwhs.universalinstaller.presentation.install.PermissionEntry
import app.pwhs.universalinstaller.ui.theme.LocalExtendedColors
import app.pwhs.universalinstaller.presentation.install.displayLanguage
import app.pwhs.universalinstaller.presentation.install.resolvePermissionEntries
import app.pwhs.universalinstaller.presentation.setting.DEFAULT_INSTALLER_PACKAGE_NAME
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.install.InstallTargetPicker
import app.pwhs.universalinstaller.presentation.install.rememberDeviceUserProfiles
import app.pwhs.core.data.local.dataStore
import kotlinx.coroutines.launch
import app.pwhs.universalinstaller.presentation.install.dialog.tabs.advancedTab
import app.pwhs.universalinstaller.presentation.install.dialog.tabs.infoTab
import app.pwhs.universalinstaller.presentation.install.dialog.tabs.securityTab

/**
 * Stage 3: Extended Menu — full-featured option panel using a Tabbed Pager.
 * Inspired by InstallerX-Revived's App Info UI.
 *
 * Tabs:
 *  1. Info (App Details, Architectures, Languages, SHA-256)
 *  2. Security (VirusTotal Scan, Permissions)
 *  3. Advanced (OBB Files / Attach OBB, Split APK selector)
 */
@Composable
fun DialogMenuContent(
    apkInfo: ApkInfo,
    attachedObbFiles: List<AttachedObb>,
    allUsers: Boolean,
    selectedUserId: Int?,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onCheckVirusTotal: () -> Unit,
    onRemoveObb: (AttachedObb) -> Unit,
    onToggleSplit: (Int) -> Unit,
    onAttachObb: () -> Unit = {},
    onToggleAllUsers: (Boolean) -> Unit = {},
    onSelectUserId: (Int?) -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val prefs by context.dataStore.data.collectAsState(initial = null)
    val spoofSource = prefs?.get(PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE) ?: false
    val installerPkg = prefs?.get(PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME) ?: DEFAULT_INSTALLER_PACKAGE_NAME
    val overridesSerialized = prefs?.get(PreferencesKeys.INSTALLER_OVERRIDES)
    val packageOverride = remember(overridesSerialized, apkInfo.packageName) {
        InstallerOverrides.get(overridesSerialized, apkInfo.packageName)
    }
    
    val onToggleSpoofSource: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE] = enabled
                it[PreferencesKeys.ROOT_SET_INSTALL_SOURCE] = enabled
            }
        }
    }
    
    val onChangeInstallerPkg: (String) -> Unit = { pkg ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME] = pkg
                it[PreferencesKeys.ROOT_INSTALLER_PACKAGE_NAME] = pkg
            }
        }
    }

    val onToggleReplaceExisting: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_REPLACE_EXISTING] = enabled
                it[PreferencesKeys.ROOT_REPLACE_EXISTING] = enabled
            }
        }
    }

    val onToggleAllowTest: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_ALLOW_TEST] = enabled
                it[PreferencesKeys.ROOT_ALLOW_TEST] = enabled
            }
        }
    }

    val onToggleRequestDowngrade: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE] = enabled
                it[PreferencesKeys.ROOT_REQUEST_DOWNGRADE] = enabled
            }
        }
    }

    val onToggleGrantAllPermissions: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS] = enabled
                it[PreferencesKeys.ROOT_GRANT_ALL_PERMISSIONS] = enabled
            }
        }
    }

    val onToggleBypassLowTargetSdk: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK] = enabled
                it[PreferencesKeys.ROOT_BYPASS_LOW_TARGET_SDK] = enabled
            }
        }
    }

    val onToggleAllowRestrictedPermissions: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_ALLOW_RESTRICTED_PERMISSIONS] = enabled
                it[PreferencesKeys.ROOT_ALLOW_RESTRICTED_PERMISSIONS] = enabled
            }
        }
    }

    val onToggleDontKillApp: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_DONT_KILL_APP] = enabled
                it[PreferencesKeys.ROOT_DONT_KILL_APP] = enabled
            }
        }
    }

    val onToggleDisableVerification: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_DISABLE_VERIFICATION] = enabled
                it[PreferencesKeys.ROOT_DISABLE_VERIFICATION] = enabled
            }
        }
    }

    val onToggleEnableRollback: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_ENABLE_ROLLBACK] = enabled
                it[PreferencesKeys.ROOT_ENABLE_ROLLBACK] = enabled
            }
        }
    }

    val onToggleRequestUpdateOwnership: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_REQUEST_UPDATE_OWNERSHIP] = enabled
                it[PreferencesKeys.ROOT_REQUEST_UPDATE_OWNERSHIP] = enabled
            }
        }
    }

    // "Remember for this app" toggle — true when an override row exists for the
    // current package. Writing flips the row in/out of the INSTALLER_OVERRIDES map.
    val onSetRemember: (Boolean) -> Unit = { remember ->
        val pkg = apkInfo.packageName
        if (pkg.isNotBlank()) {
            scope.launch {
                context.dataStore.edit { p ->
                    val current = p[PreferencesKeys.INSTALLER_OVERRIDES]
                    p[PreferencesKeys.INSTALLER_OVERRIDES] = if (remember) {
                        InstallerOverrides.put(current, pkg, installerPkg)
                    } else {
                        InstallerOverrides.remove(current, pkg)
                    }
                }
            }
        }
    }

    // When the dialog opens for a package that has a saved override and spoof
    // source is on, push the override into the active installer pref so the
    // install actually uses it. Keyed on package + override so it fires once
    // per (package, value) — re-tabbing through Menu doesn't replay it.
    androidx.compose.runtime.LaunchedEffect(apkInfo.packageName, packageOverride, spoofSource) {
        if (spoofSource && packageOverride != null && packageOverride != installerPkg) {
            onChangeInstallerPkg(packageOverride)
        }
    }
    // Keep the override in sync when the user tweaks the dropdown while
    // "Remember" is on. If they turned remember off, this is a no-op.
    androidx.compose.runtime.LaunchedEffect(installerPkg, spoofSource) {
        if (spoofSource && packageOverride != null && packageOverride != installerPkg) {
            scope.launch {
                context.dataStore.edit { p ->
                    p[PreferencesKeys.INSTALLER_OVERRIDES] =
                        InstallerOverrides.put(p[PreferencesKeys.INSTALLER_OVERRIDES], apkInfo.packageName, installerPkg)
                }
            }
        }
    }
    
    val tabs = listOf(
        DialogTabItem(stringResource(R.string.dialog_tab_info), Icons.Rounded.Info),
        DialogTabItem(stringResource(R.string.dialog_tab_security), Icons.Rounded.Security),
        DialogTabItem(stringResource(R.string.dialog_tab_advanced), Icons.Rounded.Tune),
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Tabs ──
        DialogMenuTabRow(
            tabs = tabs,
            selectedIndex = pagerState.currentPage,
            onTabSelected = { index ->
                scope.launch {
                    pagerState.animateScrollToPage(index)
                }
            },
        )

        Spacer(modifier = Modifier.height(12.dp)) // Reduced spacer

        // ── Pager Content ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f), // Allow it to take all available space
            verticalAlignment = Alignment.Top,
        ) { page ->
            // Use a LazyColumn inside each page for scrolling
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                when (page) {
                    0 -> infoTab(apkInfo, context)
                    1 -> securityTab(apkInfo, context, onCheckVirusTotal)
                    2 -> advancedTab(
                        apkInfo = apkInfo,
                        attachedObbFiles = attachedObbFiles,
                        onRemoveObb = onRemoveObb,
                        onAttachObb = onAttachObb,
                        onToggleSplit = onToggleSplit,
                        allUsers = allUsers,
                        selectedUserId = selectedUserId,
                        spoofSource = spoofSource,
                        installerPkg = installerPkg,
                        rememberForThisApp = packageOverride != null,
                        replaceExisting = prefs?.get(PreferencesKeys.SHIZUKU_REPLACE_EXISTING) ?: true,
                        allowTest = prefs?.get(PreferencesKeys.SHIZUKU_ALLOW_TEST) ?: false,
                        requestDowngrade = prefs?.get(PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE) ?: false,
                        grantAllPermissions = prefs?.get(PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS) ?: false,
                        bypassLowTargetSdk = prefs?.get(PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK) ?: false,
                        allowRestrictedPermissions = prefs?.get(PreferencesKeys.SHIZUKU_ALLOW_RESTRICTED_PERMISSIONS) ?: false,
                        dontKillApp = prefs?.get(PreferencesKeys.SHIZUKU_DONT_KILL_APP) ?: false,
                        disableVerification = prefs?.get(PreferencesKeys.SHIZUKU_DISABLE_VERIFICATION) ?: false,
                        enableRollback = prefs?.get(PreferencesKeys.SHIZUKU_ENABLE_ROLLBACK) ?: false,
                        requestUpdateOwnership = prefs?.get(PreferencesKeys.SHIZUKU_REQUEST_UPDATE_OWNERSHIP) ?: false,
                        showAdvancedFlags = (prefs?.get(PreferencesKeys.USE_SHIZUKU) == true) || (prefs?.get(PreferencesKeys.USE_ROOT) == true),
                        onToggleAllUsers = onToggleAllUsers,
                        onSelectUserId = onSelectUserId,
                        onToggleSpoofSource = onToggleSpoofSource,
                        onChangeInstallerPkg = onChangeInstallerPkg,
                        onSetRemember = onSetRemember,
                        onToggleReplaceExisting = onToggleReplaceExisting,
                        onToggleAllowTest = onToggleAllowTest,
                        onToggleRequestDowngrade = onToggleRequestDowngrade,
                        onToggleGrantAllPermissions = onToggleGrantAllPermissions,
                        onToggleBypassLowTargetSdk = onToggleBypassLowTargetSdk,
                        onToggleAllowRestrictedPermissions = onToggleAllowRestrictedPermissions,
                        onToggleDontKillApp = onToggleDontKillApp,
                        onToggleDisableVerification = onToggleDisableVerification,
                        onToggleEnableRollback = onToggleEnableRollback,
                        onToggleRequestUpdateOwnership = onToggleRequestUpdateOwnership,
                    )
                }
                
                // Add a bottom spacer so the last item isn't clipped
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }

        Spacer(modifier = Modifier.height(12.dp)) // Reduced spacer

        // ── Buttons: [Back] [Install] ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
                border = androidx.compose.foundation.BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.dialog_back_btn),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }

            Button(
                onClick = onInstall,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = stringResource(R.string.dialog_install_btn),
                    maxLines = 1,
                    modifier = Modifier.basicMarquee()
                )
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp)) // Padding from card bottom
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Tab Contents (Extension functions on LazyListScope)
// ─────────────────────────────────────────────────────────────────────────

