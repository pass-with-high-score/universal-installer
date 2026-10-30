package app.pwhs.core.presentation.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.abs

internal enum class OnboardingBadgeType {
    INSTALL,
    MANAGE,
    DEVICES,
    RESTORE,
    VIRUSTOTAL,
    XIAOMI,
    ANALYTICS,
    PERMISSION,
    GENERIC,
}

/**
 * Delight hero icon badge for the onboarding screen.
 *
 * Features:
 * - Breathing ambient halo glow behind the container.
 * - Real-time 3D tilt & parallax responsiveness bound to the pager scroll offset.
 * - Contextual micro-motions tailored to each slide (e.g. restore spin, scan beam, bounce, heartbeat).
 */
@Composable
internal fun AnimatedOnboardingBadge(
    page: OnboardingPage,
    pageOffset: Float,
    isPermissionPage: Boolean,
    hasPermission: Boolean,
    modifier: Modifier = Modifier,
) {
    val density = LocalDensity.current
    val infiniteTransition = rememberInfiniteTransition(label = "badgeMotion")

    // Ambient halo breathing effect
    val auraScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "auraScale",
    )
    val auraAlpha by infiniteTransition.animateFloat(
        initialValue = 0.22f,
        targetValue = 0.50f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "auraAlpha",
    )

    // 3D tilt and parallax responding smoothly to horizontal scroll
    val tiltRotationY = (-pageOffset * 24f).coerceIn(-32f, 32f)
    val tiltRotationZ = (-pageOffset * 8f).coerceIn(-14f, 14f)
    val badgeScale = (1f - (abs(pageOffset) * 0.14f)).coerceIn(0.78f, 1.12f)

    val primaryColor = MaterialTheme.colorScheme.primary
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer
    val onPrimaryContainer = MaterialTheme.colorScheme.onPrimaryContainer

    Box(
        modifier = modifier
            .size(118.dp)
            .graphicsLayer {
                rotationY = tiltRotationY
                rotationZ = tiltRotationZ
                scaleX = badgeScale
                scaleY = badgeScale
                cameraDistance = 14f * density.density
            },
        contentAlignment = Alignment.Center,
    ) {
        // Ambient soft glow ring
        Box(
            modifier = Modifier
                .size(112.dp)
                .graphicsLayer {
                    scaleX = auraScale
                    scaleY = auraScale
                    alpha = auraAlpha
                }
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.45f),
                            primaryContainer.copy(alpha = 0.20f),
                            Color.Transparent,
                        ),
                    ),
                    shape = CircleShape,
                )
        )

        // Main rounded hero container
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = primaryContainer,
            shadowElevation = 6.dp,
            modifier = Modifier.size(100.dp),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(MaterialTheme.shapes.extraLarge),
                contentAlignment = Alignment.Center,
            ) {
                if (isPermissionPage) {
                    AnimatedPermissionIcon(
                        hasPermission = hasPermission,
                        defaultIcon = page.icon,
                        tint = onPrimaryContainer,
                        permissionTint = primaryColor,
                        infiniteTransition = infiniteTransition,
                    )
                } else {
                    AnimatedBadgeIcon(
                        badgeType = page.badgeType,
                        icon = page.icon,
                        tint = onPrimaryContainer,
                        primaryColor = primaryColor,
                        infiniteTransition = infiniteTransition,
                    )
                }
            }
        }
    }
}

