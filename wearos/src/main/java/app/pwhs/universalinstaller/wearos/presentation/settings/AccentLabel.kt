package app.pwhs.universalinstaller.wearos.presentation.settings

import androidx.annotation.StringRes
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.presentation.theme.WearAccent

@StringRes
fun WearAccent.labelRes(): Int = when (this) {
    WearAccent.Orange -> R.string.accent_orange
    WearAccent.Blue -> R.string.accent_blue
    WearAccent.Green -> R.string.accent_green
    WearAccent.Purple -> R.string.accent_purple
}
