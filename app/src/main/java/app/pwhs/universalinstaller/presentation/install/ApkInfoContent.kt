package app.pwhs.universalinstaller.presentation.install

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SplitEntry
import app.pwhs.universalinstaller.domain.model.SplitType
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.ui.theme.LocalExtendedColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApkInfoContent(
    apkInfo: ApkInfo,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onCheckVirusTotal: () -> Unit = {},
    attachedObbFiles: List<AttachedObb> = emptyList(),
    onAttachObb: () -> Unit = {},
    onRemoveObb: (AttachedObb) -> Unit = {},
    onToggleSplit: (Int) -> Unit = {},
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // App icon
        if (apkInfo.icon != null) {
            Image(
                bitmap = apkInfo.icon.toBitmap(128, 128).asImageBitmap(),
                contentDescription = apkInfo.appName,
                modifier = Modifier
                    .size(80.dp)
                    .clip(MaterialTheme.shapes.large)
            )
            Spacer(Modifier.height(12.dp))
        } else {
            Icon(
                imageVector = Icons.Rounded.Android,
                contentDescription = null,
                modifier = Modifier.size(80.dp),
                tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
            )
            Spacer(Modifier.height(12.dp))
        }

        // App name
        Text(
            text = apkInfo.appName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(4.dp))

        // Package name
        Text(
            text = apkInfo.packageName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        Spacer(Modifier.height(16.dp))

        // Info chips row
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (apkInfo.versionName.isNotBlank()) {
                InfoChip(
                    label = stringResource(R.string.apk_info_version_chip, apkInfo.versionName),
                    leadingIcon = {
                        Icon(Icons.Rounded.Android, null, modifier = Modifier.size(16.dp))
                    },
                )
            }
            if (apkInfo.fileSizeBytes > 0) {
                InfoChip(
                    label = Formatter.formatShortFileSize(context, apkInfo.fileSizeBytes),
                    leadingIcon = {
                        Icon(Icons.Rounded.Storage, null, modifier = Modifier.size(16.dp))
                    },
                )
            }
            InfoChip(label = apkInfo.fileFormat)
            if (apkInfo.minSdkVersion > 0) {
                InfoChip(
                    label = stringResource(R.string.apk_info_min_sdk_chip, sdkToAndroid(apkInfo.minSdkVersion)),
                    leadingIcon = {
                        Icon(Icons.Rounded.PhoneAndroid, null, modifier = Modifier.size(16.dp))
                    },
                )
            }
            if (apkInfo.splitCount > 1) {
                InfoChip(label = stringResource(R.string.apk_info_splits_count, apkInfo.splitCount))
            }
            if (apkInfo.obbFileNames.isNotEmpty()) {
                val obbSize = if (apkInfo.obbTotalBytes > 0)
                    " · ${Formatter.formatShortFileSize(context, apkInfo.obbTotalBytes)}"
                else ""
                InfoChip(
                    label = stringResource(
                        R.string.apk_info_obb_chip,
                        apkInfo.obbFileNames.size,
                    ) + obbSize,
                )
            }
        }

        Spacer(Modifier.height(16.dp))

        ObbAttachCard(
            attached = attachedObbFiles,
            onAttach = onAttachObb,
            onRemove = onRemoveObb,
        )

        Spacer(Modifier.height(16.dp))

        // Details card
        DetailsCard(apkInfo)

        // Splits selector (only for bundles with multiple splits)
        if (apkInfo.splitEntries.size > 1) {
            Spacer(Modifier.height(16.dp))
            SplitsCard(
                splits = apkInfo.splitEntries,
                onToggle = onToggleSplit,
            )
        }

        // Supported ABIs section
        if (apkInfo.supportedAbis.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            AbisCard(apkInfo.supportedAbis)
        }

        // Supported Languages section
        if (apkInfo.supportedLanguages.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            LanguagesCard(apkInfo.supportedLanguages)
        }

        // VirusTotal Security Scan
        Spacer(Modifier.height(16.dp))
        VirusTotalCard(
            vt = apkInfo.vtResult,
            fileSizeBytes = apkInfo.fileSizeBytes,
            sha256 = apkInfo.sha256,
            onCheck = onCheckVirusTotal,
        )

        // Permissions section
        if (apkInfo.permissions.isNotEmpty()) {
            Spacer(Modifier.height(16.dp))
            PermissionsCard(apkInfo.permissions)
        }

        Spacer(Modifier.height(24.dp))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = onCancel,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.cancel))
            }
            Button(
                onClick = onInstall,
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.medium,
            ) {
                Icon(
                    imageVector = Icons.Rounded.InstallMobile,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.txt_install))
            }
        }
    }
}

