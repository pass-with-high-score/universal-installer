package app.pwhs.universalinstaller.presentation.install.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.collectAsState
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.network.VtQuotaStatus
import app.pwhs.core.presentation.components.VirusTotalDailyQuotaBadge
import app.pwhs.core.presentation.components.VirusTotalUnlimitedOptionsSection
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.VtEngineResult
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
    val inProgress = status == VtStatus.SCANNING ||
        status == VtStatus.UPLOADING ||
        status == VtStatus.QUEUED ||
        status == VtStatus.ANALYZING
    val hasResult = status in setOf(VtStatus.CLEAN, VtStatus.MALICIOUS, VtStatus.SUSPICIOUS)
    val vtColor = when (status) {
        VtStatus.CLEAN -> MaterialTheme.colorScheme.primary
        VtStatus.MALICIOUS, VtStatus.ERROR -> MaterialTheme.colorScheme.error
        VtStatus.SUSPICIOUS, VtStatus.NO_API_KEY, VtStatus.INVALID_API_KEY,
        VtStatus.RATE_LIMITED, VtStatus.TOO_LARGE -> extendedColors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val vtDesc = when (status) {
        VtStatus.CLEAN -> stringResource(R.string.apk_info_vt_clean)
        VtStatus.MALICIOUS -> stringResource(R.string.apk_info_vt_malicious, vt.malicious)
        VtStatus.SUSPICIOUS -> stringResource(R.string.apk_info_vt_suspicious, vt.suspicious)
        VtStatus.NOT_FOUND -> stringResource(R.string.apk_info_vt_not_found)
        VtStatus.NO_API_KEY -> if (isPro) {
            stringResource(R.string.vt_cloud_keys_temporary_unavailable)
        } else {
            stringResource(R.string.apk_info_vt_no_api_key)
        }
        VtStatus.INVALID_API_KEY -> if (isPro && !quota.hasPersonalKey) {
            stringResource(R.string.vt_cloud_keys_temporary_unavailable)
        } else {
            stringResource(R.string.apk_info_vt_invalid_key)
        }
        VtStatus.RATE_LIMITED -> vt.errorMessage.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.apk_info_vt_rate_limited_retry, it) }
            ?: stringResource(R.string.apk_info_vt_rate_limited)
        VtStatus.ERROR -> when {
            vt.errorMessage == app.pwhs.universalinstaller.data.remote.VirusTotalService.NETWORK_ERROR_TAG ->
                stringResource(R.string.apk_info_vt_network_error)
            vt.errorMessage.isNotBlank() && vt.errorMessage != "Unknown error" -> vt.errorMessage
            else -> stringResource(R.string.apk_info_vt_error)
        }
        VtStatus.TOO_LARGE -> stringResource(R.string.apk_info_vt_too_large, vt.errorMessage.orEmpty())
        VtStatus.SCANNING -> stringResource(R.string.apk_info_vt_scanning)
        VtStatus.UPLOADING -> stringResource(R.string.apk_info_vt_uploading, vt.uploadProgress)
        VtStatus.QUEUED -> stringResource(R.string.apk_info_vt_queued)
        VtStatus.ANALYZING -> stringResource(R.string.apk_info_vt_analyzing)
        null -> null
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
                Image(
                    painter = painterResource(R.drawable.ic_virustotal_logo),
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
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
            VirusTotalDailyQuotaBadge()
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
                if (sha256.isNotBlank()) {
                    TextButton(
                        onClick = onOpenLink,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.apk_info_vt_open_web),
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
            }
            if (!isPro && (status == VtStatus.NO_API_KEY || status == VtStatus.INVALID_API_KEY)) {
                Spacer(Modifier.height(10.dp))
                VirusTotalUnlimitedOptionsSection(
                    showOption1Card = false,
                    onUpgradeProClick = onUpgradeProClick ?: onOpenSettings,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onOpenSettings,
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.apk_info_vt_add_key),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(
                        onClick = onGetKey,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.apk_info_vt_get_key),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            } else if (isPro && (status == VtStatus.NO_API_KEY || status == VtStatus.INVALID_API_KEY)) {
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
                            text = stringResource(R.string.apk_info_vt_add_key),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            } else if (status == VtStatus.NOT_FOUND) {
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
                        text = stringResource(R.string.apk_info_vt_upload_action),
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
                    Image(
                        painter = painterResource(R.drawable.ic_virustotal_logo),
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

