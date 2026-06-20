package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.presentation.composable.ShimmerBox
import app.pwhs.universalinstaller.ui.theme.Spacing

/**
 * Skeleton placeholder rendered while we parse the APK. Mirrors the Prepare-stage
 * layout (icon + title + subtitle + chip row + button) so the transition to real
 * content doesn't shift focus — content fades in at roughly the position the
 * placeholders occupied.
 */
@Composable
fun LoadingContent(
    onSkipParse: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.XL, vertical = Spacing.XXL),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Icon placeholder
        ShimmerBox(modifier = Modifier.size(64.dp), shape = RoundedCornerShape(16.dp))
        Spacer(modifier = Modifier.height(Spacing.L))
        // Title line
        ShimmerBox(modifier = Modifier.width(180.dp).height(20.dp), shape = RoundedCornerShape(6.dp))
        Spacer(modifier = Modifier.height(Spacing.S))
        // Subtitle line
        ShimmerBox(modifier = Modifier.width(140.dp).height(14.dp), shape = RoundedCornerShape(6.dp))
        Spacer(modifier = Modifier.height(20.dp))
        // Chip row (2 chips)
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ShimmerBox(modifier = Modifier.width(80.dp).height(28.dp), shape = RoundedCornerShape(14.dp))
            ShimmerBox(modifier = Modifier.width(64.dp).height(28.dp), shape = RoundedCornerShape(14.dp))
        }
        
        Spacer(modifier = Modifier.height(20.dp))
        
        // Button row
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.S)) {
            ShimmerBox(modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(20.dp))
            ShimmerBox(modifier = Modifier.weight(1f).height(40.dp), shape = RoundedCornerShape(20.dp))
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Cancel button placeholder
        ShimmerBox(modifier = Modifier.fillMaxWidth().height(40.dp), shape = RoundedCornerShape(20.dp))

        if (onSkipParse != null) {
            Spacer(modifier = Modifier.height(16.dp))
            androidx.compose.material3.TextButton(onClick = onSkipParse) {
                androidx.compose.material3.Text(androidx.compose.ui.res.stringResource(app.pwhs.universalinstaller.R.string.dialog_menu_skip_analysis, "Skip & Install"))
            }
        }
    }
}

