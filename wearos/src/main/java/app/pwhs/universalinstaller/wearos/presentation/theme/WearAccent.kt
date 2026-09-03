package app.pwhs.universalinstaller.wearos.presentation.theme

import androidx.compose.ui.graphics.Color

/**
 * Accent presets. The background stays true black on every one of them — a watch panel is OLED and
 * a lit pixel costs battery, so only the accent moves.
 */
enum class WearAccent(
    val id: String,
    val primary: Color,
    val primaryDim: Color,
    val primaryContainer: Color,
    val onPrimary: Color,
    val onPrimaryContainer: Color,
) {
    /** The brand orange the phone and the launcher icon already use. */
    Orange(
        id = "orange",
        primary = Color(0xFFFB923C), primaryDim = Color(0xFFEA580C),
        primaryContainer = Color(0xFF9A3412), onPrimary = Color(0xFF431407),
        onPrimaryContainer = Color(0xFFFFEDD5),
    ),
    Blue(
        id = "blue",
        primary = Color(0xFF7DD3FC), primaryDim = Color(0xFF38BDF8),
        primaryContainer = Color(0xFF0C4A6E), onPrimary = Color(0xFF003353),
        onPrimaryContainer = Color(0xFFE0F2FE),
    ),
    Green(
        id = "green",
        primary = Color(0xFF86EFAC), primaryDim = Color(0xFF4ADE80),
        primaryContainer = Color(0xFF166534), onPrimary = Color(0xFF00391C),
        onPrimaryContainer = Color(0xFFDCFCE7),
    ),
    Purple(
        id = "purple",
        primary = Color(0xFFD8B4FE), primaryDim = Color(0xFFA855F7),
        primaryContainer = Color(0xFF6B21A8), onPrimary = Color(0xFF2E1065),
        onPrimaryContainer = Color(0xFFF3E8FF),
    );

    companion object {
        val Default = Orange
        fun fromId(id: String?): WearAccent = entries.firstOrNull { it.id == id } ?: Default
    }
}
