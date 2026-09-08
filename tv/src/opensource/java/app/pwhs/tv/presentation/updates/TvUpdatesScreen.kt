package app.pwhs.tv.presentation.updates

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import app.pwhs.tv.R
import app.pwhs.tv.presentation.updates.components.TvAddAppDialog
import app.pwhs.tv.presentation.updates.components.TvAppFilePickerDialog
import app.pwhs.tv.presentation.updates.components.TvUpdateAppRow
import app.pwhs.tv.presentation.updates.components.TvUpdateDetailsPane
import app.pwhs.tv.presentation.updates.components.TvUpdateFilterChip
import app.pwhs.updater.domain.model.TrackedApp
import app.pwhs.updater.presentation.UpdatesViewModel
import org.koin.androidx.compose.koinViewModel

private enum class FilterTab {
    INSTALLED,
    ALL,
    UPDATES_ONLY,
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvUpdatesScreen(
    modifier: Modifier = Modifier,
    viewModel: UpdatesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    var selectedFilter by remember { mutableStateOf(FilterTab.INSTALLED) }
    var focusedApp by remember { mutableStateOf<TrackedApp?>(null) }
    var showAddDialog by remember { mutableStateOf(false) }
    var appForFilePicker by remember { mutableStateOf<TrackedApp?>(null) }

    val firstRowFocus = remember { FocusRequester() }
    var didRequestFocus by remember { mutableStateOf(false) }

    val filteredApps = remember(uiState.trackedApps, selectedFilter) {
        when (selectedFilter) {
            FilterTab.INSTALLED -> uiState.trackedApps.filter { it.isInstalled }
            FilterTab.ALL -> uiState.trackedApps
            FilterTab.UPDATES_ONLY -> uiState.trackedApps.filter { it.hasUpdate }
        }
    }

    val updatesToRun = remember(filteredApps) {
        filteredApps.filter { it.hasUpdate && !it.latestDownloadUrl.isNullOrBlank() }
    }

    LaunchedEffect(filteredApps) {
        val current = focusedApp
        val fresh = filteredApps.firstOrNull { it.packageName == current?.packageName }
        when {
            fresh != null -> if (fresh != current) focusedApp = fresh
            else -> {
                focusedApp = filteredApps.firstOrNull()
                if (filteredApps.isNotEmpty()) runCatching { firstRowFocus.requestFocus() }
            }
        }
        if (!didRequestFocus && filteredApps.isNotEmpty()) {
            didRequestFocus = true
            runCatching { firstRowFocus.requestFocus() }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { err ->
            Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
        }
    }

    if (showAddDialog) {
        TvAddAppDialog(
            isLoading = uiState.isAdding,
            onAdd = { url ->
                viewModel.addTrackedAppFromUrl(
                    context = context,
                    url = url,
                    onSuccess = { showAddDialog = false },
                )
                showAddDialog = false
            },
            onDismiss = { showAddDialog = false },
        )
    }

    if (appForFilePicker != null) {
        val currentApp = appForFilePicker!!
        TvAppFilePickerDialog(
            appName = currentApp.appName,
            assets = currentApp.availableAssets,
            currentDownloadUrl = currentApp.latestDownloadUrl,
            onDismiss = { appForFilePicker = null },
            onConfirm = { chosenAsset ->
                val chosenApp = currentApp.copy(latestDownloadUrl = chosenAsset.downloadUrl)
                viewModel.updateTrackedApp(chosenApp)
                viewModel.downloadAndInstall(context, chosenApp)
                appForFilePicker = null
            },
        )
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Column: List of Tracked Apps
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 32.dp, vertical = 40.dp)
            ) {
                // Title
                Text(
                    text = stringResource(R.string.tv_updates_title),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                )

                Spacer(Modifier.height(16.dp))

                // Action Controls Row: Update All & Check Updates (weight 1f each, perfectly balanced!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val updateAllBtnShape = RoundedCornerShape(12.dp)
                    Button(
                        onClick = {
                            if (selectedFilter == FilterTab.ALL) {
                                viewModel.updateAll(context)
                            } else {
                                // Sequential update for filtered apps (Issue #135)
                                updatesToRun.forEach { app ->
                                    viewModel.downloadAndInstall(context, app)
                                }
                            }
                        },
                        enabled = updatesToRun.isNotEmpty() && !uiState.isUpdatingAll,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(updateAllBtnShape),
                        shape = ButtonDefaults.shape(updateAllBtnShape),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.tv_updates_update_all, updatesToRun.size),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = { viewModel.checkAllUpdates() },
                        enabled = !uiState.isChecking,
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(updateAllBtnShape),
                        shape = OutlinedButtonDefaults.shape(updateAllBtnShape),
                        colors = OutlinedButtonDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (uiState.isChecking) stringResource(R.string.tv_updates_checking) else stringResource(R.string.tv_updates_check_now),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                // Filter Chips Row + Add App button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TvUpdateFilterChip(
                        selected = selectedFilter == FilterTab.INSTALLED,
                        label = stringResource(R.string.tv_updates_filter_installed),
                        onClick = { selectedFilter = FilterTab.INSTALLED },
                    )
                    TvUpdateFilterChip(
                        selected = selectedFilter == FilterTab.ALL,
                        label = stringResource(R.string.tv_updates_filter_all),
                        onClick = { selectedFilter = FilterTab.ALL },
                    )
                    TvUpdateFilterChip(
                        selected = selectedFilter == FilterTab.UPDATES_ONLY,
                        label = stringResource(R.string.tv_updates_filter_updates),
                        badgeCount = uiState.updateCount,
                        onClick = { selectedFilter = FilterTab.UPDATES_ONLY },
                    )

                    Spacer(Modifier.weight(1f))

                    // Add App Button
                    val addBtnShape = RoundedCornerShape(12.dp)
                    OutlinedButton(
                        onClick = { showAddDialog = true },
                        modifier = Modifier
                            .height(40.dp)
                            .clip(addBtnShape),
                        shape = OutlinedButtonDefaults.shape(addBtnShape),
                        colors = OutlinedButtonDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.tv_updates_add_app),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(20.dp))