@Composable
private fun AnimatedBadgeIcon(
    badgeType: OnboardingBadgeType,
    icon: ImageVector,
    tint: Color,
    primaryColor: Color,
    infiniteTransition: androidx.compose.animation.core.InfiniteTransition,
) {
    val density = LocalDensity.current

    when (badgeType) {
        OnboardingBadgeType.RESTORE -> {
            val restoreAngle by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = -360f,
                animationSpec = infiniteRepeatable(
                    animation = keyframes {
                        durationMillis = 2800
                        0f at 0 using FastOutSlowInEasing
                        -360f at 1900 using FastOutSlowInEasing
                        -360f at 2800
                    },
                ),
                label = "restoreSpin",
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { rotationZ = restoreAngle },
            )
        }

        OnboardingBadgeType.INSTALL -> {
            val bouncePx = with(density) { (-6).dp.toPx() }
            val bounceY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = bouncePx,
                animationSpec = infiniteRepeatable(
                    animation = tween(1300, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "installBounce",
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { translationY = bounceY },
            )
        }

        OnboardingBadgeType.MANAGE -> {
            val wiggleZ by infiniteTransition.animateFloat(
                initialValue = -5f,
                targetValue = 5f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1700, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "widgetsWiggle",
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { rotationZ = wiggleZ },
            )
        }

        OnboardingBadgeType.DEVICES -> {
            val floatPx = with(density) { (-4).dp.toPx() }
            val floatY by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = floatPx,
                animationSpec = infiniteRepeatable(
                    animation = tween(1800, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "devicesFloat",
            )
            val scale by infiniteTransition.animateFloat(
                initialValue = 0.96f,
                targetValue = 1.04f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "devicesScale",
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        translationY = floatY
                        scaleX = scale
                        scaleY = scale
                    },
            )
        }

        OnboardingBadgeType.VIRUSTOTAL -> {
            // Scanner beam sweeping over the shield
            val scanSweep by infiniteTransition.animateFloat(
                initialValue = -0.3f,
                targetValue = 1.3f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2400, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart,
                ),
                label = "scanSweep",
            )
            Box(contentAlignment = Alignment.Center) {
                Image(
                    painter = painterResource(app.pwhs.core.R.drawable.ic_virustotal_logo),
                    contentDescription = null,
                    modifier = Modifier.size(48.dp),
                )
                Canvas(modifier = Modifier.size(54.dp)) {
                    val sweepY = size.height * scanSweep
                    if (sweepY in 0f..size.height) {
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    primaryColor.copy(alpha = 0.7f),
                                    Color.Transparent,
                                ),
                                start = Offset(0f, sweepY),
                                end = Offset(size.width, sweepY),
                            ),
                            start = Offset(0f, sweepY),
                            end = Offset(size.width, sweepY),
                            strokeWidth = 3.dp.toPx(),
                        )
                    }
                }
            }
        }

        OnboardingBadgeType.XIAOMI -> {
            val shiftPx = with(density) { 3.dp.toPx() }
            val shiftX by infiniteTransition.animateFloat(
                initialValue = -shiftPx,
                targetValue = shiftPx,
                animationSpec = infiniteRepeatable(
                    animation = tween(1500, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "tuneShift",
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer { translationX = shiftX },
            )
        }

        OnboardingBadgeType.ANALYTICS -> {
            val chartScale by infiniteTransition.animateFloat(
                initialValue = 0.94f,
                targetValue = 1.06f,
                animationSpec = infiniteRepeatable(
                    animation = tween(1400, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse,
                ),
                label = "analyticsScale",
            )
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = chartScale
                        scaleY = chartScale
                    },
            )
        }

        OnboardingBadgeType.PERMISSION,
        OnboardingBadgeType.GENERIC -> {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun AnimatedPermissionIcon(
    hasPermission: Boolean,
    defaultIcon: ImageVector,
    tint: Color,
    permissionTint: Color,
    infiniteTransition: androidx.compose.animation.core.InfiniteTransition,
) {
    val heartScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 1600
                1f at 0
                1.09f at 220 using FastOutSlowInEasing
                1f at 440 using FastOutSlowInEasing
                1.06f at 600 using FastOutSlowInEasing
                1f at 800 using FastOutSlowInEasing
                1f at 1600
            },
        ),
        label = "heartScale",
    )

    AnimatedContent(
        targetState = hasPermission,
        transitionSpec = {
            (scaleIn(animationSpec = tween(400, easing = FastOutSlowInEasing)) + fadeIn(animationSpec = tween(300)))
                .togetherWith(scaleOut(animationSpec = tween(300)) + fadeOut(animationSpec = tween(200)))
        },
        label = "permissionIconState",
    ) { granted ->
        if (granted) {
            Icon(
                imageVector = Icons.Rounded.CheckCircle,
                contentDescription = null,
                tint = permissionTint,
                modifier = Modifier.size(48.dp),
            )
        } else {
            Icon(
                imageVector = defaultIcon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .size(48.dp)
                    .graphicsLayer {
                        scaleX = heartScale
                        scaleY = heartScale
                    },
            )
        }
    }
}
