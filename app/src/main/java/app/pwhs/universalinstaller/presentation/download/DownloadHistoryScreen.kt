package app.pwhs.universalinstaller.presentation.download

import android.content.ClipData
import android.content.ClipboardManager
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.core.content.getSystemService
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.IntentHandoff
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.DownloadHistoryEntity
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import java.io.File
import java.text.DateFormat
import java.util.Date

@Destination<RootGraph>
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadHistoryScreen(
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    viewModel: DownloadHistoryViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val resource = LocalResources.current
    val items by viewModel.items.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showClearDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<DownloadHistoryEntity?>(null) }

    if (showClearDialog) {
        ConfirmDialog(
            title = stringResource(R.string.download_history_clear_title),
            message = stringResource(R.string.download_history_clear_message),
            confirmLabel = stringResource(R.string.download_history_clear_confirm),
            onConfirm = {
                viewModel.clearAll(alsoDeleteFiles = true)
                showClearDialog = false
            },
            onDismiss = { showClearDialog = false },
        )
    }
    pendingDelete?.let { entry ->
        ConfirmDialog(
            title = stringResource(R.string.download_history_delete_title),
            message = stringResource(R.string.download_history_delete_message, entry.fileName),
            confirmLabel = stringResource(R.string.download_history_delete_confirm),
            onConfirm = {
                viewModel.delete(entry, alsoDeleteFile = true)
                pendingDelete = null
            },
            onDismiss = { pendingDelete = null },
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = { Text(stringResource(R.string.download_history_title)) },
                navigationIcon = {
                    IconButton(onClick = { navigator.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = null)
                    }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { showClearDialog = true }) {
                            Icon(
                                Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.download_history_clear_cd),
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.download_history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(innerPadding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(items, key = { it.id }) { entry ->
                    DownloadRow(
                        entry = entry,
                        fileExists = viewModel.fileExists(entry),
                        onInstall = {
                            val file = File(entry.filePath)
                            if (!file.exists()) {
                                Toast.makeText(
                                    context,
                                    resource.getString(R.string.download_history_file_missing),
                                    Toast.LENGTH_SHORT,
                                ).show()
                                return@DownloadRow
                            }
                            val uri = FileProvider.getUriForFile(
                                context,
                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                file,
                            )
                            IntentHandoff.post(uri)
                            navigator.navigateUp()
                        },
                        onDelete = { pendingDelete = entry },
                        onCopyUrl = {
                            val cb = context.getSystemService<ClipboardManager>()
                            cb?.setPrimaryClip(ClipData.newPlainText("url", entry.url))
                            Toast.makeText(
                                context,
                                resource.getString(R.string.download_history_url_copied),
                                Toast.LENGTH_SHORT,
                            ).show()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadRow(
    entry: DownloadHistoryEntity,
    fileExists: Boolean,
    onInstall: () -> Unit,
    onDelete: () -> Unit,
    onCopyUrl: () -> Unit,
) {
    val context = LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }

    ElevatedCard(
        modifier = Modifier.fillMaxWidth().clickable(enabled = fileExists) { onInstall() },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = if (fileExists) Icons.Rounded.Android else Icons.Rounded.ErrorOutline,
                contentDescription = null,
                tint = if (fileExists) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(28.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.fileName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                val sizeStr = Formatter.formatShortFileSize(context, entry.sizeBytes)
                val dateStr = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
                    .format(Date(entry.downloadedAt))
                val status = if (fileExists) sizeStr
                             else stringResource(R.string.download_history_file_missing_inline)
                Text(
                    text = "$status · $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = entry.url,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = stringResource(R.string.download_history_row_menu),
                    )
                }
                DropdownMenu(
                    expanded = menuExpanded,
                    onDismissRequest = { menuExpanded = false },
                ) {
                    if (fileExists) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.download_history_action_install)) },
                            onClick = {
                                menuExpanded = false
                                onInstall()
                            },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download_history_action_copy_url)) },
                        onClick = {
                            menuExpanded = false
                            onCopyUrl()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.download_history_action_delete)) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )
}
