package app.pwhs.universalinstaller.presentation.sync

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.core.net.toUri
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pwhs.core.presentation.adaptive.LocalAdaptiveLayoutInfo
import app.pwhs.core.presentation.adaptive.MaxWidthContainer
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.components.PackageDropZone
import app.pwhs.universalinstaller.presentation.composable.QrCode
import app.pwhs.universalinstaller.presentation.setting.components.SyncOptionsBottomSheet
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncScreen(
    modifier: Modifier = Modifier,
    viewModel: SyncViewModel = koinViewModel()
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val recentlyReceived by viewModel.recentlyReceived.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var selectedTab by rememberSaveable { mutableIntStateOf(0) }
    var showQrDialog by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }

    // Storage permission check
    var hasStoragePermission by remember { mutableStateOf(checkStoragePermission()) }
    var showStorageDialog by remember { mutableStateOf(false) }

    LifecycleResumeEffect(Unit) {
        hasStoragePermission = checkStoragePermission()
        onPauseOrDispose {}
    }

    val settingsLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        hasStoragePermission = checkStoragePermission()
    }

    // Snackbar notification when a file is received
    LaunchedEffect(recentlyReceived) {
        recentlyReceived?.let { file ->
            val result = snackbarHostState.showSnackbar(
                message = context.getString(R.string.sync_receive_file_received_toast, file.name),
                actionLabel = context.getString(R.string.sync_receive_btn_install),
                duration = SnackbarDuration.Long,
            )
            if (result == SnackbarResult.ActionPerformed) {
                viewModel.installApk(context, file)
            }
            viewModel.clearRecentlyReceived()
        }
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

    // Server Settings Bottom Sheet
    if (showSettingsSheet) {
        SyncOptionsBottomSheet(
            syncOptions = uiState.syncOptions,
            onDismiss = { showSettingsSheet = false },
            onSyncServerPortChanged = viewModel::setSyncServerPort,
            onSyncRequirePinChanged = viewModel::setSyncRequirePin,
            onSyncPinCodeChanged = viewModel::setSyncPinCode,
        )
    }

    // QR Code Dialog
    val currentServerUrl = uiState.serverUrl
    if (showQrDialog && currentServerUrl != null) {
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
                            data = currentServerUrl,
                            modifier = Modifier.padding(16.dp).fillMaxSize(),
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = currentServerUrl,
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

    val onSyncFilesPicked: (List<Uri>) -> Unit = { uris ->
        if (uris.isNotEmpty()) {
            viewModel.copyFilesToShareFolder(uris)
        }
    }
    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        onSyncFilesPicked,
    )
    val fallbackFilePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
        onSyncFilesPicked,
    )
    val safeLaunchFilePicker = {
        val launched = runCatching {
            filePickerLauncher.launch(arrayOf("*/*"))
        }.isSuccess
        if (!launched) {
            val fallbackLaunched = runCatching {
                fallbackFilePickerLauncher.launch("*/*")
            }.isSuccess
            if (!fallbackLaunched) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_no_file_picker),
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    val adaptiveInfo = LocalAdaptiveLayoutInfo.current
    val useRail = adaptiveInfo.useNavigationRail

    Scaffold(
        modifier = modifier,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(stringResource(R.string.quick_transfer_sync_title)) },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                    ),
                    navigationIcon = {
                        IconButton(onClick = { (context as? android.app.Activity)?.finish() }) {
                            Icon(
                                Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.back_cd),
                            )
                        }
                    },
                    actions = {
                        AnimatedVisibility(
                            visible = uiState.state == SyncState.RUNNING && uiState.serverUrl != null,
                            enter = fadeIn(),
                            exit = fadeOut(),
                        ) {
                            IconButton(onClick = { showQrDialog = true }) {
                                Icon(
                                    Icons.Rounded.QrCode,
                                    contentDescription = stringResource(R.string.sync_qr_code_title),
                                )
                            }
                        }

                        IconButton(onClick = { showSettingsSheet = true }) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = stringResource(R.string.sync_server_settings),
                            )
                        }
                    }
                )

                if (!useRail) {
                    PrimaryTabRow(
                        selectedTabIndex = selectedTab,
                        containerColor = Color.Transparent,
                        modifier = Modifier.fillMaxWidth(),
                        divider = {},
                    ) {
                        Tab(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            text = { Text(stringResource(R.string.sync_tab_send)) },
                            icon = {
                                Icon(Icons.Rounded.UploadFile, contentDescription = null)
                            },
                        )
                        Tab(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            text = { Text(stringResource(R.string.sync_tab_receive)) },
                            icon = {
                                Icon(Icons.Rounded.FileDownload, contentDescription = null)
                            },
                        )
                    }
                }
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                )
            }
        }
    ) { padding ->
        val colors = MaterialTheme.colorScheme
        val isAmoled = colors.background == Color.Black && colors.surface == Color.Black
        val contentPane: @Composable (Modifier) -> Unit = { paneModifier ->
            PackageDropZone(
                onUrisDropped = { uris ->
                    if (!hasStoragePermission) {
                        showStorageDialog = true
                    } else {
                        onSyncFilesPicked(uris)
                    }
                },
                modifier = paneModifier,
            ) {
                MaxWidthContainer(
                    maxWidth = if (adaptiveInfo.useTwoPane) 1120.dp else 680.dp,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    AnimatedContent(
                        targetState = selectedTab,
                        transitionSpec = {
                            if (targetState > initialState) {
                                (slideInHorizontally(animationSpec = tween(220)) { it } + fadeIn(animationSpec = tween(220)))
                                    .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { -it } + fadeOut(animationSpec = tween(220)))
                            } else {
                                (slideInHorizontally(animationSpec = tween(220)) { -it } + fadeIn(animationSpec = tween(220)))
                                    .togetherWith(slideOutHorizontally(animationSpec = tween(220)) { it } + fadeOut(animationSpec = tween(220)))
                            }
                        },
                        label = "SyncTabContent",
                        modifier = Modifier.fillMaxSize(),
                    ) { tabIndex ->
                        when (tabIndex) {
                            0 -> SyncSendTab(
                                uiState = uiState,
                                onToggle = { enabled ->
                                    if (enabled && !hasStoragePermission) {
                                        showStorageDialog = true
                                    } else {
                                        viewModel.toggleServer(enabled)
                                    }
                                },
                                onShowQr = { showQrDialog = true },
                                onOpenSettings = { showSettingsSheet = true },
                                onPickFiles = {
                                    if (!hasStoragePermission) {
                                        showStorageDialog = true
                                    } else {
                                        safeLaunchFilePicker()
                                    }
                                },
                                onDeleteFile = { viewModel.deleteSharedFile(it) },
                            )

                            1 -> SyncReceiveTab(
                                uiState = uiState,
                                onToggleServer = { enabled ->
                                    if (enabled && !hasStoragePermission) {
                                        showStorageDialog = true
                                    } else {
                                        viewModel.toggleServer(enabled)
                                    }
                                },
                                onShowQr = { showQrDialog = true },
                                onOpenSettings = { showSettingsSheet = true },
                                onInstallApk = { file -> viewModel.installApk(context, file) },
                                onDeleteFile = { viewModel.deleteReceivedFile(it) },
                            )
                        }
                    }
                }
            }
        }

        if (useRail) {
            val railItemColors = NavigationRailItemDefaults.colors(
                selectedIconColor = colors.onPrimaryContainer,
                selectedTextColor = colors.primary,
                indicatorColor = colors.primaryContainer,
                unselectedIconColor = colors.onSurfaceVariant,
                unselectedTextColor = colors.onSurfaceVariant,
            )
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
            ) {
                NavigationRail(
                    containerColor = if (isAmoled) Color.Black else NavigationRailDefaults.ContainerColor,
                    windowInsets = WindowInsets(0, 0, 0, 0),
                ) {
                    Column(
                        modifier = Modifier.fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        NavigationRailItem(
                            selected = selectedTab == 0,
                            onClick = { selectedTab = 0 },
                            colors = railItemColors,
                            icon = {
                                if (uiState.sharedFiles.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text(uiState.sharedFiles.size.toString()) } }) {
                                        Icon(Icons.Rounded.UploadFile, contentDescription = stringResource(R.string.sync_tab_send))
                                    }
                                } else {
                                    Icon(Icons.Rounded.UploadFile, contentDescription = stringResource(R.string.sync_tab_send))
                                }
                            },
                            label = { Text(stringResource(R.string.sync_tab_send)) },
                        )
                        NavigationRailItem(
                            selected = selectedTab == 1,
                            onClick = { selectedTab = 1 },
                            colors = railItemColors,
                            icon = {
                                if (uiState.receivedFiles.isNotEmpty()) {
                                    BadgedBox(badge = { Badge { Text(uiState.receivedFiles.size.toString()) } }) {
                                        Icon(Icons.Rounded.FileDownload, contentDescription = stringResource(R.string.sync_tab_receive))
                                    }
                                } else {
                                    Icon(Icons.Rounded.FileDownload, contentDescription = stringResource(R.string.sync_tab_receive))
                                }
                            },
                            label = { Text(stringResource(R.string.sync_tab_receive)) },
                        )
                    }
                }
                VerticalDivider(
                    thickness = 1.dp,
                    color = colors.outlineVariant.copy(alpha = 0.5f),
                )
                contentPane(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                )
            }
        } else {
            contentPane(
                Modifier
                    .fillMaxSize()
                    .padding(padding),
            )
        }
    }
}

private fun checkStoragePermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        true
    }
}
