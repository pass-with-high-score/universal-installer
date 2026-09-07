package app.pwhs.universalinstaller.presentation.install.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.ScanState

@Composable
internal fun SheetHeader(scanState: ScanState, onRescan: () -> Unit, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.find_auto_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (scanState is ScanState.Ready && scanState.files.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.find_auto_count, scanState.files.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (scanState is ScanState.Ready) {
            IconButton(onClick = onRescan) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.find_auto_rescan),
                )
            }
        }
        IconButton(onClick = onDismiss) {
            Icon(
                imageVector = Icons.Rounded.Close,
                contentDescription = stringResource(R.string.cancel),
            )
        }
    }
}

@Composable
internal fun PermissionBody(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.find_auto_permission_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.find_auto_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onGrant,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.find_auto_grant))
        }
    }
}

@Composable
internal fun ScanningBody(state: ScanState.Scanning) {
    val percent = state.progress?.let { (it * 100).toInt().coerceIn(0, 100) }
    val animatedProgress by animateFloatAsState(
        targetValue = percent?.let { it / 100f } ?: 0f,
        label = "scanProgressAnimation",
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 36.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier.size(72.dp),
        ) {
            if (percent != null) {
                CircularProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 6.dp,
                )
                Text(
                    text = "$percent%",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                CircularProgressIndicator(
                    modifier = Modifier.size(72.dp),
                    strokeWidth = 6.dp,
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        Text(
            text = state.status.takeIf { !it.isNullOrBlank() }
                ?: stringResource(R.string.find_auto_scanning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (state.foundCount > 0) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.find_auto_found_so_far, state.foundCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
