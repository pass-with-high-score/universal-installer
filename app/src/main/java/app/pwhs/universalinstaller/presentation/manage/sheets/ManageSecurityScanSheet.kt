package app.pwhs.universalinstaller.presentation.manage.sheets

import android.content.ClipboardManager
import android.widget.Toast
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.pwhs.core.domain.model.VtResult
import app.pwhs.core.domain.model.VtStatus
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.install.components.AppShieldDailyQuotaBadge
import app.pwhs.universalinstaller.presentation.install.components.SecurityFindingsSection
import app.pwhs.universalinstaller.presentation.install.components.VtBreakdownSection
import app.pwhs.universalinstaller.ui.theme.LocalExtendedColors
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import coil3.request.crossfade
import app.pwhs.universalinstaller.util.AppIconData

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageSecurityScanSheet(
    app: InstalledApp,
    vtResult: VtResult?,
    sha256: String = "",
    onDismiss: () -> Unit,
    onRescan: () -> Unit,
) {
    val context = LocalContext.current
    val extendedColors = LocalExtendedColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val status = vtResult?.status ?: VtStatus.SCANNING
    val isScanning = status == VtStatus.SCANNING ||
        status == VtStatus.UPLOADING ||
        status == VtStatus.QUEUED ||
        status == VtStatus.ANALYZING

    val totalEngines = (vtResult?.malicious ?: 0) + (vtResult?.suspicious ?: 0) +
        (vtResult?.harmless ?: 0) + (vtResult?.undetected ?: 0)
    val threatCount = (vtResult?.malicious ?: 0) + (vtResult?.suspicious ?: 0)
    val isClean = status == VtStatus.CLEAN || (status == VtStatus.SCANNING.takeIf { false } && threatCount == 0)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
                    .padding(bottom = if (isScanning) 32.dp else 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(AppIconData(app.packageName))
                        .crossfade(true)
                        .build(),
                    contentDescription = null,
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp)),
                    success = { SubcomposeAsyncImageContent() },
                    error = {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Security,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    },
                )

                Spacer(modifier = Modifier.width(14.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.security_detail_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${app.appName} (${app.versionName})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Quota Badge
            AppShieldDailyQuotaBadge()

            // Body depending on scan status
            if (isScanning) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(48.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 4.dp,
                        )

                        val stepText = when (status) {
                            VtStatus.UPLOADING -> stringResource(R.string.apk_info_vt_desc_uploading, vtResult?.uploadProgress ?: 0)
                            VtStatus.QUEUED -> stringResource(R.string.apk_info_vt_desc_queued)
                            VtStatus.ANALYZING -> stringResource(R.string.apk_info_vt_desc_analyzing)
                            else -> stringResource(R.string.apk_info_vt_desc_scanning)
                        }

                        Text(
                            text = stepText,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }
            } else {
                when (status) {
                    VtStatus.CLEAN -> {
                        val report = vtResult?.report
                        ScanResultBanner(
                            isClean = true,
                            title = stringResource(R.string.virustotal_detail_clean_title),
                            desc = if (report != null) {
                                stringResource(R.string.virustotal_detail_score_desc, report.riskScore)
                            } else {
                                stringResource(R.string.virustotal_detail_clean_desc, totalEngines)
                            },
                        )
                        if (vtResult != null) {
                            VtBreakdownSection(
                                vt = vtResult,
                                warningColor = MaterialTheme.colorScheme.error,
                                cleanColor = extendedColors.success,
                            )
                            vtResult.report?.let { report ->
                                SecurityFindingsSection(report = report)
                            }
                        }
                    }
                    VtStatus.MALICIOUS, VtStatus.SUSPICIOUS -> {
                        ScanResultBanner(
                            isClean = false,
                            title = stringResource(R.string.virustotal_detail_flagged_title, threatCount),
                            desc = stringResource(R.string.virustotal_detail_flagged_desc),
                        )
                        if (vtResult != null) {
                            VtBreakdownSection(
                                vt = vtResult,
                                warningColor = MaterialTheme.colorScheme.error,
                                cleanColor = extendedColors.success,
                            )
                            vtResult.report?.let { report ->
                                SecurityFindingsSection(report = report)
                            }
                        }
                    }
                    VtStatus.NOT_FOUND -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp),
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.apk_info_vt_not_found),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.apk_info_vt_desc_not_found),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    VtStatus.RATE_LIMITED -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = extendedColors.warning.copy(alpha = 0.12f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = extendedColors.warning,
                                    modifier = Modifier.size(32.dp),
                                )
                                Column {
                                    Text(
                                        text = stringResource(R.string.apk_info_vt_desc_rate_limited_default),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = extendedColors.warning,
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = stringResource(R.string.quota_badge_exhausted, 3, 3),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    else -> {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Warning,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(32.dp),
                                )
                                Column {
                                    Text(
                                        text = vtResult?.errorMessage?.ifBlank { null }
                                            ?: stringResource(R.string.apk_info_vt_desc_error_default),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                }
                            }
                        }
                    }
                }

                // SHA-256 Hash Card
                if (sha256.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "SHA-256",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = sha256,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(
                                onClick = {
                                    val cm = context.getSystemService<ClipboardManager>()
                                    cm?.setPrimaryClip(android.content.ClipData.newPlainText("SHA-256", sha256))
                                    Toast.makeText(context, context.getString(R.string.virustotal_detail_sha256_copied), Toast.LENGTH_SHORT).show()
                                },
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.ContentCopy,
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
            }

            // Pinned bottom actions (always visible without scrolling)
            if (!isScanning) {
                ManageSecurityScanFooter(
                    app = app,
                    sha256 = sha256,
                    vtResult = vtResult,
                    onRescan = onRescan,
                    onDismiss = onDismiss,
                )
            }
        }
    }
}

@Composable
private fun ScanResultBanner(
    isClean: Boolean,
    title: String,
    desc: String,
) {
    val extendedColors = LocalExtendedColors.current
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = if (isClean) {
            extendedColors.success.copy(alpha = 0.12f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        },
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = if (isClean) {
                extendedColors.success.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
            },
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (isClean) Icons.Rounded.GppGood else Icons.Rounded.Warning,
                contentDescription = null,
                tint = if (isClean) extendedColors.success else MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp),
            )

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (isClean) extendedColors.success else MaterialTheme.colorScheme.error,
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
