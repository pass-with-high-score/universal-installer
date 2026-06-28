package app.pwhs.universalinstaller.presentation.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.EmptyStateView
import app.pwhs.universalinstaller.presentation.composable.SettingsSection
import app.pwhs.universalinstaller.presentation.composable.UniversalSearchBar
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.profile.PackageNamePickerDialog
import androidx.datastore.preferences.core.Preferences
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    LaunchedEffect(viewModel) {
        viewModel.events.collect { stringRes ->
            android.widget.Toast.makeText(context, stringRes, android.widget.Toast.LENGTH_LONG).show()
        }
    }

    SettingUi(
        modifier = modifier,
        uiState = uiState,
        onInstallModeChanged = viewModel::setInstallMode,
        onVirusTotalKeyChanged = viewModel::setVirusTotalApiKey,
        onShizukuOptionChanged = viewModel::setShizukuOption,
        onShizukuInstallerChanged = viewModel::setShizukuInstallerPackageName,
        onDeleteApkChanged = viewModel::setDeleteApkAfterInstall,
        onAutoOpenAfterInstallChanged = viewModel::setAutoOpenAfterInstall,
        onLanguageClick = {
            context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.setting.language.LanguageActivity::class.java))
        },
        onRootRetry = viewModel::retryRoot,
        onRootOptionChanged = viewModel::setRootOption,
        onRootInstallerChanged = viewModel::setRootInstallerPackageName,
        onSyncRequirePinChanged = viewModel::setSyncRequirePin,
        onSyncPinCodeChanged = viewModel::setSyncPinCode,
        onSyncServerPortChanged = viewModel::setSyncServerPort,
        onBiometricLockInstallChanged = viewModel::setBiometricLockInstall,
        onBiometricLockUninstallChanged = viewModel::setBiometricLockUninstall,
        onAutoConfirmExternalInstallChanged = viewModel::setAutoConfirmExternalInstall,
        onShowDownloadTabChanged = viewModel::setShowDownloadTab,
        onDefaultInstallerChanged = viewModel::toggleDefaultInstaller,
        onProfilesClick = {
            context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.setting.profile.ProfileActivity::class.java))
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingUi(
    modifier: Modifier = Modifier,
    uiState: SettingUiState = SettingUiState(),
    onInstallModeChanged: (InstallMode) -> Unit = {},
    onVirusTotalKeyChanged: (String) -> Unit = {},
    onShizukuOptionChanged: (Preferences.Key<Boolean>, Boolean) -> Unit = { _, _ -> },
    onShizukuInstallerChanged: (String) -> Unit = {},
    onDeleteApkChanged: (Boolean) -> Unit = {},
    onAutoOpenAfterInstallChanged: (Boolean) -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onRootRetry: () -> Unit = {},
    onRootOptionChanged: (Preferences.Key<Boolean>, Boolean) -> Unit = { _, _ -> },
    onRootInstallerChanged: (String) -> Unit = {},
    onSyncRequirePinChanged: (Boolean) -> Unit = {},
    onSyncPinCodeChanged: (String) -> Unit = {},
    onSyncServerPortChanged: (String) -> Unit = {},
    onBiometricLockInstallChanged: (Boolean) -> Unit = {},
    onBiometricLockUninstallChanged: (Boolean) -> Unit = {},
    onAutoConfirmExternalInstallChanged: (Boolean) -> Unit = {},
    onShowDownloadTabChanged: (Boolean) -> Unit = {},
    onDefaultInstallerChanged: (Boolean) -> Unit = {},
    onProfilesClick: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Settings search. Filters per-item (discrete rows hide individually) and per-section
    // (a section disappears entirely when nothing under it matches). Survives rotation.
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val q = searchQuery.trim()

    var searchActive by rememberSaveable { mutableStateOf(searchQuery.isNotBlank()) }
    val searchFocusRequester = remember { FocusRequester() }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            // Small delay to ensure the search bar is mounted before requesting focus.
            kotlinx.coroutines.delay(100)
            runCatching { searchFocusRequester.requestFocus() }
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                expandedHeight = 120.dp,
                title = {
                    Text(
                        text = stringResource(R.string.setting_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                actions = {
                    IconButton(onClick = {
                        if (searchActive) {
                            searchQuery = ""
                            searchActive = false
                        } else {
                            searchActive = true
                        }
                    }) {
                        Icon(
                            imageVector = if (searchActive) Icons.Rounded.Close
                            else Icons.Rounded.Search,
                            contentDescription = stringResource(
                                if (searchActive) R.string.uninstall_search_close_cd
                                else R.string.uninstall_search_open_cd,
                            ),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            UniversalSearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                active = searchActive,
                onActiveChange = { searchActive = it },
                placeholder = stringResource(R.string.setting_search_hint),
                focusRequester = searchFocusRequester,
            )

            // Resolve searchable labels here (composable scope) so the LazyListScope `if`
            // gates below — which aren't composable — can decide section visibility without
            // calling stringResource. `matchesQuery` returns true on a blank query, so the
            // full list renders when search is empty.
            val installLabels = listOf(
                stringResource(R.string.setting_install_mode_title), "shizuku", "root", "default",
                stringResource(R.string.setting_delete_apk_title),
                stringResource(R.string.setting_auto_open_title),
                stringResource(R.string.setting_auto_confirm_title),
                stringResource(R.string.setting_show_download_tab_title),
                stringResource(R.string.setting_default_installer_title),
            )
            val shizukuLabels = listOf(stringResource(R.string.setting_section_shizuku_options), "shizuku")
            val rootLabels = listOf(stringResource(R.string.setting_section_root_options), "root")
            val profileLabels = listOf(
                stringResource(R.string.setting_profiles_title),
                stringResource(R.string.setting_profiles_subtitle), "profile",
            )
            val interfaceLabels = listOf(
                "interface",
                stringResource(R.string.theme_screen_title),
                stringResource(R.string.setting_language_title),
            )
            val securityLabels = listOf("security", "lock", "biometric", "fingerprint", "installations", "uninstalls")
            val syncLabels = listOf("sync", "port", "pin")
            val advancedLabels = listOf("advanced", "virustotal", "api key")
            val aboutLabels = listOf(
                "about", "diagnostics",
                stringResource(R.string.setting_section_about),
            )

            // Whether any (currently-applicable) section survives the filter — drives the
            // "no results" state. Shizuku/Root only count when they'd be shown at all.
            val anyVisible = matchesQuery(q, installLabels) ||
                    (uiState.useShizuku && matchesQuery(q, shizukuLabels)) ||
                    (uiState.rootSupported && uiState.useRoot && matchesQuery(q, rootLabels)) ||
                    matchesQuery(q, profileLabels) ||
                    matchesQuery(q, interfaceLabels) ||
                    matchesQuery(q, securityLabels) ||
                    matchesQuery(q, syncLabels) ||
                    matchesQuery(q, advancedLabels) ||
                    matchesQuery(q, aboutLabels)

            androidx.compose.animation.Crossfade(
                targetState = uiState.isLoading,
                label = "SettingsLoading",
                modifier = Modifier.fillMaxSize()
            ) { isLoading ->
                if (isLoading) {
                    androidx.compose.foundation.layout.Box(modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize(),
                        contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 8.dp,
                    bottom = navBarPadding + 16.dp
                ),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // ── Installation Section ─────────────────────
                if (matchesQuery(q, installLabels)) item {
                    SettingsSection(title = stringResource(R.string.setting_section_installation), icon = Icons.Rounded.SettingsApplications) {
                        SearchableItem(q, stringResource(R.string.setting_install_mode_title), "shizuku root default") {
                            InstallModeSelector(
                                currentMode = InstallMode.from(uiState.useShizuku, uiState.useRoot),
                                shizukuState = uiState.shizukuState,
                                rootSupported = uiState.rootSupported,
                                rootState = uiState.rootState,
                                onModeChange = onInstallModeChanged,
                            )
                            if (uiState.rootSupported && uiState.useRoot && uiState.rootState == RootState.DENIED) {
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.setting_retry_root)) },
                                    leadingContent = { Icon(Icons.Rounded.RocketLaunch, null, tint = MaterialTheme.colorScheme.primary) },
                                    modifier = Modifier.clickable { onRootRetry() },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                )
                            }
                        }

                        SearchableItem(q, stringResource(R.string.setting_delete_apk_title)) {
                            SwitchPreference(
                                title = stringResource(R.string.setting_delete_apk_title),
                                checked = uiState.deleteApkAfterInstall,
                                onCheckedChange = onDeleteApkChanged,
                            )
                        }
                        SearchableItem(q, stringResource(R.string.setting_auto_open_title), stringResource(R.string.setting_auto_open_subtitle)) {
                            SwitchPreference(
                                title = stringResource(R.string.setting_auto_open_title),
                                subtitle = stringResource(R.string.setting_auto_open_subtitle),
                                checked = uiState.autoOpenAfterInstall,
                                onCheckedChange = onAutoOpenAfterInstallChanged,
                            )
                        }
                        SearchableItem(q, stringResource(R.string.setting_auto_confirm_title), stringResource(R.string.setting_auto_confirm_subtitle)) {
                            SwitchPreference(
                                title = stringResource(R.string.setting_auto_confirm_title),
                                subtitle = stringResource(R.string.setting_auto_confirm_subtitle),
                                checked = uiState.autoConfirmExternalInstall,
                                onCheckedChange = onAutoConfirmExternalInstallChanged,
                            )
                        }
                        SearchableItem(q, stringResource(R.string.setting_show_download_tab_title), stringResource(R.string.setting_show_download_tab_subtitle)) {
                            SwitchPreference(
                                title = stringResource(R.string.setting_show_download_tab_title),
                                subtitle = stringResource(R.string.setting_show_download_tab_subtitle),
                                checked = uiState.showDownloadTab,
                                onCheckedChange = onShowDownloadTabChanged,
                            )
                        }

                        // Divider only makes sense when the full (unfiltered) list shows.
                        if (q.isBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                        }

                        SearchableItem(q, stringResource(R.string.setting_default_installer_title), stringResource(R.string.setting_default_installer_subtitle)) {
                            SwitchPreference(
                                title = stringResource(R.string.setting_default_installer_title),
                                subtitle = stringResource(R.string.setting_default_installer_subtitle),
                                checked = uiState.isDefaultInstaller,
                                onCheckedChange = onDefaultInstallerChanged,
                                // Don't gate on shizukuAvailable here — that's true at NO_PERMISSION too,
                                // and the toggle would silently no-op. Require the backend to be actually
                                // ready; tapping the disabled-state hint covers the "needs grant" case.
                                enabled = uiState.shizukuState == ShizukuState.READY ||
                                        uiState.rootState == RootState.READY
                            )
                        }
                    }
                }

                // ── Shizuku Options Section (visible only when Shizuku is the chosen backend) ──
                // These are the *defaults* the install pipeline reads when no per-app profile
                // overrides them. They were removed in the profile-screen refactor but the
                // backend logic still reads these prefs, so without this UI the user has no way
                // to flip them globally. Restored to match the same flag set ProfileEditScreen
                // already exposes.
                if (uiState.useShizuku && matchesQuery(q, shizukuLabels)) {
                    item {
                        SettingsSection(
                            title = stringResource(R.string.setting_section_shizuku_options),
                            icon = Icons.Rounded.AdminPanelSettings,
                            collapsible = true,
                            defaultExpanded = false,
                        ) {
                            OptionGroupHeader(stringResource(R.string.setting_shizuku_options_install_group))
                            OptionSwitch(
                                title = stringResource(R.string.setting_shizuku_replace),
                                subtitle = stringResource(R.string.setting_shizuku_replace_sub),
                                checked = uiState.shizukuOptions.replaceExisting,
                                onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_REPLACE_EXISTING, it) },
                            )
                            OptionSwitch(
                                title = stringResource(R.string.setting_shizuku_downgrade),
                                subtitle = stringResource(R.string.setting_shizuku_downgrade_sub),
                                checked = uiState.shizukuOptions.requestDowngrade,
                                onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE, it) },
                            )
                            OptionSwitch(
                                title = stringResource(R.string.setting_shizuku_grant_permissions),
                                subtitle = stringResource(R.string.setting_shizuku_grant_permissions_sub),
                                checked = uiState.shizukuOptions.grantAllPermissions,
                                onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS, it) },
                            )
                            OptionSwitch(
                                title = stringResource(R.string.setting_shizuku_allow_test),
                                subtitle = stringResource(R.string.setting_shizuku_allow_test_sub),
                                checked = uiState.shizukuOptions.allowTest,
                                onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_ALLOW_TEST, it) },
                            )
                            OptionSwitch(
                                title = stringResource(R.string.setting_shizuku_bypass_sdk),
                                subtitle = stringResource(R.string.setting_shizuku_bypass_sdk_sub),
                                checked = uiState.shizukuOptions.bypassLowTargetSdk,
                                onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK, it) },
                            )
                            OptionSwitch(
                                title = stringResource(R.string.setting_shizuku_all_users),
                                subtitle = stringResource(R.string.setting_shizuku_all_users_sub),
                                checked = uiState.shizukuOptions.allUsers,
                                onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_ALL_USERS, it) },
                            )
                            InstallSourceItem(
                                title = stringResource(R.string.setting_shizuku_set_source),
                                subtitle = stringResource(R.string.setting_shizuku_set_source_sub),
                                enabled = uiState.shizukuOptions.setInstallSource,
                                installerPackageName = uiState.shizukuOptions.installerPackageName,
                                onToggle = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE, it) },
                                onInstallerChange = onShizukuInstallerChanged,
                            )

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

                // ── Root Options Section (full flavor only, visible when Root is the chosen backend) ──
                if (uiState.rootSupported && uiState.useRoot && matchesQuery(q, rootLabels)) {
                    item {
                        SettingsSection(
                            title = stringResource(R.string.setting_section_root_options),
                            icon = Icons.Rounded.Key,
                            collapsible = true,
                            defaultExpanded = false,
                        ) {
                            OptionSwitch(
                                title = stringResource(R.string.setting_root_replace),
                                subtitle = stringResource(R.string.setting_root_replace_sub),
                                checked = uiState.rootOptions.replaceExisting,
                                onCheckedChange = { onRootOptionChanged(PreferencesKeys.ROOT_REPLACE_EXISTING, it) },
                            )
                            OptionSwitch(
                                title = stringResource(R.string.setting_root_downgrade),
                                subtitle = stringResource(R.string.setting_root_downgrade_sub),
                                checked = uiState.rootOptions.requestDowngrade,
                                onCheckedChange = { onRootOptionChanged(PreferencesKeys.ROOT_REQUEST_DOWNGRADE, it) },
                            )
                            OptionSwitch(
                                title = stringResource(R.string.setting_root_grant_permissions),
                                subtitle = stringResource(R.string.setting_root_grant_permissions_sub),
                                checked = uiState.rootOptions.grantAllPermissions,
                                onCheckedChange = { onRootOptionChanged(PreferencesKeys.ROOT_GRANT_ALL_PERMISSIONS, it) },
                            )
                            OptionSwitch(
                                title = stringResource(R.string.setting_root_allow_test),
                                subtitle = stringResource(R.string.setting_root_allow_test_sub),
                                checked = uiState.rootOptions.allowTest,
                                onCheckedChange = { onRootOptionChanged(PreferencesKeys.ROOT_ALLOW_TEST, it) },
                            )
                            OptionSwitch(
                                title = stringResource(R.string.setting_root_bypass_sdk),
                                subtitle = stringResource(R.string.setting_root_bypass_sdk_sub),
                                checked = uiState.rootOptions.bypassLowTargetSdk,
                                onCheckedChange = { onRootOptionChanged(PreferencesKeys.ROOT_BYPASS_LOW_TARGET_SDK, it) },
                            )
                            OptionSwitch(
                                title = stringResource(R.string.setting_root_all_users),
                                subtitle = stringResource(R.string.setting_root_all_users_sub),
                                checked = uiState.rootOptions.allUsers,
                                onCheckedChange = { onRootOptionChanged(PreferencesKeys.ROOT_ALL_USERS, it) },
                            )
                            InstallSourceItem(
                                title = stringResource(R.string.setting_root_set_source),
                                subtitle = stringResource(R.string.setting_root_set_source_sub),
                                enabled = uiState.rootOptions.setInstallSource,
                                installerPackageName = uiState.rootOptions.installerPackageName,
                                onToggle = { onRootOptionChanged(PreferencesKeys.ROOT_SET_INSTALL_SOURCE, it) },
                                onInstallerChange = onRootInstallerChanged,
                            )
                        }
                    }
                }

                // ── Profiles Section ─────────────────────────
                if (matchesQuery(q, profileLabels)) item {
                    SettingsSection(
                        title = stringResource(R.string.setting_section_profiles),
                        icon = Icons.Rounded.Badge
                    ) {
                        ListItem(
                            headlineContent = {
                                Text(stringResource(R.string.setting_profiles_title), style = MaterialTheme.typography.bodyLarge)
                            },
                            supportingContent = {
                                Text(
                                    text = stringResource(R.string.setting_profiles_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Rounded.Badge,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp),
                                )
                            },
                            trailingContent = {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            },
                            modifier = Modifier.clickable(onClick = onProfilesClick),
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                        )
                    }
                }

                // ── Interface Section ────────────────────────
                if (matchesQuery(q, interfaceLabels)) item {
                    SettingsSection(title = stringResource(R.string.setting_section_interface), icon = Icons.Rounded.Palette) {
                        SearchableItem(q, stringResource(R.string.theme_screen_title), "interface theme") {
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.theme_screen_title)) },
                                leadingContent = { Icon(Icons.Rounded.Palette, null, tint = MaterialTheme.colorScheme.primary) },
                                modifier = Modifier.clickable {
                                    context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.setting.theme.ThemeActivity::class.java))
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                        if (q.isBlank()) {
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                        }
                        SearchableItem(q, stringResource(R.string.setting_language_title), stringResource(R.string.setting_language_subtitle)) {
                            ListItem(
                                headlineContent = {
                                    Text(stringResource(R.string.setting_language_title), style = MaterialTheme.typography.bodyLarge)
                                },
                                supportingContent = {
                                    Text(
                                        text = stringResource(R.string.setting_language_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        imageVector = Icons.Rounded.Language,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                modifier = Modifier.clickable(onClick = onLanguageClick),
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            )
                        }
                    }
                }

                // ── Security Section ─────────────────────────
                if (matchesQuery(q, securityLabels)) item {
                    SettingsSection(title = stringResource(R.string.setting_section_security), icon = Icons.Rounded.Fingerprint) {
                        SearchableItem(q, stringResource(R.string.setting_lock_install_title), "biometric security install") {
                            SwitchPreference(
                                title = stringResource(R.string.setting_lock_install_title),
                                subtitle = stringResource(R.string.setting_lock_install_subtitle),
                                checked = uiState.biometricLockInstall,
                                onCheckedChange = onBiometricLockInstallChanged,
                                enabled = uiState.biometricEnrolmentAvailable
                            )
                        }
                        SearchableItem(q, stringResource(R.string.setting_lock_uninstall_title), "biometric security uninstall") {
                            SwitchPreference(
                                title = stringResource(R.string.setting_lock_uninstall_title),
                                subtitle = stringResource(R.string.setting_lock_uninstall_subtitle),
                                checked = uiState.biometricLockUninstall,
                                onCheckedChange = onBiometricLockUninstallChanged,
                                enabled = uiState.biometricEnrolmentAvailable
                            )
                        }
                        if (!uiState.biometricEnrolmentAvailable) {
                            Text(
                                text = stringResource(R.string.setting_biometric_unavailable),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }
                    }
                }

                // ── Sync Section ─────────────────────────────
                if (matchesQuery(q, syncLabels)) item {
                    SettingsSection(title = stringResource(R.string.setting_section_sync_short), icon = Icons.Rounded.WifiTethering) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.setting_sync_control_panel)) },
                            leadingContent = { Icon(Icons.Rounded.WifiTethering, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.sync.SyncActivity::class.java))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
                        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
                            OutlinedTextField(
                                value = uiState.syncOptions.serverPort,
                                onValueChange = onSyncServerPortChanged,
                                label = { Text(stringResource(R.string.sync_port)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                singleLine = true
                            )
                            SwitchPreference(
                                title = stringResource(R.string.sync_require_pin),
                                checked = uiState.syncOptions.requirePin,
                                onCheckedChange = onSyncRequirePinChanged
                            )
                            if (uiState.syncOptions.requirePin) {
                                OutlinedTextField(
                                    value = uiState.syncOptions.pinCode,
                                    onValueChange = onSyncPinCodeChanged,
                                    label = { Text(stringResource(R.string.sync_pin_code)) },
                                    modifier = Modifier.fillMaxWidth(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                    singleLine = true
                                )
                            }
                        }
                    }
                }

                // ── Advanced Options ─────────────────────────
                if (matchesQuery(q, advancedLabels)) item {
                    SettingsSection(title = stringResource(R.string.setting_section_advanced), icon = Icons.Rounded.Terminal) {
                        OutlinedTextField(
                            value = uiState.virusTotalApiKey,
                            onValueChange = onVirusTotalKeyChanged,
                            label = { Text(stringResource(R.string.setting_vt_api_key_title)) },
                            modifier = Modifier.fillMaxWidth().padding(16.dp),
                            leadingIcon = { Icon(Icons.Rounded.Key, null, tint = MaterialTheme.colorScheme.primary) },
                            placeholder = { Text(stringResource(R.string.setting_vt_api_key_placeholder)) },
                            singleLine = true,
                        )
                    }
                }

                // ── About Section ────────────────────────────
                if (matchesQuery(q, aboutLabels)) item {
                    SettingsSection(title = stringResource(R.string.setting_section_about), icon = Icons.Rounded.Info) {
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.setting_section_about)) },
                            supportingContent = { Text("v${uiState.appVersion}") },
                            leadingContent = { Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.setting.about.AboutActivity::class.java))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                        ListItem(
                            headlineContent = { Text(stringResource(R.string.setting_diagnostics_title)) },
                            leadingContent = { Icon(Icons.Rounded.BugReport, null, tint = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.clickable {
                                context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.setting.diagnostics.DiagnosticsActivity::class.java))
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                        )
                    }
                }

                if (q.isNotBlank() && !anyVisible) item {
                    EmptyStateView(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.setting_search_no_results),
                        subtitle = stringResource(R.string.setting_search_no_results_sub, q),
                        modifier = Modifier
                            .fillParentMaxWidth()
                            .padding(top = 48.dp, start = 32.dp, end = 32.dp),
                    )
                }
            } // end of LazyColumn
                } // end of else
            } // end of Crossfade
        } // end of Column
    } // end of Scaffold
} // end of SettingUi

