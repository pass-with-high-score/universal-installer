package app.pwhs.universalinstaller.presentation.setting

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.datastore.preferences.core.Preferences
import app.pwhs.universalinstaller.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.LanguageScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel

@Destination<RootGraph>
@Composable
fun SettingScreen(
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SettingUi(
        modifier = modifier,
        uiState = uiState,
        onThemeChanged = viewModel::setThemeMode,
        onShizukuChanged = viewModel::setUseShizuku,
        onVirusTotalKeyChanged = viewModel::setVirusTotalApiKey,
        onShizukuOptionChanged = viewModel::setShizukuOption,
        onShizukuInstallerChanged = viewModel::setShizukuInstallerPackageName,
        onDeleteApkChanged = viewModel::setDeleteApkAfterInstall,
        onLanguageClick = { navigator.navigate(LanguageScreenDestination) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingUi(
    modifier: Modifier = Modifier,
    uiState: SettingUiState = SettingUiState(),
    onThemeChanged: (ThemeMode) -> Unit = {},
    onShizukuChanged: (Boolean) -> Unit = {},
    onVirusTotalKeyChanged: (String) -> Unit = {},
    onShizukuOptionChanged: (Preferences.Key<Boolean>, Boolean) -> Unit = { _, _ -> },
    onShizukuInstallerChanged: (String) -> Unit = {},
    onDeleteApkChanged: (Boolean) -> Unit = {},
    onLanguageClick: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
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
                    val shizukuStatusColor = when (uiState.shizukuState) {
                        ShizukuState.READY -> MaterialTheme.colorScheme.primary
                        ShizukuState.NO_PERMISSION -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_shizuku_backend), style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = shizukuStatusText,
                                style = MaterialTheme.typography.bodySmall,
                                color = shizukuStatusColor,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.AdminPanelSettings,
                                contentDescription = null,
                                tint = if (uiState.shizukuState == ShizukuState.READY)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.useShizuku,
                                onCheckedChange = onShizukuChanged,
                                enabled = uiState.shizukuAvailable,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_delete_apk_title), style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.setting_delete_apk_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.CleaningServices,
                                contentDescription = null,
                                tint = if (uiState.deleteApkAfterInstall)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        trailingContent = {
                            Switch(
                                checked = uiState.deleteApkAfterInstall,
                                onCheckedChange = onDeleteApkChanged,
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            // ── Shizuku Options Section (visible when Shizuku enabled) ──
            if (uiState.useShizuku) {
                item {
                    SettingsSection(title = stringResource(R.string.setting_section_shizuku_options), icon = Icons.Rounded.AdminPanelSettings) {
                        ShizukuOptionItem(
                            title = stringResource(R.string.setting_shizuku_replace),
                            subtitle = stringResource(R.string.setting_shizuku_replace_sub),
                            checked = uiState.shizukuOptions.replaceExisting,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_REPLACE_EXISTING, it) },
                        )
                        ShizukuOptionItem(
                            title = stringResource(R.string.setting_shizuku_downgrade),
                            subtitle = stringResource(R.string.setting_shizuku_downgrade_sub),
                            checked = uiState.shizukuOptions.requestDowngrade,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE, it) },
                        )
                        ShizukuOptionItem(
                            title = stringResource(R.string.setting_shizuku_grant_permissions),
                            subtitle = stringResource(R.string.setting_shizuku_grant_permissions_sub),
                            checked = uiState.shizukuOptions.grantAllPermissions,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS, it) },
                        )
                        ShizukuOptionItem(
                            title = stringResource(R.string.setting_shizuku_allow_test),
                            subtitle = stringResource(R.string.setting_shizuku_allow_test_sub),
                            checked = uiState.shizukuOptions.allowTest,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_ALLOW_TEST, it) },
                        )
                        ShizukuOptionItem(
                            title = stringResource(R.string.setting_shizuku_bypass_sdk),
                            subtitle = stringResource(R.string.setting_shizuku_bypass_sdk_sub),
                            checked = uiState.shizukuOptions.bypassLowTargetSdk,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK, it) },
                        )
                        ShizukuOptionItem(
                            title = stringResource(R.string.setting_shizuku_all_users),
                            subtitle = stringResource(R.string.setting_shizuku_all_users_sub),
                            checked = uiState.shizukuOptions.allUsers,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_ALL_USERS, it) },
                        )
                        ShizukuInstallSourceItem(
                            enabled = uiState.shizukuOptions.setInstallSource,
                            installerPackageName = uiState.shizukuOptions.installerPackageName,
                            onToggle = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE, it) },
                            onInstallerChange = onShizukuInstallerChanged,
                        )
                    }
                }
            }

            // ── Security Section ─────────────────────────
            item {
                SettingsSection(title = stringResource(R.string.setting_section_security), icon = Icons.Rounded.Security) {
                    var apiKeyInput by remember(uiState.virusTotalApiKey) {
                        mutableStateOf(uiState.virusTotalApiKey)
                    }
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_vt_key_title), style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = stringResource(R.string.setting_vt_key_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                OutlinedTextField(
                                    value = apiKeyInput,
                                    onValueChange = {
                                        apiKeyInput = it
                                        onVirusTotalKeyChanged(it)
                                    },
                                    placeholder = { Text(stringResource(R.string.setting_vt_key_placeholder)) },
                                    singleLine = true,
                                    visualTransformation = PasswordVisualTransformation(),
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = MaterialTheme.shapes.medium,
                                )
                            }
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            // ── Appearance Section ───────────────────────
            item {
                SettingsSection(title = stringResource(R.string.setting_section_appearance), icon = Icons.Rounded.Palette) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_theme_title), style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = stringResource(R.string.setting_theme_subtitle),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    ThemeMode.entries.forEachIndexed { index, mode ->
                                        SegmentedButton(
                                            selected = uiState.themeMode == mode,
                                            onClick = { onThemeChanged(mode) },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = ThemeMode.entries.size,
                                            ),
                                            icon = {
                                                Icon(
                                                    imageVector = when (mode) {
                                                        ThemeMode.System -> Icons.Rounded.SettingsApplications
                                                        ThemeMode.Light -> Icons.Rounded.LightMode
                                                        ThemeMode.Dark -> Icons.Rounded.DarkMode
                                                    },
                                                    contentDescription = null,
                                                    modifier = Modifier.size(18.dp),
                                                )
                                            }
                                        ) {
                                            Text(mode.label, style = MaterialTheme.typography.labelMedium)
                                        }
                                    }
                                }
                            }
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
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
                        trailingContent = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        modifier = Modifier.clickable(onClick = onLanguageClick),
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            // ── About Section ────────────────────────────
            item {
                val uriHandler = LocalUriHandler.current
                SettingsSection(title = stringResource(R.string.setting_section_about), icon = Icons.Rounded.Info) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_about_app_name), style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.setting_version, uiState.appVersion.ifBlank { "1.0" }),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Code,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_github_title), style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.setting_github_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_github),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/pass-with-high-score/universal-installer")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_telegram_title), style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.setting_telegram_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                painter = painterResource(R.drawable.ic_telegram),
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://t.me/blockads_android")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.setting_sponsor_title), style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = stringResource(R.string.setting_sponsor_subtitle),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        },
                        leadingContent = {
                            Icon(
                                imageVector = Icons.Rounded.Favorite,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(24.dp),
                            )
                        },
                        modifier = Modifier.clickable {
                            uriHandler.openUri("https://github.com/sponsors/pass-with-high-score")
                        },
                        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun SettingsSection(
    title: String,
    icon: ImageVector,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(vertical = 8.dp)) {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            content()
        }
    }
}

