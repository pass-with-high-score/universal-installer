package app.pwhs.tv.presentation.settings

import android.os.Build
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.tv.R
import app.pwhs.tv.presentation.settings.components.AboutSettingsPane
import app.pwhs.tv.presentation.settings.components.GeneralSettingsPane
import app.pwhs.tv.presentation.settings.components.InstallSettingsPane
import app.pwhs.tv.presentation.settings.components.SettingsCategorySidebar
import app.pwhs.tv.util.LocaleHelper
import kotlinx.coroutines.flow.map

enum class SettingsCategory(
    val title: String,
    val description: String,
    val icon: ImageVector
) {
    Install(
        title = "Install & Security",
        description = "Root, Shizuku, permissions",
        icon = Icons.Default.Security
    ),
    Appearance(
        title = "Appearance",
        description = "Theme, accent color, language",
        icon = Icons.Default.Palette
    ),
    About(
        title = "About & Device",
        description = "Version, device info, GitHub",
        icon = Icons.Default.Info
    )
}

/**
 * Modern 2-Pane Settings screen for Android TV.
 * Left: Category Sidebar (32% width)
 * Right: Category Content Pane (68% width)
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier) {
    var showLanguageScreen by remember { mutableStateOf(false) }
    var showInstallOptions by remember { mutableStateOf(false) }
    var selectedCategory by remember { mutableStateOf(SettingsCategory.Install) }

    if (showLanguageScreen) {
        LanguageScreen(onBack = { showLanguageScreen = false })
        return
    }
    if (showInstallOptions) {
        InstallOptionsScreen(onBack = { showInstallOptions = false })
        return
    }

    val context = LocalContext.current
    val version = remember {
        runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull().orEmpty()
    }
    val deviceInfo = remember {
        "${Build.MANUFACTURER} ${Build.MODEL} (Android ${Build.VERSION.RELEASE}, API ${Build.VERSION.SDK_INT})"
    }

    val themeModeName by context.dataStore.data
        .map { it[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name }
        .collectAsState(initial = ThemeMode.System.name)

    val themePresetName by context.dataStore.data
        .map { it[stringPreferencesKey("theme_preset")] ?: AppThemePreset.Orange.name }
        .collectAsState(initial = AppThemePreset.Orange.name)

    val currentLanguage = remember {
        val tag = LocaleHelper.getStoredLanguage(context)
        if (tag.isEmpty()) "System Default" else tag.uppercase()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(start = 36.dp, end = 36.dp, top = 24.dp, bottom = 20.dp)
    ) {
        Text(
            stringResource(R.string.tv_settings_title),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            // Left Sidebar: Categories
            SettingsCategorySidebar(
                categories = SettingsCategory.entries,
                selectedCategory = selectedCategory,
                onSelectCategory = { selectedCategory = it },
                modifier = Modifier.fillMaxWidth(0.32f)
            )

            // Right Pane: Active Category Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)
                ) {
                    item {
                        when (selectedCategory) {
                            SettingsCategory.Install -> {
                                InstallSettingsPane(
                                    onOpenInstallOptions = { showInstallOptions = true }
                                )
                            }
                            SettingsCategory.Appearance -> {
                                GeneralSettingsPane(
                                    themeModeName = themeModeName,
                                    themePresetName = themePresetName,
                                    currentLanguage = currentLanguage,
                                    onOpenLanguage = { showLanguageScreen = true }
                                )
                            }
                            SettingsCategory.About -> {
                                AboutSettingsPane(
                                    version = version,
                                    deviceInfo = deviceInfo
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
