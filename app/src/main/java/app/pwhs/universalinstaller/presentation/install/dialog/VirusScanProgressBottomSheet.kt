package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.ProgressIndicatorDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.domain.model.VtScanPhase
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.ui.theme.LocalExtendedColors
import kotlinx.coroutines.delay

/**
 * Dedicated BottomSheet showing live virus scan progress with a LinearProgressIndicator
 * and a step-by-step checklist. Shown when the user taps the scan card, triggers a deep scan,
 * or taps Install while a security scan is still running.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirusScanProgressBottomSheet(
    apkInfo: ApkInfo,
    pendingInstall: Boolean,
    onDismiss: () -> Unit,
    onProceedInstall: () -> Unit,
    onViewDetails: () -> Unit,
    onRetryScan: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extendedColors = LocalExtendedColors.current
    val vt = apkInfo.vtResult
    val inProgress = vt.isScanInProgress()
    val hasSha256 = apkInfo.sha256.isNotBlank()
    val uiProgress = computeScanUiProgress(vt, hasSha256)
    val animatedProgress by animateFloatAsState(
        targetValue = uiProgress.fraction,
        animationSpec = ProgressIndicatorDefaults.ProgressAnimationSpec,
        label = "VirusScanLinearProgress",
    )

    val isClean = vt?.status == VtStatus.CLEAN
    val isFlagged = vt?.status == VtStatus.MALICIOUS || vt?.status == VtStatus.SUSPICIOUS
    val isError = vt?.status in setOf(
        VtStatus.ERROR,
        VtStatus.TOO_LARGE,
        VtStatus.RATE_LIMITED,
        VtStatus.NO_API_KEY,
        VtStatus.INVALID_API_KEY,
    )

    // Auto-proceed to install shortly after scan finishes clean when opened from Install button.
    LaunchedEffect(pendingInstall, vt?.status) {
        if (pendingInstall && vt?.status == VtStatus.CLEAN) {
            delay(500)
            onProceedInstall()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            // ── Header ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val (iconBg, iconTint, iconVector) = when {
                    isClean -> Triple(
                        extendedColors.success.copy(alpha = 0.14f),
                        extendedColors.success,
                        Icons.Rounded.GppGood,
                    )
                    isFlagged -> Triple(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.error,
                        Icons.Rounded.Warning,
                    )
                    isError -> Triple(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                        MaterialTheme.colorScheme.error,
                        Icons.Rounded.ErrorOutline,
                    )
                    else -> Triple(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.primary,
                        Icons.Rounded.Security,
                    )
                }

                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(iconBg),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp),
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.vt_scan_sheet_title),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = apkInfo.appName.ifBlank { apkInfo.packageName },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (inProgress) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.vt_scan_step_counter,
                                uiProgress.stepIndex,
                                uiProgress.totalSteps,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(18.dp))

            // ── Linear Progress Bar + Live Status Text ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val statusLabel = when {
                    inProgress -> activeScanStepLabel(vt, hasSha256)
                    isClean && pendingInstall -> stringResource(R.string.vt_scan_sheet_clean_proceeding)
                    isClean -> stringResource(R.string.apk_info_vt_clean)
                    isFlagged -> stringResource(
                        R.string.apk_info_vt_chip_flagged,
                        (vt?.malicious ?: 0) + (vt?.suspicious ?: 0),
                    )
                    vt?.status == VtStatus.NOT_FOUND -> stringResource(R.string.apk_info_vt_not_found)
                    else -> vt?.errorMessage?.ifBlank { stringResource(R.string.apk_info_vt_desc_error_default) }
                        ?: stringResource(R.string.apk_info_vt_desc_error_default)
                }
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = when {
                        isClean -> extendedColors.success
                        isFlagged || isError -> MaterialTheme.colorScheme.error
                        else -> MaterialTheme.colorScheme.onSurface
                    },
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            val barColor = when {
                isClean -> extendedColors.success
                isFlagged || isError -> MaterialTheme.colorScheme.error
                else -> MaterialTheme.colorScheme.primary
            }

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(50)),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
            )

            Spacer(modifier = Modifier.height(18.dp))

            // ── Step-by-step Checklist ──
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val currentStep = uiProgress.stepIndex
                    val allDone = isClean || isFlagged

                    fun stateFor(stepNumber: Int): ScanStepVisualState = when {
                        allDone || currentStep > stepNumber -> ScanStepVisualState.DONE
                        inProgress && currentStep == stepNumber -> ScanStepVisualState.ACTIVE
                        else -> ScanStepVisualState.PENDING
                    }

                    ScanStepChecklistRow(
                        label = stringResource(R.string.vt_scan_step_hashing),
                        subtitle = if (hasSha256) apkInfo.sha256.take(16) + "…" else null,
                        state = stateFor(1),
                    )
                    ScanStepChecklistRow(
                        label = stringResource(R.string.vt_scan_step_lookup),
                        subtitle = if (vt?.scanPhase == VtScanPhase.FALLBACK) {
                            stringResource(R.string.vt_scan_step_fallback)
                        } else null,
                        state = stateFor(2),
                    )
                    if (uiProgress.isDeepScan) {
                        ScanStepChecklistRow(
                            label = if (vt?.status == VtStatus.UPLOADING && vt.uploadProgress > 0) {
                                stringResource(R.string.vt_scan_step_upload_progress, vt.uploadProgress)
                            } else {
                                stringResource(R.string.vt_scan_step_upload)
                            },
                            subtitle = null,
                            state = stateFor(3),
                        )
                        ScanStepChecklistRow(
                            label = stringResource(R.string.vt_scan_step_analyzing),
                            subtitle = vt?.stageDescription?.takeIf { it.isNotBlank() && stateFor(4) == ScanStepVisualState.ACTIVE },
                            state = stateFor(4),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // ── Action Buttons ──
            when {
                inProgress && pendingInstall -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.dialog_cancel_btn))
                        }
                        OutlinedButton(
                            onClick = onProceedInstall,
                            modifier = Modifier.weight(1.4f),
                        ) {
                            Text(
                                text = stringResource(R.string.vt_scan_sheet_skip_install),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
                inProgress -> {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.vt_scan_sheet_run_background))
                    }
                }
                isFlagged -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onViewDetails()
                            },
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.apk_info_vt_view_details), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        Button(
                            onClick = onProceedInstall,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error,
                                contentColor = MaterialTheme.colorScheme.onError,
                            ),
                        ) {
                            Text(stringResource(R.string.vt_scan_sheet_install_now), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
                isError -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        OutlinedButton(
                            onClick = onRetryScan,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(stringResource(R.string.dialog_failed_retry))
                        }
                        if (pendingInstall) {
                            Button(
                                onClick = onProceedInstall,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.vt_scan_sheet_install_now))
                            }
                        } else {
                            Button(
                                onClick = onDismiss,
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.dialog_cancel_btn))
                            }
                        }
                    }
                }
                else -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        if (vt != null && vt.status == VtStatus.CLEAN) {
                            OutlinedButton(
                                onClick = {
                                    onDismiss()
                                    onViewDetails()
                                },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(stringResource(R.string.apk_info_vt_view_details), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Button(
                            onClick = if (pendingInstall) onProceedInstall else onDismiss,
                            modifier = Modifier.weight(1f),
                        ) {
                            Text(
                                text = if (pendingInstall) stringResource(R.string.vt_scan_sheet_install_now)
                                else stringResource(android.R.string.ok),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanStepChecklistRow(
    label: String,
    subtitle: String?,
    state: ScanStepVisualState,
) {
    val extendedColors = LocalExtendedColors.current
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        when (state) {
            ScanStepVisualState.DONE -> {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = extendedColors.success,
                    modifier = Modifier.size(18.dp),
                )
            }
            ScanStepVisualState.ACTIVE -> {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ScanStepVisualState.PENDING -> {
                Icon(
                    imageVector = Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.outlineVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (state == ScanStepVisualState.ACTIVE) FontWeight.SemiBold else FontWeight.Normal,
                color = when (state) {
                    ScanStepVisualState.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    else -> MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
