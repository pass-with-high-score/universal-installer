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
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
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
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel
import java.io.File

@Destination<RootGraph>
@Composable
fun SyncScreen(
    navigator: DestinationsNavigator,
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val state by viewModel.state.collectAsState()
    val serverUrl by viewModel.serverUrl.collectAsState()
    val pinCode by viewModel.pinCode.collectAsState()
    val activeConnections by viewModel.activeConnections.collectAsState()
    val sharedFiles by viewModel.sharedFiles.collectAsState()
    val activeTransfers by viewModel.activeTransfers.collectAsState()

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
            title = { Text("Storage Permission Required") },
            text = {
                Text(
                    "Sync Server needs full storage access to share and manage APK files. " +
                    "Please grant \"All files access\" in Settings.",
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
                    Text("Open Settings")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStorageDialog = false }) {
                    Text("Cancel")
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
        state = state,
        serverUrl = serverUrl,
        pinCode = pinCode,
        activeConnections = activeConnections,
        sharedFiles = sharedFiles,
        activeTransfers = activeTransfers,
        onBack = { navigator.navigateUp() },
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
    state: SyncState = SyncState.STOPPED,
    serverUrl: String? = null,
    pinCode: String? = null,
    activeConnections: Int = 0,
    sharedFiles: List<File> = emptyList(),
    activeTransfers: Map<String, TransferProgress> = emptyMap(),
    onBack: () -> Unit = {},
    onToggle: (Boolean) -> Unit = {},
    onPickFiles: () -> Unit = {},
    onDeleteFile: (File) -> Unit = {},
) {
    var showQrDialog by remember { mutableStateOf(false) }

    // QR Code Dialog
    if (showQrDialog && serverUrl != null) {
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
                        text = "Scan QR Code",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Open your camera and scan this code to access the server.",
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
                            data = serverUrl,
                            modifier = Modifier.padding(16.dp).fillMaxSize()
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = serverUrl,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(Modifier.height(24.dp))
                    TextButton(onClick = { showQrDialog = false }) {
                        Text("Close")
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
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    AnimatedVisibility(visible = state == SyncState.RUNNING && serverUrl != null, enter = fadeIn(), exit = fadeOut()) {
                        IconButton(onClick = { showQrDialog = true }) {
                            Icon(Icons.Rounded.QrCode, contentDescription = "Show QR Code")
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
                        color = if (state == SyncState.RUNNING)
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
                                tint = if (state == SyncState.RUNNING)
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                else
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Text(
                        text = when (state) {
                            SyncState.STOPPED -> "Server Offline"
                            SyncState.STARTING -> "Starting..."
                            SyncState.RUNNING -> "Server Online"
                            SyncState.ERROR -> "Error"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Share APK files to other devices on the same Wi-Fi network.",
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
                            tint = if (state == SyncState.RUNNING) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Server Mode",
                                style = MaterialTheme.typography.titleMedium,
                            )
                            Text(
                                text = if (state == SyncState.RUNNING) "Running" else "Tap to start",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = state == SyncState.RUNNING || state == SyncState.STARTING,
                            onCheckedChange = onToggle
                        )
                    }
                }
            }

            // Server info card (only when running)
            if (state == SyncState.RUNNING) {
                item(key = "info") {
                    Surface(
                        shape = MaterialTheme.shapes.large,
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            // Connection status
                            ListItem(
                                headlineContent = { Text("Active Downloads") },
                                supportingContent = {
                                    Text(
                                        if (activeConnections > 0) "$activeConnections downloading"
                                        else "No active downloads",
                                        color = if (activeConnections > 0) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Rounded.People,
                                        contentDescription = null,
                                        tint = if (activeConnections > 0) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                            // Server URL
                            ListItem(
                                headlineContent = { Text("Server URL") },
                                supportingContent = {
                                    Text(
                                        serverUrl ?: "",
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
                            val port = serverUrl?.substringAfterLast(":") ?: "8080"
                            ListItem(
                                headlineContent = { Text("Port") },
                                supportingContent = {
                                    Text(
                                        port,
                                        style = MaterialTheme.typography.bodyLarge,
                                        fontWeight = FontWeight.Bold,
                                    )
                                },
                                leadingContent = {
                                    Icon(Icons.Rounded.Public, contentDescription = null)
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )

                            // PIN (if enabled)
                            if (pinCode != null) {
                                HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))
                                ListItem(
                                    headlineContent = { Text("PIN Code") },
                                    supportingContent = {
                                        Text(
                                            pinCode,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 3.sp,
                                        )
                                    },
                                    leadingContent = {
                                        Icon(Icons.Rounded.Key, contentDescription = null)
                                    },
                                    colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                                )
                            }
                        }
                    }
                }

                // Transfer progress cards (supports multiple concurrent transfers)
                if (activeTransfers.isNotEmpty()) {
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
                                text = if (activeTransfers.size == 1) "Active Transfer" else "Active Transfers (${activeTransfers.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }

                    items(
                        items = activeTransfers.entries.toList(),
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
                                        text = "${progress.percentage}%",
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
                                    text = "${formatFileSize(progress.bytesTransferred)} / ${formatFileSize(progress.totalBytes)}",
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
                        Text("Add Files to Share", style = MaterialTheme.typography.labelLarge)
                    }
                }

                // Shared files list header
                if (sharedFiles.isNotEmpty()) {
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
                                text = "Shared Files (${sharedFiles.size})",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }

                // File items
                items(items = sharedFiles, key = { it.absolutePath }) { file ->
                    SharedFileItem(
                        file = file,
                        onDelete = { onDeleteFile(file) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // Empty state for stopped server
            if (state == SyncState.STOPPED) {
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
                            text = "Turn on the server to start sharing files",
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
                    text = formatFileSize(file.length()),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = "Delete",
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> "%.1f GB".format(gb)
        mb >= 1.0 -> "%.1f MB".format(mb)
        kb >= 1.0 -> "%.0f KB".format(kb)
        else -> "$bytes B"
    }
}