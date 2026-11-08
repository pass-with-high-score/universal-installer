package app.pwhs.universalinstaller.presentation.install.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.core.R as CoreR
import app.pwhs.universalinstaller.R

@Composable
fun InstallTopBarActions(
    isProUser: Boolean,
    isSyncRunning: Boolean,
    showWatch: Boolean,
    onOpenPro: () -> Unit,
    onOpenWatch: () -> Unit,
    onOpenSendToTv: () -> Unit,
    onOpenSyncServer: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // High-visibility Pro Crown / Medal Button
        IconButton(
            onClick = onOpenPro,
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isProUser) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.WorkspacePremium,
                    contentDescription = stringResource(CoreR.string.pro_upgrade_sheet_title),
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
        }

        if (showWatch) {
            IconButton(onClick = onOpenWatch) {
                Icon(
                    imageVector = Icons.Rounded.Watch,
                    contentDescription = stringResource(R.string.watch_send_title),
                )
            }
        }

        IconButton(onClick = onOpenSendToTv) {
            Icon(
                imageVector = Icons.Rounded.Tv,
                contentDescription = stringResource(R.string.tv_sync_screen_title),
            )
        }

        IconButton(
            onClick = onOpenSyncServer,
            modifier = if (isSyncRunning) Modifier.background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                CircleShape
            ) else Modifier
        ) {
            Icon(
                imageVector = Icons.Rounded.WifiTethering,
                contentDescription = stringResource(R.string.setting_section_sync),
                tint = if (isSyncRunning) MaterialTheme.colorScheme.primary else LocalContentColor.current
            )
        }
    }
}
