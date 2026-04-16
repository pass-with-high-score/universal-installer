package com.nqmgaming.universalinstaller.presentation.install

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.nqmgaming.universalinstaller.presentation.composable.SessionCard
import com.nqmgaming.universalinstaller.util.extension.getDisplayName
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

    InstallUi(
        modifier = modifier,
        uiState = uiState,
        onFilePicked = { uri, splitPackage, fileName ->
            viewModel.parseApkInfo(context, uri, splitPackage, fileName)
        },
        onConfirmInstall = viewModel::confirmInstall,
        onDismissPreview = viewModel::dismissPendingInstall,
        onCancel = viewModel::cancelSession,
        onRetry = viewModel::retrySession,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InstallUi(
    modifier: Modifier = Modifier,
    uiState: InstallUiState = InstallUiState(),
    onFilePicked: (uri: Uri, splitPackage: SplitPackage.Provider, fileName: String) -> Unit = { _, _, _ -> },
    onConfirmInstall: () -> Unit = {},
    onDismissPreview: () -> Unit = {},
    onCancel: (java.util.UUID) -> Unit = {},
    onRetry: (java.util.UUID) -> Unit = {},
) {
    val context = LocalContext.current
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    val filePickerLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri == null) return@rememberLauncherForActivityResult
            val mimeType = context.contentResolver.getType(uri)?.lowercase()
            val displayName = context.contentResolver.getDisplayName(uri)
            val extension = displayName.substringAfterLast('.', "").lowercase()
            Timber.d("Selected file: $uri, MIME type: $mimeType")
            val apks = when {
                (mimeType == "application/vnd.android.package-archive" || extension == "apk") -> SingletonApkSequence(
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
            )
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                expandedHeight = 120.dp,
                title = {
                    Text(
                        text = "Install Package",
                        style = MaterialTheme.typography.headlineMedium,
                    )
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
            // File picker card — always visible
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
                        text = "Sessions",
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
        }
    }
}
