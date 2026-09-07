package app.pwhs.universalinstaller.presentation.install.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
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
        VtStatus.SUSPICIOUS, VtStatus.NO_API_KEY, VtStatus.INVALID_API_KEY,
        VtStatus.RATE_LIMITED, VtStatus.TOO_LARGE -> extendedColors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val vtDesc = when (status) {
        VtStatus.CLEAN -> stringResource(R.string.apk_info_vt_clean)
        VtStatus.MALICIOUS -> stringResource(R.string.apk_info_vt_malicious, vt.malicious)
        VtStatus.SUSPICIOUS -> stringResource(R.string.apk_info_vt_suspicious, vt.suspicious)
        VtStatus.NOT_FOUND -> stringResource(R.string.apk_info_vt_not_found)
        VtStatus.NO_API_KEY -> stringResource(R.string.apk_info_vt_no_api_key)
        VtStatus.INVALID_API_KEY -> stringResource(R.string.apk_info_vt_invalid_key)
        VtStatus.RATE_LIMITED -> vt.errorMessage.takeIf { it.isNotBlank() }
            ?.let { stringResource(R.string.apk_info_vt_rate_limited_retry, it) }
            ?: stringResource(R.string.apk_info_vt_rate_limited)
        VtStatus.ERROR -> vt.errorMessage.takeIf { it.isNotBlank() } ?: stringResource(R.string.apk_info_vt_error)
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
                Icon(Icons.Rounded.Security, null, tint = vtColor, modifier = Modifier.size(20.dp))
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
            if (status == VtStatus.NO_API_KEY || status == VtStatus.INVALID_API_KEY) {
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

@Composable
fun VtBreakdownSection(vt: VtResult, warningColor: Color, cleanColor: Color) {
    val total = (vt.malicious + vt.suspicious + vt.harmless + vt.undetected).coerceAtLeast(1)
    val malFraction = vt.malicious.toFloat() / total
    val susFraction = vt.suspicious.toFloat() / total
    val cleanFraction = (vt.harmless + vt.undetected).toFloat() / total
    val errorColor = MaterialTheme.colorScheme.error
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(MaterialTheme.shapes.small),
    ) {
        val w = size.width
        val h = size.height
        var x = 0f
        val malW = w * malFraction
        if (malW > 0f) {
            drawRect(color = errorColor, topLeft = Offset(x, 0f), size = Size(malW, h))
            x += malW
        }
        val susW = w * susFraction
        if (susW > 0f) {
            drawRect(color = warningColor, topLeft = Offset(x, 0f), size = Size(susW, h))
            x += susW
        }
        val cleanW = w * cleanFraction
        if (cleanW > 0f) {
            drawRect(color = cleanColor, topLeft = Offset(x, 0f), size = Size(cleanW, h))
            x += cleanW
        }
        drawRect(color = Color.Gray.copy(alpha = 0.3f), topLeft = Offset(x, 0f), size = Size(w - x, h))
    }
}

@Composable
fun VtEngineList(engines: List<VtEngineResult>, warningColor: Color) {
    var expanded by remember { mutableStateOf(false) }
    val flagged = engines.filter { it.category == "malicious" || it.category == "suspicious" }
    val visible = if (expanded) engines else flagged
    Column(modifier = Modifier.fillMaxWidth()) {
        if (visible.isNotEmpty()) Spacer(Modifier.height(8.dp))
        visible.forEach { engine ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = engine.engineName,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = engine.result ?: engine.category,
                    style = MaterialTheme.typography.bodySmall,
                    color = when (engine.category) {
                        "malicious" -> MaterialTheme.colorScheme.error
                        "suspicious" -> warningColor
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        TextButton(
            onClick = { expanded = !expanded },
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
        ) {
            Text(
                text = if (expanded) {
                    stringResource(R.string.apk_info_vt_engines_hide)
                } else {
                    stringResource(R.string.apk_info_vt_engines_show)
                },
                style = MaterialTheme.typography.labelMedium,
            )
        }
    }
}

@Composable
fun VtStatusChip(
    vt: VtResult,
    onClick: (() -> Unit)? = null,
    onOpenWeb: (() -> Unit)? = null,
    onOpenSettings: (() -> Unit)? = null,
) {
    val extendedColors = LocalExtendedColors.current
    when (vt.status) {
        VtStatus.CLEAN -> {
            val totalEngines = vt.malicious + vt.suspicious + vt.harmless + vt.undetected
            val tallySuffix = if (totalEngines > 0) " (0/$totalEngines)" else ""
            InfoChip(
                label = stringResource(R.string.apk_info_vt_chip_clean) + tallySuffix,
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.GppGood,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = extendedColors.success,
                    )
                },
                contentColor = extendedColors.success,
                onClick = onOpenWeb,
            )
        }
        VtStatus.MALICIOUS, VtStatus.SUSPICIOUS -> {
            val alarming = vt.status == VtStatus.MALICIOUS
            InfoChip(
                label = stringResource(R.string.apk_info_vt_chip_flagged, vt.malicious + vt.suspicious),
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Warning,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = if (alarming) MaterialTheme.colorScheme.onErrorContainer else extendedColors.warning,
                    )
                },
                containerColor = if (alarming) MaterialTheme.colorScheme.errorContainer else extendedColors.warningContainer,
                contentColor = if (alarming) MaterialTheme.colorScheme.onErrorContainer else extendedColors.warning,
                onClick = onOpenWeb,
            )
        }
        VtStatus.SCANNING, VtStatus.UPLOADING, VtStatus.QUEUED, VtStatus.ANALYZING -> {
            val progressText = when (vt.status) {
                VtStatus.UPLOADING -> stringResource(R.string.apk_info_vt_uploading, vt.uploadProgress)
                VtStatus.QUEUED -> stringResource(R.string.apk_info_vt_queued)
                VtStatus.ANALYZING -> stringResource(R.string.apk_info_vt_analyzing)
                else -> stringResource(R.string.apk_info_vt_chip_scanning)
            }
            InfoChip(
                label = progressText,
                leadingIcon = {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
            )
        }
        VtStatus.NOT_FOUND -> InfoChip(
            label = stringResource(R.string.apk_info_vt_upload_action),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.CloudUpload,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            contentColor = MaterialTheme.colorScheme.primary,
            onClick = onClick,
        )
        VtStatus.ERROR -> InfoChip(
            label = stringResource(R.string.dialog_failed_retry),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            contentColor = MaterialTheme.colorScheme.error,
            onClick = onClick,
        )
        VtStatus.NO_API_KEY, VtStatus.INVALID_API_KEY -> InfoChip(
            label = stringResource(R.string.apk_info_vt_add_key),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = extendedColors.warning,
                )
            },
            contentColor = extendedColors.warning,
            onClick = onOpenSettings,
        )
        else -> InfoChip(
            label = stringResource(R.string.scan_virustotal_btn),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            contentColor = MaterialTheme.colorScheme.primary,
            onClick = onClick,
        )
    }
}
