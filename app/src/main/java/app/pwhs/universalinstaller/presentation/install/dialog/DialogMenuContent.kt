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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
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
import androidx.compose.material.icons.rounded.Warning
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
import app.pwhs.universalinstaller.presentation.install.displayLanguage
import app.pwhs.universalinstaller.presentation.install.resolvePermissionEntries
import app.pwhs.universalinstaller.presentation.setting.DEFAULT_INSTALLER_PACKAGE_NAME
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.dataStore
import kotlinx.coroutines.launch

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
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onCheckVirusTotal: () -> Unit,
    onRemoveObb: (AttachedObb) -> Unit,
    onToggleSplit: (Int) -> Unit,
    onAttachObb: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val prefs by context.dataStore.data.collectAsState(initial = null)
    val allUsers = prefs?.get(PreferencesKeys.SHIZUKU_ALL_USERS) ?: false
    val spoofSource = prefs?.get(PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE) ?: false
    val installerPkg = prefs?.get(PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME) ?: DEFAULT_INSTALLER_PACKAGE_NAME
    val overridesSerialized = prefs?.get(PreferencesKeys.INSTALLER_OVERRIDES)
    val packageOverride = remember(overridesSerialized, apkInfo.packageName) {
        InstallerOverrides.get(overridesSerialized, apkInfo.packageName)
    }

    val onToggleAllUsers: (Boolean) -> Unit = { enabled ->
        scope.launch {
            context.dataStore.edit {
                it[PreferencesKeys.SHIZUKU_ALL_USERS] = enabled
                it[PreferencesKeys.ROOT_ALL_USERS] = enabled
            }
        }
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
        stringResource(R.string.dialog_tab_info),
        stringResource(R.string.dialog_tab_security),
        stringResource(R.string.dialog_tab_advanced),
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Title ──
        Text(
            text = stringResource(R.string.dialog_menu_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Tabs ──
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = Color.Transparent,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) },
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Pager Content ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.heightIn(min = 300.dp, max = 380.dp),
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
                        spoofSource = spoofSource,
                        installerPkg = installerPkg,
                        rememberForThisApp = packageOverride != null,
                        onToggleAllUsers = onToggleAllUsers,
                        onToggleSpoofSource = onToggleSpoofSource,
                        onChangeInstallerPkg = onChangeInstallerPkg,
                        onSetRemember = onSetRemember,
                    )
                }
                
                // Add a bottom spacer so the last item isn't clipped
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Buttons: [Back] [Install] ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.dialog_back_btn))
            }

            Button(
                onClick = onInstall,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.dialog_install_btn))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Tab Contents (Extension functions on LazyListScope)
