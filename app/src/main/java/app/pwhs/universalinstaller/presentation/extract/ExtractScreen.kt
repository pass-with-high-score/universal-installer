package app.pwhs.universalinstaller.presentation.extract

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.util.AppIconData
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExtractScreen(
    modifier: Modifier = Modifier,
    viewModel: ExtractViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    // Drive snackbars from the extract result. Re-run when the state object reference
    // changes — emitting once per Done/Error transition.
    LaunchedEffect(uiState.extractState) {
        when (val s = uiState.extractState) {
            is ExtractState.Done -> {
                val res = snackbarHostState.showSnackbar(
                    message = context.getString(R.string.extract_done, s.file.name),
                    actionLabel = context.getString(R.string.extract_done_action_open),
                    withDismissAction = true,
                )
                if (res == SnackbarResult.ActionPerformed) {
                    openContainingFolder(context, s.file)
                }
                viewModel.dismissResult()
            }
            is ExtractState.Error -> {
                snackbarHostState.showSnackbar(
                    message = context.getString(R.string.extract_failed, s.message),
                    withDismissAction = true,
                )
                viewModel.dismissResult()
            }
            else -> Unit
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.screen_title_extract)) })
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = viewModel::setQuery,
                    placeholder = { Text(stringResource(R.string.extract_search_hint)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(R.string.extract_show_system),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = uiState.showSystemApps,
                    onCheckedChange = { viewModel.toggleSystemApps() },
                )
            }

            val running = uiState.extractState as? ExtractState.Running
            if (running != null) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.extract_running, running.appName),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.height(4.dp))
                    val progress = if (running.totalBytes > 0)
                        running.bytesCopied.toFloat() / running.totalBytes
                    else 0f
                    LinearProgressIndicator(
                        progress = { progress.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) { CircularProgressIndicator() }
                }
                uiState.filteredApps.isEmpty() -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.extract_empty),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(
                            items = uiState.filteredApps,
                            key = { it.packageName },
                        ) { app ->
                            AppRow(
                                app = app,
                                enabled = uiState.extractState !is ExtractState.Running,
                                onClick = { viewModel.extract(app.packageName, app.appName) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AppRow(
    app: InstalledApp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val context = LocalContext.current
    ListItem(
        headlineContent = {
            Text(app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
        },
        supportingContent = {
            Text(
                buildString {
                    append(app.packageName)
                    if (app.versionName.isNotBlank()) {
                        append(" · ")
                        append(app.versionName)
                    }
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        leadingContent = {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(AppIconData(app.packageName))
                    .build(),
                contentDescription = null,
                modifier = Modifier.size(40.dp),
                error = {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                success = { SubcomposeAsyncImageContent() },
            )
        },
        trailingContent = {
            AssistChip(
                onClick = onClick,
                enabled = enabled,
                leadingIcon = {
                    Icon(
                        imageVector = if (app.hasSplits) Icons.Rounded.FolderZip
                            else Icons.Rounded.Folder,
                        contentDescription = null,
                        modifier = Modifier.size(AssistChipDefaults.IconSize),
                    )
                },
                label = {
                    Text(
                        text = if (app.hasSplits)
                            stringResource(R.string.extract_label_split)
                        else
                            stringResource(R.string.extract_label_single),
                    )
                },
            )
        },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
    )
}

/** Open the parent directory in the user's file manager. */
private fun openContainingFolder(
    context: android.content.Context,
    file: java.io.File,
) {
    val parent = file.parentFile ?: return
    val uri: Uri = try {
        FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            parent,
        )
    } catch (_: IllegalArgumentException) {
        // FileProvider doesn't have a path config covering the public Downloads folder —
        // fall back to ACTION_VIEW on a generic Downloads URI.
        Uri.parse("content://com.android.externalstorage.documents/root/primary")
    }
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, "resource/folder")
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    runCatching { context.startActivity(intent) }
}
