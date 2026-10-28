package app.pwhs.tv.presentation.settings.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.Brightness7
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Language
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.tv.R
import app.pwhs.tv.ui.components.QrCode
import app.pwhs.tv.ui.theme.accentSwatchColor
import kotlinx.coroutines.launch


@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun GeneralSettingsPane(
    themeModeName: String,
    themePresetName: String,
    currentLanguage: String,
    onOpenLanguage: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            stringResource(R.string.tv_settings_section_appearance),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
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

        Text(
            "Accent Color",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
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

        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.tv_settings_section_language),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        TvSettingRow(
            title = "App Language",
            subtitle = currentLanguage,
            icon = Icons.Default.Language,
            onClick = onOpenLanguage,
            trailingContent = {
                Icon(
                    Icons.Default.ChevronRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        )
    }
}
