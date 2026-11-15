package app.pwhs.universalinstaller.presentation.sync

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import app.pwhs.universalinstaller.R
import java.io.File

/**
 * Mobile-first tab for receiving APK packages over local Wi-Fi and Web browser.
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Wi-Fi Server Hero Status Card
        item(key = "receive_server_hero") {
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

        // 2. Live Incoming Transfers (Uploads from PC to Phone)
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

        // 3. Received Packages Section
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

        // 4. Illustrated How-It-Works Guide
        item(key = "receive_guide") {
            SyncReceiveHowItWorksCard()
        }
    }
}
