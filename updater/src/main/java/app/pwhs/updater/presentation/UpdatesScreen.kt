package app.pwhs.updater.presentation

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.core.R
import app.pwhs.core.ui.component.EmptyStateView
import app.pwhs.core.ui.theme.Spacing
import app.pwhs.updater.domain.model.TrackedApp
import app.pwhs.updater.presentation.component.TrackedAppCard
import app.pwhs.updater.presentation.component.UpdatesTopAppBar
import app.pwhs.updater.presentation.dialog.EditCategoryDialog
import app.pwhs.updater.presentation.dialog.TrackedAppDetailBottomSheet
import kotlinx.coroutines.launch
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.focus.FocusRequester
import app.pwhs.updater.presentation.component.UpdaterSearchBar

import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.ui.input.nestedscroll.nestedScroll
import app.pwhs.updater.presentation.dialog.AppFilePickerDialog
import app.pwhs.updater.presentation.dialog.SourceTokensDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdatesScreen(
    viewModel: UpdatesViewModel,
    onNavigateToAddApp: () -> Unit,
    onBackClick: (() -> Unit)? = null,
    bottomBar: @Composable () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    var searchActive by rememberSaveable { mutableStateOf(false) }
    val searchFocusRequester = remember { FocusRequester() }
    var appToEditCategory by remember { mutableStateOf<TrackedApp?>(null) }
    var appForFilePicker by remember { mutableStateOf<TrackedApp?>(null) }

    LaunchedEffect(searchActive) {
        if (searchActive) {
            searchFocusRequester.requestFocus()
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            runCatching {
                val stream = context.contentResolver.openInputStream(uri)
                val jsonString = stream?.bufferedReader()?.use { it.readText() }.orEmpty()
                if (jsonString.isNotBlank()) {
                    viewModel.importTrackedAppsFromJson(jsonString) { count ->
                        scope.launch {
                            snackbarHostState.showSnackbar(context.getString(R.string.updates_import_success, count))
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            snackbarHostState.showSnackbar(it)
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            UpdatesTopAppBar(
                uiState = uiState,
                context = context,
                searchActive = searchActive,
                scrollBehavior = scrollBehavior,
                onToggleSearch = {
                    searchActive = !searchActive
                    if (!searchActive) {
                        viewModel.onSearchQueryChanged("")
                    }
                },
                onBackClick = onBackClick,
                onUpdateAll = { viewModel.updateAll(context) },
                onSortOptionChanged = viewModel::onSortOptionChanged,
                onCheckAllUpdates = { viewModel.checkAllUpdates() },
                onTokensClick = { viewModel.showSourceTokensDialog(true) },
                onImportClick = { importLauncher.launch("*/*") },
                onExportClick = {
                    val jsonString = viewModel.exportTrackedAppsJson()
                    val sendIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "application/json"
                        putExtra(Intent.EXTRA_TEXT, jsonString)
                        putExtra(Intent.EXTRA_TITLE, "universal_installer_apps.json")
                    }
                    context.startActivity(Intent.createChooser(sendIntent, "Export Tracked Apps"))
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onNavigateToAddApp,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.updates_track_app)) },
                shape = MaterialTheme.shapes.large,
            )
        },
        bottomBar = bottomBar,
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
    ) { innerPadding ->
        PullToRefreshBox(
            isRefreshing = uiState.isChecking,
            onRefresh = { viewModel.checkAllUpdates() },
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Collapsible Search Bar
                UpdaterSearchBar(
                    query = uiState.searchQuery,
                    onQueryChange = viewModel::onSearchQueryChanged,
                    active = searchActive,
                    focusRequester = searchFocusRequester,
                )

                // Category Filter Chips
                if (uiState.trackedApps.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = Spacing.L, vertical = Spacing.XS),
                        horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    ) {
                        item {
                            FilterChip(
                                selected = uiState.selectedCategory == null,
                                onClick = { viewModel.onCategorySelected(null) },
                                label = { Text(stringResource(R.string.updates_filter_all)) },
                            )
                        }

                        item {
                            FilterChip(
                                selected = uiState.selectedCategory == UpdatesUiState.CATEGORY_INSTALLED,
                                onClick = {
                                    if (uiState.selectedCategory == UpdatesUiState.CATEGORY_INSTALLED) {
                                        viewModel.onCategorySelected(null)
                                    } else {
                                        viewModel.onCategorySelected(UpdatesUiState.CATEGORY_INSTALLED)
                                    }
                                },
                                label = {
                                    Text(
                                        if (uiState.installedCount > 0) {
                                            "${stringResource(R.string.updates_filter_installed)} (${uiState.installedCount})"
                                        } else {
                                            stringResource(R.string.updates_filter_installed)
                                        }
                                    )
                                },
                            )
                        }

                        item {
                            FilterChip(
                                selected = uiState.selectedCategory == UpdatesUiState.CATEGORY_UPDATES,
                                onClick = {
                                    if (uiState.selectedCategory == UpdatesUiState.CATEGORY_UPDATES) {
                                        viewModel.onCategorySelected(null)
                                    } else {
                                        viewModel.onCategorySelected(UpdatesUiState.CATEGORY_UPDATES)
                                    }
                                },
                                label = {
                                    Text(
                                        if (uiState.updateCount > 0) {
                                            "${stringResource(R.string.updates_filter_updates)} (${uiState.updateCount})"
                                        } else {
                                            stringResource(R.string.updates_filter_updates)
                                        }
                                    )
                                },
                            )
                        }

                        items(uiState.categories) { category ->
                            FilterChip(
                                selected = uiState.selectedCategory == category,
                                onClick = {
                                    if (uiState.selectedCategory == category) {
                                        viewModel.onCategorySelected(null)
                                    } else {
                                        viewModel.onCategorySelected(category)
                                    }
                                },
                                label = { Text(category) },
                            )
                        }
                    }
                }

                if (uiState.trackedApps.isEmpty()) {
                    // Empty State
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.XXL),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateView(
                            icon = Icons.Rounded.CloudDownload,
                            title = stringResource(R.string.updates_no_apps_title),
                            subtitle = stringResource(R.string.updates_no_apps_subtitle),
                            actionLabel = stringResource(R.string.updates_track_first_app),
                            onAction = onNavigateToAddApp,
                        )
                    }
                } else if (uiState.filteredApps.isEmpty()) {
                    // Search / Filter Empty State
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(Spacing.XXL),
                        contentAlignment = Alignment.Center,
                    ) {
                        EmptyStateView(
                            icon = Icons.Rounded.SearchOff,
                            title = stringResource(R.string.updates_no_results_title),
                            subtitle = if (uiState.searchQuery.isNotBlank()) {
                                stringResource(R.string.updates_no_results_subtitle, uiState.searchQuery)
                            } else {
                                stringResource(R.string.updates_no_apps_subtitle)
                            },
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Spacing.L,
                            end = Spacing.L,
                            top = Spacing.M,
                            bottom = 100.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(Spacing.M),
                    ) {
                        items(
                            items = uiState.filteredApps,
                            key = { it.packageName },
                        ) { app ->
                            TrackedAppCard(
                                app = app,
                                isDownloading = uiState.downloadingPackage == app.packageName,
                                downloadProgress = uiState.downloadProgress,
                                downloadBytesText = uiState.downloadBytesText,
                                onClick = {
                                    viewModel.selectAppForDetail(app)
                                },
                                onUpdateClick = {
                                    if (app.availableAssets.size > 1) {
                                        appForFilePicker = app
                                    } else {
                                        viewModel.downloadAndInstall(context, app)
                                    }
                                },
                                onCheckClick = {
                                    viewModel.checkSingleUpdate(app.packageName)
                                },
                                onDeleteClick = {
                                    viewModel.removeTrackedApp(app.packageName)
                                },
                                onEditCategoryClick = {
                                    appToEditCategory = app
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (appToEditCategory != null) {
        val currentApp = appToEditCategory!!
        EditCategoryDialog(
            appName = currentApp.appName,
            currentCategory = currentApp.category,
            existingCategories = uiState.categories,
            onDismiss = { appToEditCategory = null },
            onConfirm = { newCategory ->
                viewModel.updateAppCategory(currentApp.packageName, newCategory)
                appToEditCategory = null
            },
        )
    }

    if (uiState.selectedAppForDetail != null) {
        TrackedAppDetailBottomSheet(
            app = uiState.selectedAppForDetail!!,
            sheetState = detailSheetState,
            onDismiss = { viewModel.selectAppForDetail(null) },
            onUpdateApp = { updatedApp ->
                viewModel.updateTrackedApp(updatedApp)
            },
            onDeleteApp = { appToDelete ->
                viewModel.removeTrackedApp(appToDelete.packageName)
                viewModel.selectAppForDetail(null)
            },
            onCheckUpdate = { appToCheck ->
                viewModel.checkSingleUpdate(appToCheck.packageName)
            },
            onDownloadAndInstall = { appToInstall ->
                if (appToInstall.availableAssets.size > 1) {
                    appForFilePicker = appToInstall
                } else {
                    viewModel.downloadAndInstall(context, appToInstall)
                }
            },
        )
    }

    if (appForFilePicker != null) {
        val currentApp = appForFilePicker!!
        AppFilePickerDialog(
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

    if (uiState.showSourceTokensDialog) {
        SourceTokensDialog(
            initialGithubToken = uiState.githubToken,
            initialGitlabToken = uiState.gitlabToken,
            initialCodebergToken = uiState.codebergToken,
            onDismiss = { viewModel.showSourceTokensDialog(false) },
            onSave = { gh, gl, cb ->
                viewModel.saveSourceTokens(gh, gl, cb)
            },
        )
    }
}
