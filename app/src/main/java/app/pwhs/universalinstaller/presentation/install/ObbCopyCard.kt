package app.pwhs.universalinstaller.presentation.install

import android.text.format.Formatter
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R

@Composable
internal fun ObbCopyCard(
    modifier: Modifier = Modifier,
    state: ObbCopyState,
    onDismiss: () -> Unit,
    onGrantFolder: () -> Unit = {},
) {
    if (state is ObbCopyState.Idle) return
    val context = LocalContext.current

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val (icon, tint) = when (state) {
                    is ObbCopyState.Done -> Icons.Rounded.CheckCircle to MaterialTheme.colorScheme.primary
                    is ObbCopyState.Error -> Icons.Rounded.Error to MaterialTheme.colorScheme.error
                    is ObbCopyState.NeedSafGrant -> Icons.Rounded.Error to MaterialTheme.colorScheme.tertiary
                    else -> Icons.Rounded.FolderZip to MaterialTheme.colorScheme.primary
                }
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    val title = when (state) {
                        is ObbCopyState.Running -> stringResource(R.string.obb_copy_title)
                        is ObbCopyState.Done -> stringResource(R.string.obb_copy_done, state.appName, state.fileCount)
                        is ObbCopyState.Error -> stringResource(R.string.obb_copy_error, state.message)
                        is ObbCopyState.NeedSafGrant -> stringResource(R.string.obb_copy_need_grant_title)
                        ObbCopyState.Idle -> ""
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    val subtitle = when (state) {
                        is ObbCopyState.Running -> {
                            val copied = Formatter.formatShortFileSize(context, state.bytesCopied)
                            val total = Formatter.formatShortFileSize(context, state.totalBytes.coerceAtLeast(state.bytesCopied))
                            stringResource(R.string.obb_copy_progress, copied, total, state.progressPercent)
                        }
                        else -> state.appNameOrNull().orEmpty()
                    }
                    if (subtitle.isNotEmpty()) {
                        Text(
                            text = subtitle,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (state is ObbCopyState.NeedSafGrant) {
                    TextButton(onClick = onGrantFolder) {
                        Text(stringResource(R.string.obb_copy_grant_button))
                    }
                } else if (state !is ObbCopyState.Running) {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.obb_copy_dismiss))
                    }
                }
            }
            if (state is ObbCopyState.NeedSafGrant) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.obb_copy_need_grant_body, state.packageName),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (state is ObbCopyState.Running) {
                Spacer(Modifier.height(10.dp))
                if (state.totalBytes > 0) {
                    LinearProgressIndicator(
                        progress = { state.progressPercent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
            }
        }
    }
}

private fun ObbCopyState.appNameOrNull(): String? = when (this) {
    is ObbCopyState.Running -> appName
    is ObbCopyState.Done -> appName
    is ObbCopyState.Error -> appName
    is ObbCopyState.NeedSafGrant -> appName
    ObbCopyState.Idle -> null
}
