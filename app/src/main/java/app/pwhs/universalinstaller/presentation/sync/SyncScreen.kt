package app.pwhs.universalinstaller.presentation.sync

import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Public
import androidx.compose.material.icons.rounded.QrCode
import androidx.compose.material.icons.rounded.Tv
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.QrCode
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Composable
fun SyncScreen(
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    // Storage permission check
    var hasStoragePermission by remember { mutableStateOf(checkStoragePermission()) }
    var showStorageDialog by remember { mutableStateOf(false) }

    // Refresh permission state when returning from settings
    LifecycleResumeEffect(Unit) {
        hasStoragePermission = checkStoragePermission()
        onPauseOrDispose {}
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission = checkStoragePermission()
    }

    // Storage permission dialog
    if (showStorageDialog) {
        AlertDialog(
            onDismissRequest = { showStorageDialog = false },
            icon = {
                Icon(
                    Icons.Rounded.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
            title = { Text(stringResource(R.string.sync_storage_permission_title)) },
            text = {
                Text(
                    stringResource(R.string.sync_storage_permission_message),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showStorageDialog = false
                    val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                    } else {
                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                            data = "package:${context.packageName}".toUri()
                        }
                    }
                    settingsLauncher.launch(intent)
                }) {
                    Text(stringResource(R.string.sync_open_settings))
                }
            },
            dismissButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    val filePickerLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        if (uris.isNotEmpty()) {
            viewModel.copyFilesToShareFolder(uris)
        }
    }

    SyncUi(
        modifier = modifier,
        uiState = uiState,
        onBack = { val a = context as? android.app.Activity; a?.finish() },
        onToggle = { enabled ->
            if (enabled && !hasStoragePermission) {
                showStorageDialog = true
            } else {
                viewModel.toggleServer(enabled)
            }
        },
        onPickFiles = {
            if (!hasStoragePermission) {
                showStorageDialog = true
            } else {
                filePickerLauncher.launch(arrayOf("*/*"))
            }
        },
        onDeleteFile = { viewModel.deleteSharedFile(it) },
        onSetPort = viewModel::setSyncServerPort,
        onSetRequirePin = viewModel::setSyncRequirePin,
        onSetPinCode = viewModel::setSyncPinCode
    )
}

