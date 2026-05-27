package app.pwhs.universalinstaller.presentation.install

import android.text.format.Formatter
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Menu
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.InstallerProfile
import app.pwhs.universalinstaller.domain.model.SplitEntry
import app.pwhs.universalinstaller.domain.model.SplitType
import app.pwhs.universalinstaller.domain.model.VtEngineResult
import app.pwhs.universalinstaller.domain.model.VtResult
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
    profiles: List<InstallerProfile> = emptyList(),
    appProfileMapping: Map<String, String> = emptyMap(),
    onProfileSelected: (InstallerProfile?) -> Unit = {},
    onMappingChanged: (String, String?) -> Unit = { _, _ -> },
    startCompact: Boolean = true,
) {
    val context = LocalContext.current
    val currentMappingProfileId = appProfileMapping[apkInfo.packageName]
    var isExpanded by rememberSaveable { mutableStateOf(!startCompact) }
    
    val iconBitmap = remember(apkInfo.icon) {
        apkInfo.icon?.toBitmap(128, 128)?.asImageBitmap()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp)
            .padding(bottom = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (isExpanded) {
            if (iconBitmap != null) {
                Image(
                    bitmap = iconBitmap,
                    contentDescription = apkInfo.appName,
                    modifier = Modifier.size(80.dp).clip(MaterialTheme.shapes.large),
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
            Text(
                text = apkInfo.appName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = apkInfo.packageName,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = apkInfo.appName,
                        modifier = Modifier.size(48.dp).clip(MaterialTheme.shapes.medium),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                    )
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = apkInfo.appName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "v${apkInfo.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        val isDowngrade = apkInfo.installedVersionCode != null &&
                apkInfo.installedVersionCode > 0 &&
                apkInfo.versionCode < apkInfo.installedVersionCode

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isDowngrade) {
                InfoChip(
                    label = stringResource(R.string.dialog_chip_downgrade),
                    leadingIcon = {
                        Icon(
                            Icons.Rounded.Warning,
                            null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
            if (isExpanded) {
                if (apkInfo.versionName.isNotBlank()) {
                    InfoChip(label = stringResource(R.string.apk_info_version_chip, apkInfo.versionName))
                }
                if (apkInfo.fileSizeBytes > 0) {
                    InfoChip(label = Formatter.formatShortFileSize(context, apkInfo.fileSizeBytes))
                }
            } else {
                if (apkInfo.fileSizeBytes > 0) {
                    InfoChip(label = Formatter.formatShortFileSize(context, apkInfo.fileSizeBytes))
                }
            }
            if (apkInfo.splitCount > 1) {
                InfoChip(label = stringResource(R.string.apk_info_splits_count, apkInfo.splitCount))
            }
            if (apkInfo.obbFileNames.isNotEmpty()) {
                InfoChip(label = "OBB: ${apkInfo.obbFileNames.size}")
            }
        }

        if (isExpanded) {
            Spacer(Modifier.height(16.dp))
            ObbAttachCard(attached = attachedObbFiles, onAttach = onAttachObb, onRemove = onRemoveObb)
            Spacer(Modifier.height(16.dp))
            DetailsCard(apkInfo)
            if (apkInfo.splitEntries.size > 1) {
                Spacer(Modifier.height(16.dp))
                SplitsCard(splits = apkInfo.splitEntries, onToggle = onToggleSplit)
            }
            if (apkInfo.supportedAbis.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                AbisCard(apkInfo.supportedAbis)
            }
            Spacer(Modifier.height(16.dp))
            VirusTotalCard(vt = apkInfo.vtResult, fileSizeBytes = apkInfo.fileSizeBytes, sha256 = apkInfo.sha256, onCheck = onCheckVirusTotal)
            if (apkInfo.permissions.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                PermissionsCard(apkInfo.permissions)
            }
            if (profiles.isNotEmpty()) {
                Spacer(Modifier.height(16.dp))
                ProfilePickerCard(
                    profiles = profiles,
                    currentMappingProfileId = currentMappingProfileId,
                    onProfileSelected = onProfileSelected,
                    onMappingToggle = { profileId -> onMappingChanged(apkInfo.packageName, profileId) }
                )
            }
        }

        Spacer(Modifier.height(24.dp))

        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!isExpanded) {
                FilledTonalButton(
                    onClick = { isExpanded = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.Menu, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Details")
                }
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { if (isExpanded) isExpanded = false else onCancel() },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(if (isExpanded) "Back" else stringResource(R.string.cancel))
                }
                Button(
                    onClick = onInstall,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                    colors = if (isDowngrade) ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error) else ButtonDefaults.buttonColors()
                ) {
                    Icon(Icons.Rounded.InstallMobile, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (isDowngrade) "Downgrade" else stringResource(R.string.txt_install))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProfilePickerCard(
    profiles: List<InstallerProfile>,
    currentMappingProfileId: String?,
    onProfileSelected: (InstallerProfile?) -> Unit,
    onMappingToggle: (String?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedProfile = profiles.find { it.id == currentMappingProfileId }

    SectionCard(
        icon = Icons.Rounded.Badge,
        title = stringResource(R.string.setting_profiles_title),
        summary = selectedProfile?.name ?: stringResource(R.string.install_profile_none),
        defaultExpanded = false
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.install_profile_picker_label), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = selectedProfile?.name ?: stringResource(R.string.install_profile_none),
                    onValueChange = {},
                    readOnly = true,
                    modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                    colors = ExposedDropdownMenuDefaults.outlinedTextFieldColors()
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    DropdownMenuItem(text = { Text(stringResource(R.string.install_profile_none)) }, onClick = { onProfileSelected(null); onMappingToggle(null); expanded = false })
                    profiles.forEach { profile ->
                        DropdownMenuItem(text = { Text(profile.name) }, onClick = { onProfileSelected(profile); onMappingToggle(profile.id); expanded = false })
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().clickable { if (currentMappingProfileId != null) onMappingToggle(null) }) {
                Checkbox(checked = currentMappingProfileId != null, onCheckedChange = { checked -> if (!checked) onMappingToggle(null) }, enabled = currentMappingProfileId != null)
                Column(modifier = Modifier.padding(start = 8.dp)) {
                    Text(stringResource(R.string.profile_mapping_header), style = MaterialTheme.typography.bodyMedium)
                    Text(stringResource(R.string.profile_mapping_desc), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DetailsCard(apkInfo: ApkInfo) {
    SectionCard(icon = Icons.Rounded.Android, title = stringResource(R.string.apk_info_label_package), summary = apkInfo.packageName, defaultExpanded = false) {
        Column {
            InfoRow(stringResource(R.string.apk_info_label_package), apkInfo.packageName)
            if (apkInfo.versionName.isNotBlank()) InfoRow(stringResource(R.string.apk_info_label_version), apkInfo.versionName)
            if (apkInfo.minSdkVersion > 0) InfoRow(stringResource(R.string.apk_info_label_min_sdk), sdkToAndroid(apkInfo.minSdkVersion))
            if (apkInfo.targetSdkVersion > 0) InfoRow(stringResource(R.string.apk_info_label_target_sdk), sdkToAndroid(apkInfo.targetSdkVersion))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbisCard(abis: List<String>) {
    SectionCard(icon = Icons.Rounded.Memory, title = stringResource(R.string.apk_info_section_architectures), summary = abis.joinToString(", "), badge = abis.size.toString(), defaultExpanded = false) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            abis.forEach { abi -> InfoChip(label = abi) }
        }
    }
}

@Composable
private fun VirusTotalCard(vt: VtResult?, fileSizeBytes: Long, sha256: String = "", onCheck: () -> Unit) {
    val extendedColors = LocalExtendedColors.current
    val status = vt?.status
    val inProgress = status == VtStatus.SCANNING || status == VtStatus.UPLOADING || status == VtStatus.QUEUED || status == VtStatus.ANALYZING
    val hasResult = status in setOf(VtStatus.CLEAN, VtStatus.MALICIOUS, VtStatus.SUSPICIOUS)
    val vtColor = when (status) {
        VtStatus.CLEAN -> MaterialTheme.colorScheme.primary
        VtStatus.MALICIOUS, VtStatus.TOO_LARGE -> MaterialTheme.colorScheme.error
        VtStatus.SUSPICIOUS -> extendedColors.warning
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = if (status == VtStatus.MALICIOUS) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Security, null, tint = vtColor, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.apk_info_vt_scan_title), style = MaterialTheme.typography.labelLarge, color = vtColor)
                if (inProgress) { Spacer(Modifier.width(8.dp)); CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = vtColor) }
            }
            if (hasResult && vt != null) {
                Spacer(Modifier.height(12.dp))
                VtBreakdownSection(vt = vt, warningColor = extendedColors.warning)
            }
            TextButton(onClick = onCheck, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Rounded.CloudUpload, null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(6.dp))
                Text(if (status == null) stringResource(R.string.apk_info_vt_check_button) else stringResource(R.string.apk_info_vt_rescan_button))
            }
        }
    }
}

@Composable
private fun VtBreakdownSection(vt: VtResult, warningColor: Color) {
    val total = (vt.malicious + vt.suspicious + vt.harmless + vt.undetected).coerceAtLeast(1)
    val malFraction = vt.malicious.toFloat() / total
    val susFraction = vt.suspicious.toFloat() / total
    val harmFraction = vt.harmless.toFloat() / total
    Canvas(modifier = Modifier.fillMaxWidth().height(8.dp).clip(MaterialTheme.shapes.small)) {
        val w = size.width
        val h = size.height
        var x = 0f
        val malW = w * malFraction
        if (malW > 0f) { drawRect(color = Color.Red, topLeft = Offset(x, 0f), size = Size(malW, h)); x += malW }
        val susW = w * susFraction
        if (susW > 0f) { drawRect(color = warningColor, topLeft = Offset(x, 0f), size = Size(susW, h)); x += susW }
        val harmW = w * harmFraction
        if (harmW > 0f) { drawRect(color = Color.Green, topLeft = Offset(x, 0f), size = Size(harmW, h)); x += harmW }
        drawRect(color = Color.Gray.copy(alpha = 0.3f), topLeft = Offset(x, 0f), size = Size(w - x, h))
    }
}

@Composable
private fun PermissionsCard(permissions: List<String>) {
    val context = LocalContext.current
    val entries = remember(permissions) { resolvePermissionEntries(context, permissions) }
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) entries else entries.take(5)
    SectionCard(icon = Icons.Rounded.Security, title = stringResource(R.string.apk_info_section_permissions, permissions.size), badge = entries.size.toString(), defaultExpanded = false) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            visible.forEach { entry ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (entry.isDangerous) Icons.Rounded.Warning else Icons.Rounded.CheckCircle, null, tint = if (entry.isDangerous) Color.Red else Color.Gray, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(entry.label, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (entries.size > 5) TextButton(onClick = { expanded = !expanded }, modifier = Modifier.fillMaxWidth()) { Text(if (expanded) "Show less" else "Show more") }
        }
    }
}

@Composable
private fun SplitsCard(splits: List<SplitEntry>, onToggle: (Int) -> Unit) {
    SectionCard(icon = Icons.Rounded.Memory, title = stringResource(R.string.apk_info_section_splits, splits.size), defaultExpanded = true) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            splits.forEachIndexed { index, split ->
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = split.selected, onCheckedChange = { onToggle(index) }, enabled = split.type != SplitType.Base)
                    Text(split.name, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun SectionCard(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, summary: String? = null, badge: String? = null, defaultExpanded: Boolean = true, content: @Composable () -> Unit) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(modifier = Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    if (!expanded && summary != null) Text(summary, style = MaterialTheme.typography.bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                if (badge != null) Text(badge, modifier = Modifier.padding(end = 8.dp), style = MaterialTheme.typography.labelSmall)
                Icon(if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null)
            }
            if (expanded) Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)) { content() }
        }
    }
}

@Composable
internal fun InfoChip(label: String, leadingIcon: @Composable (() -> Unit)? = null, containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Surface(shape = MaterialTheme.shapes.small, color = containerColor, contentColor = contentColor) {
        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            leadingIcon?.invoke()
            Text(label, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
internal fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(0.4f))
        Text(value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
    }
}

internal fun sdkToAndroid(sdk: Int): String = when {
    sdk >= 35 -> "15"; sdk >= 34 -> "14"; sdk >= 33 -> "13"; sdk >= 32 -> "12L"; sdk >= 31 -> "12"; sdk >= 30 -> "11"; sdk >= 29 -> "10"; sdk >= 28 -> "9"; sdk >= 26 -> "8"; sdk >= 24 -> "7"; sdk >= 23 -> "6"; sdk >= 21 -> "5"; else -> "$sdk"
}

@Composable
private fun ObbAttachCard(attached: List<AttachedObb>, onAttach: () -> Unit, onRemove: (AttachedObb) -> Unit) {
    ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("OBB Files", style = MaterialTheme.typography.titleSmall)
            attached.forEach { obb ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(obb.fileName, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                    IconButton(onClick = { onRemove(obb) }) { Icon(Icons.Rounded.Delete, null) }
                }
            }
            OutlinedButton(onClick = onAttach, modifier = Modifier.fillMaxWidth()) { Text("Attach OBB") }
        }
    }
}
