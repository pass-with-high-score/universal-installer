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
import app.pwhs.universalinstaller.presentation.setting.ThemeMode
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
        onThemeChanged = viewModel::setThemeMode,
        onDynamicColorChanged = viewModel::setDynamicColor,
        onAmoledModeChanged = viewModel::setAmoledMode,
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
    dynamicColor: Boolean = true,
    amoledMode: Boolean = false,
    onThemeChanged: (ThemeMode) -> Unit = {},
    onDynamicColorChanged: (Boolean) -> Unit = {},
    onAmoledModeChanged: (Boolean) -> Unit = {},
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp)
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