private fun checkStoragePermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true // On older versions, manifest permission is enough
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SyncUi(
    modifier: Modifier = Modifier,
    uiState: SyncUiState = SyncUiState(),
    onBack: () -> Unit = {},
    onToggle: (Boolean) -> Unit = {},
    onPickFiles: () -> Unit = {},
    onDeleteFile: (File) -> Unit = {},
    onSetPort: (String) -> Unit = {},
    onSetRequirePin: (Boolean) -> Unit = {},
    onSetPinCode: (String) -> Unit = {},
) {
    val context = LocalContext.current
    var showQrDialog by remember { mutableStateOf(false) }

    // QR Code Dialog
    if (showQrDialog && uiState.serverUrl != null) {
        Dialog(onDismissRequest = { showQrDialog = false }) {
            Surface(
                shape = MaterialTheme.shapes.extraLarge,
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = stringResource(R.string.sync_qr_code_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.sync_qr_code_message),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(24.dp))
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = Color.White,
                        modifier = Modifier.size(240.dp),
                    ) {
                        QrCode(
                            data = uiState.serverUrl,
                            modifier = Modifier.padding(16.dp).fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = uiState.serverUrl,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { showQrDialog = false }) {
                        Text(stringResource(R.string.sync_close))
                    }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.setting_section_sync)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = stringResource(R.string.back_cd))
                    }
                },
                actions = {
                    // Send an APK to an Android TV running Universal Installer (scan its QR).
                    val ctx = LocalContext.current
                    IconButton(onClick = {
                        ctx.startActivity(Intent(ctx, SendToTvActivity::class.java))
                    }) {
                        Icon(Icons.Rounded.Tv, contentDescription = stringResource(R.string.tv_sync_screen_title))
                    }
                    AnimatedVisibility(visible = uiState.state == SyncState.RUNNING && uiState.serverUrl != null, enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { showQrDialog = true }) {
                            Icon(Icons.Rounded.QrCode, contentDescription = stringResource(R.string.sync_qr_code_title))
                        }
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = padding.calculateTopPadding()),
            contentPadding = PaddingValues(
                start = 24.dp,
                end = 24.dp,
                top = 16.dp,
                bottom = 16.dp + padding.calculateBottomPadding()
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Hero section
            item(key = "hero") {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Surface(
                        shape = CircleShape,
                        color = if (uiState.state == SyncState.RUNNING)
                            MaterialTheme.colorScheme.primaryContainer
                        else
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.size(100.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Rounded.WifiTethering,
                                contentDescription = null,
                                modifier = Modifier.size(56.dp),
                                tint = if (uiState.state == SyncState.RUNNING)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = when (uiState.state) {
                            SyncState.STOPPED -> stringResource(R.string.sync_server_offline)
                            SyncState.STARTING -> stringResource(R.string.sync_server_starting)
                            SyncState.RUNNING -> stringResource(R.string.sync_server_online)
                            SyncState.ERROR -> stringResource(R.string.sync_server_error)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.sync_hero_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            // Server toggle card
            item(key = "toggle") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cloud,
                            contentDescription = null,
                            tint = if (uiState.state == SyncState.RUNNING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.sync_server_mode),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (uiState.state == SyncState.RUNNING) stringResource(R.string.sync_server_running) else stringResource(R.string.sync_server_tap_to_start),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.state == SyncState.RUNNING || uiState.state == SyncState.STARTING,
                            onCheckedChange = onToggle
                        )
                    }
                }
            }

            // Server Settings Card
            item(key = "settings") {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.sync_server_settings),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )

                        OutlinedTextField(
                            value = uiState.syncOptions.serverPort,
                            onValueChange = onSetPort,
                            label = { Text(stringResource(R.string.sync_port)) },
                            modifier = Modifier.fillMaxWidth(),
                            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                imeAction = androidx.compose.ui.text.input.ImeAction.Next
                            ),
                            singleLine = true,
                            enabled = uiState.state == SyncState.STOPPED
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth().clickable { onSetRequirePin(!uiState.syncOptions.requirePin) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(stringResource(R.string.sync_require_pin), style = MaterialTheme.typography.bodyLarge)
                                Text(stringResource(R.string.sync_require_pin_subtitle), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Switch(
                                checked = uiState.syncOptions.requirePin,
                                onCheckedChange = onSetRequirePin
                            )
                        }

                        if (uiState.syncOptions.requirePin) {
                            OutlinedTextField(
                                value = uiState.syncOptions.pinCode,
                                onValueChange = onSetPinCode,
                                label = { Text(stringResource(R.string.sync_pin_code)) },
                                modifier = Modifier.fillMaxWidth(),
                                keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                                    keyboardType = androidx.compose.ui.text.input.KeyboardType.Number,
                                    imeAction = androidx.compose.ui.text.input.ImeAction.Done
                                ),
                                singleLine = true
                            )
                        }
                    }
                }
            }

            // Server info card (only when running)
            if (uiState.state == SyncState.RUNNING) {
                item(key = "info") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Connection status
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.sync_active_downloads)) },
                                supportingContent = {
                                    Text(
                                        if (uiState.activeConnections > 0) stringResource(R.string.sync_active_downloads_count, uiState.activeConnections)
                                        else stringResource(R.string.sync_no_active_downloads),
                                        color = if (uiState.activeConnections > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Rounded.People,
                                        contentDescription = null,
                                        tint = if (uiState.activeConnections > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                            // Server URL
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.sync_server_url)) },
                                supportingContent = {
                                    Text(
                                        uiState.serverUrl ?: "",
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.Rounded.Link, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                            // Port
                            val port = uiState.serverUrl?.substringAfterLast(":") ?: "8080"
                            ListItem(
                                headlineContent = { Text(stringResource(R.string.sync_port)) },
                                supportingContent = {
                                    Text(
                                        port,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.Rounded.Public, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            // PIN (if enabled)
                            if (uiState.pinCode != null) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                ListItem(
                                    headlineContent = { Text(stringResource(R.string.sync_pin_code)) },
                                    supportingContent = {
                                        Text(
                                            uiState.pinCode,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 3.sp,
                                        )
                                    },
                                    leadingContent = {
                                        Icon(Icons.Rounded.Key, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }

                // Transfer progress cards (supports multiple concurrent transfers)
                if (uiState.activeTransfers.isNotEmpty()) {
                    item(key = "transfers_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.Cloud,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (uiState.activeTransfers.size == 1) stringResource(R.string.sync_active_transfer) else stringResource(R.string.sync_active_transfers_count, uiState.activeTransfers.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    items(
                        items = uiState.activeTransfers.entries.toList(),
                        key = { it.key }
                    ) { (_, progress) ->
                        Surface(
                            shape = MaterialTheme.shapes.large,
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .animateItem()
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = progress.fileName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Text(
                                        text = stringResource(R.string.sync_percentage, progress.percentage),
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progress.percentage / 100f },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp),
                                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                                )
                                Text(
                                    text = stringResource(
                                        R.string.sync_transfer_progress,
                                        formatFileSize(context, progress.bytesTransferred),
                                        formatFileSize(context, progress.totalBytes)
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                // Share Files button
                item(key = "share_btn") {
                    Button(
                        onClick = onPickFiles,
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = PaddingValues(16.dp)
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.sync_add_files_to_share), style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Shared files list header
                if (uiState.sharedFiles.isNotEmpty()) {
                    item(key = "files_header") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                Icons.Rounded.FolderOpen,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.sync_shared_files_count, uiState.sharedFiles.size),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // File items
                items(items = uiState.sharedFiles, key = { it.absolutePath }) { file ->
                    SharedFileItem(
                        file = file,
                        onDelete = { onDeleteFile(file) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // Empty state for stopped server
            if (uiState.state == SyncState.STOPPED) {
                item(key = "empty") {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WifiTethering,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp),
                            tint = MaterialTheme.colorScheme.outlineVariant,
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            text = stringResource(R.string.sync_empty_state_message),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SharedFileItem(
    file: File,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Surface(
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(44.dp),
                contentAlignment = Alignment.Center,
            ) {
                coil3.compose.SubcomposeAsyncImage(
                    model = coil3.request.ImageRequest.Builder(context)
                        .data(app.pwhs.universalinstaller.util.ApkFileIconData(file.absolutePath))
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    error = {
                        Icon(
                            imageVector = Icons.Rounded.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(32.dp),
                        )
                    },
                    loading = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp,
                        )
                    },
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = formatFileSize(context, file.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.sync_delete_cd),
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatFileSize(context: android.content.Context, bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> context.getString(R.string.file_size_gb, gb)
        mb >= 1.0 -> context.getString(R.string.file_size_mb, mb)
        kb >= 1.0 -> context.getString(R.string.file_size_kb, kb)
        else -> context.getString(R.string.file_size_b, bytes)
    }
}
