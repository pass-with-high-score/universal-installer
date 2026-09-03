package app.pwhs.universalinstaller.wearos.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.MaterialTheme

private fun colorScheme(accent: WearAccent) = ColorScheme(
    primary = accent.primary,
    primaryDim = accent.primaryDim,
    primaryContainer = accent.primaryContainer,
    onPrimary = accent.onPrimary,
    onPrimaryContainer = accent.onPrimaryContainer,
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
fun UniversalInstallerTheme(
    accent: WearAccent = WearAccent.Default,
    content: @Composable () -> Unit,
) {
    MaterialTheme(colorScheme = remember(accent) { colorScheme(accent) }, content = content)
}