/** True when [query] is blank (everything passes) or any [haystacks] entry contains it. */
private fun matchesQuery(query: String, haystacks: List<String>): Boolean =
    query.isBlank() || haystacks.any { it.contains(query, ignoreCase = true) }

/**
 * Renders [content] only when the search [query] is blank or matches [label] / any of the
 * extra space-joined [keywords]. Lets individual rows hide while their section stays
 * visible (section-level gates decide whether the section appears at all).
 */
@Composable
private fun SearchableItem(
    query: String,
    label: String,
    keywords: String = "",
    content: @Composable () -> Unit,
) {
    if (query.isBlank() ||
        label.contains(query, ignoreCase = true) ||
        keywords.contains(query, ignoreCase = true)
    ) {
        content()
    }
}

@Composable
private fun OptionGroupHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
    )
}

@Composable
private fun OptionSwitch(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = { Text(title, style = MaterialTheme.typography.bodyMedium) },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = { Switch(checked = checked, onCheckedChange = onCheckedChange) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable { onCheckedChange(!checked) },
    )
}

@Composable
private fun SwitchPreference(
    title: String,
    subtitle: String? = null,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    ListItem(
        headlineContent = {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
            )
        },
        supportingContent = subtitle?.let {
            {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                )
            }
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        },
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
    )
}