                // Tracked Apps List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer()
                        .focusGroup(),
                    contentPadding = PaddingValues(bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    if (filteredApps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillParentMaxHeight(0.5f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = stringResource(R.string.tv_updates_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge,
                                )
                            }
                        }
                    } else {
                        val firstPackage = filteredApps.firstOrNull()?.packageName
                        items(filteredApps, key = { it.packageName }) { app ->
                            TvUpdateAppRow(
                                app = app,
                                isSelected = { focusedApp?.packageName == app.packageName },
                                isDownloading = uiState.downloadingPackage == app.packageName,
                                downloadProgress = uiState.downloadProgress,
                                downloadBytesText = uiState.downloadBytesText,
                                focusRequester = if (app.packageName == firstPackage) firstRowFocus else null,
                                onFocus = { focusedApp = app },
                                onClick = {
                                    if (app.hasUpdate || !app.isInstalled) {
                                        if (app.availableAssets.size > 1) {
                                            appForFilePicker = app
                                        } else {
                                            viewModel.downloadAndInstall(context, app)
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }

            // Right Column: Focused App Details & Actions
            TvUpdateDetailsPane(
                app = focusedApp,
                isChecking = uiState.isChecking,
                isDownloading = uiState.downloadingPackage == focusedApp?.packageName,
                onUpdateOrInstall = { app ->
                    if (app.availableAssets.size > 1) {
                        appForFilePicker = app
                    } else {
                        viewModel.downloadAndInstall(context, app)
                    }
                },
                onCheckUpdate = { app -> viewModel.checkSingleUpdate(app.packageName) },
                onIgnoreVersion = { app ->
                    viewModel.updateTrackedApp(app.copy(ignoredVersion = if (app.isVersionIgnored) null else app.latestVersionName))
                },
                onRemove = { app -> viewModel.removeTrackedApp(app.packageName) },
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
    }
}
