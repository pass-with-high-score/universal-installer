package app.pwhs.universalinstaller.presentation.setting.theme

import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.core.domain.ThemeMode
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.presentation.adaptive.MaxWidthContainer
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.foundation.BorderStroke
import org.koin.androidx.compose.koinViewModel

@Composable
fun ThemeScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = koinViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    ThemeUi(
        modifier = modifier,
        themeMode = uiState.themeMode,
        dynamicColor = uiState.dynamicColor,
        amoledMode = uiState.amoledMode,
        themePreset = uiState.themePreset,
        onThemeChanged = viewModel::setThemeMode,
        onDynamicColorChanged = viewModel::setDynamicColor,
        onAmoledModeChanged = viewModel::setAmoledMode,
        onThemePresetChanged = viewModel::setThemePreset,
        onBack = {
            val a = context as? android.app.Activity
            a?.finish()
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeUi(
    modifier: Modifier = Modifier,
    themeMode: ThemeMode = ThemeMode.System,
    dynamicColor: Boolean = false,
    amoledMode: Boolean = false,
    themePreset: AppThemePreset = AppThemePreset.Orange,
    onThemeChanged: (ThemeMode) -> Unit = {},
    onDynamicColorChanged: (Boolean) -> Unit = {},
    onAmoledModeChanged: (Boolean) -> Unit = {},
    onThemePresetChanged: (AppThemePreset) -> Unit = {},
    onBack: () -> Unit = {}
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.theme_screen_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd)
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                )
            )
        }
    ) { innerPadding ->
        MaxWidthContainer(
            maxWidth = 680.dp,
            modifier = Modifier
                .padding(innerPadding)
                .navigationBarsPadding()
                .fillMaxSize(),
        ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                Text(
                    text = stringResource(R.string.theme_mode_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 8.dp)
                )
            }
            
            ThemeMode.entries.forEach { mode ->
                item {
                    val labelRes = when (mode) {
                        ThemeMode.System -> R.string.theme_mode_system
                        ThemeMode.Light -> R.string.theme_mode_light
                        ThemeMode.Dark -> R.string.theme_mode_dark
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onThemeChanged(mode) }
                            .padding(horizontal = 24.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = when (mode) {
                                ThemeMode.System -> Icons.Rounded.SettingsApplications
                                ThemeMode.Light -> Icons.Rounded.LightMode
                                ThemeMode.Dark -> Icons.Rounded.DarkMode
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(end = 16.dp).size(24.dp)
                        )
                        Text(
                            text = stringResource(labelRes),
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.weight(1f)
                        )
                        RadioButton(
                            selected = themeMode == mode,
                            onClick = { onThemeChanged(mode) }
                        )
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 16.dp))
            }

            item {
                val isAndroid12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                ListItem(
                    headlineContent = { Text(stringResource(R.string.theme_dynamic_color_title)) },
                    supportingContent = { Text(stringResource(R.string.theme_dynamic_color_subtitle)) },
                    trailingContent = {
                        Switch(
                            checked = dynamicColor,
                            onCheckedChange = onDynamicColorChanged,
                            enabled = isAndroid12Plus
                        )
                    },
                    modifier = Modifier.clickable(enabled = isAndroid12Plus) {
                        onDynamicColorChanged(!dynamicColor)
                    }
                )
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            item {
                Column(modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)) {
                    Text(
                        text = stringResource(R.string.theme_preset_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = if (dynamicColor) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f) else MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = stringResource(R.string.theme_preset_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = if (dynamicColor) 0.38f else 1.0f),
                        modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
                    )
                }
            }

            item {
                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(AppThemePreset.entries) { preset ->
                        ThemePresetCard(
                            preset = preset,
                            selected = themePreset == preset && !dynamicColor,
                            enabled = !dynamicColor,
                            onClick = { onThemePresetChanged(preset) }
                        )
                    }
                }
            }

            item {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
            }

            item {
                val isDarkEnabled = themeMode == ThemeMode.Dark || themeMode == ThemeMode.System
                ListItem(
                    headlineContent = { Text(stringResource(R.string.theme_amoled_mode_title)) },
                    supportingContent = { Text(stringResource(R.string.theme_amoled_mode_subtitle)) },
                    trailingContent = {
                        Switch(
                            checked = amoledMode,
                            onCheckedChange = onAmoledModeChanged,
                            enabled = isDarkEnabled
                        )
                    },
                    modifier = Modifier.clickable(enabled = isDarkEnabled) {
                        onAmoledModeChanged(!amoledMode)
                    }
                )
            }
        }
        }
    }
}

@Composable
private fun ThemePresetCard(
    preset: AppThemePreset,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    val primaryColor = when (preset) {
        AppThemePreset.Orange -> Color(0xFFEA580C)
        AppThemePreset.Blue -> Color(0xFF0284C7)
        AppThemePreset.Green -> Color(0xFF16A34A)
        AppThemePreset.Red -> Color(0xFFDC2626)
        AppThemePreset.Purple -> Color(0xFF7C3AED)
    }
    val secondaryColor = when (preset) {
        AppThemePreset.Orange -> Color(0xFF3B82F6)
        AppThemePreset.Blue -> Color(0xFF0F766E)
        AppThemePreset.Green -> Color(0xFF0891B2)
        AppThemePreset.Red -> Color(0xFFD97706)
        AppThemePreset.Purple -> Color(0xFFDB2777)
    }
    val nameRes = when (preset) {
        AppThemePreset.Orange -> R.string.theme_preset_orange
        AppThemePreset.Blue -> R.string.theme_preset_blue
        AppThemePreset.Green -> R.string.theme_preset_green
        AppThemePreset.Red -> R.string.theme_preset_red
        AppThemePreset.Purple -> R.string.theme_preset_purple
    }

    val alpha = if (enabled) 1.0f else 0.38f

    Card(
        modifier = Modifier
            .width(140.dp)
            .height(100.dp)
            .clickable(enabled = enabled) { onClick() },
        shape = RoundedCornerShape(16.dp),
        border = if (selected && enabled) BorderStroke(3.dp, primaryColor) else null,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = androidx.compose.ui.graphics.Brush.linearGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = alpha),
                            secondaryColor.copy(alpha = alpha)
                        )
                    )
                )
        ) {
            // Checkmark in the corner
            if (selected && enabled) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .size(20.dp)
                )
            }

            // Theme Name at the bottom
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .background(Color.Black.copy(alpha = 0.4f * alpha))
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = stringResource(nameRes),
                    color = Color.White.copy(alpha = alpha),
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                )
            }
        }
    }
}
