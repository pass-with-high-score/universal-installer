package app.pwhs.universalinstaller.presentation.install.components

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.ActiveDownloadItem
import app.pwhs.universalinstaller.presentation.install.ActiveDownloadStatus
import app.pwhs.universalinstaller.presentation.install.DownloadPartProgress

@Composable
internal fun ActiveDownloadCard(
    item: ActiveDownloadItem,
    onPause: (() -> Unit)?,
    onResume: (() -> Unit)?,
    onCancel: () -> Unit,
) {
    val context = LocalContext.current
    val percent = item.progressPercent
    val host = remember(item.url) {
        runCatching { Uri.parse(item.url).host }.getOrNull().orEmpty()
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = item.fileName,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                        if (item.threadCount > 1) {
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = MaterialTheme.colorScheme.secondaryContainer,
                            ) {
                                Text(
                                    text = stringResource(R.string.source_download_threads, item.threadCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }
                    if (host.isNotBlank()) {
                        Text(
                            text = host,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                if (item.status == ActiveDownloadStatus.DOWNLOADING && onPause != null) {
                    IconButton(onClick = onPause, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.Pause,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else if (item.status == ActiveDownloadStatus.PAUSED && onResume != null) {
                    IconButton(onClick = onResume, modifier = Modifier.size(34.dp)) {
                        Icon(
                            imageVector = Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                IconButton(onClick = onCancel, modifier = Modifier.size(34.dp)) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.remote_download_cancel),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }

            // ── Segmented or Linear Horizontal Progress Bar ──
            if (item.parts.isNotEmpty() && item.totalBytes > 0) {
                SegmentedDownloadProgressBar(
                    parts = item.parts,
                    totalBytes = item.totalBytes,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp),
                )
            } else if (percent != null && item.status != ActiveDownloadStatus.PREPARING) {
                val animated by animateFloatAsState(
                    targetValue = percent / 100f,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "DownloadItemLinearProgress",
                )
                LinearProgressIndicator(
                    progress = { animated },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(50)),
                    strokeCap = StrokeCap.Round,
                )
            } else {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .clip(RoundedCornerShape(50)),
                    strokeCap = StrokeCap.Round,
                )
            }

            // ── Metadata Row: Size + Speed | Percent + ETA ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val leftText = when (item.status) {
                    ActiveDownloadStatus.PREPARING -> stringResource(R.string.source_download_preparing)
                    ActiveDownloadStatus.PAUSED -> stringResource(R.string.source_download_paused)
                    ActiveDownloadStatus.FAILED -> item.errorMessage.orEmpty()
                    else -> {
                        val downloadedStr = Formatter.formatFileSize(context, item.bytesRead)
                        val totalStr = if (item.totalBytes > 0) Formatter.formatFileSize(context, item.totalBytes) else "—"
                        val speedStr = app.pwhs.core.util.TransferFormatter.formatSpeed(item.speedBytesPerSec)
                        if (speedStr.isNotEmpty()) "$downloadedStr / $totalStr • $speedStr"
                        else "$downloadedStr / $totalStr"
                    }
                }
                Text(
                    text = leftText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (item.status == ActiveDownloadStatus.FAILED) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                val etaStr = app.pwhs.core.util.TransferFormatter.formatEta(context, item.etaSeconds)
                val rightText = when {
                    item.status == ActiveDownloadStatus.PREPARING -> ""
                    percent != null && etaStr != null -> "$percent% ($etaStr)"
                    percent != null -> "$percent%"
                    etaStr != null -> etaStr
                    else -> stringResource(R.string.dialog_downloading_package)
                }
                if (rightText.isNotEmpty()) {
                    Text(
                        text = rightText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * ABDM-style multi-segment progress bar that visualizes parallel byte-range parts
 * filling concurrently across the total file size.
 */
@Composable
internal fun SegmentedDownloadProgressBar(
    parts: List<DownloadPartProgress>,
    totalBytes: Long,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceContainerHighest
    val fillColor = MaterialTheme.colorScheme.primary
    Canvas(modifier = modifier.clip(RoundedCornerShape(50))) {
        drawRoundRect(
            color = trackColor,
            size = size,
            cornerRadius = CornerRadius(size.height / 2f, size.height / 2f),
        )
        if (totalBytes <= 0L) return@Canvas
        parts.forEach { part ->
            val startFrac = (part.startByte.toFloat() / totalBytes).coerceIn(0f, 1f)
            val currentFrac = (part.currentByte.toFloat() / totalBytes).coerceIn(startFrac, 1f)
            val left = startFrac * size.width
            val width = (currentFrac - startFrac) * size.width
            if (width > 0.5f) {
                drawRect(
                    color = fillColor,
                    topLeft = Offset(left, 0f),
                    size = Size(width, size.height),
                )
            }
        }
    }
}