@Composable
private fun InstallSourceItem(
    title: String,
    subtitle: String,
    enabled: Boolean,
    installerPackageName: String,
    onToggle: (Boolean) -> Unit,
    onInstallerChange: (String) -> Unit,
) {
    Column {
        OptionSwitch(
            title = title,
            subtitle = subtitle,
            checked = enabled,
            onCheckedChange = onToggle,
        )
        if (enabled) {
            var showDialog by remember { mutableStateOf(false) }
            ListItem(
                headlineContent = { Text("Installer Package", style = MaterialTheme.typography.bodyMedium) },
                supportingContent = { Text(installerPackageName, style = MaterialTheme.typography.bodySmall) },
                leadingContent = { Spacer(Modifier.width(32.dp)) },
                modifier = Modifier.clickable { showDialog = true },
                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
            )

            if (showDialog) {
                PackageNamePickerDialog(
                    initialValue = installerPackageName,
                    onDismiss = { showDialog = false },
                    onConfirm = { newPkg ->
                        onInstallerChange(newPkg)
                        showDialog = false
                    }
                )
            }
        }
    }
}

/**
 * Picker for the global install backend.
 *
 * Root option disappears when the build has no libsu (store flavor).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallModeSelector(
    currentMode: InstallMode,
    shizukuState: ShizukuState,
    rootSupported: Boolean,
    rootState: RootState,
    onModeChange: (InstallMode) -> Unit,
) {
    val options: List<InstallMode> = remember(rootSupported) {
        if (rootSupported) listOf(InstallMode.DEFAULT, InstallMode.SHIZUKU, InstallMode.ROOT)
        else listOf(InstallMode.DEFAULT, InstallMode.SHIZUKU)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        Text(
            text = stringResource(R.string.setting_install_mode_title),
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 8.dp),
        )
        // Root stays tappable whenever libsu shipped (it's only in the row then) — tapping it
        // when su isn't ready fires the root request. We only DIM it (greyed label) to show
        // it isn't the ready engine, rather than disabling the click.
        // Not dimmed when Root is the active engine — a fresh probe reads su as UNKNOWN
        // (libsu confirms READY only after a shell attempt), which otherwise left Root greyed
        // even after it had been granted and selected.
        val rootDimmed = rootState != RootState.READY && currentMode != InstallMode.ROOT
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, mode ->
                val dim = mode == InstallMode.ROOT && rootDimmed
                SegmentedButton(
                    selected = mode == currentMode,
                    onClick = { if (mode != currentMode) onModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                    enabled = true,
                    label = {
                        Text(
                            text = when (mode) {
                                InstallMode.DEFAULT -> stringResource(R.string.setting_install_mode_default)
                                InstallMode.SHIZUKU -> stringResource(R.string.setting_install_mode_shizuku)
                                InstallMode.ROOT -> stringResource(R.string.setting_install_mode_root)
                            },
                            color = if (dim)
                                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                            else
                                androidx.compose.ui.graphics.Color.Unspecified,
                        )
                    },
                )
            }
        }
        val statusText = when (currentMode) {
            InstallMode.DEFAULT -> stringResource(R.string.setting_install_mode_default_sub)
            InstallMode.SHIZUKU -> when (shizukuState) {
                ShizukuState.NOT_INSTALLED -> stringResource(R.string.setting_shizuku_not_installed)
                ShizukuState.NOT_RUNNING -> stringResource(R.string.setting_shizuku_not_running)
                ShizukuState.UNSUPPORTED -> stringResource(R.string.setting_shizuku_unsupported)
                ShizukuState.NO_PERMISSION -> stringResource(R.string.setting_shizuku_no_permission)
                ShizukuState.READY -> stringResource(R.string.setting_shizuku_ready)
            }
            InstallMode.ROOT -> when (rootState) {
                RootState.UNAVAILABLE -> "Unavailable"
                RootState.UNKNOWN -> "Checking..."
                RootState.DENIED -> "Denied"
                RootState.READY -> "Ready"
                else -> "Not Rooted"
            }
        }
        Text(
            text = statusText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}
