package app.pwhs.universalinstaller.presentation.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
 * Adaptive tab for receiving APK packages over local Wi-Fi and Web browser.
 * - Expanded / Book Fold: 2-pane (Server control + Guide on left, Received Packages on right).
 * - Tabletop Fold: Server hero on top half, Received Packages on bottom half.
 * - Compact / Medium: Single vertical scrolling column.
 */
@Composable
fun SyncReceiveTab(
    uiState: SyncUiState,
    modifier: Modifier = Modifier,
    onToggleServer: (Boolean) -> Unit,
    onShowQr: () -> Unit,
    onOpenSettings: () -> Unit,
    onInstallApk: (File) -> Unit,
    onDeleteFile: (File) -> Unit,
) {
    val adaptiveInfo = LocalAdaptiveLayoutInfo.current
    val tabletopPosture = adaptiveInfo.posture as? FoldablePosture.Tabletop

    val serverHeroCard: @Composable () -> Unit = {
        SyncServerHeroCard(
            state = uiState.state,
            serverUrl = uiState.serverUrl,
            syncOptions = uiState.syncOptions,
            onToggle = onToggleServer,
            onShowQr = onShowQr,
            onOpenSettings = onOpenSettings,
            subtitleOverride = if (uiState.state == SyncState.RUNNING) {
                stringResource(R.string.sync_receive_subtitle)
            } else {
                stringResource(R.string.sync_receive_empty_desc)
            },
        )
    }

    val receivedItems: LazyListScope.() -> Unit = {
        // Live Incoming Transfers (Uploads from PC to Phone)
        if (uiState.activeTransfers.isNotEmpty()) {
            item(key = "receive_transfers_header") {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.sync_receive_incoming_transfer),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            items(
                items = uiState.activeTransfers.values.toList(),
                key = { "receive_transfer_${it.fileName}" },
            ) { progress ->
                SyncTransferProgressCard(progress = progress)
            }
        }

        // Received Packages Section
        item(key = "received_packages_header") {
            Text(
                text = stringResource(R.string.sync_receive_packages_header, uiState.receivedFiles.size),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        }

        if (uiState.receivedFiles.isNotEmpty()) {
            items(
                items = uiState.receivedFiles,
                key = { it.absolutePath },
            ) { file ->
                SyncPackageItemCard(
                    file = file,
                    isReceived = true,
                    onInstall = { onInstallApk(file) },
                    onDelete = { onDeleteFile(file) },
                )
            }
        } else {
            item(key = "received_packages_empty") {
                SyncEmptyStateCard(
                    icon = Icons.Rounded.Inbox,
                    title = stringResource(R.string.sync_receive_empty_title),
                    description = stringResource(R.string.sync_receive_empty_desc),
                    actionButtonText = if (uiState.state != SyncState.RUNNING) {
                        stringResource(R.string.sync_receive_start_server_btn)
                    } else null,
                    onActionClick = if (uiState.state != SyncState.RUNNING) {
                        { onToggleServer(true) }
                    } else null,
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
                        content = receivedItems,
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
                        item(key = "receive_server_hero") { serverHeroCard() }
                        item(key = "receive_guide") { SyncReceiveHowItWorksCard() }
                    }
                },
                second = {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        content = receivedItems,
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
                item(key = "receive_server_hero") { serverHeroCard() }
                receivedItems()
                item(key = "receive_guide") { SyncReceiveHowItWorksCard() }
            }
        }
    }
}
