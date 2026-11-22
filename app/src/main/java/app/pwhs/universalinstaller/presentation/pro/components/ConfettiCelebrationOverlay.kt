package app.pwhs.universalinstaller.presentation.pro.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private enum class ConfettiShape { Ribbon, Circle, Diamond }

private data class ConfettiPiece(
    val originXFraction: Float,
    val originYFraction: Float,
    val vx: Float,
    val vy: Float,
    val gravity: Float,
    val swayAmp: Float,
    val swayFreq: Float,
    val phase: Float,
    val baseRotation: Float,
    val rotationSpeed: Float,
    val flipSpeed: Float,
    val widthDp: Float,
    val heightDp: Float,
    val color: Color,
    val shape: ConfettiShape,
    val delaySec: Float,
)

private val CelebrationPalette = listOf(
    Color(0xFFFFB300), // Gold
    Color(0xFFFF6D00), // Vibrant Orange
    Color(0xFF00E676), // Emerald Green
    Color(0xFF2979FF), // Electric Blue
    Color(0xFFFF4081), // Rose Pink
    Color(0xFFAA00FF), // Royal Purple
    Color(0xFF00E5FF), // Cyan Spark
    Color(0xFFFFEA00), // Lemon Yellow
)

/**
 * Rich multi-wave confetti cannon & falling ribbons overlay rendered purely with Compose Canvas.
 * Fires two side cannons plus a top golden shower over [durationMillis].
 */
@Composable
fun ConfettiCelebrationOverlay(
    modifier: Modifier = Modifier,
    particleCount: Int = 95,
    durationMillis: Long = 3800L,
) {
    val pieces = remember(particleCount) {
        val rng = Random(System.nanoTime())
        List(particleCount) { index ->
            val wave = index % 3
            // Wave 0: Left cannon, Wave 1: Right cannon, Wave 2: Top center burst
            val originX = when (wave) {
                0 -> rng.nextFloat() * 0.18f
                1 -> 0.82f + rng.nextFloat() * 0.18f
                else -> 0.2f + rng.nextFloat() * 0.6f
            }
            val originY = when (wave) {
                0, 1 -> 0.28f + rng.nextFloat() * 0.18f
                else -> -0.05f + rng.nextFloat() * 0.12f
            }
            val angle = when (wave) {
                0 -> Math.toRadians((-78.0 + rng.nextDouble() * 48.0))
                1 -> Math.toRadians((-102.0 - rng.nextDouble() * 48.0))
                else -> Math.toRadians((-120.0 + rng.nextDouble() * 60.0))
            }
            val speed = when (wave) {
                0, 1 -> 420f + rng.nextFloat() * 520f
                else -> 120f + rng.nextFloat() * 280f
            }
            val shape = when (index % 5) {
                0 -> ConfettiShape.Circle
                1 -> ConfettiShape.Diamond
                else -> ConfettiShape.Ribbon
            }
            ConfettiPiece(
                originXFraction = originX,
                originYFraction = originY,
                vx = (cos(angle) * speed).toFloat(),
                vy = (sin(angle) * speed).toFloat(),
                gravity = 520f + rng.nextFloat() * 280f,
                swayAmp = 18f + rng.nextFloat() * 34f,
                swayFreq = 2.4f + rng.nextFloat() * 3.5f,
                phase = rng.nextFloat() * 6.28f,
                baseRotation = rng.nextFloat() * 360f,
                rotationSpeed = -260f + rng.nextFloat() * 520f,
                flipSpeed = 3.5f + rng.nextFloat() * 6.5f,
                widthDp = if (shape == ConfettiShape.Ribbon) 10f + rng.nextFloat() * 6f else 7f + rng.nextFloat() * 5f,
                heightDp = if (shape == ConfettiShape.Ribbon) 5f + rng.nextFloat() * 4f else 7f + rng.nextFloat() * 5f,
                color = CelebrationPalette[index % CelebrationPalette.size],
                shape = shape,
                delaySec = if (wave == 2) rng.nextFloat() * 0.45f else rng.nextFloat() * 0.18f,
            )
        }
    }

    var elapsedSec by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(Unit) {
        val startNanos = withFrameNanos { it }
        val maxSec = durationMillis / 1000f
        while (elapsedSec < maxSec) {
            val now = withFrameNanos { it }
            elapsedSec = ((now - startNanos) / 1_000_000_000f).coerceAtMost(maxSec)
        }
    }

    val totalSec = durationMillis / 1000f
    if (elapsedSec >= totalSec) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas
        val densityScale = density / 2.75f

        for (p in pieces) {
            val t = elapsedSec - p.delaySec
            if (t <= 0f) continue

            // Damped horizontal velocity + gentle air sway
            val dampedT = (1f - kotlin.math.exp(-1.6f * t)) / 1.6f
            val x = p.originXFraction * w +
                (p.vx * dampedT * densityScale) +
                sin(t * p.swayFreq + p.phase) * p.swayAmp * densityScale

            val y = p.originYFraction * h +
                (p.vy * t + 0.5f * p.gravity * t * t) * densityScale

            if (y > h + 40f || x < -60f || x > w + 60f) continue

            val fadeOutStart = totalSec * 0.65f
            val alpha = if (elapsedSec > fadeOutStart) {
                (1f - (elapsedSec - fadeOutStart) / (totalSec - fadeOutStart)).coerceIn(0f, 1f)
            } else {
                1f
            }
            if (alpha <= 0.02f) continue

            // 3D tumbling illusion by oscillating the ribbon height
            val flipFactor = cos(t * p.flipSpeed + p.phase)
            val pw = p.widthDp * density
            val ph = (p.heightDp * density * kotlin.math.abs(flipFactor)).coerceAtLeast(2.5f)
            val angle = p.baseRotation + p.rotationSpeed * t

            rotate(degrees = angle, pivot = Offset(x, y)) {
                val drawColor = p.color.copy(alpha = alpha)
                when (p.shape) {
                    ConfettiShape.Ribbon -> {
                        drawRoundRect(
                            color = drawColor,
                            topLeft = Offset(x - pw / 2f, y - ph / 2f),
                            size = Size(pw, ph),
                            cornerRadius = CornerRadius(2f, 2f),
                        )
                    }
                    ConfettiShape.Circle -> {
                        drawOval(
                            color = drawColor,
                            topLeft = Offset(x - pw / 2f, y - ph / 2f),
                            size = Size(pw, ph),
                        )
                    }
                    ConfettiShape.Diamond -> {
                        val path = Path().apply {
                            moveTo(x, y - ph / 2f)
                            lineTo(x + pw / 2f, y)
                            lineTo(x, y + ph / 2f)
                            lineTo(x - pw / 2f, y)
                            close()
                        }
                        drawPath(path = path, color = drawColor)
                    }
                }
            }
        }
    }
}
