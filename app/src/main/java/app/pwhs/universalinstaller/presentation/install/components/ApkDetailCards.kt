package app.pwhs.universalinstaller.presentation.install.components

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CallMerge
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SplitEntry
import app.pwhs.universalinstaller.domain.model.SplitType
import app.pwhs.universalinstaller.presentation.install.AttachedObb
import app.pwhs.universalinstaller.presentation.install.InstallTargetPicker
import app.pwhs.universalinstaller.presentation.install.dialog.components.SmartSplitOptimizerBar
import app.pwhs.universalinstaller.presentation.install.dialog.components.splitChipLabel
import app.pwhs.universalinstaller.presentation.install.rememberDeviceUserProfiles
import android.text.format.Formatter
import androidx.compose.ui.platform.LocalContext

@Composable
fun sectionCardBorder() = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)

@Composable
fun SectionCard(
    icon: ImageVector,
    title: String,
    summary: String? = null,
    badge: String? = null,
    defaultExpanded: Boolean = true,
    content: @Composable () -> Unit,
) {
    var expanded by remember { mutableStateOf(defaultExpanded) }
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = sectionCardBorder(),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (!expanded && summary != null) {
                        Text(
                            text = summary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (badge != null) {
                    Text(
                        text = badge,
                        modifier = Modifier.padding(end = 8.dp),
                        style = MaterialTheme.typography.labelSmall,
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                    contentDescription = null,
                )
            }
            if (expanded) {
                Column(
                    modifier = Modifier.padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = 16.dp,
                    ),
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
fun InfoChip(
    label: String,
    leadingIcon: @Composable (() -> Unit)? = null,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    onClick: (() -> Unit)? = null,
) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        contentColor = contentColor,
        modifier = if (onClick != null) {
            Modifier
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onClick)
        } else {
            Modifier
        },
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
fun InfoRow(label: String, value: String) {
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
            modifier = Modifier.weight(0.4f),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(0.6f),
            textAlign = TextAlign.End,
        )
    }
}

fun sdkToAndroid(sdk: Int): String = when {
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
fun DetailsCard(apkInfo: ApkInfo) {
    SectionCard(
        icon = Icons.Rounded.Android,
        title = stringResource(R.string.apk_info_label_package),
        summary = apkInfo.packageName,
        defaultExpanded = false,
    ) {
        Column {
            InfoRow(stringResource(R.string.apk_info_label_package), apkInfo.packageName)
            if (apkInfo.versionName.isNotBlank()) {
                InfoRow(stringResource(R.string.apk_info_label_version), apkInfo.versionName)
            }
            if (apkInfo.minSdkVersion > 0) {
                InfoRow(stringResource(R.string.apk_info_label_min_sdk), sdkToAndroid(apkInfo.minSdkVersion))
            }
            if (apkInfo.targetSdkVersion > 0) {
                InfoRow(stringResource(R.string.apk_info_label_target_sdk), sdkToAndroid(apkInfo.targetSdkVersion))
            }
            if (apkInfo.isAndroidAutoSupported) {
                InfoRow(stringResource(R.string.aa_compatibility_title), stringResource(R.string.aa_compatibility_ok))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AbisCard(abis: List<String>) {
    SectionCard(
        icon = Icons.Rounded.Memory,
        title = stringResource(R.string.apk_info_section_architectures),
        summary = abis.joinToString(", "),
        badge = abis.size.toString(),
        defaultExpanded = false,
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            abis.forEach { abi -> InfoChip(label = abi) }
        }
    }
}

@Composable
fun PermissionsCard(permissions: List<String>) {
    var expanded by remember { mutableStateOf(false) }
    val visible = if (expanded) permissions else permissions.take(5)
    SectionCard(
        icon = Icons.Rounded.Security,
        title = stringResource(R.string.apk_info_section_permissions, permissions.size),
        badge = permissions.size.toString(),
        defaultExpanded = false,
    ) {
        if (permissions.isEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.apk_info_permissions_empty),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                visible.forEach { perm ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = perm.substringAfterLast('.'),
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (permissions.size > 5) {
                    TextButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(if (expanded) "Show less" else "Show more")
                    }
                }
            }
        }
    }
}

@Composable
fun SplitsCard(
    splits: List<SplitEntry>,
    onToggle: (Int) -> Unit,
    onMergeSplits: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val selectedCount = splits.count { it.selected }
    SectionCard(
        icon = Icons.Rounded.Memory,
        title = stringResource(R.string.apk_info_section_splits, splits.size),
        badge = "$selectedCount/${splits.size}",
        defaultExpanded = true,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            SmartSplitOptimizerBar(
                entries = splits,
                onToggle = onToggle,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            if (onMergeSplits != null) {
                OutlinedButton(
                    onClick = onMergeSplits,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CallMerge,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.antisplit_action_merge),
                        style = MaterialTheme.typography.labelLarge,
                    )
                }
            }
            splits.forEachIndexed { index, split ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(
                        checked = split.selected,
                        onCheckedChange = { onToggle(index) },
                        enabled = split.type != SplitType.Base,
                    )
                    Text(
                        text = "${splitChipLabel(split)} (${split.name})",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (split.sizeBytes > 0L) {
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = Formatter.formatShortFileSize(context, split.sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ObbAttachCard(
    attached: List<AttachedObb>,
    onAttach: () -> Unit,
    onRemove: (AttachedObb) -> Unit,
) {
    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.outlinedCardColors(containerColor = Color.Transparent),
        border = sectionCardBorder(),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = stringResource(R.string.apk_info_obb_attach_title),
                style = MaterialTheme.typography.titleSmall,
            )
            attached.forEach { obb ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = obb.fileName,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    IconButton(onClick = { onRemove(obb) }) {
                        Icon(Icons.Rounded.Delete, null)
                    }
                }
            }
            OutlinedButton(
                onClick = onAttach,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.apk_info_obb_attach_button))
            }
        }
    }
}

@Composable
fun InstallTargetCard(
    allUsers: Boolean,
    selectedUserId: Int?,
    onToggleAllUsers: (Boolean) -> Unit,
    onSelectUserId: (Int?) -> Unit,
) {
    val profiles = rememberDeviceUserProfiles()
    val allUsersDesc = if (allUsers) {
        stringResource(R.string.dialog_menu_all_users_on)
    } else {
        stringResource(R.string.dialog_menu_all_users_off)
    }

    SectionCard(
        icon = Icons.Rounded.Person,
        title = stringResource(R.string.dialog_menu_install_target),
        summary = allUsersDesc,
        defaultExpanded = profiles.size > 1,
    ) {
        InstallTargetPicker(
            profiles = profiles,
            allUsers = allUsers,
            selectedUserId = selectedUserId,
            onSelectAllUsers = onToggleAllUsers,
            onSelectUserId = onSelectUserId,
        )
    }
}
