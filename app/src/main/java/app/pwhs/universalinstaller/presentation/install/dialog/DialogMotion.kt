package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Shared motion specifications for the installation dialog,
 * matching the "Expressive" feel of InstallerX-Revived.
 */
object DialogMotion {
    /**
     * The "Expressive" spring used for layout changes in InstallerX.
     * Low stiffness and medium bouncy provide the characteristic "living" feel.
     */
    val ContentSpring = spring<androidx.compose.ui.unit.IntSize>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )

    /**
     * Bouncy spring for generic transitions.
     */
    val GenericSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessLow,
    )
}
