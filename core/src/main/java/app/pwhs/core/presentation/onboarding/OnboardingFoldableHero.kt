package app.pwhs.core.presentation.onboarding

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Rich hero container rendered in the left pane of Onboarding on Expanded / Book Foldables
 * so the animated badge fills the wide canvas with balanced visual weight.
 */
@Composable
internal fun OnboardingHeroPanel(
    page: OnboardingPage,
    pageOffset: Float,
    isPermissionPage: Boolean,
    hasPermission: Boolean,
    pageIndex: Int,
    totalPages: Int,
    modifier: Modifier = Modifier,
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val surfaceLow = MaterialTheme.colorScheme.surfaceContainerLow

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight(0.76f)
            .heightIn(min = 320.dp, max = 520.dp),
        shape = RoundedCornerShape(32.dp),
        color = surfaceLow,
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f),
        ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            primaryContainer.copy(alpha = 0.55f),
                            primaryContainer.copy(alpha = 0.14f),
                            Color.Transparent,
                        ),
                    ),
                )
                .padding(24.dp),
        ) {
            // Subtle step pill at top-start
            Surface(
                modifier = Modifier.align(Alignment.TopStart),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                ),
            ) {
                Text(
                    text = "${pageIndex + 1} / $totalPages",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = primary,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }

            // Soft decorative ring behind the scaled badge
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(220.dp)
                    .clip(CircleShape)
                    .background(primary.copy(alpha = 0.06f)),
            )

            // Scaled animated badge
            AnimatedOnboardingBadge(
                page = page,
                pageOffset = pageOffset,
                isPermissionPage = isPermissionPage,
                hasPermission = hasPermission,
                modifier = Modifier
                    .align(Alignment.Center)
                    .graphicsLayer {
                        scaleX = 1.48f
                        scaleY = 1.48f
                    },
            )
        }
    }
}

/**
 * Compact format pills shown under the Install slide description so Page 1 carries
 * balanced visual density on wide screens (matching Page 2's DeviceSupportGrid).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun SupportedFormatPills(
    modifier: Modifier = Modifier,
) {
    val formats = listOf("APK", "APKS", "XAPK", "APKM", "ZIP")
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        formats.forEach { format ->
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                border = BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.2f),
                ),
            ) {
                Text(
                    text = ".$format",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}
