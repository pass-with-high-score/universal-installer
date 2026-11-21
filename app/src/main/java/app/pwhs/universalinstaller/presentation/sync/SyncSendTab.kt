package app.pwhs.universalinstaller.presentation.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import app.pwhs.core.presentation.adaptive.AdaptiveTwoPane
import app.pwhs.core.presentation.adaptive.FoldablePosture
import app.pwhs.core.presentation.adaptive.LocalAdaptiveLayoutInfo
import app.pwhs.core.presentation.adaptive.TabletopSplitLayout
import app.pwhs.universalinstaller.R
import java.io.File

/**
 * Adaptive tab for sharing local APK packages with computers and devices over Wi-Fi.
 * - Expanded / Book Fold: 2-pane (Server control + Guide on left, Shared Files on right).
 * - Tabletop Fold: Server hero on top half, Shared Files & controls on bottom half.
 * - Compact / Medium: Single vertical scrolling column.
 */
@Composable
fun SyncSendTab(
    uiState: SyncUiState,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
    onShowQr: () -> Unit,
    onOpenSettings: () -> Unit,
    onPickFiles: () -> Unit,
    onDeleteFile: (File) -> Unit,
) {
    val adaptiveInfo = LocalAdaptiveLayoutInfo.current
    val tabletopPosture = adaptiveInfo.posture as? FoldablePosture.Tabletop

    val serverHeroCard: @Composable () -> Unit = {
        SyncServerHeroCard(
            state = uiState.state,
            serverUrl = uiState.serverUrl,
            syncOptions = uiState.syncOptions,
            onToggle = onToggle,
            onShowQr = onShowQr,
            onOpenSettings = onOpenSettings,
        )
    }

    val filesItems: LazyListScope.() -> Unit = {
        // Active Outgoing Transfers (Downloads from Phone to PC)
        if (uiState.activeTransfers.isNotEmpty()) {
            item(key = "send_transfers_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Upload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.sync_active_transfers_count, uiState.activeTransfers.size),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            items(
                items = uiState.activeTransfers.values.toList(),
                key = { "send_transfer_${it.fileName}" },
            ) { progress ->
                SyncTransferProgressCard(progress = progress)
            }
        }

        // Shared Packages Header & Add Files Button
        item(key = "shared_packages_header") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.sync_shared_files_count, uiState.sharedFiles.size),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )

                FilledTonalButton(
                    onClick = onPickFiles,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                ) {
                    Icon(
                        Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.sync_add_files_to_share),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        if (uiState.sharedFiles.isNotEmpty()) {
            items(
                items = uiState.sharedFiles,
                key = { it.absolutePath },
            ) { file ->
                SyncPackageItemCard(
                    file = file,
                    isReceived = false,
                    onDelete = { onDeleteFile(file) },
                )
            }
        } else {
            item(key = "shared_packages_empty") {
                SyncEmptyStateCard(
                    icon = Icons.Rounded.FolderOpen,
                    title = stringResource(R.string.sync_send_empty_title),
                    description = stringResource(R.string.sync_send_empty_desc),
                    actionButtonText = stringResource(R.string.sync_add_files_to_share),
                    onActionClick = onPickFiles,
                )
            }
        }
    }

    when {
        tabletopPosture != null -> {
            TabletopSplitLayout(
                posture = tabletopPosture,
                modifier = modifier.fillMaxSize(),
                topContent = {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        serverHeroCard()
                    }
                },
                bottomContent = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        content = filesItems,
                    )
                },
            )
        }

        adaptiveInfo.useTwoPane -> {
            AdaptiveTwoPane(
                firstWeight = 0.44f,
                modifier = modifier.fillMaxSize(),
                first = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 20.dp, end = 16.dp, top = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        item(key = "send_server_hero") { serverHeroCard() }
                        item(key = "send_guide") { SyncSendHowItWorksCard() }
                    }
                },
                second = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        content = filesItems,
                    )
                },
            )
        }

        else -> {
            LazyColumn(
                modifier = modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                item(key = "send_server_hero") { serverHeroCard() }
                filesItems()
                item(key = "send_guide") { SyncSendHowItWorksCard() }
            }
        }
    }
}
