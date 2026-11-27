package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.billing.BillingManager
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.presentation.install.components.SecurityQuotaUnlockSheet
import app.pwhs.universalinstaller.presentation.pro.UpgradeProBottomSheet
import app.pwhs.universalinstaller.ui.theme.LocalExtendedColors
import org.koin.compose.koinInject

/**
 * Full-width security scan status card displayed on the installation prepare dialog.
 *
 * Replaces the old small FlowRow chip with a prominent card featuring:
 * - LinearProgressIndicator + active step description + step counter while scanning
 * - Clear status summary and tap actions when complete, flagged, uncached, or errored
 * - Tapping during a scan or initiating a deep scan opens [VirusScanProgressBottomSheet]
 */
@Composable
fun VirusTotalInstallPrompt(
    apkInfo: ApkInfo,
    onScan: () -> Unit,
    onOpenScanProgress: () -> Unit = {},
    modifier: Modifier = Modifier,
    billingManager: BillingManager = koinInject(),
) {
    val extendedColors = LocalExtendedColors.current
    val vt = apkInfo.vtResult
    val status = vt?.status
    var showDetailSheet by remember { mutableStateOf(false) }
    var showQuotaSheet by remember { mutableStateOf(false) }
    var showUpgradeProSheet by remember { mutableStateOf(false) }

    val inProgress = vt.isScanInProgress()

    AnimatedContent(
        targetState = status,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(140)) },
        label = "VirusTotalCardAnimation",
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
    ) { currentStatus ->
        when {
            inProgress && vt != null -> {
                val hasSha256 = apkInfo.sha256.isNotBlank()
                val uiProgress = computeScanUiProgress(vt, hasSha256)
                val animatedProgress by animateFloatAsState(
                    targetValue = uiProgress.fraction,
                    animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
                    label = "CardLinearProgress",
                )
                val stepLabel = activeScanStepLabel(vt, hasSha256)

                Surface(
                    onClick = onOpenScanProgress,
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.28f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.vt_scan_sheet_title),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = stringResource(
                                    R.string.vt_scan_step_counter,
                                    uiProgress.stepIndex,
                                    uiProgress.totalSteps,
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium,
                            )
                        }

                        Spacer(Modifier.height(6.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = stepLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${(animatedProgress * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }

                        Spacer(Modifier.height(8.dp))

                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .clip(RoundedCornerShape(50)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                            strokeCap = StrokeCap.Round,
                        )
                    }
                }
            }

            currentStatus == VtStatus.CLEAN && vt != null && vt.isPrecheckOnly -> {
                val isTooLarge = apkInfo.fileSizeBytes > app.pwhs.core.domain.scanner.impl.VirusDetecScanner.MAX_FILE_SIZE_BYTES
                val accent = if (isTooLarge) extendedColors.warning else MaterialTheme.colorScheme.primary
                val bg = if (isTooLarge) {
                    extendedColors.warning.copy(alpha = 0.10f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerHigh
                }
                SecurityStatusCard(
                    icon = Icons.Rounded.Security,
                    title = stringResource(
                        if (isTooLarge) R.string.apk_info_vt_chip_precheck_too_large
                        else R.string.apk_info_vt_chip_precheck,
                    ),
                    subtitle = stringResource(
                        if (isTooLarge) R.string.apk_info_vt_precheck_too_large
                        else R.string.apk_info_vt_precheck_clean,
                    ),
                    accentColor = accent,
                    containerColor = bg,
                    borderColor = accent.copy(alpha = 0.30f),
                    onClick = {
                        if (isTooLarge) {
                            showDetailSheet = true
                        } else {
                            onScan()
                            onOpenScanProgress()
                        }
                    },
                )
            }

            currentStatus == VtStatus.CLEAN && vt != null -> {
                val scoreSuffix = if (vt.report != null) {
                    " (${vt.report!!.riskScore}/100)"
                } else {
                    val totalEngines = vt.malicious + vt.suspicious + vt.harmless + vt.undetected
                    if (totalEngines > 0) " (0/$totalEngines)" else ""
                }
                SecurityStatusCard(
                    icon = Icons.Rounded.GppGood,
                    title = stringResource(R.string.apk_info_vt_chip_clean) + scoreSuffix,
                    subtitle = stringResource(R.string.vt_scan_card_tap_details),
                    accentColor = extendedColors.success,
                    containerColor = extendedColors.success.copy(alpha = 0.10f),
                    borderColor = extendedColors.success.copy(alpha = 0.35f),
                    onClick = { showDetailSheet = true },
                )
            }

            (currentStatus == VtStatus.MALICIOUS || currentStatus == VtStatus.SUSPICIOUS) && vt != null -> {
                val threatCount = vt.malicious + vt.suspicious
                val isSevere = currentStatus == VtStatus.MALICIOUS
                val containerColor = if (isSevere) MaterialTheme.colorScheme.errorContainer else extendedColors.warningContainer
                val contentColor = if (isSevere) MaterialTheme.colorScheme.onErrorContainer else extendedColors.warning
                val titleText = if (vt.report != null) {
                    stringResource(R.string.apk_info_vt_chip_flagged, threatCount) + " (${vt.report!!.riskScore}/100)"
                } else {
                    stringResource(R.string.apk_info_vt_chip_flagged, threatCount)
                }
                SecurityStatusCard(
                    icon = Icons.Rounded.Warning,
                    title = titleText,
                    subtitle = stringResource(R.string.apk_info_vt_view_details),
                    accentColor = contentColor,
                    containerColor = containerColor,
                    borderColor = contentColor.copy(alpha = 0.6f),
                    onClick = { showDetailSheet = true },
                )
            }

            currentStatus == VtStatus.NOT_FOUND -> {
                SecurityStatusCard(
                    icon = Icons.Rounded.CloudUpload,
                    title = stringResource(R.string.apk_info_vt_btn_upload_and_scan),
                    subtitle = stringResource(R.string.apk_info_vt_desc_not_found),
                    accentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    onClick = {
                        onScan()
                        onOpenScanProgress()
                    },
                )
            }

            currentStatus == VtStatus.RATE_LIMITED ||
                currentStatus == VtStatus.NO_API_KEY ||
                currentStatus == VtStatus.INVALID_API_KEY -> {
                val title = when (currentStatus) {
                    VtStatus.RATE_LIMITED -> stringResource(R.string.apk_info_vt_chip_rate_limited)
                    VtStatus.INVALID_API_KEY -> stringResource(R.string.apk_info_vt_chip_invalid_key)
                    else -> stringResource(
                        if (billingManager.isAvailable) R.string.apk_info_vt_chip_need_key
                        else R.string.apk_info_vt_chip_need_key_opensource,
                    )
                }
                val subtitle = when (currentStatus) {
                    VtStatus.RATE_LIMITED -> stringResource(R.string.apk_info_vt_desc_rate_limited_default)
                    VtStatus.INVALID_API_KEY -> stringResource(R.string.apk_info_vt_desc_invalid_key)
                    else -> stringResource(R.string.apk_info_vt_desc_no_api_key)
                }
                SecurityStatusCard(
                    icon = Icons.Rounded.Warning,
                    title = title,
                    subtitle = subtitle,
                    accentColor = extendedColors.warning,
                    containerColor = extendedColors.warningContainer,
                    borderColor = extendedColors.warning.copy(alpha = 0.45f),
                    onClick = { showQuotaSheet = true },
                )
            }

            currentStatus == VtStatus.ERROR -> {
                SecurityStatusCard(
                    icon = Icons.Rounded.Refresh,
                    title = stringResource(R.string.dialog_failed_retry),
                    subtitle = stringResource(R.string.apk_info_vt_desc_error_default),
                    accentColor = MaterialTheme.colorScheme.error,
                    containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                    borderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                    onClick = {
                        onScan()
                        onOpenScanProgress()
                    },
                )
            }

            else -> {
                SecurityStatusCard(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.scan_virustotal_btn),
                    subtitle = stringResource(R.string.apk_info_vt_scan_title),
                    accentColor = MaterialTheme.colorScheme.primary,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    borderColor = MaterialTheme.colorScheme.outlineVariant,
                    onClick = {
                        onScan()
                        onOpenScanProgress()
                    },
                )
            }
        }
    }

    if (showDetailSheet && vt != null) {
        VirusTotalDetailSheet(
            vt = vt,
            apkInfo = apkInfo,
            onDismiss = { showDetailSheet = false },
        )
    }

    if (showQuotaSheet) {
        SecurityQuotaUnlockSheet(
            onDismiss = { showQuotaSheet = false },
            onKeySaved = {
                showQuotaSheet = false
                onScan()
                onOpenScanProgress()
            },
            onUpgradeProClick = if (billingManager.isAvailable) {
                { showUpgradeProSheet = true }
            } else {
                null
            },
        )
    }

    if (showUpgradeProSheet) {
        UpgradeProBottomSheet(
            billingManager = billingManager,
            onDismissRequest = { showUpgradeProSheet = false },
            source = app.pwhs.core.telemetry.TelemetryEvents.SOURCE_INSTALL_DIALOG,
        )
    }
}

@Composable
private fun SecurityStatusCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    accentColor: Color,
    containerColor: Color,
    borderColor: Color,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = containerColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = accentColor,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}