// ── Section cards ───────────────────────────────────────

@Composable
private fun DetailsCard(apkInfo: ApkInfo) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            InfoRow(stringResource(R.string.apk_info_label_package), apkInfo.packageName)
            if (apkInfo.versionName.isNotBlank()) {
                InfoRow(
                    stringResource(R.string.apk_info_label_version),
                    stringResource(R.string.apk_info_version_detail, apkInfo.versionName, apkInfo.versionCode),
                )
            }
            if (apkInfo.minSdkVersion > 0) {
                InfoRow(
                    stringResource(R.string.apk_info_label_min_sdk),
                    stringResource(R.string.apk_info_sdk_detail, apkInfo.minSdkVersion, sdkToAndroid(apkInfo.minSdkVersion)),
                )
            }
            if (apkInfo.targetSdkVersion > 0) {
                InfoRow(
                    stringResource(R.string.apk_info_label_target_sdk),
                    stringResource(R.string.apk_info_sdk_detail, apkInfo.targetSdkVersion, sdkToAndroid(apkInfo.targetSdkVersion)),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbisCard(abis: List<String>) {
    SectionCard(
        icon = Icons.Rounded.Memory,
        title = stringResource(R.string.apk_info_section_architectures),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            abis.forEach { abi -> InfoChip(label = abi) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun LanguagesCard(languages: List<String>) {
    SectionCard(
        icon = Icons.Rounded.Language,
        title = stringResource(R.string.apk_info_section_languages, languages.size),
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            languages.take(20).forEach { lang -> InfoChip(label = lang) }
            if (languages.size > 20) {
                InfoChip(label = "+${languages.size - 20}")
            }
        }
    }
}

@Composable
private fun VirusTotalCard(
    vt: app.pwhs.universalinstaller.domain.model.VtResult?,
    fileSizeBytes: Long,
    sha256: String = "",
    onCheck: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = androidx.compose.ui.platform.LocalUriHandler.current
    val extendedColors = LocalExtendedColors.current
    val warningColor = extendedColors.warning
    val status = vt?.status
    val inProgress = status == VtStatus.SCANNING || status == VtStatus.UPLOADING ||
            status == VtStatus.QUEUED || status == VtStatus.ANALYZING
    val hasResult = status in setOf(VtStatus.CLEAN, VtStatus.MALICIOUS, VtStatus.SUSPICIOUS)

    val vtColor = when (status) {
        VtStatus.CLEAN -> MaterialTheme.colorScheme.primary
        VtStatus.MALICIOUS, VtStatus.TOO_LARGE -> MaterialTheme.colorScheme.error
        VtStatus.SUSPICIOUS -> warningColor
        VtStatus.SCANNING, VtStatus.UPLOADING,
        VtStatus.QUEUED, VtStatus.ANALYZING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val vtContainerColor = when (status) {
        VtStatus.MALICIOUS, VtStatus.TOO_LARGE -> MaterialTheme.colorScheme.errorContainer
        VtStatus.SUSPICIOUS -> extendedColors.warningContainer
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(containerColor = vtContainerColor),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Security,
                    contentDescription = null,
                    tint = vtColor,
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
            Spacer(Modifier.height(8.dp))
            when (status) {
                VtStatus.SCANNING -> Text(
                    text = stringResource(R.string.apk_info_vt_scanning),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VtStatus.UPLOADING -> {
                    Text(
                        text = stringResource(R.string.apk_info_vt_uploading, vt.uploadProgress),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { vt.uploadProgress.coerceIn(0, 100) / 100f },
                        modifier = Modifier.fillMaxWidth(),
                        color = vtColor,
                    )
                }
                VtStatus.QUEUED -> Text(
                    text = stringResource(R.string.apk_info_vt_queued),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VtStatus.ANALYZING -> Text(
                    text = stringResource(R.string.apk_info_vt_analyzing),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VtStatus.CLEAN -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.apk_info_vt_clean), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
                VtStatus.MALICIOUS -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.apk_info_vt_malicious, vt.malicious), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                }
                VtStatus.SUSPICIOUS -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = warningColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.apk_info_vt_suspicious, vt.suspicious), style = MaterialTheme.typography.bodySmall, color = warningColor)
                    }
                }
                VtStatus.NOT_FOUND -> Text(
                    stringResource(R.string.apk_info_vt_not_found),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                VtStatus.TOO_LARGE -> Text(
                    text = stringResource(
                        R.string.apk_info_vt_too_large,
                        android.text.format.Formatter.formatShortFileSize(context, fileSizeBytes),
                        650,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                VtStatus.ERROR -> Text(
                    stringResource(R.string.apk_info_vt_error, vt.errorMessage),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
                VtStatus.NO_API_KEY -> Text(
                    text = stringResource(R.string.apk_info_vt_no_api_key),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> {}
            }

            // Detailed breakdown — shown when results are available
            if (hasResult && vt != null) {
                Spacer(Modifier.height(12.dp))
                VtBreakdownSection(vt = vt, warningColor = warningColor)
            }

            // Action buttons
            if (!inProgress && status != VtStatus.TOO_LARGE && status != VtStatus.NO_API_KEY) {
                Spacer(Modifier.height(12.dp))
                val label = when (status) {
                    null -> stringResource(R.string.apk_info_vt_check_button)
                    else -> stringResource(R.string.apk_info_vt_rescan_button)
                }
                TextButton(
                    onClick = onCheck,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudUpload,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(label, style = MaterialTheme.typography.labelLarge)
                }
            }

            // "View on VirusTotal" link button
            if (hasResult && sha256.isNotBlank()) {
                TextButton(
                    onClick = {
                        uriHandler.openUri("https://www.virustotal.com/gui/file/$sha256")
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.OpenInNew,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.apk_info_vt_view_report),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
        }
    }
}

/**
 * Detailed breakdown of VirusTotal engine results with visual indicator bars.
 */
@Composable
private fun VtBreakdownSection(
    vt: app.pwhs.universalinstaller.domain.model.VtResult,
    warningColor: androidx.compose.ui.graphics.Color,
) {
    val total = (vt.malicious + vt.suspicious + vt.harmless + vt.undetected).coerceAtLeast(1)

    Text(
        text = stringResource(R.string.apk_info_vt_total_engines, total),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Spacer(Modifier.height(8.dp))

    // Stacked progress bar showing all categories
    val malFraction = vt.malicious.toFloat() / total
    val susFraction = vt.suspicious.toFloat() / total
    val harmFraction = vt.harmless.toFloat() / total
    // undetected fills the rest

    val errorColor = MaterialTheme.colorScheme.error
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariantColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    androidx.compose.foundation.Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(MaterialTheme.shapes.small)
    ) {
        val w = size.width
        val h = size.height
        var x = 0f

        // Malicious (red)
        val malW = w * malFraction
        if (malW > 0f) {
            drawRect(color = errorColor, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(malW, h))
            x += malW
        }
        // Suspicious (warning/orange)
        val susW = w * susFraction
        if (susW > 0f) {
            drawRect(color = warningColor, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(susW, h))
            x += susW
        }
        // Harmless (green/primary)
        val harmW = w * harmFraction
        if (harmW > 0f) {
            drawRect(color = primaryColor, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(harmW, h))
            x += harmW
        }
        // Undetected (subtle gray)
        val undetW = w - x
        if (undetW > 0f) {
            drawRect(color = surfaceVariantColor, topLeft = androidx.compose.ui.geometry.Offset(x, 0f), size = androidx.compose.ui.geometry.Size(undetW, h))
        }
    }

    Spacer(Modifier.height(8.dp))

    // Legend rows
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (vt.malicious > 0) {
            VtLegendRow(
                color = MaterialTheme.colorScheme.error,
                label = stringResource(R.string.apk_info_vt_label_malicious),
                count = vt.malicious,
            )
        }
        if (vt.suspicious > 0) {
            VtLegendRow(
                color = warningColor,
                label = stringResource(R.string.apk_info_vt_label_suspicious),
                count = vt.suspicious,
            )
        }
        VtLegendRow(
            color = MaterialTheme.colorScheme.primary,
            label = stringResource(R.string.apk_info_vt_label_harmless),
            count = vt.harmless,
        )
        VtLegendRow(
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
            label = stringResource(R.string.apk_info_vt_label_undetected),
            count = vt.undetected,
        )
    }

    // Per-engine detail list
    if (vt.engineResults.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))

        val threats = remember(vt.engineResults) {
            vt.engineResults.filter { it.category == "malicious" || it.category == "suspicious" }
        }
        val cleanEngines = remember(vt.engineResults) {
            vt.engineResults.filter { it.category != "malicious" && it.category != "suspicious" }
        }

        var showAll by remember { mutableStateOf(false) }

        // Always show threatening engines
        if (threats.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                threats.forEach { engine ->
                    VtEngineRow(
                        engine = engine,
                        warningColor = warningColor,
                    )
                }
            }
        }

        // Collapsible clean engines
        if (cleanEngines.isNotEmpty()) {
            if (showAll) {
                Spacer(Modifier.height(4.dp))
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    cleanEngines.forEach { engine ->
                        VtEngineRow(
                            engine = engine,
                            warningColor = warningColor,
                        )
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { showAll = !showAll },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (showAll) stringResource(R.string.apk_info_vt_hide_engines)
                    else stringResource(R.string.apk_info_vt_show_all_engines, cleanEngines.size),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

@Composable
private fun VtEngineRow(
    engine: app.pwhs.universalinstaller.domain.model.VtEngineResult,
    warningColor: androidx.compose.ui.graphics.Color,
) {
    val dotColor = when (engine.category) {
        "malicious" -> MaterialTheme.colorScheme.error
        "suspicious" -> warningColor
        "harmless" -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
    }
    val detectionText = engine.result
    val isThreat = engine.category == "malicious" || engine.category == "suspicious"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .then(
                if (isThreat) Modifier.background(
                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                ) else Modifier
            )
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Spacer(
            modifier = Modifier
                .size(8.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(dotColor),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = engine.engineName,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = if (isThreat) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isThreat) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        if (detectionText != null) {
            Text(
                text = detectionText,
                style = MaterialTheme.typography.labelSmall,
                color = if (isThreat) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        } else {
            Text(
                text = engine.category.replaceFirstChar { it.uppercase() },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

@Composable
private fun VtLegendRow(
    color: androidx.compose.ui.graphics.Color,
    label: String,
    count: Int,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Spacer(
            modifier = Modifier
                .size(10.dp)
                .clip(MaterialTheme.shapes.extraSmall)
                .background(color),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@Composable
private fun PermissionsCard(permissions: List<String>) {
    // Well-known dangerous permissions that warrant visual emphasis
    val dangerousSet = remember {
        setOf(
            "android.permission.CAMERA",
            "android.permission.RECORD_AUDIO",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.ACCESS_BACKGROUND_LOCATION",
            "android.permission.READ_CONTACTS",
            "android.permission.WRITE_CONTACTS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.CALL_PHONE",
            "android.permission.READ_PHONE_STATE",
            "android.permission.READ_PHONE_NUMBERS",
            "android.permission.SEND_SMS",
            "android.permission.RECEIVE_SMS",
            "android.permission.READ_SMS",
            "android.permission.READ_EXTERNAL_STORAGE",
            "android.permission.WRITE_EXTERNAL_STORAGE",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.READ_MEDIA_IMAGES",
            "android.permission.READ_MEDIA_VIDEO",
            "android.permission.READ_MEDIA_AUDIO",
            "android.permission.BODY_SENSORS",
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.READ_CALENDAR",
            "android.permission.WRITE_CALENDAR",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.REQUEST_INSTALL_PACKAGES",
        )
    }

    // Sort: dangerous first, then alphabetical within each group
    val sorted = remember(permissions) {
        permissions.sortedWith(
            compareByDescending<String> { it in dangerousSet }
                .thenBy { it.substringAfterLast('.') }
        )
    }

    var expanded by remember { mutableStateOf(false) }
    val collapsedCount = 5
    val showToggle = sorted.size > collapsedCount
    val visiblePerms = if (expanded || !showToggle) sorted else sorted.take(collapsedCount)

    SectionCard(
        icon = Icons.Rounded.Security,
        title = stringResource(R.string.apk_info_section_permissions, permissions.size),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            visiblePerms.forEach { perm ->
                val isDangerous = perm in dangerousSet
                val shortName = perm.substringAfterLast('.')
                    .replace('_', ' ')
                    .lowercase()
                    .replaceFirstChar { it.uppercase() }
                val prefix = perm.substringBeforeLast('.', "")

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .then(
                            if (isDangerous) Modifier.background(
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
                            ) else Modifier
                        )
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (isDangerous) Icons.Rounded.Warning else Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = if (isDangerous) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = shortName,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isDangerous) FontWeight.SemiBold else FontWeight.Normal,
                            color = if (isDangerous) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onSurface,
                        )
                        if (prefix.isNotEmpty()) {
                            Text(
                                text = prefix,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                            )
                        }
                    }
                }
            }
        }
        if (showToggle) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (expanded) "Show less"
                    else stringResource(R.string.apk_info_permissions_more, sorted.size - collapsedCount),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ── Splits card ─────────────────────────────────────────

@Composable
private fun SplitsCard(
    splits: List<SplitEntry>,
    onToggle: (Int) -> Unit,
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(false) }
    val collapsedCount = 5
    val showToggle = splits.size > collapsedCount
    val visibleSplits = if (expanded || !showToggle) splits else splits.take(collapsedCount)

    SectionCard(
        icon = Icons.Rounded.Memory,
        title = stringResource(R.string.apk_info_section_splits, splits.size),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            visibleSplits.forEachIndexed { index, split ->
                val actualIndex = if (expanded || !showToggle) index
                else splits.indexOf(split)
                val typeLabel = when (split.type) {
                    SplitType.Base -> stringResource(R.string.apk_info_split_base)
                    SplitType.Libs -> stringResource(R.string.apk_info_split_libs)
                    SplitType.Locale -> stringResource(R.string.apk_info_split_locale)
                    SplitType.ScreenDensity -> stringResource(R.string.apk_info_split_density)
                    SplitType.Feature -> stringResource(R.string.apk_info_split_feature)
                    SplitType.Other -> stringResource(R.string.apk_info_split_other)
                }
                val isBase = split.type == SplitType.Base

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .then(
                            if (isBase) Modifier.background(
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                            ) else Modifier
                        )
                        .padding(start = 4.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    androidx.compose.material3.Checkbox(
                        checked = split.selected,
                        onCheckedChange = { onToggle(actualIndex) },
                        enabled = !isBase,
                        modifier = Modifier.size(36.dp),
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = split.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = if (isBase) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Text(
                            text = typeLabel + if (split.sizeBytes > 0)
                                " · ${Formatter.formatShortFileSize(context, split.sizeBytes)}"
                            else "",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        )
                    }
                }
            }
        }
        if (showToggle) {
            Spacer(Modifier.height(4.dp))
            TextButton(
                onClick = { expanded = !expanded },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (expanded) stringResource(R.string.apk_info_split_show_less)
                    else stringResource(R.string.apk_info_permissions_more, splits.size - collapsedCount),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }
}

// ── Reusable helpers ────────────────────────────────────

@Composable
private fun SectionCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    content: @Composable () -> Unit,
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Spacer(Modifier.height(8.dp))
            content()
        }
    }
}

@Composable
internal fun InfoChip(
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            leadingIcon?.invoke()
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(0.35f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(0.65f),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

internal fun sdkToAndroid(sdk: Int): String = when {
    sdk >= 35 -> "15"
    sdk >= 34 -> "14"
    sdk >= 33 -> "13"
    sdk >= 32 -> "12L"
    sdk >= 31 -> "12"
    sdk >= 30 -> "11"
    sdk >= 29 -> "10"
    sdk >= 28 -> "9"
    sdk >= 26 -> "8"
    sdk >= 24 -> "7"
    sdk >= 23 -> "6"
    sdk >= 21 -> "5"
    else -> "$sdk"
}

@Composable
private fun ObbAttachCard(
    attached: List<AttachedObb>,
    onAttach: () -> Unit,
    onRemove: (AttachedObb) -> Unit,
) {
    val context = LocalContext.current
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.apk_info_obb_attach_title),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(R.string.apk_info_obb_attach_desc),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (attached.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                attached.forEach { obb ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Storage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = obb.fileName,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (obb.sizeBytes > 0) {
                                Text(
                                    text = Formatter.formatShortFileSize(context, obb.sizeBytes),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                        TextButton(onClick = { onRemove(obb) }) {
                            Text(stringResource(R.string.apk_info_obb_attach_remove))
                        }
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onAttach,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            ) {
                Text(stringResource(R.string.apk_info_obb_attach_button))
            }
        }
    }
}
