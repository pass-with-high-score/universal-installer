package app.pwhs.core.presentation.adaptive

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Centers content and constrains its maximum width on Medium / Expanded screens so forms,
 * settings lists, and primary CTAs never stretch awkwardly across a wide inner foldable display.
 */
@Composable
fun MaxWidthContainer(
    modifier: Modifier = Modifier,
    maxWidth: Dp = 640.dp,
    contentAlignment: Alignment = Alignment.TopCenter,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = contentAlignment,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = maxWidth),
            content = content,
        )
    }
}

/**
 * Renders two panes side-by-side on Expanded screens or in Book posture.
 * When a vertical separating hinge is present, leaves a hinge-sized gap aligned to the fold
 * so neither pane's content or touch targets cross the crease.
 */
@Composable
fun AdaptiveTwoPane(
    first: @Composable () -> Unit,
    second: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    firstWeight: Float = 0.45f,
    showDividerWhenFlat: Boolean = true,
) {
    val adaptiveInfo = LocalAdaptiveLayoutInfo.current
    val bookPosture = adaptiveInfo.posture as? FoldablePosture.Book

    if (bookPosture != null) {
        val density = LocalDensity.current
        BoxWithConstraints(modifier = modifier.fillMaxSize()) {
            val totalWidthPx = with(density) { maxWidth.toPx() }.coerceAtLeast(1f)
            val leftWidthDp = with(density) { bookPosture.hingeBounds.left.toDp() }
            val hingeWidthDp = bookPosture.hingeWidth
            val validHinge = bookPosture.hingeBounds.left > 0 &&
                bookPosture.hingeBounds.right < totalWidthPx

            Row(modifier = Modifier.fillMaxSize()) {
                if (validHinge) {
                    Box(
                        modifier = Modifier
                            .width(leftWidthDp)
                            .fillMaxHeight(),
                    ) {
                        first()
                    }
                    Spacer(modifier = Modifier.width(hingeWidthDp))
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    ) {
                        second()
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight(),
                    ) {
                        first()
                    }
                    Spacer(modifier = Modifier.width(hingeWidthDp))
                    Box(
                        modifier = Modifier
                            .weight(0.5f)
                            .fillMaxHeight(),
                    ) {
                        second()
                    }
                }
            }
        }
    } else {
        val clampedFirst = firstWeight.coerceIn(0.25f, 0.75f)
        Row(modifier = modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(clampedFirst)
                    .fillMaxHeight(),
            ) {
                first()
            }
            if (showDividerWhenFlat) {
                VerticalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f - clampedFirst)
                    .fillMaxHeight(),
            ) {
                second()
            }
        }
    }
}

/**
 * Splits content vertically around a horizontal folding hinge in Tabletop posture:
 * [topContent] occupies the upper half (display / preview / QR / summary),
 * [bottomContent] occupies the lower half (controls / actions).
 */
@Composable
fun TabletopSplitLayout(
    topContent: @Composable () -> Unit,
    bottomContent: @Composable () -> Unit,
    posture: FoldablePosture.Tabletop,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val totalHeightPx = with(density) { maxHeight.toPx() }.coerceAtLeast(1f)
        val topHeightDp = with(density) { posture.hingeBounds.top.toDp() }
        val hingeHeightDp = posture.hingeHeight
        val validHinge = posture.hingeBounds.top > 0 &&
            posture.hingeBounds.bottom < totalHeightPx

        Column(modifier = Modifier.fillMaxSize()) {
            if (validHinge) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(topHeightDp),
                ) {
                    topContent()
                }
                HorizontalDivider(
                    modifier = Modifier.height(hingeHeightDp),
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    bottomContent()
                }
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f),
                ) {
                    topContent()
                }
                Spacer(modifier = Modifier.height(hingeHeightDp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(0.5f),
                ) {
                    bottomContent()
                }
            }
        }
    }
}
