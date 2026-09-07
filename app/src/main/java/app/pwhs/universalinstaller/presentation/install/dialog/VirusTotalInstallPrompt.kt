package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.ui.theme.LocalExtendedColors

/**
 * Compact VirusTotal status badge and scan prompt displayed on the installation dialog & bottomsheet.
 *
 * Supports:
 * - Idle / Unscanned: Prompt to scan with VirusTotal.
 * - In-progress: Animated progress indicator (Scanning, Uploading %, Queued, Analyzing).
 * - Result (Clean / Flagged): Reassuring or warning badge linking to the full VirusTotal report.
 */
@Composable
fun VirusTotalInstallPrompt(
    apkInfo: ApkInfo,
    onScan: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val extendedColors = LocalExtendedColors.current
    val uriHandler = LocalUriHandler.current
    val vt = apkInfo.vtResult
    val status = vt?.status

    val inProgress = status in setOf(
        VtStatus.SCANNING,
        VtStatus.UPLOADING,
        VtStatus.QUEUED,
        VtStatus.ANALYZING,
    )

    AnimatedContent(
        targetState = status,
        transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(150)) },
        label = "VirusTotalPromptAnimation",
        modifier = modifier.animateContentSize(),
    ) { currentStatus ->
        when {
            inProgress && vt != null -> {
                val progressText = when (currentStatus) {
                    VtStatus.UPLOADING -> stringResource(R.string.apk_info_vt_uploading, vt.uploadProgress)
                    VtStatus.QUEUED -> stringResource(R.string.apk_info_vt_queued)
                    VtStatus.ANALYZING -> stringResource(R.string.apk_info_vt_analyzing)
                    else -> stringResource(R.string.apk_info_vt_scanning)
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            currentStatus == VtStatus.CLEAN && vt != null -> {
                val totalEngines = vt.malicious + vt.suspicious + vt.harmless + vt.undetected
                val tallySuffix = if (totalEngines > 0) " (0/$totalEngines)" else ""
                val label = stringResource(R.string.apk_info_vt_chip_clean) + tallySuffix

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = apkInfo.sha256.isNotBlank()) {
                            uriHandler.openUri("https://www.virustotal.com/gui/file/${apkInfo.sha256}/detection")
                        }
                        .background(extendedColors.success.copy(alpha = 0.12f))
                        .border(
                            BorderStroke(0.5.dp, extendedColors.success.copy(alpha = 0.35f)),
                            RoundedCornerShape(50),
                        )
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.GppGood,
                        contentDescription = null,
                        tint = extendedColors.success,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = extendedColors.success,
                    )
                    if (apkInfo.sha256.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = extendedColors.success.copy(alpha = 0.7f),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }

            (currentStatus == VtStatus.MALICIOUS || currentStatus == VtStatus.SUSPICIOUS) && vt != null -> {
                val threatCount = vt.malicious + vt.suspicious
                val isSevere = currentStatus == VtStatus.MALICIOUS
                val containerColor = if (isSevere) MaterialTheme.colorScheme.errorContainer else extendedColors.warningContainer
                val contentColor = if (isSevere) MaterialTheme.colorScheme.onErrorContainer else extendedColors.warning
                val label = stringResource(R.string.apk_info_vt_chip_flagged, threatCount)

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = apkInfo.sha256.isNotBlank()) {
                            uriHandler.openUri("https://www.virustotal.com/gui/file/${apkInfo.sha256}/detection")
                        }
                        .background(containerColor)
                        .border(BorderStroke(1.dp, contentColor), RoundedCornerShape(50))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = contentColor,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = contentColor,
                    )
                    if (apkInfo.sha256.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            tint = contentColor.copy(alpha = 0.8f),
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }

            currentStatus == VtStatus.NOT_FOUND -> {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onScan)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.apk_info_vt_upload_action),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            currentStatus == VtStatus.ERROR -> {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onScan)
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.dialog_failed_retry),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                    )
                }
            }

            else -> {
                // Prompt to scan with VirusTotal
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .clickable(onClick = onScan)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(14.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.scan_virustotal_btn),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
