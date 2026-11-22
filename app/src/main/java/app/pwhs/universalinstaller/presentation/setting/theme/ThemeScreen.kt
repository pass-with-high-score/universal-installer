package app.pwhs.universalinstaller.presentation.setting.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.SettingsBrightness
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.core.presentation.adaptive.MaxWidthContainer
import app.pwhs.core.ui.theme.getPresetColorScheme
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.setting.components.SettingsCardGroup
import app.pwhs.universalinstaller.presentation.setting.components.SettingsSwitchRow
import org.koin.androidx.compose.koinViewModel

@Composable
fun ThemeScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = koinViewModel(),
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
        onBack = { (context as? Activity)?.finish() },
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
    onBack: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val systemDark = isSystemInDarkTheme()
    val effectiveDark = when (themeMode) {
        ThemeMode.System -> systemDark
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.theme_screen_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd),
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
        MaxWidthContainer(
            maxWidth = 680.dp,
            modifier = Modifier
                .padding(innerPadding)
                .navigationBarsPadding()
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                // ── 1. Theme Mode (System / Light / Dark) + AMOLED ──
                item {
                    val isDarkEnabled = themeMode == ThemeMode.Dark || themeMode == ThemeMode.System
                    SettingsCardGroup(
                        title = stringResource(R.string.theme_mode_title),
                        items = listOf(
                            {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                                        ThemeMode.entries.forEachIndexed { index, mode ->
                                            val labelRes = when (mode) {
                                                ThemeMode.System -> R.string.theme_mode_system
                                                ThemeMode.Light -> R.string.theme_mode_light
                                                ThemeMode.Dark -> R.string.theme_mode_dark
                                            }
                                            val icon = when (mode) {
                                                ThemeMode.System -> Icons.Rounded.SettingsBrightness
                                                ThemeMode.Light -> Icons.Rounded.LightMode
                                                ThemeMode.Dark -> Icons.Rounded.DarkMode
                                            }
                                            SegmentedButton(
                                                selected = themeMode == mode,
                                                onClick = { onThemeChanged(mode) },
                                                shape = SegmentedButtonDefaults.itemShape(
                                                    index = index,
                                                    count = ThemeMode.entries.size,
                                                ),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                                                icon = {},
                                            ) {
                                                Text(
                                                    text = stringResource(labelRes),
                                                    style = MaterialTheme.typography.labelLarge,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.theme_amoled_mode_title),
                                    subtitle = stringResource(R.string.theme_amoled_mode_subtitle),
                                    icon = Icons.Rounded.Contrast,
                                    checked = amoledMode,
                                    onCheckedChange = onAmoledModeChanged,
                                    enabled = isDarkEnabled,
                                )
                            },
                        ),
                    )
                }

                // ── 2. Color Palette (Material You + M3 Swatches) ──
                item {
                    val isAndroid12Plus = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                    SettingsCardGroup(
                        title = stringResource(R.string.theme_preset_title),
                        items = listOf(
                            {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.theme_dynamic_color_title),
                                    subtitle = stringResource(R.string.theme_dynamic_color_subtitle),
                                    icon = Icons.Rounded.AutoAwesome,
                                    checked = dynamicColor,
                                    onCheckedChange = onDynamicColorChanged,
                                    enabled = isAndroid12Plus,
                                )
                            },
                            {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 16.dp)
                                        .alpha(if (dynamicColor) 0.4f else 1f),
                                ) {
                                    Text(
                                        text = stringResource(R.string.theme_preset_subtitle),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 16.dp),
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    LazyRow(
                                        modifier = Modifier.fillMaxWidth(),
                                        contentPadding = PaddingValues(horizontal = 16.dp),
                                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        items(AppThemePreset.entries) { preset ->
                                            ThemeColorSwatch(
                                                preset = preset,
                                                isDark = effectiveDark,
                                                selected = themePreset == preset && !dynamicColor,
                                                enabled = !dynamicColor,
                                                onClick = { onThemePresetChanged(preset) },
                                            )
                                        }
                                    }
                                }
                            },
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Material 3 palette swatch (matching Android Wallpaper & Style):
 * a 3-tone circle built from the preset's real M3 ColorScheme (primaryContainer,
 * secondaryContainer, tertiaryContainer) with a crisp primary check badge when selected.
 */
@Composable
private fun ThemeColorSwatch(
    preset: AppThemePreset,
    isDark: Boolean,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val scheme = remember(preset, isDark) { getPresetColorScheme(isDark, preset) }
    val nameRes = when (preset) {
        AppThemePreset.Orange -> R.string.theme_preset_orange
        AppThemePreset.Blue -> R.string.theme_preset_blue
        AppThemePreset.Green -> R.string.theme_preset_green
        AppThemePreset.Red -> R.string.theme_preset_red
        AppThemePreset.Purple -> R.string.theme_preset_purple
    }

    val shape = RoundedCornerShape(18.dp)
    Surface(
        shape = shape,
        color = if (selected) scheme.primaryContainer.copy(alpha = 0.35f)
        else MaterialTheme.colorScheme.surfaceContainer,
        border = if (selected) BorderStroke(2.dp, scheme.primary) else null,
        modifier = Modifier
            .width(104.dp)
            .clip(shape)
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.size(48.dp),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val diameter = size.minDimension
                    val arcSize = Size(diameter, diameter)
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    // Top half: primary
                    drawArc(
                        color = scheme.primary,
                        startAngle = 180f,
                        sweepAngle = 180f,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                    // Bottom-left quarter: primaryContainer
                    drawArc(
                        color = scheme.primaryContainer,
                        startAngle = 90f,
                        sweepAngle = 90f,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                    // Bottom-right quarter: secondary
                    drawArc(
                        color = scheme.secondary,
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = true,
                        topLeft = topLeft,
                        size = arcSize,
                    )
                }

                if (selected) {
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(scheme.surface),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = null,
                            tint = scheme.primary,
                            modifier = Modifier.size(15.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = stringResource(nameRes),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
