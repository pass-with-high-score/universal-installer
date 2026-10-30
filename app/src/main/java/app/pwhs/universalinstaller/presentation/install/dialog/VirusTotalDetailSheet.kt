package app.pwhs.universalinstaller.presentation.install.dialog

import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.GppGood
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.pwhs.core.telemetry.AnalyticsHelper
import app.pwhs.core.util.openUriSafely
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtEngineResult
import app.pwhs.universalinstaller.domain.model.VtResult
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.presentation.install.components.VtBreakdownSection
import app.pwhs.universalinstaller.ui.theme.LocalExtendedColors

/**
 * BottomSheet displaying complete VirusTotal inspection details and engine breakdown
 * when a user clicks on the VirusTotal status chip.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VirusTotalDetailSheet(
    vt: VtResult,
    apkInfo: ApkInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val extendedColors = LocalExtendedColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val totalEngines = vt.malicious + vt.suspicious + vt.harmless + vt.undetected
    val threatCount = vt.malicious + vt.suspicious
    val isClean = vt.status == VtStatus.CLEAN || (vt.malicious == 0 && vt.suspicious == 0)

    val sortedEngines = remember(vt.engineResults) {
        vt.engineResults.sortedWith(
            compareByDescending<VtEngineResult> { it.category == "malicious" }
                .thenByDescending { it.category == "suspicious" }
                .thenBy { it.engineName.lowercase() }
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // ── Header: Logo, Title & Close Button ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Image(
                    painter = painterResource(R.drawable.ic_virustotal_logo),
                    contentDescription = "VirusTotal",
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape),
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.virustotal_detail_sheet_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = apkInfo.appName.ifBlank { apkInfo.packageName },
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

            // ── Status Banner Card ──
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
                            text = if (isClean) {
                                stringResource(R.string.virustotal_detail_clean_title)
                            } else {
                                stringResource(R.string.virustotal_detail_flagged_title, threatCount)
                            },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isClean) extendedColors.success else MaterialTheme.colorScheme.error,
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (isClean) {
                                stringResource(R.string.virustotal_detail_clean_desc, totalEngines)
                            } else {
                                stringResource(R.string.virustotal_detail_flagged_desc)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            // ── Engine Stats Counter & Breakdown Bar ──
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    VtCounterBadge(
                        label = stringResource(R.string.virustotal_detail_stat_clean),
                        count = vt.harmless + vt.undetected,
                        color = extendedColors.success,
                    )
                    VtCounterBadge(
                        label = stringResource(R.string.virustotal_detail_stat_suspicious),
                        count = vt.suspicious,
                        color = extendedColors.warning,
                    )
                    VtCounterBadge(
                        label = stringResource(R.string.virustotal_detail_stat_malicious),
                        count = vt.malicious,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                VtBreakdownSection(
                    vt = vt,
                    warningColor = extendedColors.warning,
                    cleanColor = extendedColors.success,
                )
            }

            // ── Scrollable Antivirus Engine Results List ──
            if (sortedEngines.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.virustotal_detail_engine_title, sortedEngines.size),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 220.dp),
                ) {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    ) {
                        items(sortedEngines, key = { it.engineName }) { engine ->
                            val isThreat = engine.category == "malicious" || engine.category == "suspicious"
                            val engineColor = when (engine.category) {
                                "malicious" -> MaterialTheme.colorScheme.error
                                "suspicious" -> extendedColors.warning
                                else -> extendedColors.success
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = engine.engineName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                if (isThreat) {
                                    Text(
                                        text = engine.result ?: engine.category,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = engineColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                } else {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Rounded.Check,
                                            contentDescription = null,
                                            tint = extendedColors.success,
                                            modifier = Modifier.size(14.dp),
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = stringResource(R.string.virustotal_detail_engine_clean),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = extendedColors.success,
                                        )
                                    }
                                }
                            }
                            HorizontalDivider(
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                                thickness = 0.5.dp,
                            )
                        }
                    }
                }
            }

            // ── SHA-256 Hash with Copy Action ──
            if (apkInfo.sha256.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.virustotal_detail_sha256),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = apkInfo.sha256,
                                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                color = MaterialTheme.colorScheme.onSurface,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }

                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService<ClipboardManager>()
                                clipboard?.setPrimaryClip(
                                    android.content.ClipData.newPlainText("SHA-256", apkInfo.sha256)
                                )
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.virustotal_detail_sha256_copied),
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            modifier = Modifier.size(28.dp),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentCopy,
                                contentDescription = stringResource(R.string.virustotal_detail_copy_sha256),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            // ── Footer Action Buttons ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (apkInfo.sha256.isNotBlank()) {
                    OutlinedButton(
                        onClick = {
                            AnalyticsHelper.logVirusTotalAction("open_web_from_detail_sheet")
                            uriHandler.openUriSafely(
                                context,
                                "https://www.virustotal.com/gui/file/${apkInfo.sha256}/detection"
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.apk_info_vt_open_web),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                Button(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.virustotal_detail_close))
                }
            }
        }
    }
}

@Composable
private fun VtCounterBadge(
    label: String,
    count: Int,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = "$label: $count",
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}
