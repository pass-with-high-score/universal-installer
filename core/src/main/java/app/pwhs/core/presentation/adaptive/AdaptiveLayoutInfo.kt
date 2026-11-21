package app.pwhs.core.presentation.adaptive

import android.app.Activity
import android.graphics.Rect
import androidx.compose.material3.windowsizeclass.ExperimentalMaterial3WindowSizeClassApi
import androidx.compose.material3.windowsizeclass.WindowHeightSizeClass
import androidx.compose.material3.windowsizeclass.WindowWidthSizeClass
import androidx.compose.material3.windowsizeclass.calculateWindowSizeClass
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.window.layout.FoldingFeature
import androidx.window.layout.WindowInfoTracker

@Immutable
sealed interface FoldablePosture {
    data object Normal : FoldablePosture

    /** Half-opened or separating vertical fold (e.g. Fold opened like a book). */
    data class Book(
        val hingeBounds: Rect,
        val hingeWidth: Dp,
    ) : FoldablePosture

    /** Half-opened horizontal fold (e.g. Fold resting on a table at ~90 degrees). */
    data class Tabletop(
        val hingeBounds: Rect,
        val hingeHeight: Dp,
    ) : FoldablePosture
}

@Immutable
data class AdaptiveLayoutInfo(
    val widthSizeClass: WindowWidthSizeClass = WindowWidthSizeClass.Compact,
    val heightSizeClass: WindowHeightSizeClass = WindowHeightSizeClass.Medium,
    val posture: FoldablePosture = FoldablePosture.Normal,
) {
    val isCompact: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.Compact

    val isMedium: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.Medium

    val isExpanded: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.Expanded

    /** Use NavigationRail instead of BottomBar whenever width is >= 600dp. */
    val useNavigationRail: Boolean
        get() = widthSizeClass != WindowWidthSizeClass.Compact

    /** Use 2-pane / List-Detail layout on Expanded screens or Book posture. */
    val useTwoPane: Boolean
        get() = widthSizeClass == WindowWidthSizeClass.Expanded || posture is FoldablePosture.Book

    val isBook: Boolean
        get() = posture is FoldablePosture.Book

    val isTabletop: Boolean
        get() = posture is FoldablePosture.Tabletop
}

val LocalAdaptiveLayoutInfo = compositionLocalOf { AdaptiveLayoutInfo() }

@OptIn(ExperimentalMaterial3WindowSizeClassApi::class)
@Composable
fun ProvideAdaptiveLayoutInfo(
    activity: Activity,
    content: @Composable () -> Unit,
) {
    val windowSizeClass = calculateWindowSizeClass(activity)
    val density = LocalDensity.current

    val foldingFeature by produceState<FoldingFeature?>(initialValue = null, key1 = activity) {
        WindowInfoTracker.getOrCreate(activity)
            .windowLayoutInfo(activity)
            .collect { layoutInfo ->
                value = layoutInfo.displayFeatures
                    .filterIsInstance<FoldingFeature>()
                    .firstOrNull()
            }
    }

    val posture = remember(foldingFeature, density) {
        val feature = foldingFeature
        if (feature == null) {
            FoldablePosture.Normal
        } else {
            val isSeparatingOrHalfOpened =
                feature.state == FoldingFeature.State.HALF_OPENED || feature.isSeparating
            when {
                isSeparatingOrHalfOpened && feature.orientation == FoldingFeature.Orientation.VERTICAL -> {
                    FoldablePosture.Book(
                        hingeBounds = feature.bounds,
                        hingeWidth = with(density) { feature.bounds.width().toDp() }.coerceAtLeast(16.dp),
                    )
                }
                isSeparatingOrHalfOpened && feature.orientation == FoldingFeature.Orientation.HORIZONTAL -> {
                    FoldablePosture.Tabletop(
                        hingeBounds = feature.bounds,
                        hingeHeight = with(density) { feature.bounds.height().toDp() }.coerceAtLeast(16.dp),
                    )
                }
                else -> FoldablePosture.Normal
            }
        }
    }

    val adaptiveInfo = remember(windowSizeClass, posture) {
        AdaptiveLayoutInfo(
            widthSizeClass = windowSizeClass.widthSizeClass,
            heightSizeClass = windowSizeClass.heightSizeClass,
            posture = posture,
        )
    }

    CompositionLocalProvider(LocalAdaptiveLayoutInfo provides adaptiveInfo) {
        content()
    }
}
