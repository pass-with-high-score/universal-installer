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
        onConfirmInstall = viewModel::confirmInstall,
        onDismissPreview = viewModel::dismissPendingInstall,
        onCancel = viewModel::cancelSession,
        onRetry = viewModel::retrySession,
        onClearHistory = viewModel::clearHistory,
        onCheckVirusTotal = { viewModel.scanVirusTotal(context) },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallUi(
    modifier: Modifier = Modifier,
    uiState: InstallUiState = InstallUiState(),
    history: List<InstallHistoryEntity> = emptyList(),
    onFilePicked: (uri: Uri, splitPackage: SplitPackage.Provider, fileName: String) -> Unit = { _, _, _ -> },
    onConfirmInstall: () -> Unit = {},
    onDismissPreview: () -> Unit = {},
    onCancel: (java.util.UUID) -> Unit = {},
    onRetry: (java.util.UUID) -> Unit = {},
    onClearHistory: () -> Unit = {},
    onCheckVirusTotal: () -> Unit = {},
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

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

            if (!isApkMime && extension !in validExtensions) {
                Toast.makeText(
                    context,
                    context.getString(R.string.install_unsupported_file),
                    Toast.LENGTH_LONG
                ).show()
                return@rememberLauncherForActivityResult
            }

            Timber.d("Selected file: $uri, MIME type: $mimeType")
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

                else -> SplitPackage.empty()
            }
            onFilePicked(uri, apks, displayName)
        }

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
            // File picker card
            item(key = "file_picker") {
                FilePickerCard(
                    isLoading = uiState.isLoading,
                    onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
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
