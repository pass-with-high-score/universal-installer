package app.pwhs.universalinstaller.ui.theme

import androidx.compose.ui.unit.dp

/**
 * Shared spacing scale. Use these instead of bare `dp` literals so padding and
 * gaps stay consistent across screens and a single edit can re-tune the whole app.
 *
 * Scale (4dp base): XS=4 · S=8 · M=12 · L=16 · XL=24 · XXL=32.
 *
 * Adoption is incremental — new and recently-touched composables use these; older
 * files keep their literals until they're next edited (no big-bang migration).
 */
object Spacing {
    val XS = 4.dp
    val S = 8.dp
    val M = 12.dp
    val L = 16.dp
    val XL = 24.dp
    val XXL = 32.dp
}
