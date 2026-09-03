package app.pwhs.universalinstaller.wearos.presentation.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private val WearColorScheme = ColorScheme(
    primary = WearPrimary,
    primaryDim = WearPrimaryDim,
    primaryContainer = WearPrimaryContainer,
    onPrimary = WearOnPrimary,
    onPrimaryContainer = WearOnPrimaryContainer,
    secondary = WearSecondary,
    secondaryDim = WearSecondaryDim,
    secondaryContainer = WearSecondaryContainer,
    onSecondary = WearOnSecondary,
    onSecondaryContainer = WearOnSecondaryContainer,
    tertiary = WearTertiary,
    tertiaryDim = WearTertiaryDim,
    tertiaryContainer = WearTertiaryContainer,
    onTertiary = WearOnTertiary,
    onTertiaryContainer = WearOnTertiaryContainer,
    error = WearError,
    errorDim = WearErrorDim,
    errorContainer = WearErrorContainer,
    onError = WearOnError,
    onErrorContainer = WearOnErrorContainer,
    background = WearBackground,
    onBackground = WearOnBackground,
    surfaceContainerLow = WearSurfaceContainerLow,
    surfaceContainer = WearSurfaceContainer,
    surfaceContainerHigh = WearSurfaceContainerHigh,
    onSurface = WearOnSurface,
    onSurfaceVariant = WearOnSurfaceVariant,
    outline = WearOutline,
    outlineVariant = WearOutlineVariant,
)

@Composable
fun UniversalInstallerTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = WearColorScheme, content = content)
}
