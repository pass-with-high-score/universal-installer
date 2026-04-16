package app.nqmgaming.universalinstaller.presentation.install

import android.text.format.Formatter
import androidx.compose.foundation.Image
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
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.pwhs.universalinstaller.domain.model.ApkInfo
import com.pwhs.universalinstaller.domain.model.VtStatus
import com.pwhs.universalinstaller.ui.theme.LocalExtendedColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ApkInfoContent(
    apkInfo: ApkInfo,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
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
                    label = "v${apkInfo.versionName}",
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
                    label = "Android ${sdkToAndroid(apkInfo.minSdkVersion)}+",
                    leadingIcon = {
                        Icon(Icons.Rounded.PhoneAndroid, null, modifier = Modifier.size(16.dp))
                    },
                )
            }
            if (apkInfo.splitCount > 1) {
                InfoChip(label = "${apkInfo.splitCount} splits")
            }
        }

        Spacer(Modifier.height(16.dp))

        // Details card
        DetailsCard(apkInfo)

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
        apkInfo.vtResult?.let { vt ->
            Spacer(Modifier.height(16.dp))
            VirusTotalCard(vt)
        }

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
                Text("Cancel")
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
                Text("Install")
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
            InfoRow("Package", apkInfo.packageName)
            if (apkInfo.versionName.isNotBlank()) {
                InfoRow("Version", "${apkInfo.versionName} (${apkInfo.versionCode})")
            }
            if (apkInfo.minSdkVersion > 0) {
                InfoRow("Min SDK", "API ${apkInfo.minSdkVersion} (Android ${sdkToAndroid(apkInfo.minSdkVersion)})")
            }
            if (apkInfo.targetSdkVersion > 0) {
                InfoRow("Target SDK", "API ${apkInfo.targetSdkVersion} (Android ${sdkToAndroid(apkInfo.targetSdkVersion)})")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AbisCard(abis: List<String>) {
    SectionCard(
        icon = Icons.Rounded.Memory,
        title = "Supported Architectures",
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
        title = "Supported Languages (${languages.size})",
    ) {
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            languages.take(20).forEach { lang -> InfoChip(label = lang) }
            if (languages.size > 20) {
                InfoChip(label = "+${languages.size - 20} more")
            }
        }
    }
}

@Composable
private fun VirusTotalCard(vt: com.pwhs.universalinstaller.domain.model.VtResult) {
    val extendedColors = LocalExtendedColors.current
    val warningColor = extendedColors.warning
    val vtColor = when (vt.status) {
        VtStatus.CLEAN -> MaterialTheme.colorScheme.primary
        VtStatus.MALICIOUS -> MaterialTheme.colorScheme.error
        VtStatus.SUSPICIOUS -> warningColor
        VtStatus.SCANNING -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val vtContainerColor = when (vt.status) {
        VtStatus.MALICIOUS -> MaterialTheme.colorScheme.errorContainer
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
                    text = "VirusTotal Scan",
                    style = MaterialTheme.typography.labelLarge,
                    color = vtColor,
                )
                if (vt.status == VtStatus.SCANNING) {
                    Spacer(Modifier.width(8.dp))
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = vtColor,
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            when (vt.status) {
                VtStatus.SCANNING -> {
                    Text(
                        text = "Scanning file…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VtStatus.CLEAN -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.CheckCircle, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("No threats detected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Text(
                        text = "${vt.harmless} engines confirmed safe · ${vt.undetected} undetected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VtStatus.MALICIOUS -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${vt.malicious} engine(s) detected threats!", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                    }
                    Text(
                        text = "${vt.malicious} malicious · ${vt.suspicious} suspicious · ${vt.harmless} harmless",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VtStatus.SUSPICIOUS -> {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Warning, null, tint = warningColor, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("${vt.suspicious} engine(s) flagged as suspicious", style = MaterialTheme.typography.bodySmall, color = warningColor)
                    }
                    Text(
                        text = "${vt.suspicious} suspicious · ${vt.harmless} harmless · ${vt.undetected} undetected",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                VtStatus.NOT_FOUND -> {
                    Text("File not found in VirusTotal database", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                VtStatus.ERROR -> {
                    Text("Scan error: ${vt.errorMessage}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
                }
                else -> {}
            }
        }
    }
}

@Composable
private fun PermissionsCard(permissions: List<String>) {
    SectionCard(
        icon = Icons.Rounded.Security,
        title = "Permissions (${permissions.size})",
    ) {
        permissions.take(10).forEach { perm ->
            val shortPerm = perm.substringAfterLast('.')
            Text(
                text = "• $shortPerm",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 2.dp),
            )
        }
        if (permissions.size > 10) {
            Text(
                text = "… and ${permissions.size - 10} more",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 4.dp),
            )
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
