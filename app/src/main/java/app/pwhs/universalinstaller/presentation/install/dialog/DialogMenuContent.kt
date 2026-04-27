package app.pwhs.universalinstaller.presentation.install.dialog

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Splitscreen
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SplitEntry
import app.pwhs.universalinstaller.domain.model.SplitType
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.presentation.install.AttachedObb
import kotlinx.coroutines.launch

/**
 * Stage 3: Extended Menu — full-featured option panel using a Tabbed Pager.
 * Inspired by InstallerX-Revived's App Info UI.
 *
 * Tabs:
 *  1. Info (App Details, Architectures, Languages, SHA-256)
 *  2. Security (VirusTotal Scan, Permissions)
 *  3. Advanced (OBB Files / Attach OBB, Split APK selector)
 */
@Composable
fun DialogMenuContent(
    apkInfo: ApkInfo,
    attachedObbFiles: List<AttachedObb>,
    onBack: () -> Unit,
    onInstall: () -> Unit,
    onCheckVirusTotal: () -> Unit,
    onRemoveObb: (AttachedObb) -> Unit,
    onToggleSplit: (Int) -> Unit,
    onAttachObb: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    
    val tabs = listOf(
        stringResource(R.string.dialog_tab_info),
        stringResource(R.string.dialog_tab_security),
        stringResource(R.string.dialog_tab_advanced),
    )
    val pagerState = rememberPagerState(pageCount = { tabs.size })

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // ── Title ──
        Text(
            text = stringResource(R.string.dialog_menu_title),
            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        )

        Spacer(modifier = Modifier.height(16.dp))

        // ── Tabs ──
        TabRow(
            selectedTabIndex = pagerState.currentPage,
            containerColor = androidx.compose.ui.graphics.Color.Transparent,
            divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)) },
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    Modifier.tabIndicatorOffset(tabPositions[pagerState.currentPage]),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = pagerState.currentPage == index,
                    onClick = {
                        scope.launch {
                            pagerState.animateScrollToPage(index)
                        }
                    },
                    text = {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.labelLarge,
                            color = if (pagerState.currentPage == index) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Pager Content ──
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.heightIn(min = 300.dp, max = 380.dp),
            verticalAlignment = Alignment.Top,
        ) { page ->
            // Use a LazyColumn inside each page for scrolling
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                when (page) {
                    0 -> infoTab(apkInfo, context)
                    1 -> securityTab(apkInfo, onCheckVirusTotal)
                    2 -> advancedTab(apkInfo, attachedObbFiles, onRemoveObb, onAttachObb, onToggleSplit)
                }
                
                // Add a bottom spacer so the last item isn't clipped
                item { Spacer(modifier = Modifier.height(8.dp)) }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // ── Buttons: [Back] [Install] ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(stringResource(R.string.dialog_back_btn))
            }

            Button(
                onClick = onInstall,
                modifier = Modifier.weight(1f),
            ) {
                Text(stringResource(R.string.dialog_install_btn))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// Tab Contents (Extension functions on LazyListScope)
// ─────────────────────────────────────────────────────────────────────────

private fun androidx.compose.foundation.lazy.LazyListScope.infoTab(
    apkInfo: ApkInfo,
    context: Context,
) {
    // 1. App Details
    item(key = "details") {
        MenuCard(
            title = stringResource(R.string.dialog_menu_details),
            description = stringResource(R.string.dialog_menu_details_desc),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Info,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            expanded = true, // Always expanded in this tab
            onClick = { /* Do nothing, static */ },
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                DetailRow(stringResource(R.string.apk_info_label_package), apkInfo.packageName)
                if (apkInfo.versionName.isNotBlank()) {
                    DetailRow(
                        stringResource(R.string.apk_info_label_version),
                        stringResource(R.string.apk_info_version_detail, apkInfo.versionName, apkInfo.versionCode),
                    )
                }
                if (apkInfo.minSdkVersion > 0) {
                    DetailRow(stringResource(R.string.apk_info_label_min_sdk), "API ${apkInfo.minSdkVersion}")
                }
                if (apkInfo.targetSdkVersion > 0) {
                    DetailRow(stringResource(R.string.apk_info_label_target_sdk), "API ${apkInfo.targetSdkVersion}")
                }
                if (apkInfo.fileSizeBytes > 0) {
                    DetailRow(stringResource(R.string.install_storage_title), Formatter.formatFileSize(context, apkInfo.fileSizeBytes))
                }
                DetailRow("Format", apkInfo.fileFormat)
            }
        }
    }

    // 2. Architectures
    if (apkInfo.supportedAbis.isNotEmpty()) {
        item(key = "architectures") {
            var expanded by remember { mutableStateOf(false) }
            MenuCard(
                title = stringResource(R.string.dialog_menu_architectures),
                description = apkInfo.supportedAbis.joinToString(", "),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
                badge = "${apkInfo.supportedAbis.size}",
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    apkInfo.supportedAbis.forEach { abi ->
                        Text(
                            text = abi,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // 3. Languages
    if (apkInfo.supportedLanguages.isNotEmpty()) {
        item(key = "languages") {
            var expanded by remember { mutableStateOf(false) }
            MenuCard(
                title = stringResource(R.string.dialog_menu_languages),
                description = stringResource(R.string.dialog_menu_languages_desc),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Language,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
                badge = "${apkInfo.supportedLanguages.size}",
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    apkInfo.supportedLanguages.chunked(4).forEach { row ->
                        Text(
                            text = row.joinToString("  ·  "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }

    // 4. SHA-256 Hash
    if (apkInfo.sha256.isNotBlank()) {
        item(key = "sha256") {
            var expanded by remember { mutableStateOf(false) }
            MenuCard(
                title = stringResource(R.string.dialog_menu_sha256),
                description = apkInfo.sha256.take(24) + "…",
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.ContentCopy,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = apkInfo.sha256,
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cm.setPrimaryClip(ClipData.newPlainText("SHA-256", apkInfo.sha256))
                            Toast.makeText(context, context.getString(R.string.dialog_menu_sha256_copied), Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy")
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.securityTab(
    apkInfo: ApkInfo,
    onCheckVirusTotal: () -> Unit,
) {
    // 1. VirusTotal
    item(key = "virustotal") {
        val vtResult = apkInfo.vtResult
        val vtDesc = when (vtResult?.status) {
            VtStatus.CLEAN -> stringResource(R.string.apk_info_vt_clean)
            VtStatus.MALICIOUS -> stringResource(R.string.apk_info_vt_malicious, vtResult.malicious)
            VtStatus.SUSPICIOUS -> stringResource(R.string.apk_info_vt_suspicious, vtResult.suspicious)
            VtStatus.SCANNING -> stringResource(R.string.apk_info_vt_scanning)
            VtStatus.UPLOADING -> stringResource(R.string.apk_info_vt_uploading, vtResult.uploadProgress)
            VtStatus.NO_API_KEY -> stringResource(R.string.apk_info_vt_no_api_key)
            else -> stringResource(R.string.dialog_menu_virustotal_desc)
        }
        val vtColor = when (vtResult?.status) {
            VtStatus.CLEAN -> MaterialTheme.colorScheme.tertiary
            VtStatus.MALICIOUS -> MaterialTheme.colorScheme.error
            VtStatus.SUSPICIOUS -> MaterialTheme.colorScheme.error
            else -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        MenuCard(
            title = stringResource(R.string.dialog_menu_virustotal),
            description = vtDesc,
            descriptionColor = vtColor,
            icon = {
                Icon(
                    imageVector = if (vtResult?.status == VtStatus.CLEAN)
                        Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                    tint = vtColor,
                )
            },
            onClick = onCheckVirusTotal,
        )
    }

    // 2. Permissions
    if (apkInfo.permissions.isNotEmpty()) {
        item(key = "permissions") {
            var expanded by remember { mutableStateOf(true) } // Expanded by default in this tab
            MenuCard(
                title = stringResource(R.string.dialog_menu_permissions),
                description = stringResource(R.string.dialog_menu_permissions_desc),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
                badge = "${apkInfo.permissions.size}",
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    apkInfo.permissions.forEach { perm ->
                        Text(
                            text = perm.substringAfterLast('.'),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.advancedTab(
    apkInfo: ApkInfo,
    attachedObbFiles: List<AttachedObb>,
    onRemoveObb: (AttachedObb) -> Unit,
    onAttachObb: () -> Unit,
    onToggleSplit: (Int) -> Unit,
) {
    // 1. OBB Files
    if (apkInfo.obbFileNames.isNotEmpty() || attachedObbFiles.isNotEmpty()) {
        item(key = "obb") {
            var expanded by remember { mutableStateOf(true) }
            val obbCount = apkInfo.obbFileNames.size + attachedObbFiles.size
            MenuCard(
                title = stringResource(R.string.dialog_menu_obb),
                description = stringResource(R.string.dialog_menu_obb_desc),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
                badge = "$obbCount",
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    apkInfo.obbFileNames.forEach { name ->
                        Text(
                            text = name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    attachedObbFiles.forEach { obb ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                text = obb.fileName,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f),
                            )
                            IconButton(
                                onClick = { onRemoveObb(obb) },
                                modifier = Modifier.size(24.dp),
                            ) {
                                Text(
                                    text = "✕",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // 1b. Attach OBB button
    item(key = "obb_attach") {
        MenuCard(
            title = stringResource(R.string.dialog_menu_obb_attach),
            description = stringResource(R.string.dialog_menu_obb_attach_desc),
            icon = {
                Icon(
                    imageVector = Icons.Rounded.Add,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
                )
            },
            onClick = onAttachObb,
        )
    }

    // 2. Split APKs
    if (apkInfo.splitEntries.size > 1) {
        item(key = "splits") {
            var expanded by remember { mutableStateOf(true) }
            val selectedCount = apkInfo.splitEntries.count { it.selected }
            MenuCard(
                title = stringResource(R.string.dialog_menu_splits),
                description = stringResource(R.string.dialog_menu_splits_desc),
                icon = {
                    Icon(
                        imageVector = Icons.Rounded.Splitscreen,
                        contentDescription = null,
                        modifier = Modifier.size(24.dp),
                    )
                },
                expanded = expanded,
                onClick = { expanded = !expanded },
                badge = "$selectedCount / ${apkInfo.splitEntries.size}",
            ) {
                val context = LocalContext.current
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    apkInfo.splitEntries.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = entry.selected,
                                onCheckedChange = {
                                    if (entry.type != SplitType.Base) onToggleSplit(index)
                                },
                                enabled = entry.type != SplitType.Base,
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = entry.name,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    text = Formatter.formatFileSize(context, entry.sizeBytes),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────
// UI Components
// ─────────────────────────────────────────────────────────────────────────

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp),
        )
    }
}

@Composable
private fun MenuCard(
    title: String,
    description: String,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    expanded: Boolean = false,
    badge: String? = null,
    descriptionColor: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurfaceVariant,
    expandedContent: (@Composable () -> Unit)? = null,
) {
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Column(modifier = Modifier.animateContentSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                icon()
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        if (badge != null) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = badge,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Text(
                        text = description,
                        style = MaterialTheme.typography.bodySmall,
                        color = descriptionColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            if (expanded && expandedContent != null) {
                HorizontalDivider(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                )
                expandedContent()
            }
        }
    }
}
