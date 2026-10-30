package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Step 1 animation: Smartphone being attacked by invading virus bugs and warning alerts.
 */
@Composable
fun VirusTotalDangerAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "dangerAnim")

    // Continuous attack cycle (0f to 1f)
    val cycleProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "attackCycle",
    )

    // Subtle alert vibration / jitter of the phone
    val jitter by transition.animateFloat(
        initialValue = -1.5f,
        targetValue = 1.5f,
        animationSpec = infiniteRepeatable(
            animation = tween(120, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phoneJitter",
    )

    // Red warning pulse on screen
    val screenAlertPulse by transition.animateFloat(
        initialValue = 0.25f,
        targetValue = 0.65f,
        animationSpec = infiniteRepeatable(
            animation = tween(600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "screenAlertPulse",
    )

    val errorColor = MaterialTheme.colorScheme.error

    Box(
        modifier = modifier.size(width = 180.dp, height = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        // Red threat pulse waves hitting the phone
        Box(
            modifier = Modifier
                .size(width = 74.dp, height = 110.dp)
                .graphicsLayer {
                    val scale = 1f + (cycleProgress * 0.35f)
                    scaleX = scale
                    scaleY = scale
                    alpha = (1f - cycleProgress) * 0.45f
                }
                .clip(RoundedCornerShape(16.dp))
                .background(errorColor)
        )

        // ── Central Smartphone ──
        Box(
            modifier = Modifier
                .offset { IntOffset(jitter.roundToInt(), 0) }
                .size(width = 56.dp, height = 96.dp)
                .shadow(6.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                .border(2.dp, errorColor, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // Speaker notch
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(width = 16.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outline)
            )

            // Screen alert glow
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 10.dp)
                    .matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(errorColor.copy(alpha = screenAlertPulse * 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WarningAmber,
                    contentDescription = null,
                    tint = errorColor,
                    modifier = Modifier.size(24.dp),
                )
            }
        }

        // ── Invading Threats ──
        // Threat 1: Bug from top-left flying towards the phone center
        val bug1Prog = (cycleProgress + 0.0f) % 1f
        val bug1X = -65f + (bug1Prog * 50f)
        val bug1Y = -40f + (bug1Prog * 35f)
        val bug1Alpha = when {
            bug1Prog < 0.2f -> bug1Prog / 0.2f
            bug1Prog > 0.8f -> (1f - bug1Prog) / 0.2f
            else -> 1f
        }
        Icon(
            imageVector = Icons.Rounded.BugReport,
            contentDescription = null,
            tint = errorColor,
            modifier = Modifier
                .offset(x = bug1X.dp, y = bug1Y.dp)
                .size(22.dp)
                .graphicsLayer {
                    alpha = bug1Alpha
                    rotationZ = -25f
                }
        )

        // Threat 2: Bug from right flying inward
        val bug2Prog = (cycleProgress + 0.45f) % 1f
        val bug2X = 65f - (bug2Prog * 48f)
        val bug2Y = -15f + (sin(bug2Prog * Math.PI.toFloat()) * 18f)
        val bug2Alpha = when {
            bug2Prog < 0.2f -> bug2Prog / 0.2f
            bug2Prog > 0.8f -> (1f - bug2Prog) / 0.2f
            else -> 1f
        }
        Icon(
            imageVector = Icons.Rounded.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error,
            modifier = Modifier
                .offset(x = bug2X.dp, y = bug2Y.dp)
                .size(19.dp)
                .graphicsLayer {
                    alpha = bug2Alpha
                    rotationZ = 35f
                }
        )

        // Threat 3: Warning particle from bottom-left infiltrating upward
        val bug3Prog = (cycleProgress + 0.7f) % 1f
        val bug3X = -55f + (bug3Prog * 40f)
        val bug3Y = 42f - (bug3Prog * 38f)
        val bug3Alpha = when {
            bug3Prog < 0.2f -> bug3Prog / 0.2f
            bug3Prog > 0.8f -> (1f - bug3Prog) / 0.2f
            else -> 1f
        }
        Box(
            modifier = Modifier
                .offset(x = bug3X.dp, y = bug3Y.dp)
                .size(14.dp)
                .graphicsLayer { alpha = bug3Alpha }
                .clip(CircleShape)
                .background(errorColor),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.WarningAmber,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(10.dp),
            )
        }
    }
}

/**
 * Step 2 animation: Smartphone safely protected by a glowing VirusTotal shield barrier.
 * Invading threats are deflected away.
 */
@Composable
fun VirusTotalSolutionAnimation(modifier: Modifier = Modifier) {
    val transition = rememberInfiniteTransition(label = "protectedAnim")

    // Shield forcefield pulse
    val shieldPulse by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "shieldPulse",
    )

    // Sweeping scanner beam
    val scanSweep by transition.animateFloat(
        initialValue = -0.2f,
        targetValue = 1.2f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "scanSweep",
    )

    // Deflected threat cycle
    val deflectProgress by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "deflectProgress",
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val containerColor = MaterialTheme.colorScheme.primaryContainer

    Box(
        modifier = modifier.size(width = 180.dp, height = 120.dp),
        contentAlignment = Alignment.Center,
    ) {
        // ── Glowing Protective Forcefield / Shield Aura ──
        Box(
            modifier = Modifier
                .size(width = 82.dp, height = 114.dp)
                .graphicsLayer {
                    scaleX = shieldPulse
                    scaleY = shieldPulse
                }
                .clip(RoundedCornerShape(22.dp))
                .background(containerColor.copy(alpha = 0.35f))
                .border(
                    width = 2.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            primaryColor.copy(alpha = 0.9f),
                            primaryColor.copy(alpha = 0.3f),
                        )
                    ),
                    shape = RoundedCornerShape(22.dp)
                )
        )

        // ── Central Safe Smartphone ──
        Box(
            modifier = Modifier
                .size(width = 56.dp, height = 96.dp)
                .shadow(4.dp, RoundedCornerShape(14.dp))
                .clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLow)
                .border(2.dp, primaryColor, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            // Speaker notch
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 4.dp)
                    .size(width = 16.dp, height = 3.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )

            // Safe screen with shield check
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp, vertical = 10.dp)
                    .matchParentSize()
                    .clip(RoundedCornerShape(8.dp))
                    .background(containerColor.copy(alpha = 0.25f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = primaryColor,
                    modifier = Modifier.size(24.dp),
                )

                // Laser scan beam sweeping vertically over screen
                Canvas(modifier = Modifier.matchParentSize()) {
                    val sweepY = size.height * scanSweep
                    if (sweepY in 0f..size.height) {
                        drawLine(
                            brush = Brush.linearGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    primaryColor.copy(alpha = 0.85f),
                                    Color.Transparent,
                                ),
                                start = Offset(0f, sweepY),
                                end = Offset(size.width, sweepY),
                            ),
                            start = Offset(0f, sweepY),
                            end = Offset(size.width, sweepY),
                            strokeWidth = 2.5.dp.toPx(),
                        )
                    }
                }
            }
        }

        // ── VirusTotal Logo Badge Protecting the Phone ──
        Image(
            painter = painterResource(R.drawable.ic_virustotal_logo),
            contentDescription = "VirusTotal Protected",
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = (-32).dp, y = 4.dp)
                .size(34.dp)
                .shadow(4.dp, CircleShape)
                .clip(CircleShape)
        )

        // ── Deflected Threats (Bouncing off the barrier) ──
        // Threat 1: Bug bounces off the right shield barrier and dissolves
        val defProg1 = deflectProgress
        val defX1 = if (defProg1 < 0.5f) {
            70f - (defProg1 * 46f) // Moving towards shield
        } else {
            47f + ((defProg1 - 0.5f) * 35f) // Bouncing off away!
        }
        val defY1 = if (defProg1 < 0.5f) {
            -10f + (defProg1 * 10f)
        } else {
            -5f - ((defProg1 - 0.5f) * 20f)
        }
        val defAlpha1 = if (defProg1 < 0.5f) 1f else (1f - (defProg1 - 0.5f) * 2f)

        Icon(
            imageVector = Icons.Rounded.BugReport,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.error.copy(alpha = 0.6f),
            modifier = Modifier
                .offset(x = defX1.dp, y = defY1.dp)
                .size(16.dp)
                .graphicsLayer {
                    alpha = defAlpha1
                    rotationZ = if (defProg1 < 0.5f) 30f else 160f
                }
        )

        // Shield spark particle at bounce point
        if (defProg1 in 0.45f..0.65f) {
            Icon(
                imageVector = Icons.Rounded.Shield,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier
                    .offset(x = 46.dp, y = (-5).dp)
                    .size(14.dp)
                    .graphicsLayer { alpha = 1f - (defProg1 - 0.45f) * 5f }
            )
        }
    }
}
