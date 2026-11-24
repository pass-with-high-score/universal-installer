package app.pwhs.universalinstaller.presentation.install.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Dangerous
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VtQuotaStatus
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.ui.theme.LocalExtendedColors

@Composable
fun VirusTotalCard(
    vt: VtResult?,
    fileSizeBytes: Long,
    sha256: String = "",
    onCheck: () -> Unit,
    onOpenSettings: () -> Unit = {},
    onGetKey: () -> Unit = {},
    onOpenLink: () -> Unit = {},
    onUpgradeProClick: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val quotaFlow = remember(context) { VirusTotalKeyManager.observeQuotaStatus(context) }
    val quota by quotaFlow.collectAsState(initial = VtQuotaStatus())
    val isPro = quota.isProUser
    val extendedColors = LocalExtendedColors.current
    val status = vt?.status
    val isPrecheckClean = status == VtStatus.CLEAN && vt.isPrecheckOnly
    val isTooLargeForDeepScan = fileSizeBytes > app.pwhs.core.domain.scanner.impl.VirusDetecScanner.MAX_FILE_SIZE_BYTES
    val inProgress = status == VtStatus.SCANNING ||
        status == VtStatus.UPLOADING ||
        status == VtStatus.QUEUED ||
        status == VtStatus.ANALYZING
    val hasResult = status in setOf(VtStatus.CLEAN, VtStatus.MALICIOUS, VtStatus.SUSPICIOUS) && !isPrecheckClean
    val needsQuotaOrKeyResolution = status in setOf(
        VtStatus.RATE_LIMITED,
        VtStatus.NO_API_KEY,
        VtStatus.INVALID_API_KEY,
    ) || (!quota.isUnlimited && quota.remainingToday == 0 && !hasResult && !inProgress)
    val vtColor = when {
        isPrecheckClean && isTooLargeForDeepScan -> extendedColors.warning
        isPrecheckClean -> MaterialTheme.colorScheme.onSurfaceVariant
        status == VtStatus.CLEAN -> MaterialTheme.colorScheme.primary
        status == VtStatus.MALICIOUS || status == VtStatus.ERROR -> MaterialTheme.colorScheme.error
        status in setOf(
            VtStatus.SUSPICIOUS, VtStatus.NO_API_KEY, VtStatus.INVALID_API_KEY,
            VtStatus.RATE_LIMITED, VtStatus.TOO_LARGE,
        ) -> extendedColors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val vtDesc = when {
        isPrecheckClean && isTooLargeForDeepScan -> stringResource(R.string.apk_info_vt_precheck_too_large)
        isPrecheckClean -> stringResource(R.string.apk_info_vt_precheck_clean)
        status == VtStatus.CLEAN -> stringResource(R.string.apk_info_vt_clean)
        status == VtStatus.MALICIOUS -> stringResource(R.string.apk_info_vt_malicious, vt?.malicious ?: 0)
        status == VtStatus.SUSPICIOUS -> stringResource(R.string.apk_info_vt_suspicious, vt?.suspicious ?: 0)
        status == VtStatus.NOT_FOUND -> stringResource(R.string.apk_info_vt_desc_not_found)
        status == VtStatus.NO_API_KEY -> stringResource(R.string.apk_info_vt_desc_no_api_key)
        status == VtStatus.INVALID_API_KEY -> stringResource(R.string.apk_info_vt_desc_invalid_key)
        status == VtStatus.RATE_LIMITED -> vt?.errorMessage?.takeIf { it.isNotBlank() }
            ?: stringResource(R.string.apk_info_vt_desc_rate_limited_default)
        status == VtStatus.ERROR -> if (vt?.errorMessage.isNullOrBlank()) {
            stringResource(R.string.apk_info_vt_desc_error_default)
        } else {
            vt.errorMessage
        }
        status == VtStatus.TOO_LARGE -> stringResource(R.string.apk_info_vt_too_large, vt?.errorMessage.orEmpty())
        status == VtStatus.SCANNING -> stringResource(R.string.apk_info_vt_desc_scanning)
        status == VtStatus.UPLOADING -> stringResource(R.string.apk_info_vt_desc_uploading, vt?.uploadProgress ?: 0)
        status == VtStatus.QUEUED -> stringResource(R.string.apk_info_vt_desc_queued)
        status == VtStatus.ANALYZING -> stringResource(R.string.apk_info_vt_desc_analyzing)
        else -> null
    }
    val isAlarming = status == VtStatus.MALICIOUS

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.outlinedCardColors(
            containerColor = if (isAlarming) MaterialTheme.colorScheme.errorContainer else Color.Transparent,
        ),
        border = if (isAlarming) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.error)
        } else {
            sectionCardBorder()
        },
    ) {
        Column(modifier = Modifier.padding(16.dp).animateContentSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        isPrecheckClean -> Icons.Rounded.Security
                        status == VtStatus.CLEAN -> Icons.Rounded.GppGood
                        status == VtStatus.MALICIOUS -> Icons.Rounded.Dangerous
                        status == VtStatus.SUSPICIOUS -> Icons.Rounded.Warning
                        else -> Icons.Rounded.Security
                    },
                    contentDescription = null,
                    tint = vtColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.apk_info_vt_scan_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = vtColor,
                )
                if (inProgress) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = vtColor,
                    )
                }
            }

            if (vtDesc != null) {
                Spacer(Modifier.height(8.dp))
                Text(vtDesc, style = MaterialTheme.typography.bodySmall, color = vtColor)
            }

            Spacer(Modifier.height(10.dp))
            AppShieldDailyQuotaBadge()

            if (hasResult && vt != null) {
                val total = vt.malicious + vt.suspicious + vt.harmless + vt.undetected
                Spacer(Modifier.height(12.dp))
                VtBreakdownSection(
                    vt = vt,
                    warningColor = extendedColors.warning,
                    cleanColor = extendedColors.success,
                )
                if (total > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(
                            R.string.apk_info_vt_tally,
                            vt.malicious + vt.suspicious,
                            total,
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (vt.engineResults.isNotEmpty()) {
                    VtEngineList(
                        engines = vt.engineResults,
                        warningColor = extendedColors.warning,
                    )
                }
                TextButton(
                    onClick = onOpenLink,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = stringResource(R.string.apk_info_vt_view_details),
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }

            if (!isPro && needsQuotaOrKeyResolution) {
                Spacer(Modifier.height(10.dp))
                SecurityQuotaExhaustedSection(
                    onAddKey = onOpenSettings,
                    onGetKey = onGetKey,
                    onUpgradeProClick = onUpgradeProClick,
                )
            } else if (isPro && (status == VtStatus.NO_API_KEY || status == VtStatus.INVALID_API_KEY || status == VtStatus.RATE_LIMITED)) {
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onCheck,
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.dialog_failed_retry),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(
                        onClick = onOpenSettings,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.quota_unlock_enter_key),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            } else if (status == VtStatus.NOT_FOUND || (isPrecheckClean && !isTooLargeForDeepScan)) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onCheck,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(
                            if (isPrecheckClean) R.string.apk_info_vt_btn_deep_scan
                            else R.string.apk_info_vt_btn_upload_and_scan
                        ),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else if (status == VtStatus.ERROR) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onCheck,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.dialog_failed_retry),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            } else if (status == null) {
                Spacer(Modifier.height(8.dp))
                FilledTonalButton(
                    onClick = onCheck,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.scan_virustotal_btn),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
