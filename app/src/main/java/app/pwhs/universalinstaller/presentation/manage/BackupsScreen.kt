package app.pwhs.universalinstaller.presentation.manage

import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.SettingsSection
import app.pwhs.universalinstaller.util.ApkFileIconData
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import org.koin.androidx.compose.koinViewModel
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupsScreen(
    modifier: Modifier = Modifier,
    viewModel: BackupsViewModel = koinViewModel(),
    onBack: () -> Unit,
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var pendingDeleteAll by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<BackupFile?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val navBarPadding = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.screen_title_backups))
                        if (uiState.files.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.backups_count_size,
                                    uiState.files.size,
                                    Formatter.formatShortFileSize(context, uiState.totalBytes),
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cancel),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = "Extraction Settings",
                        )
                    }
                    IconButton(
                        onClick = { openContainingFolder(context, viewModel.backupsDir) },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.FolderOpen,
                            contentDescription = stringResource(R.string.backup_action_open_folder),
                        )
                    }
                    if (uiState.files.isNotEmpty()) {
                        IconButton(onClick = { pendingDeleteAll = true }) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteSweep,
                                contentDescription = stringResource(R.string.backup_action_delete_all),
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        when {
            uiState.isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator() }
            }
            uiState.files.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.backups_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentPadding = PaddingValues(bottom = navBarPadding + 16.dp)
                ) {
                    items(items = uiState.files, key = { it.file.absolutePath }) { backup ->
                        BackupRow(
                            backup = backup,
                            onInstall = { installBackup(context, backup.file) },
                            onShare = { shareBackup(context, backup.file) },
                            onDelete = { pendingDelete = backup },
                        )
                    }
                }
            }
        }
    }

    if (showSettings) {
        ModalBottomSheet(
            onDismissRequest = { showSettings = false },
            sheetState = sheetState,
            dragHandle = null,
            contentWindowInsets = { WindowInsets(0, 0, 0, 0) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
                    .padding(bottom = navBarPadding + 16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                Text(
                    text = "Extraction Settings",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )

                SettingsSection(
                    title = "APK Extractor",
                    icon = Icons.Rounded.Folder
                ) {
                    var showDirPicker by remember { mutableStateOf(false) }
                    if (showDirPicker) {
                        DirectoryPickerDialog(
                            onPick = { path ->
                                viewModel.setExtractorOutputPath(path)
                                showDirPicker = false
                            },
                            onDismiss = { showDirPicker = false },
                        )
                    }
                    val folderPickerLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                        androidx.activity.result.contract.ActivityResultContracts.OpenDocumentTree()
                    ) { uri ->
                        uri?.let { viewModel.setExtractorOutputPath(it.toString()) }
                    }

                    val currentPath = uiState.extractorOutputPath
                    val displayPath = if (currentPath.startsWith("content://")) {
                        androidx.documentfile.provider.DocumentFile.fromTreeUri(context, Uri.parse(currentPath))?.name
                            ?: currentPath
                    } else {
                        currentPath.ifBlank { "Default (Download/UniversalInstaller/Extracted)" }
                    }

                    OutlinedTextField(
                        value = displayPath,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.backup_output_path)) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .clickable { folderPickerLauncher.launch(null) },
                        leadingIcon = { Icon(Icons.Rounded.Folder, null) },
                        trailingIcon = {
                            IconButton(onClick = { folderPickerLauncher.launch(null) }) {
                                Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, null)
                            }
                        },
                        enabled = true,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                            .also { interactionSource ->
                                LaunchedEffect(interactionSource) {
                                    interactionSource.interactions.collect {
                                        if (it is androidx.compose.foundation.interaction.PressInteraction.Release) {
                                            folderPickerLauncher.launch(null)
                                        }
                                    }
                                }
                            }
                    )

                    // SAF (above) blocks folders like Download; this built-in browser reaches
                    // them via the File API (needs All-files access). See #78.
                    TextButton(
                        onClick = { showDirPicker = true },
                        modifier = Modifier.padding(horizontal = 16.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(stringResource(R.string.dir_picker_browse_action))
                    }

                    // Drive the field from LOCAL state, not directly from uiState. Persisting
                    // each keystroke through DataStore and reading the value back is async, so
                    // binding `value` to the flow made the caret jump to the end mid-edit.
                    // Local state updates synchronously (caret stays put); DataStore is just a sink.
                    var templateField by remember {
                        mutableStateOf(
                            androidx.compose.ui.text.input.TextFieldValue(uiState.extractorFilenameTemplate)
                        )
                    }
                    OutlinedTextField(
                        value = templateField,
                        onValueChange = {
                            templateField = it
                            viewModel.setExtractorFilenameTemplate(it.text)
                        },
                        label = { Text(stringResource(R.string.backup_filename_template)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        leadingIcon = { Icon(Icons.Rounded.DriveFileRenameOutline, null) },
                        placeholder = { Text("{name}-{version}") },
                        supportingText = { Text(stringResource(R.string.backup_tags_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                    )

                    // Split-app output container. Single-APK apps are always saved as .apk;
                    // this only affects apps that ship split APKs (.apks vs .xapk).
                    Text(
                        text = stringResource(R.string.backup_split_format_label),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        listOf("apks" to "APKS", "xapk" to "XAPK").forEach { (value, label) ->
                            androidx.compose.material3.FilterChip(
                                selected = uiState.extractorSplitFormat == value,
                                onClick = { viewModel.setExtractorSplitFormat(value) },
                                label = { Text(label) },
                            )
                        }
                    }
                }

                TextButton(
                    onClick = { showSettings = false },
                    modifier = Modifier.align(Alignment.End)
                ) {
                    Text(stringResource(R.string.dialog_success_done))
                }
            }
        }
    }

    if (pendingDeleteAll) {
        AlertDialog(
            onDismissRequest = { pendingDeleteAll = false },
            title = { Text(stringResource(R.string.backup_delete_all_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.backup_delete_all_msg,
                        uiState.files.size,
                    ),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteAll()
                        pendingDeleteAll = false
                    },
                ) { Text(stringResource(R.string.backup_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteAll = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.backup_delete_one_title)) },
            text = { Text(target.name) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target)
                        pendingDelete = null
                    },
                ) { Text(stringResource(R.string.backup_action_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun BackupRow(
    backup: BackupFile,
    onInstall: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    ListItem(
        headlineContent = {
            Text(backup.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                text = "${Formatter.formatShortFileSize(context, backup.sizeBytes)} · " +
                    DateUtils.getRelativeTimeSpanString(
                        backup.lastModified,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(ApkFileIconData(backup.file.absolutePath))
                    .build(),
                contentDescription = backup.name,
                modifier = Modifier.size(40.dp),
                error = {
                    // Parse failed (corrupt APK, missing base.apk in split bundle, etc.) —
                    // fall back to a generic icon that still hints at the file type.
                    Icon(
                        imageVector = if (backup.isSplitBundle) Icons.Rounded.FolderZip
                            else Icons.Rounded.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                success = { SubcomposeAsyncImageContent() },
            )
        },
        trailingContent = {
            androidx.compose.foundation.layout.Row {
                IconButton(onClick = onInstall) {
                    Icon(
                        imageVector = Icons.Rounded.InstallMobile,
                        contentDescription = stringResource(R.string.backup_action_install),
                    )
                }
                IconButton(onClick = onShare) {
                    Icon(
                        imageVector = Icons.Rounded.Share,
                        contentDescription = stringResource(R.string.backup_action_share),
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.backup_action_delete),
                    )
                }
            }
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Route the backup file back through our own install pipeline (MainActivity intent filter). */
private fun installBackup(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        file,
    )
    val mime = "application/vnd.android.package-archive"
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mime)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        setPackage(BuildConfig.APPLICATION_ID)
    }
    runCatching { context.startActivity(intent) }
}

private fun shareBackup(context: android.content.Context, file: File) {
    val uri = FileProvider.getUriForFile(
        context,
        "${BuildConfig.APPLICATION_ID}.fileprovider",
        file,
    )
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, null))
    }
}

private fun openContainingFolder(context: android.content.Context, dir: File) {
    if (!dir.exists()) dir.mkdirs()
    val uri: Uri = try {
        FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            dir,
        )
    } catch (_: IllegalArgumentException) {
        Uri.parse("content://com.android.externalstorage.documents/root/primary")
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "resource/folder")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
