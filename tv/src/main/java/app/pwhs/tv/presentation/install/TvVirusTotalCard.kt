package app.pwhs.tv.presentation.install

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.tv.R

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVirusTotalCard(
    vtResult: VtResult?,
    onUploadAndScan: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (vtResult == null) return

    val status = vtResult.status
    val isClean = status == VtStatus.CLEAN
    val isThreat = status == VtStatus.MALICIOUS || status == VtStatus.SUSPICIOUS
    val isBusy = status == VtStatus.SCANNING || status == VtStatus.UPLOADING ||
        status == VtStatus.QUEUED || status == VtStatus.ANALYZING
    val isNotFound = status == VtStatus.NOT_FOUND

    val containerBg = when {
        isClean -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        isThreat -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        status == VtStatus.ERROR || status == VtStatus.INVALID_API_KEY -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        status == VtStatus.RATE_LIMITED || status == VtStatus.TOO_LARGE -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    }

    val iconColor = when {
        isClean -> MaterialTheme.colorScheme.primary
        isThreat -> MaterialTheme.colorScheme.error
        status == VtStatus.ERROR || status == VtStatus.INVALID_API_KEY -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val titleText = when (status) {
        VtStatus.CLEAN -> stringResource(R.string.tv_vt_status_clean)
        VtStatus.MALICIOUS -> stringResource(R.string.tv_vt_status_malicious, vtResult.malicious)
        VtStatus.SUSPICIOUS -> stringResource(R.string.tv_vt_status_suspicious, vtResult.suspicious)
        VtStatus.SCANNING -> stringResource(R.string.tv_vt_status_scanning)
        VtStatus.UPLOADING -> stringResource(R.string.tv_vt_status_uploading, vtResult.uploadProgress)
        VtStatus.QUEUED -> stringResource(R.string.tv_vt_status_queued)
        VtStatus.ANALYZING -> stringResource(R.string.tv_vt_status_analyzing)
        VtStatus.NOT_FOUND -> stringResource(R.string.tv_vt_status_not_found)
        VtStatus.NO_API_KEY,
        VtStatus.INVALID_API_KEY,
        VtStatus.RATE_LIMITED,
        VtStatus.TOO_LARGE,
        VtStatus.ERROR -> stringResource(R.string.tv_vt_status_error)
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(containerBg)
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isBusy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                } else {
                    val icon = when {
                        isClean -> Icons.Rounded.GppGood
                        isThreat -> Icons.Rounded.Warning
                        status == VtStatus.NOT_FOUND -> Icons.Rounded.CloudUpload
                        else -> Icons.Rounded.Security
                    }
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(22.dp),
                    )
                }

                Spacer(Modifier.width(10.dp))

                Column {
                    Text(
                        text = titleText,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isThreat || isClean) FontWeight.Bold else FontWeight.Medium,
                        color = if (isThreat) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (status == VtStatus.UPLOADING) {
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { vtResult.uploadProgress / 100f },
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                    }
                }
            }

            // Action button for Not Found (Upload) or Error (Retry)
            if (isNotFound) {
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onUploadAndScan,
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Text(
                        stringResource(R.string.tv_vt_action_upload),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            } else if (status == VtStatus.ERROR || status == VtStatus.RATE_LIMITED) {
                Spacer(Modifier.width(10.dp))
                Button(
                    onClick = onRetry,
                    modifier = Modifier.height(34.dp),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            stringResource(R.string.tv_vt_action_retry),
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }
        }
    }
    }
}
