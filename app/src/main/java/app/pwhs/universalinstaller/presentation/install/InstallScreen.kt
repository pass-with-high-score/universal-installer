package app.pwhs.universalinstaller.presentation.install

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.IntentHandoff
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.InstallHistoryEntity
import app.pwhs.universalinstaller.presentation.composable.InstallerModeBadge
import app.pwhs.universalinstaller.presentation.composable.SessionCard
import app.pwhs.universalinstaller.util.extension.getDisplayName




import kotlinx.coroutines.delay
import kotlinx.coroutines.yield
import org.koin.androidx.compose.koinViewModel
import ru.solrudev.ackpine.splits.ApkSplits.validate
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.splits.SplitPackage.Companion.toSplitPackage
import ru.solrudev.ackpine.splits.ZippedApkSplits
import timber.log.Timber

@Composable
fun InstallScreen(
    modifier: Modifier = Modifier,
    viewModel: InstallViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val history by viewModel.history.collectAsState()

    InstallUi(
        modifier = modifier,
        uiState = uiState,
        history = history,
        onFilePicked = { uri, splitPackage, fileName ->
            viewModel.parseApkInfo(context, uri, splitPackage, fileName)
        },
        onDownloadFromUrl = { url -> viewModel.downloadFromUrl(context, url) },
        onCancelDownload = viewModel::cancelDownload,
        onDismissDownloadError = viewModel::dismissDownloadError,
        onConfirmInstall = viewModel::confirmInstall,
        onDismissPreview = viewModel::dismissPendingInstall,
        onCancel = viewModel::cancelSession,
        onRetry = viewModel::retrySession,
        onClearHistory = viewModel::clearHistory,
        onCheckVirusTotal = { viewModel.scanVirusTotal(context) },
        onStartDeviceScan = { viewModel.startDeviceScan(context) },
        onDismissDeviceScan = viewModel::dismissDeviceScan,
        onPickFromScan = { found -> viewModel.pickFromScan(context, found) },
        onPickManyFromScan = { found -> viewModel.pickManyFromScan(context, found) },
        onDeleteScannedFiles = { found -> viewModel.deleteFoundFiles(context, found) },
        onDismissObbCopy = viewModel::dismissObbCopy,
        onAttachObb = { uri -> viewModel.attachObbFile(context, uri) },
        onRemoveObb = { obb -> viewModel.removeAttachedObb(obb.uri) },
        onGrantObbFolder = viewModel::onObbTreeGranted,
        obbTreeHintUri = viewModel::obbTreeHintUri,
        onOpenDownloadHistory = {
            context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.download.DownloadHistoryActivity::class.java))
        },
        onBatchPicked = { uris -> viewModel.parseBatch(context, uris) },
        onBatchToggleEntry = viewModel::toggleBatchSelection,
        onBatchToggleAll = viewModel::setBatchAllSelected,
        onBatchConfirm = viewModel::confirmBatchInstall,
        onBatchDismiss = viewModel::dismissBatchInstall,
        onToggleSplit = viewModel::toggleSplit,
        onOpenSyncServer = {
            context.startActivity(android.content.Intent(context, app.pwhs.universalinstaller.presentation.sync.SyncActivity::class.java))
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallUi(
    modifier: Modifier = Modifier,
    uiState: InstallUiState = InstallUiState(),
    history: List<InstallHistoryEntity> = emptyList(),
    onFilePicked: (uri: Uri, splitPackage: SplitPackage.Provider, fileName: String) -> Unit = { _, _, _ -> },
    onDownloadFromUrl: (String) -> Unit = {},
    onCancelDownload: () -> Unit = {},
    onDismissDownloadError: () -> Unit = {},
    onConfirmInstall: () -> Unit = {},
    onDismissPreview: () -> Unit = {},
    onCancel: (java.util.UUID) -> Unit = {},
    onRetry: (java.util.UUID) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onCheckVirusTotal: () -> Unit = {},
    onStartDeviceScan: () -> Unit = {},
    onDismissDeviceScan: () -> Unit = {},
    onPickFromScan: (FoundPackageFile) -> Unit = {},
    onPickManyFromScan: (List<FoundPackageFile>) -> Unit = {},
    onDeleteScannedFiles: (List<FoundPackageFile>) -> Unit = {},
    onDismissObbCopy: () -> Unit = {},
    onAttachObb: (Uri) -> Unit = {},
    onRemoveObb: (AttachedObb) -> Unit = {},
    onGrantObbFolder: (Uri?) -> Unit = {},
    obbTreeHintUri: () -> Uri? = { null },
    onOpenDownloadHistory: () -> Unit = {},
    onBatchPicked: (List<Uri>) -> Unit = {},
    onBatchToggleEntry: (Uri) -> Unit = {},
    onBatchToggleAll: (Boolean) -> Unit = {},
    onBatchConfirm: () -> Unit = {},
    onBatchDismiss: () -> Unit = {},
    onToggleSplit: (Int) -> Unit = {},
    onOpenSyncServer: () -> Unit = {},
) {
    val context = LocalContext.current
    val resource = LocalResources.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // "strict" means validate extension strictly against known package types; "permissive" accepts anything.
    var strictPickerMode by remember { mutableStateOf(true) }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
            if (uris.isEmpty()) return@rememberLauncherForActivityResult

            // Batch flow: 2+ files go through BatchInstallSheet where user can deselect
            // individual items and see per-row parse errors.
            if (uris.size >= 2) {
                uris.forEach { u ->
                    runCatching {
                        context.contentResolver.takePersistableUriPermission(
                            u, Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                }
                onBatchPicked(uris)
                return@rememberLauncherForActivityResult
            }

            val uri = uris.first()
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) { /* some providers don't support persistable permissions */ }
            val mimeType = context.contentResolver.getType(uri)?.lowercase()
            val displayName = context.contentResolver.getDisplayName(uri)
            val extension = displayName.substringAfterLast('.', "").lowercase()
            val validExtensions = listOf("apk", "apks", "xapk", "apkm", "zip")
            val isApkMime = mimeType == "application/vnd.android.package-archive"

            if (strictPickerMode && !isApkMime && extension !in validExtensions) {
                Toast.makeText(
                    context,
                    resource.getString(R.string.install_unsupported_file),
                    Toast.LENGTH_LONG
                ).show()
                return@rememberLauncherForActivityResult
            }

            Timber.d("Selected file: $uri, MIME type: $mimeType, strict: $strictPickerMode")
            val apks = when {
                (isApkMime || extension == "apk") -> SingletonApkSequence(
                    uri,
                    context
                ).toSplitPackage()

                extension in listOf("apks", "xapk", "apkm", "zip") -> ZippedApkSplits.getApksForUri(
                    uri,
                    context
                )
                    .validate()
                    .toSplitPackage()
                    .filterCompatible(context)

                else -> {
                    // Browse all: unknown extension. Try treating as APK — ackpine will error
                    // cleanly if it's not a real package.
                    SingletonApkSequence(uri, context).toSplitPackage()
                }
            }
            onFilePicked(uri, apks, displayName)
        }

    // OBB picker — reuses the manifest OpenDocument contract. We don't filter MIME strictly
    // (.obb has no standard MIME), so the VM validates extension on attach.
    val obbPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        uris.forEach { onAttachObb(it) }
    }

    // SAF folder picker for granting access to /Android/obb/<pkg>/ when Shizuku isn't
    // available. Result is passed straight to the VM which validates the chosen folder
    // matches the target package and persists the grant.
    val obbTreeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri -> onGrantObbFolder(uri) }

    var selectedTab by rememberSaveable { mutableStateOf(SourceTab.Local) }

    // Consume URIs delivered via ACTION_VIEW (Chrome downloads, file managers, Gmail). We
    // strict-validate the extension here because the manifest filter also accepts
    // `application/octet-stream`, which covers many non-package binaries.
    val pendingViewUri by IntentHandoff.pendingUri.collectAsState()
    LaunchedEffect(pendingViewUri) {
        val uri = pendingViewUri ?: return@LaunchedEffect
        IntentHandoff.consume()
        // Yield to let the composable tree finish its first layout pass so the bottom
        // sheet (driven by uiState.pendingApkInfo) can actually render.
        yield()
        delay(500)
        try {
            val mimeType = context.contentResolver.getType(uri)?.lowercase()
            val displayName = context.contentResolver.getDisplayName(uri)
            val extension = displayName.substringAfterLast('.', "").lowercase()
            val validExtensions = listOf("apk", "apks", "xapk", "apkm", "zip")
            val isApkMime = mimeType == "application/vnd.android.package-archive"
            if (!isApkMime && extension !in validExtensions) {
                Toast.makeText(
                    context,
                    resource.getString(R.string.install_unsupported_file),
                    Toast.LENGTH_LONG,
                ).show()
                return@LaunchedEffect
            }
            val apks = when {
                (isApkMime || extension == "apk") -> SingletonApkSequence(uri, context).toSplitPackage()
                extension in listOf("apks", "xapk", "apkm", "zip") -> ZippedApkSplits.getApksForUri(uri, context)
                    .validate()
                    .toSplitPackage()
                    .filterCompatible(context)
                else -> SingletonApkSequence(uri, context).toSplitPackage()
            }
            onFilePicked(uri, apks, displayName)
        } catch (e: Exception) {
            Timber.e(e, "Failed to process intent URI: $uri")
            Toast.makeText(
                context,
                resource.getString(R.string.install_unsupported_file),
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    // Batch share (`ACTION_SEND_MULTIPLE`) — hand the URIs to the VM's batch parser.
    val pendingViewUris by IntentHandoff.pendingUris.collectAsState()
    LaunchedEffect(pendingViewUris) {
        val uris = pendingViewUris ?: return@LaunchedEffect
        IntentHandoff.consumeBatch()
        yield()
        if (uris.size >= 2) onBatchPicked(uris)
    }

    val grantPermissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // Re-check on return — user may or may not have granted.
            onStartDeviceScan()
        }

    FoundApksSheet(
        scanState = uiState.scanState,
        onDismiss = onDismissDeviceScan,
        onGrantPermission = {
            grantPermissionLauncher.launch(ApkScanner.buildGrantIntent(context))
        },
        onRescan = onStartDeviceScan,
        onPick = onPickFromScan,
        onPickMany = onPickManyFromScan,
        onDeleteMany = onDeleteScannedFiles,
    )

    // APK Info Preview Bottom Sheet
    if (uiState.pendingApkInfo != null) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = onDismissPreview,
            sheetState = sheetState,
            shape = MaterialTheme.shapes.extraLarge,
        ) {
            ApkInfoContent(
                apkInfo = uiState.pendingApkInfo,
                onInstall = onConfirmInstall,
                onCancel = onDismissPreview,
                onCheckVirusTotal = onCheckVirusTotal,
                attachedObbFiles = uiState.attachedObbFiles,
                onAttachObb = { obbPickerLauncher.launch(arrayOf("*/*")) },
                onRemoveObb = onRemoveObb,
                onToggleSplit = onToggleSplit,
            )
        }
    }

    BatchInstallSheet(
        state = uiState.batchState,
        onDismiss = onBatchDismiss,
        onToggleEntry = onBatchToggleEntry,
        onToggleAll = onBatchToggleAll,
        onConfirm = onBatchConfirm,
    )

    var showPermissions by remember { mutableStateOf(false) }
    PermissionCenterSheet(
        visible = showPermissions,
        onDismiss = { showPermissions = false },
    )

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                expandedHeight = 140.dp,
                title = {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = stringResource(R.string.fab_install),
                            style = MaterialTheme.typography.headlineMedium,
                        )
                        InstallerModeBadge()
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSyncServer) {
                        Icon(
                            imageVector = Icons.Rounded.WifiTethering,
                            contentDescription = stringResource(R.string.setting_section_sync),
                        )
                    }
                    IconButton(onClick = { showPermissions = true }) {
                        Icon(
                            imageVector = Icons.Rounded.Shield,
                            contentDescription = stringResource(R.string.permissions_menu_cd),
                        )
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
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item(key = "storage") { StorageCard() }

            if (uiState.obbCopyState !is ObbCopyState.Idle) {
                item(key = "obb_copy") {
                    ObbCopyCard(
                        state = uiState.obbCopyState,
                        onDismiss = onDismissObbCopy,
                        onGrantFolder = {
                            @Suppress("DEPRECATION")
                            obbTreeLauncher.launch(obbTreeHintUri())
                        },
                    )
                }
            }

            item(key = "source_picker") {
                SourcePicker(
                    selectedTab = selectedTab,
                    onTabChange = { selectedTab = it },
                    isParsing = uiState.isLoading,
                    downloadState = uiState.downloadState,
                    onFindAutomatic = onStartDeviceScan,
                    onBrowsePackages = {
                        strictPickerMode = true
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onBrowseAll = {
                        strictPickerMode = false
                        filePickerLauncher.launch(arrayOf("*/*"))
                    },
                    onStartDownload = onDownloadFromUrl,
                    onCancelDownload = onCancelDownload,
                    onDismissDownloadError = onDismissDownloadError,
                    onOpenDownloadHistory = onOpenDownloadHistory,
                )
            }

            // Active sessions
            if (uiState.sessions.isNotEmpty()) {
                item(key = "sessions_header") {
                    Text(
                        text = stringResource(R.string.install_sessions_header),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                items(
                    items = uiState.sessions,
                    key = { it.id }
                ) { session ->
                    val sessionProgress =
                        uiState.sessionsProgress.find { it.id == session.id }
                    SessionCard(
                        sessionData = session,
                        sessionProgress = sessionProgress,
                        onCancel = { onCancel(session.id) },
                        onRetry = { onRetry(session.id) },
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            // Install history
            if (history.isNotEmpty()) {
                item(key = "history_header") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = stringResource(R.string.install_history_header),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        TextButton(onClick = onClearHistory) {
                            Text(stringResource(R.string.install_history_clear), style = MaterialTheme.typography.labelMedium)
                        }
                    }
                }
                items(
                    items = history,
                    key = { "history_${it.id}" }
                ) { entry ->
                    HistoryCard(
                        entry = entry,
                        modifier = Modifier.animateItem(),
                    )
                }
            }
        }
    }
}