@Composable
private fun ShizukuOptionItem(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(title, style = MaterialTheme.typography.bodyMedium)
        },
        supportingContent = {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingContent = {
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

private data class InstallerPreset(val packageName: String, val label: String)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ShizukuInstallSourceItem(
    enabled: Boolean,
    installerPackageName: String,
    onToggle: (Boolean) -> Unit,
    onInstallerChange: (String) -> Unit,
) {
    val presets = listOf(
        InstallerPreset("com.android.vending", stringResource(R.string.setting_shizuku_installer_preset_play)),
        InstallerPreset("com.aurora.store", stringResource(R.string.setting_shizuku_installer_preset_aurora)),
        InstallerPreset("org.fdroid.fdroid", stringResource(R.string.setting_shizuku_installer_preset_fdroid)),
        InstallerPreset("com.amazon.venezia", stringResource(R.string.setting_shizuku_installer_preset_amazon)),
        InstallerPreset("com.sec.android.app.samsungapps", stringResource(R.string.setting_shizuku_installer_preset_samsung)),
        InstallerPreset("com.huawei.appmarket", stringResource(R.string.setting_shizuku_installer_preset_huawei)),
        InstallerPreset("com.xiaomi.market", stringResource(R.string.setting_shizuku_installer_preset_xiaomi)),
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        ListItem(
            headlineContent = {
                Text(
                    stringResource(R.string.setting_shizuku_set_source),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            supportingContent = {
                Text(
                    text = stringResource(R.string.setting_shizuku_set_source_sub),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingContent = {
                Switch(checked = enabled, onCheckedChange = onToggle)
            },
            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        )

        if (enabled) {
            var expanded by remember { mutableStateOf(false) }
            var text by remember(installerPackageName) { mutableStateOf(installerPackageName) }

            ExposedDropdownMenuBox(
                expanded = expanded,
                onExpandedChange = { expanded = !expanded },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            ) {
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        onInstallerChange(it)
                    },
                    modifier = Modifier
                        .menuAnchor(androidx.compose.material3.ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true)
                        .fillMaxWidth(),
                    singleLine = true,
                    label = { Text(stringResource(R.string.setting_shizuku_installer_label)) },
                    leadingIcon = {
                        Icon(Icons.Rounded.Badge, contentDescription = null)
                    },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                    },
                )
                ExposedDropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                ) {
                    presets.forEach { preset ->
                        DropdownMenuItem(
                            text = {
                                Column {
                                    Text(preset.label, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = preset.packageName,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                text = preset.packageName
                                onInstallerChange(preset.packageName)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }
    }
}