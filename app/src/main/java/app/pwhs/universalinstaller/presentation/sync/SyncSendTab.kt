package app.pwhs.universalinstaller.presentation.sync

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import app.pwhs.universalinstaller.R
import java.io.File

/**
 * Mobile-first tab for sharing local APK packages with computers and devices over Wi-Fi.
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
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 32.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        // 1. Wi-Fi Server Hero Status Card
        item(key = "send_server_hero") {
            SyncServerHeroCard(
                state = uiState.state,
                serverUrl = uiState.serverUrl,
                syncOptions = uiState.syncOptions,
                onToggle = onToggle,
                onShowQr = onShowQr,
                onOpenSettings = onOpenSettings,
            )
        }

        // 2. Active Outgoing Transfers (Downloads from Phone to PC)
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

        // 3. Shared Packages Header & Add Files Button
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

        // 4. Illustrated How-It-Works Guide
        item(key = "send_guide") {
            SyncSendHowItWorksCard()
        }
    }
}
