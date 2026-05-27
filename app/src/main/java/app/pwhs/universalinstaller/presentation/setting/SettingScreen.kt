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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.RocketLaunch
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.SettingsSection
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import androidx.datastore.preferences.core.Preferences
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    SettingUi(
        modifier = modifier,
        uiState = uiState,
        onShizukuChanged = viewModel::setUseShizuku,
        onVirusTotalKeyChanged = viewModel::setVirusTotalApiKey,
        onShizukuOptionChanged = viewModel::setShizukuOption,
        onShizukuInstallerChanged = viewModel::setShizukuInstallerPackageName,
        onDeleteApkChanged = viewModel::setDeleteApkAfterInstall,
        onAutoOpenAfterInstallChanged = viewModel::setAutoOpenAfterInstall,
        onLanguageClick = {
            context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.setting.language.LanguageActivity::class.java))
        },
        onRootChanged = viewModel::setUseRoot,
        onRootRetry = viewModel::retryRoot,
        onRootOptionChanged = viewModel::setRootOption,
        onRootInstallerChanged = viewModel::setRootInstallerPackageName,
        onSyncRequirePinChanged = viewModel::setSyncRequirePin,
        onSyncPinCodeChanged = viewModel::setSyncPinCode,
        onSyncServerPortChanged = viewModel::setSyncServerPort,
        onBiometricLockInstallChanged = viewModel::setBiometricLockInstall,
        onBiometricLockUninstallChanged = viewModel::setBiometricLockUninstall,
        onAutoConfirmExternalInstallChanged = viewModel::setAutoConfirmExternalInstall,
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
    onShizukuChanged: (Boolean) -> Unit = {},
    onVirusTotalKeyChanged: (String) -> Unit = {},
    onShizukuOptionChanged: (Preferences.Key<Boolean>, Boolean) -> Unit = { _, _ -> },
    onShizukuInstallerChanged: (String) -> Unit = {},
    onDeleteApkChanged: (Boolean) -> Unit = {},
    onAutoOpenAfterInstallChanged: (Boolean) -> Unit = {},
    onLanguageClick: () -> Unit = {},
    onRootChanged: (Boolean) -> Unit = {},
    onRootRetry: () -> Unit = {},
    onRootOptionChanged: (Preferences.Key<Boolean>, Boolean) -> Unit = { _, _ -> },
    onRootInstallerChanged: (String) -> Unit = {},
    onSyncRequirePinChanged: (Boolean) -> Unit = {},
    onSyncPinCodeChanged: (String) -> Unit = {},
    onSyncServerPortChanged: (String) -> Unit = {},
    onBiometricLockInstallChanged: (Boolean) -> Unit = {},
    onBiometricLockUninstallChanged: (Boolean) -> Unit = {},
    onAutoConfirmExternalInstallChanged: (Boolean) -> Unit = {},
    onProfilesClick: () -> Unit = {},
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

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
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + navBarPadding + 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Installation Section ─────────────────────
            item {
                SettingsSection(title = stringResource(R.string.setting_section_installation), icon = Icons.Rounded.SettingsApplications) {
                    val shizukuStatusText = when (uiState.shizukuState) {
                        ShizukuState.NOT_INSTALLED -> stringResource(R.string.setting_shizuku_not_installed)
                        ShizukuState.NOT_RUNNING -> stringResource(R.string.setting_shizuku_not_running)
                        ShizukuState.UNSUPPORTED -> stringResource(R.string.setting_shizuku_unsupported)
                        ShizukuState.NO_PERMISSION -> stringResource(R.string.setting_shizuku_no_permission)
                        ShizukuState.READY -> stringResource(R.string.setting_shizuku_ready)
                    }

                    SwitchPreference(
                        title = stringResource(R.string.setting_shizuku_backend),
                        subtitle = shizukuStatusText,
                        checked = uiState.useShizuku,
                        onCheckedChange = onShizukuChanged,
                    )
                    
                    if (uiState.rootSupported) {
                        val rootStatusText = when (uiState.rootState) {
                            RootState.UNAVAILABLE -> "Unavailable"
                            RootState.UNKNOWN -> "Checking..."
                            RootState.DENIED -> "Denied"
                            RootState.READY -> "Ready"
                            else -> "Not Rooted"
                        }
                        SwitchPreference(
                            title = "Root Mode",
                            subtitle = rootStatusText,
                            checked = uiState.useRoot,
                            onCheckedChange = onRootChanged,
                        )
                        if (uiState.rootState == RootState.DENIED) {
                            ListItem(
                                headlineContent = { Text("Retry Root Probe") },
                                leadingContent = { Icon(Icons.Rounded.RocketLaunch, null) },
                                modifier = Modifier.clickable { onRootRetry() },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }

                    SwitchPreference(
                        title = stringResource(R.string.setting_delete_apk_title),
                        checked = uiState.deleteApkAfterInstall,
                        onCheckedChange = onDeleteApkChanged,
                    )
                    SwitchPreference(
                        title = stringResource(R.string.setting_auto_open_title),
                        subtitle = stringResource(R.string.setting_auto_open_subtitle),
                        checked = uiState.autoOpenAfterInstall,
                        onCheckedChange = onAutoOpenAfterInstallChanged,
                    )
                    SwitchPreference(
                        title = stringResource(R.string.setting_auto_confirm_title),
                        subtitle = stringResource(R.string.setting_auto_confirm_subtitle),
                        checked = uiState.autoConfirmExternalInstall,
                        onCheckedChange = onAutoConfirmExternalInstallChanged,
                    )
                }
            }

            // ── Profiles Section ─────────────────────────
            item {
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
            item {
                SettingsSection(title = "Interface", icon = Icons.Rounded.Palette) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.theme_screen_title)) },
                        leadingContent = { Icon(Icons.Rounded.Palette, null) },
                        modifier = Modifier.clickable {
                            context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.setting.theme.ThemeActivity::class.java))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp)
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

            // ── Security Section ─────────────────────────
            item {
                SettingsSection(title = "Security", icon = Icons.Rounded.Fingerprint) {
                    SwitchPreference(
                        title = "Lock Installations",
                        subtitle = "Require biometric to confirm install",
                        checked = uiState.biometricLockInstall,
                        onCheckedChange = onBiometricLockInstallChanged,
                        enabled = uiState.biometricEnrolmentAvailable
                    )
                    SwitchPreference(
                        title = "Lock Uninstalls",
                        subtitle = "Require biometric to confirm uninstall",
                        checked = uiState.biometricLockUninstall,
                        onCheckedChange = onBiometricLockUninstallChanged,
                        enabled = uiState.biometricEnrolmentAvailable
                    )
                    if (!uiState.biometricEnrolmentAvailable) {
                        Text(
                            text = "No biometric or device lock set up on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // ── Sync Section ─────────────────────────────
            item {
                SettingsSection(title = "Sync", icon = Icons.Rounded.WifiTethering) {
                    ListItem(
                        headlineContent = { Text("Sync Control Panel") },
                        leadingContent = { Icon(Icons.Rounded.WifiTethering, null) },
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
                            label = { Text("Port") },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            singleLine = true
                        )
                        SwitchPreference(
                            title = "Require PIN",
                            checked = uiState.syncOptions.requirePin,
                            onCheckedChange = onSyncRequirePinChanged
                        )
                        if (uiState.syncOptions.requirePin) {
                            OutlinedTextField(
                                value = uiState.syncOptions.pinCode,
                                onValueChange = onSyncPinCodeChanged,
                                label = { Text("PIN Code") },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // ── Advanced Options ─────────────────────────
            item {
                SettingsSection(title = "Advanced", icon = Icons.Rounded.Terminal) {
                    OutlinedTextField(
                        value = uiState.virusTotalApiKey,
                        onValueChange = onVirusTotalKeyChanged,
                        label = { Text("VirusTotal API Key") },
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        leadingIcon = { Icon(Icons.Rounded.Key, null) },
                        placeholder = { Text("Paste API key here...") },
                        singleLine = true,
                    )
                }
            }

            // ── About Section ────────────────────────────
            item {
                SettingsSection(title = "About", icon = Icons.Rounded.Info) {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.setting_section_about)) },
                        supportingContent = { Text("v${uiState.appVersion}") },
                        leadingContent = { Icon(Icons.Rounded.Info, null) },
                        modifier = Modifier.clickable {
                            context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.setting.about.AboutActivity::class.java))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                    ListItem(
                        headlineContent = { Text("Diagnostics") },
                        leadingContent = { Icon(Icons.Rounded.BugReport, null) },
                        modifier = Modifier.clickable {
                            context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.setting.diagnostics.DiagnosticsActivity::class.java))
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                    )
                }
            }
        }
    }
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
                color = if (enabled) Color.Unspecified else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
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
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(enabled = enabled) { onCheckedChange(!checked) }
    )
}
