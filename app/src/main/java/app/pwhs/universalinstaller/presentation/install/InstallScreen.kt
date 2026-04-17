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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.InstallHistoryEntity
import app.pwhs.universalinstaller.presentation.composable.InstallerModeBadge
import app.pwhs.universalinstaller.presentation.composable.SessionCard
import app.pwhs.universalinstaller.util.extension.getDisplayName
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import org.koin.androidx.compose.koinViewModel
import ru.solrudev.ackpine.splits.ApkSplits.validate
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.splits.SplitPackage.Companion.toSplitPackage
import ru.solrudev.ackpine.splits.ZippedApkSplits
import timber.log.Timber

@Destination<RootGraph>(start = true)
@Composable
fun InstallScreen(modifier: Modifier = Modifier, viewModel: InstallViewModel = koinViewModel()) {
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
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    // "strict" means validate extension strictly against known package types; "permissive" accepts anything.
    var strictPickerMode by remember { mutableStateOf(true) }

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            // Take persistable permissions so we can delete the file later if needed
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
                    context.getString(R.string.install_unsupported_file),
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

    var selectedTab by rememberSaveable { mutableStateOf(SourceTab.Local) }

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
            )
        }
    }

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
