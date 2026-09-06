package app.pwhs.tv.presentation.settings

import app.pwhs.tv.presentation.settings.components.AccentSwatch
import app.pwhs.tv.presentation.settings.components.SectionHeader
import app.pwhs.tv.presentation.settings.components.SettingsCard
import app.pwhs.tv.presentation.settings.components.ThemeOptionCard
import app.pwhs.tv.presentation.settings.components.TitleValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.pwhs.tv.util.LocaleHelper
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.foundation.border
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.pwhs.tv.R
import app.pwhs.tv.ui.components.QrCode

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Shield
import androidx.tv.material3.Icon
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.core.util.RootShell
import app.pwhs.tv.ui.theme.accentSwatchColor
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private const val REPO_URL = "https://github.com/pass-with-high-score/universal-installer"


/**
 * Settings/About for the TV app. Modernized with premium Surface effects and Theme selection.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var showLanguageScreen by remember { mutableStateOf(false) }
    var showInstallOptions by remember { mutableStateOf(false) }

    if (showLanguageScreen) {
        LanguageScreen(onBack = { showLanguageScreen = false })
        return
    }
    if (showInstallOptions) {
        InstallOptionsScreen(onBack = { showInstallOptions = false })
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }

    val themeModeName by context.dataStore.data
        .map { it[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name }
        .collectAsState(initial = ThemeMode.System.name)

    val themePresetName by context.dataStore.data
        .map { it[stringPreferencesKey("theme_preset")] ?: AppThemePreset.Orange.name }
        .collectAsState(initial = AppThemePreset.Orange.name)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp),
        contentPadding = PaddingValues(top = 32.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(
                stringResource(R.string.tv_settings_title),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        // ── Appearance ────────────────────────────────────────────────────────
        item { SectionHeader(stringResource(R.string.tv_settings_section_appearance), Icons.Default.Palette) }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                ThemeOptionCard(
                    selected = themeModeName == ThemeMode.System.name,
                    label = "System",
                    icon = Icons.Default.BrightnessAuto,
                    onClick = {
                        scope.launch {
                            context.dataStore.edit { it[stringPreferencesKey("theme_mode")] = ThemeMode.System.name }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionCard(
                    selected = themeModeName == ThemeMode.Light.name,
                    label = "Light",
                    icon = Icons.Default.Brightness7,
                    onClick = {
                        scope.launch {
                            context.dataStore.edit { it[stringPreferencesKey("theme_mode")] = ThemeMode.Light.name }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
                ThemeOptionCard(
                    selected = themeModeName == ThemeMode.Dark.name,
                    label = "Dark",
                    icon = Icons.Default.Brightness4,
                    onClick = {
                        scope.launch {
                            context.dataStore.edit { it[stringPreferencesKey("theme_mode")] = ThemeMode.Dark.name }
                        }
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        // Accent color presets (parity with mobile; persisted to the shared theme_preset key).
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                AppThemePreset.entries.forEach { preset ->
                    AccentSwatch(
                        color = accentSwatchColor(preset),
                        selected = themePresetName == preset.name,
                        onClick = {
                            scope.launch {
                                context.dataStore.edit { it[stringPreferencesKey("theme_preset")] = preset.name }
                            }
                        },
                    )
                }
            }
        }

        // ── Language ────────────────────────────────────────────────────────
        item { SectionHeader(stringResource(R.string.tv_settings_section_language), Icons.Default.Language) }
        item {
            val currentLanguage = remember {
                val tag = LocaleHelper.getStoredLanguage(context)
                if (tag.isEmpty()) "System Default" else tag.uppercase()
            }
            SettingsCard(onClick = { showLanguageScreen = true }) {
                TitleValue(
                    "Language",
                    currentLanguage
                )
            }
        }

        // ── Installation & Security ──────────────────────────────────────────
        item { SectionHeader(stringResource(R.string.tv_settings_section_install), Icons.Default.Shield) }
        item {
            SettingsCard(onClick = {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}"),
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) {
                TitleValue(
                    stringResource(R.string.tv_settings_allow_installs_title),
                    stringResource(R.string.tv_settings_allow_installs_subtitle)
                )
            }
        }
        item {
            // Probing root here surfaces the device's grant prompt once; the result is cached.
            val rootAvailable by produceState<Boolean?>(initialValue = null) {
                value = RootShell.isAvailable()
            }
            val silentEnabled by context.dataStore.data
                .map { it[SharedPrefsKeys.ROOT_SILENT_INSTALL] ?: true }
                .collectAsState(initial = true)
            SettingsCard(enabled = rootAvailable == true, onClick = {
                scope.launch {
                    context.dataStore.edit { it[SharedPrefsKeys.ROOT_SILENT_INSTALL] = !silentEnabled }
                }
            }) {
                TitleValue(
                    stringResource(R.string.tv_settings_silent_install_title),
                    if (silentEnabled) stringResource(R.string.tv_settings_silent_install_on)
                    else stringResource(R.string.tv_settings_silent_install_off)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when (rootAvailable) {
                        null -> stringResource(R.string.tv_settings_checking_root)
                        true -> stringResource(R.string.tv_settings_root_available)
                        else -> stringResource(R.string.tv_settings_root_unavailable)
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (rootAvailable == false) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
            }
        }

        item { ShizukuSetting() }
        item {
            SettingsCard(onClick = { showInstallOptions = true }) {
                TitleValue("Install options", "Configure Shizuku and Root flags")
            }
        }

        // ── Device Info ──────────────────────────────────────────────────────
        item { SectionHeader(stringResource(R.string.tv_settings_section_device), Icons.Default.Devices) }
        item {
            SettingsCard(onClick = {}) {
                TitleValue(
                    stringResource(R.string.tv_settings_device_title),
                    stringResource(
                        R.string.tv_settings_device_info,
                        Build.MANUFACTURER,
                        Build.MODEL,
                        Build.VERSION.RELEASE,
                        Build.VERSION.SDK_INT
                    ),
                )
            }
        }

        // ── Project ──────────────────────────────────────────────────────────
        item { SectionHeader(stringResource(R.string.tv_settings_section_project), Icons.Default.Info) }
        item {
            SettingsCard(onClick = {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White)
                            .padding(8.dp)
                    ) {
                        QrCode(data = REPO_URL, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.width(24.dp))
                    Column {
                        Text(
                            stringResource(R.string.tv_settings_open_source_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            REPO_URL,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            stringResource(R.string.tv_settings_scan_to_view),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // ── About ────────────────────────────────────────────────────────────
        item { SectionHeader(stringResource(R.string.tv_settings_section_about), Icons.Default.Info) }
        item {
            SettingsCard(onClick = {}) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Image(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier
                            .size(80.dp)
                            .clip(RoundedCornerShape(16.dp)),
                    )
                    Spacer(Modifier.width(24.dp))
                    Column {
                        Text(
                            stringResource(R.string.tv_settings_app_name),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            stringResource(R.string.tv_settings_app_tagline),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        if (version.isNotBlank()) {
                            Text(
                                stringResource(R.string.tv_settings_version, version),
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}