// ─────────────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.infoTab(
    apkInfo: ApkInfo,
    context: Context,
) {
    // 1. App Details
    item(key = "details") {
        MenuCard(
            title = stringResource(R.string.dialog_menu_details),
            description = stringResource(R.string.dialog_menu_details_desc),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            expanded = true, // Always expanded in this tab
            onClick = { /* Do nothing, static */ },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DetailRow(stringResource(R.string.apk_info_label_package), apkInfo.packageName)
                if (apkInfo.versionName.isNotBlank()) {
                    DetailRow(
                        stringResource(R.string.apk_info_label_version),
                        stringResource(R.string.apk_info_version_detail, apkInfo.versionName, apkInfo.versionCode),
                    )
                }
                if (apkInfo.minSdkVersion > 0) {
                    DetailRow(stringResource(R.string.apk_info_label_min_sdk), "API ${apkInfo.minSdkVersion}")
                }
                if (apkInfo.targetSdkVersion > 0) {
                    DetailRow(stringResource(R.string.apk_info_label_target_sdk), "API ${apkInfo.targetSdkVersion}")
                }
                if (apkInfo.fileSizeBytes > 0) {
                    DetailRow(stringResource(R.string.install_storage_title), Formatter.formatFileSize(context, apkInfo.fileSizeBytes))
                }
                DetailRow("Format", apkInfo.fileFormat)
            }
        }
    }

    // 2. Architectures
    if (apkInfo.supportedAbis.isNotEmpty()) {
        item(key = "architectures") {
            var expanded by remember { mutableStateOf(false) }
            MenuCard(
                title = stringResource(R.string.dialog_menu_architectures),
                description = apkInfo.supportedAbis.joinToString(", "),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
                badge = "${apkInfo.supportedAbis.size}",
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    apkInfo.supportedAbis.forEach { abi ->
                        Text(
                            text = abi,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // 3. Languages
    if (apkInfo.supportedLanguages.isNotEmpty()) {
        item(key = "languages") {
            var expanded by remember { mutableStateOf(false) }
            val previewText = remember(apkInfo.supportedLanguages) {
                apkInfo.supportedLanguages
                    .take(3)
                    .joinToString(", ") { displayLanguage(it) }
                    .let {
                        if (apkInfo.supportedLanguages.size > 3) "$it, …" else it
                    }
            }
            MenuCard(
                title = stringResource(R.string.dialog_menu_languages),
                description = previewText,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
                badge = "${apkInfo.supportedLanguages.size}",
            ) {
                LanguageChipGrid(
                    languages = apkInfo.supportedLanguages,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
        }
    }

    // 4. SHA-256 Hash
    if (apkInfo.sha256.isNotBlank()) {
        item(key = "sha256") {
            var expanded by remember { mutableStateOf(false) }
            MenuCard(
                title = stringResource(R.string.dialog_menu_sha256),
                description = apkInfo.sha256.take(24) + "…",
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = apkInfo.sha256,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("SHA-256", apkInfo.sha256))
                            Toast.makeText(context, context.getString(R.string.dialog_menu_sha256_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.securityTab(
    apkInfo: ApkInfo,
    context: Context,
    onCheckVirusTotal: () -> Unit,
) {
    // 1. VirusTotal
    item(key = "virustotal") {
        val vtResult = apkInfo.vtResult
        val vtDesc = when (vtResult?.status) {
            VtStatus.CLEAN -> stringResource(R.string.apk_info_vt_clean)
            VtStatus.MALICIOUS -> stringResource(R.string.apk_info_vt_malicious, vtResult.malicious)
            VtStatus.SUSPICIOUS -> stringResource(R.string.apk_info_vt_suspicious, vtResult.suspicious)
            VtStatus.SCANNING -> stringResource(R.string.apk_info_vt_scanning)
            VtStatus.UPLOADING -> stringResource(R.string.apk_info_vt_uploading, vtResult.uploadProgress)
            VtStatus.NO_API_KEY -> stringResource(R.string.apk_info_vt_no_api_key)
            else -> stringResource(R.string.dialog_menu_virustotal_desc)
        }
        val vtColor = when (vtResult?.status) {
            VtStatus.CLEAN -> MaterialTheme.colorScheme.tertiary
            VtStatus.MALICIOUS -> MaterialTheme.colorScheme.error
            VtStatus.SUSPICIOUS -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        MenuCard(
            title = stringResource(R.string.dialog_menu_virustotal),
            description = vtDesc,
            descriptionColor = vtColor,
            icon = {
                Icon(
                    imageVector = if (vtResult?.status == VtStatus.CLEAN)
                        Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = vtColor,
                )
            },
            onClick = onCheckVirusTotal,
        )
    }

    // 2. Permissions
    if (apkInfo.permissions.isNotEmpty()) {
        item(key = "permissions") {
            var expanded by remember { mutableStateOf(true) } // Expanded by default in this tab
            val entries = remember(apkInfo.permissions) {
                resolvePermissionEntries(context, apkInfo.permissions)
            }
            val dangerousCount = entries.count { it.isDangerous }
            val description = if (dangerousCount > 0) {
                stringResource(
                    R.string.dialog_menu_permissions_breakdown,
                    dangerousCount,
                    entries.size - dangerousCount,
                )
            } else {
                stringResource(R.string.dialog_menu_permissions_desc)
            }
            MenuCard(
                title = stringResource(R.string.dialog_menu_permissions),
                description = description,
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
                badge = "${apkInfo.permissions.size}",
            ) {
                PermissionRowList(
                    entries = entries,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                )
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.advancedTab(
    apkInfo: ApkInfo,
    attachedObbFiles: List<AttachedObb>,
    onRemoveObb: (AttachedObb) -> Unit,
    onAttachObb: () -> Unit,
    onToggleSplit: (Int) -> Unit,
    allUsers: Boolean,
    spoofSource: Boolean,
    installerPkg: String,
    rememberForThisApp: Boolean,
    onToggleAllUsers: (Boolean) -> Unit,
    onToggleSpoofSource: (Boolean) -> Unit,
    onChangeInstallerPkg: (String) -> Unit,
    onSetRemember: (Boolean) -> Unit,
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
                SplitChipPicker(
                    entries = apkInfo.splitEntries,
                    selectedBytes = selectedBytes,
                    onToggle = onToggleSplit,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                )
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
                    onSelectAllUsers = onToggleAllUsers,
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
}

// ─────────────────────────────────────────────────────────────────────────
// UI Components
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun MenuCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    expanded: Boolean = false,
    badge: String? = null,
    trailingContent: (@Composable () -> Unit)? = null,
    descriptionColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    expandedContent: (@Composable () -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (badge != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = descriptionColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (trailingContent != null) {
                    trailingContent()
                }
            }

            if (expanded && expandedContent != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                expandedContent()
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Languages
// ─────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguageChipGrid(
    languages: List<String>,
    modifier: Modifier = Modifier,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        languages.forEach { code ->
            AssistChip(
                onClick = {},
                label = {
                    Text(
                        text = displayLanguage(code),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                colors = AssistChipDefaults.assistChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    labelColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Permissions
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun PermissionRowList(
    entries: List<PermissionEntry>,
    modifier: Modifier = Modifier,
) {
    var showAll by remember(entries) { mutableStateOf(false) }
    val collapsedCount = 5
    val needsToggle = entries.size > collapsedCount
    val visible = if (showAll || !needsToggle) entries else entries.take(collapsedCount)

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        visible.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .then(
                        if (entry.isDangerous) {
                            Modifier.background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                            )
                        } else Modifier
                    )
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (entry.isDangerous) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = if (entry.isDangerous) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.label,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = if (entry.isDangerous) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (entry.isDangerous) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (entry.prefix.isNotEmpty()) {
                        Text(
                            text = entry.prefix,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        if (needsToggle) {
            androidx.compose.material3.TextButton(
                onClick = { showAll = !showAll },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (showAll) {
                        stringResource(R.string.dialog_menu_show_less)
                    } else {
                        stringResource(R.string.dialog_menu_show_more, entries.size - collapsedCount)
                    },
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Split APK picker (FilterChip grid)
// ─────────────────────────────────────────────────────────────────────────

/**
 * Renders splits as a FilterChip grid. Tapping a chip toggles its selection;
 * the Base APK chip is always selected and can't be disabled (required for
 * the install). Locale chips render localized language names ("English") so
 * users don't have to read raw "config.en" tags.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
private fun SplitChipPicker(
    entries: List<SplitEntry>,
    selectedBytes: Long,
    onToggle: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Column(modifier = modifier.fillMaxWidth()) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            entries.forEachIndexed { index, entry ->
                val isBase = entry.type == SplitType.Base
                FilterChip(
                    selected = entry.selected,
                    onClick = { if (!isBase) onToggle(index) },
                    enabled = !isBase,
                    label = {
                        Text(
                            text = splitChipLabel(entry),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    },
                    leadingIcon = if (isBase) {
                        {
                            Icon(
                                imageVector = Icons.Rounded.Lock,
                                contentDescription = null,
                                modifier = Modifier.size(FilterChipDefaults.IconSize),
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        // Base sticks to selected styling even though it's disabled — visually
                        // it's "locked on", not greyed out into ambiguity.
                        disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                        disabledLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        disabledSelectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    ),
                )
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = if (selectedBytes > 0) {
                "Selected: ${Formatter.formatFileSize(context, selectedBytes)}"
            } else {
                "Selected: —"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * Friendly chip label per split type. Strips the "config." prefix that Android
 * Bundles add to ABI/locale/density splits, and runs locales through
 * displayLanguage so "config.en" renders as "English".
 */
@Composable
private fun splitChipLabel(entry: SplitEntry): String {
    val raw = entry.name.removePrefix("config.")
    return when (entry.type) {
        SplitType.Base -> "Base"
        SplitType.Locale -> displayLanguage(raw)
        SplitType.Libs -> raw.replace('_', '-')
        SplitType.ScreenDensity -> raw
        SplitType.Feature -> raw
        SplitType.Other -> entry.name
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Installer source picker
// ─────────────────────────────────────────────────────────────────────────

private data class InstallerPreset(val packageName: String, val labelRes: Int)

private val INSTALLER_PRESETS = listOf(
    InstallerPreset("com.android.vending", R.string.setting_shizuku_installer_preset_play),
    InstallerPreset("com.aurora.store", R.string.setting_shizuku_installer_preset_aurora),
    InstallerPreset("org.fdroid.fdroid", R.string.setting_shizuku_installer_preset_fdroid),
    InstallerPreset("com.amazon.venezia", R.string.setting_shizuku_installer_preset_amazon),
    InstallerPreset("com.sec.android.app.samsungapps", R.string.setting_shizuku_installer_preset_samsung),
    InstallerPreset("com.huawei.appmarket", R.string.setting_shizuku_installer_preset_huawei),
    InstallerPreset("com.xiaomi.market", R.string.setting_shizuku_installer_preset_xiaomi),
)

@Composable
private fun rememberInstallerLabel(packageName: String): String {
    val preset = INSTALLER_PRESETS.firstOrNull { it.packageName == packageName }
    return if (preset != null) stringResource(preset.labelRes) else packageName
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallerSourcePicker(
    installerPackageName: String,
    onInstallerChange: (String) -> Unit,
    rememberForThisApp: Boolean,
    onSetRemember: (Boolean) -> Unit,
    canRemember: Boolean,
    modifier: Modifier = Modifier,
) {
    val presets = INSTALLER_PRESETS.map { it.packageName to stringResource(it.labelRes) }

    var expanded by remember { mutableStateOf(false) }
    var text by remember(installerPackageName) { mutableStateOf(installerPackageName) }

    Column(modifier = modifier.fillMaxWidth()) {
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = {
                    text = it
                    onInstallerChange(it)
                },
                modifier = Modifier
                    .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                    .fillMaxWidth(),
                singleLine = true,
                label = { Text(stringResource(R.string.setting_shizuku_installer_label)) },
                leadingIcon = { Icon(Icons.Rounded.Badge, contentDescription = null) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                textStyle = MaterialTheme.typography.bodyMedium,
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                presets.forEach { (pkg, label) ->
                    DropdownMenuItem(
                        text = {
                            Column {
                                Text(label, style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    text = pkg,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        },
                        onClick = {
                            text = pkg
                            onInstallerChange(pkg)
                            expanded = false
                        },
                    )
                }
            }
        }

        if (canRemember) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .selectable(
                        selected = rememberForThisApp,
                        onClick = { onSetRemember(!rememberForThisApp) },
                        role = Role.Checkbox,
                    )
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(checked = rememberForThisApp, onCheckedChange = null)
                Spacer(modifier = Modifier.width(4.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.dialog_menu_remember_for_app),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.dialog_menu_remember_for_app_sub),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Install target — user profile picker
// ─────────────────────────────────────────────────────────────────────────

private data class DeviceUserProfile(
    val displayName: String,
    val isOwner: Boolean,
    val isWorkProfile: Boolean,
)

@Composable
private fun rememberDeviceUserProfiles(): List<DeviceUserProfile> {
    val context = LocalContext.current
    return remember(context) { loadDeviceUserProfiles(context) }
}

private fun loadDeviceUserProfiles(context: Context): List<DeviceUserProfile> {
    val um = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return emptyList()
    val ownHandle = Process.myUserHandle()
    val handles: List<UserHandle> = runCatching { um.userProfiles }.getOrElse { listOf(ownHandle) }
    return handles.map { handle ->
        val isOwner = handle == ownHandle
        DeviceUserProfile(
            displayName = if (isOwner) "Owner" else "Work profile",
            isOwner = isOwner,
            isWorkProfile = !isOwner,
        )
    }
}

@Composable
private fun InstallTargetPicker(
    profiles: List<DeviceUserProfile>,
    allUsers: Boolean,
    onSelectAllUsers: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        TargetOptionRow(
            icon = Icons.Rounded.Person,
            title = stringResource(R.string.dialog_menu_target_current),
            subtitle = stringResource(R.string.dialog_menu_target_current_sub),
            selected = !allUsers,
            onClick = { onSelectAllUsers(false) },
        )
        TargetOptionRow(
            icon = Icons.Rounded.Lock,
            title = stringResource(R.string.dialog_menu_target_all),
            subtitle = stringResource(R.string.dialog_menu_target_all_sub),
            selected = allUsers,
            onClick = { onSelectAllUsers(true) },
        )

        if (profiles.size > 1) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            Text(
                text = stringResource(R.string.dialog_menu_target_visible_profiles),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, top = 8.dp, bottom = 4.dp),
            )
            profiles.forEach { profile ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = when {
                            profile.isWorkProfile -> Icons.Rounded.Work
                            profile.isOwner -> Icons.Rounded.Person
                            else -> Icons.Rounded.Lock
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = profile.displayName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.dialog_menu_target_hidden_users_hint),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 8.dp, top = 6.dp, bottom = 4.dp, end = 8.dp),
        )
    }
}

@Composable
private fun TargetOptionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
    } else {
        Color.Transparent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.RadioButton,
            )
            .background(container)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
