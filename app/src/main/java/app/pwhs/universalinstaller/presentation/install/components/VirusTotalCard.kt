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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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
        VtStatus.SUSPICIOUS, VtStatus.RATE_LIMITED, VtStatus.TOO_LARGE -> extendedColors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val vtDesc = when (status) {
        VtStatus.CLEAN -> stringResource(R.string.apk_info_vt_clean)
        VtStatus.MALICIOUS -> stringResource(R.string.apk_info_vt_malicious, vt?.malicious ?: 0)
        VtStatus.SUSPICIOUS -> stringResource(R.string.apk_info_vt_suspicious, vt?.suspicious ?: 0)
        VtStatus.NOT_FOUND -> "Chưa có kết quả phân tích trong cơ sở dữ liệu."
        VtStatus.RATE_LIMITED -> "Tần suất yêu cầu quá nhanh, vui lòng chờ giây lát."
        VtStatus.ERROR -> if (vt?.errorMessage.isNullOrBlank()) "Không thể kết nối đến dịch vụ bảo mật" else vt.errorMessage
        VtStatus.TOO_LARGE -> stringResource(R.string.apk_info_vt_too_large, vt?.errorMessage.orEmpty())
        VtStatus.SCANNING -> "Đang phân tích chữ ký và manifest..."
        VtStatus.UPLOADING -> "Đang tải tệp lên để phân tích (${vt?.uploadProgress ?: 0}%)..."
        VtStatus.QUEUED -> "Đang chờ hàng đợi phân tích..."
        VtStatus.ANALYZING -> "Đang chạy quét DEX, BadPack & Call Graph..."
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
                    imageVector = when (status) {
                        VtStatus.CLEAN -> Icons.Rounded.GppGood
                        VtStatus.MALICIOUS -> Icons.Rounded.Dangerous
                        VtStatus.SUSPICIOUS -> Icons.Rounded.Warning
                        else -> Icons.Rounded.Security
                    },
                    contentDescription = null,
                    tint = vtColor,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Bảo mật AppShield",
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
                        text = "Xem chi tiết phát hiện",
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

            if (status == VtStatus.NOT_FOUND) {
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
                        text = "Tải lên & Quét bảo mật",
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
                        text = "Quét bảo mật",
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}
