package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun PositionDialog(
    modifier: Modifier = Modifier,
    centerIcon: @Composable (() -> Unit)? = null,
    centerTitle: @Composable (() -> Unit)? = null,
    centerSubtitle: @Composable (() -> Unit)? = null,
    centerText: @Composable (() -> Unit)? = null,
    centerContent: @Composable (() -> Unit)? = null,
    centerButton: @Composable (() -> Unit)? = null,
    titleContentColor: Color = MaterialTheme.colorScheme.onSurface,
    textContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = DialogMotion.ContentSpring
            )
            .padding(vertical = 16.dp)
    ) {
        centerIcon?.let { icon ->
            Box(
                modifier = Modifier
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .align(Alignment.CenterHorizontally)
            ) {
                icon()
            }
        }

        centerTitle?.let { title ->
            CompositionLocalProvider(LocalContentColor provides titleContentColor) {
                ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold)) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 2.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        title()
                    }
                }
            }
        }

        centerSubtitle?.let { subtitle ->
            CompositionLocalProvider(LocalContentColor provides textContentColor) {
                ProvideTextStyle(MaterialTheme.typography.bodySmall) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 0.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        subtitle()
                    }
                }
            }
        }

        centerText?.let { text ->
            CompositionLocalProvider(LocalContentColor provides textContentColor) {
                ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 16.dp, vertical = 4.dp)
                            .align(Alignment.CenterHorizontally)
                    ) {
                        text()
                    }
                }
            }
        }

        centerContent?.let { content ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                content()
            }
        }

        centerButton?.let { button ->
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.primary) {
                ProvideTextStyle(MaterialTheme.typography.labelLarge) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 0.dp)
                    ) {
                        button()
                    }
                }
            }
        }
    }
}
