package app.pwhs.tv.presentation.manage

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import app.pwhs.core.domain.InstalledApp
import app.pwhs.tv.R
import app.pwhs.tv.presentation.components.TvInstallerModeBadge
import app.pwhs.tv.presentation.manage.components.AppDetailsPane
import app.pwhs.tv.presentation.manage.components.AppListRow
import app.pwhs.tv.presentation.manage.components.ConfirmAction
import app.pwhs.tv.presentation.manage.components.ConfirmDialog
import app.pwhs.tv.presentation.manage.components.LoadingRow
import app.pwhs.tv.presentation.manage.components.ManageStatusOverlay
import app.pwhs.tv.presentation.manage.components.SmallFilterChip

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ManageScreen(
    modifier: Modifier = Modifier,
    viewModel: ManageViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val actionResult by viewModel.actionResult.collectAsState()
    var focusedApp by remember { mutableStateOf<InstalledApp?>(null) }
    var confirm by remember { mutableStateOf<ConfirmAction?>(null) }
    val context = LocalContext.current

    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val firstRowFocus = remember { FocusRequester() }
    var didRequestFocus by remember { mutableStateOf(false) }

    val uninstallLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        viewModel.refresh()
    }

    fun scrollToTopAndFocus() {
        coroutineScope.launch {
            listState.scrollToItem(0)
            focusedApp = uiState.filteredApps.firstOrNull()
            delay(50)
            runCatching { firstRowFocus.requestFocus() }
        }
    }

    var lastSortBy by remember { mutableStateOf(uiState.sortBy) }
    var lastFilter by remember { mutableStateOf(uiState.filter) }

    LaunchedEffect(uiState.sortBy, uiState.filter) {
        if (uiState.sortBy != lastSortBy || uiState.filter != lastFilter) {
            lastSortBy = uiState.sortBy
            lastFilter = uiState.filter
            if (uiState.filteredApps.isNotEmpty()) {
                listState.scrollToItem(0)
                focusedApp = uiState.filteredApps.firstOrNull()
                delay(50)
                runCatching { firstRowFocus.requestFocus() }
            }
        }
    }

    LaunchedEffect(uiState.filteredApps, uiState.isLoading) {
        if (uiState.isLoading) return@LaunchedEffect
        val current = focusedApp
        val fresh = uiState.filteredApps.firstOrNull { it.packageName == current?.packageName }
        when {
            fresh != null -> if (fresh != current) focusedApp = fresh
            else -> {
                focusedApp = uiState.filteredApps.firstOrNull()
                if (uiState.filteredApps.isNotEmpty()) runCatching { firstRowFocus.requestFocus() }
            }
        }
        if (!didRequestFocus && uiState.filteredApps.isNotEmpty()) {
            didRequestFocus = true
            runCatching { firstRowFocus.requestFocus() }
        }
    }

    confirm?.let { pending ->
        ConfirmDialog(
            action = pending,
            onConfirm = {
                when (pending) {
                    is ConfirmAction.Uninstall -> viewModel.uninstallSilent(pending.app)
                    is ConfirmAction.ClearData -> viewModel.clearData(pending.app)
                }
                confirm = null
            },
            onCancel = { confirm = null },
        )
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Row(modifier = Modifier.fillMaxSize()) {
            // Left Column: Apps List (with Sidebar Background)
            Column(
                modifier = Modifier
                    .weight(1.1f)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = stringResource(R.string.tv_manage_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )
                    TvInstallerModeBadge()
                }
                
                Spacer(Modifier.height(12.dp))

                // Search Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.weight(1f).height(54.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        placeholder = { Text(stringResource(R.string.tv_manage_search_placeholder), style = MaterialTheme.typography.bodyLarge) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(22.dp)) },
                        shape = RoundedCornerShape(12.dp),
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
                            focusedLeadingIconColor = MaterialTheme.colorScheme.primary,
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }
                
                Spacer(Modifier.height(10.dp))
                
                // Filters and Sort in one or two compact rows
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    SmallFilterChip(
                        selected = uiState.filter == AppFilter.User,
                        label = stringResource(R.string.tv_manage_filter_user),
                        onClick = {
                            viewModel.setFilter(AppFilter.User)
                            scrollToTopAndFocus()
                        }
                    )
                    SmallFilterChip(
                        selected = uiState.filter == AppFilter.Sideloaded,
                        label = stringResource(R.string.tv_manage_filter_sideloaded),
                        onClick = {
                            viewModel.setFilter(AppFilter.Sideloaded)
                            scrollToTopAndFocus()
                        }
                    )
                    SmallFilterChip(
                        selected = uiState.filter == AppFilter.System,
                        label = stringResource(R.string.tv_manage_filter_system),
                        onClick = {
                            viewModel.setFilter(AppFilter.System)
                            scrollToTopAndFocus()
                        }
                    )
                    SmallFilterChip(
                        selected = uiState.filter == AppFilter.Disabled,
                        label = stringResource(R.string.tv_manage_filter_disabled),
                        onClick = {
                            viewModel.setFilter(AppFilter.Disabled)
                            scrollToTopAndFocus()
                        }
                    )
                }

                Spacer(Modifier.height(8.dp))
                
                // Sort chips & Batch select toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallFilterChip(
                            selected = uiState.sortBy == SortBy.Name,
                            label = stringResource(R.string.tv_manage_sort_name),
                            onClick = {
                                viewModel.setSortBy(SortBy.Name)
                                scrollToTopAndFocus()
                            }
                        )
                        SmallFilterChip(
                            selected = uiState.sortBy == SortBy.Size,
                            label = stringResource(R.string.tv_manage_sort_size),
                            onClick = {
                                viewModel.setSortBy(SortBy.Size)
                                scrollToTopAndFocus()
                            }
                        )
                        SmallFilterChip(
                            selected = uiState.sortBy == SortBy.Date,
                            label = stringResource(R.string.tv_manage_sort_date),
                            onClick = {
                                viewModel.setSortBy(SortBy.Date)
                                scrollToTopAndFocus()
                            }
                        )
                    }

                    SmallFilterChip(
                        selected = uiState.selectionMode,
                        label = if (uiState.selectionMode) stringResource(R.string.tv_manage_confirm_cancel) else stringResource(R.string.tv_manage_batch_select),
                        onClick = {
                            if (uiState.selectionMode) viewModel.exitSelection() else viewModel.enterSelection()
                        }
                    )
                }

                if (uiState.selectionMode) {
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        SmallFilterChip(
                            selected = false,
                            label = stringResource(R.string.tv_manage_batch_select_all),
                            onClick = { viewModel.selectAllVisible() }
                        )
                        SmallFilterChip(
                            selected = false,
                            label = stringResource(R.string.tv_manage_batch_clear),
                            onClick = { viewModel.exitSelection(); viewModel.enterSelection() }
                        )
                        if (uiState.selectedPackages.isNotEmpty() && uiState.rootAvailable) {
                            SmallFilterChip(
                                selected = true,
                                label = stringResource(R.string.tv_manage_batch_uninstall, uiState.selectedPackages.size),
                                onClick = { viewModel.batchUninstall() }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer()
                        .focusGroup(),
                    contentPadding = PaddingValues(bottom = 64.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (uiState.isLoading) {
                        items(8) { LoadingRow() }
                    } else if (uiState.filteredApps.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillParentMaxHeight(0.5f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = stringResource(R.string.tv_manage_empty),
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodyLarge
                                )
                            }
                        }
                    } else {
                        val firstPackage = uiState.filteredApps.firstOrNull()?.packageName
                        items(uiState.filteredApps, key = { it.packageName }) { app ->
                            AppListRow(
                                app = app,
                                isSelected = focusedApp?.packageName == app.packageName,
                                focusRequester = if (app.packageName == firstPackage) firstRowFocus else null,
                                selectionMode = uiState.selectionMode,
                                isChecked = uiState.selectedPackages.contains(app.packageName),
                                onToggleCheck = { viewModel.toggleSelection(app.packageName) },
                                onFocus = { focusedApp = app }
                            )
                        }
                    }
                }
            }

            // Right Column: Details & Actions (Main Content Background)
            AppDetailsPane(
                focusedApp = focusedApp,
                isLoading = uiState.isLoading,
                rootAvailable = uiState.rootAvailable,
                searchQuery = uiState.searchQuery,
                onOpen = { app ->
                    runCatching {
                        val pm = context.packageManager
                        val intent = pm.getLeanbackLaunchIntentForPackage(app.packageName)
                            ?: pm.getLaunchIntentForPackage(app.packageName)
                        intent?.let { context.startActivity(it) }
                    }
                },
                onForceStop = { app -> viewModel.forceStop(app) },
                onToggleEnabled = { app -> viewModel.setEnabled(app, !app.enabled) },
                onClearData = { app -> confirm = ConfirmAction.ClearData(app) },
                onUninstall = { app ->
                    if (uiState.rootAvailable) {
                        confirm = ConfirmAction.Uninstall(app)
                    } else {
                        uninstallLauncher.launch(
                            Intent(Intent.ACTION_DELETE, Uri.parse("package:${app.packageName}"))
                        )
                    }
                },
                onExtract = { app -> viewModel.extractApp(app.packageName, app.appName) },
                onOpenSettings = { app ->
                    runCatching {
                        context.startActivity(
                            Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.parse("package:${app.packageName}")
                            }
                        )
                    }
                },
                onClearSearch = { viewModel.setSearchQuery("") },
                modifier = Modifier.weight(0.9f)
            )
        }

        ManageStatusOverlay(
            actionResult = actionResult,
            extractState = uiState.extractState,
            onDismissAction = { viewModel.clearActionResult() },
            onDismissExtract = { viewModel.dismissExtractResult() },
            onErrorDismiss = {
                viewModel.clearActionResult()
                viewModel.dismissExtractResult()
                runCatching { firstRowFocus.requestFocus() }
            },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 40.dp),
        )
    }
}
