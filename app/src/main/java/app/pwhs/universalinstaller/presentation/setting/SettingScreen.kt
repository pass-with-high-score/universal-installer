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
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.datastore.preferences.core.Preferences
import app.pwhs.universalinstaller.R
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import org.koin.androidx.compose.koinViewModel

@Destination<RootGraph>
@Composable
fun SettingScreen(modifier: Modifier = Modifier, viewModel: SettingViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    SettingUi(
        modifier = modifier,
        uiState = uiState,
        onThemeChanged = viewModel::setThemeMode,
        onShizukuChanged = viewModel::setUseShizuku,
        onVirusTotalKeyChanged = viewModel::setVirusTotalApiKey,
        onShizukuOptionChanged = viewModel::setShizukuOption,
        onDeleteApkChanged = viewModel::setDeleteApkAfterInstall,
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
    onDeleteApkChanged: (Boolean) -> Unit = {},
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
                        text = "Settings",
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
                SettingsSection(title = "Installation", icon = Icons.Rounded.SettingsApplications) {
                    val shizukuStatusText = when (uiState.shizukuState) {
                        ShizukuState.NOT_INSTALLED -> "Shizuku not installed"
                        ShizukuState.NOT_RUNNING -> "Shizuku installed but not running"
                        ShizukuState.UNSUPPORTED -> "Shizuku version too old (pre-v11)"
                        ShizukuState.NO_PERMISSION -> "Tap to grant Shizuku permission"
                        ShizukuState.READY -> "Silent install without prompts"
                    }
                    val shizukuStatusColor = when (uiState.shizukuState) {
                        ShizukuState.READY -> MaterialTheme.colorScheme.primary
                        ShizukuState.NO_PERMISSION -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    ListItem(
                        headlineContent = {
                            Text("Shizuku Backend", style = MaterialTheme.typography.bodyLarge)
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
                            Text("Delete APK after install", style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = "Automatically delete the source file after successful installation",
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
                    SettingsSection(title = "Shizuku Options", icon = Icons.Rounded.AdminPanelSettings) {
                        ShizukuOptionItem(
                            title = "Replace existing",
                            subtitle = "Replace already installed packages",
                            checked = uiState.shizukuOptions.replaceExisting,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_REPLACE_EXISTING, it) },
                        )
                        ShizukuOptionItem(
                            title = "Allow downgrade",
                            subtitle = "Allow installing older version over newer",
                            checked = uiState.shizukuOptions.requestDowngrade,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE, it) },
                        )
                        ShizukuOptionItem(
                            title = "Grant all permissions",
                            subtitle = "Auto-grant all requested permissions",
                            checked = uiState.shizukuOptions.grantAllPermissions,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS, it) },
                        )
                        ShizukuOptionItem(
                            title = "Allow test packages",
                            subtitle = "Allow installing debug/test APKs",
                            checked = uiState.shizukuOptions.allowTest,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_ALLOW_TEST, it) },
                        )
                        ShizukuOptionItem(
                            title = "Bypass low target SDK block",
                            subtitle = "Install apps targeting old SDK versions",
                            checked = uiState.shizukuOptions.bypassLowTargetSdk,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK, it) },
                        )
                        ShizukuOptionItem(
                            title = "Install for all users",
                            subtitle = "Install package for all device users",
                            checked = uiState.shizukuOptions.allUsers,
                            onCheckedChange = { onShizukuOptionChanged(PreferencesKeys.SHIZUKU_ALL_USERS, it) },
                        )
                    }
                }
            }

            // ── Security Section ─────────────────────────
            item {
                SettingsSection(title = "Security", icon = Icons.Rounded.Security) {
                    var apiKeyInput by remember(uiState.virusTotalApiKey) {
                        mutableStateOf(uiState.virusTotalApiKey)
                    }
                    ListItem(
                        headlineContent = {
                            Text("VirusTotal API Key", style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = "Enable malware scanning before install",
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
                                    placeholder = { Text("Enter API key…") },
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
                SettingsSection(title = "Appearance", icon = Icons.Rounded.Palette) {
                    ListItem(
                        headlineContent = {
                            Text("Theme", style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Column {
                                Text(
                                    text = "Choose your preferred theme",
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
                }
            }

            // ── About Section ────────────────────────────
            item {
                val uriHandler = LocalUriHandler.current
                SettingsSection(title = "About", icon = Icons.Rounded.Info) {
                    ListItem(
                        headlineContent = {
                            Text("Universal Installer", style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = "Version ${uiState.appVersion.ifBlank { "1.0" }}",
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
                            Text("GitHub", style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = "Source code & contributions",
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
                            Text("Telegram", style = MaterialTheme.typography.bodyLarge)
                        },
                        supportingContent = {
                            Text(
                                text = "Join our community",
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