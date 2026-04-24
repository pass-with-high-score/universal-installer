package app.pwhs.universalinstaller.presentation.uninstall

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.composable.EmptyStateView
import app.pwhs.universalinstaller.presentation.composable.InstallerModeBadge
import app.pwhs.universalinstaller.presentation.install.controller.SystemAppMethod
import app.pwhs.universalinstaller.presentation.uninstall.logs.UninstallLogsActivity
import app.pwhs.universalinstaller.util.AppIconData
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest




import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel


@Composable
fun UninstallScreen(
    modifier: Modifier = Modifier,
    viewModel: UninstallViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    UninstallUi(
        modifier = modifier,
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onToggleSystemApps = viewModel::toggleSystemApps,
        onUninstall = viewModel::uninstallApp,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onUninstallSelected = viewModel::uninstallSelected,
        onOpenLogs = { 
            context.startActivity(Intent(context, UninstallLogsActivity::class.java))
        },
        onRefresh = viewModel::refreshApps,
        onSortChange = viewModel::setSort,
        onRequestUsageAccess = {
            // Send user to the system Usage Access settings — we re-check on resume via
            // refreshUsageAccess() and reload the list if it flipped.
            runCatching {
                context.startActivity(
                    Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
        onRefreshUsageAccess = viewModel::refreshUsageAccess,
        onConfirmSystemApp = viewModel::confirmSystemAppPrompt,
        onDismissSystemApp = viewModel::dismissSystemAppPrompt,
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UninstallUi(
    modifier: Modifier = Modifier,
    uiState: UninstallUiState = UninstallUiState(),
    onSearchQueryChanged: (String) -> Unit = {},
    onToggleSystemApps: () -> Unit = {},
    onUninstall: (String) -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
    onUninstallSelected: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onSortChange: (UninstallSortBy) -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
    onRefreshUsageAccess: () -> Unit = {},
    onConfirmSystemApp: (SystemAppMethod?) -> Unit = {},
    onDismissSystemApp: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showBatchConfirm by remember { mutableStateOf(false) }
    // Lifted so the filter FAB's long-press can drive the list (scroll to top).
    val listState = rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    if (showFilterSheet) {
        FilterSheet(
            sortBy = uiState.sortBy,
            direction = uiState.sortDirection,
            showSystemApps = uiState.showSystemApps,
            usageGranted = uiState.usageAccessGranted,
            onSortChange = onSortChange,
            onToggleSystemApps = onToggleSystemApps,
            onRequestUsageAccess = onRequestUsageAccess,
            onDismiss = { showFilterSheet = false },
        )
    }

    if (showBatchConfirm) {
        AlertDialog(
            onDismissRequest = { showBatchConfirm = false },
            confirmButton = {
                TextButton(onClick = {
                    showBatchConfirm = false
                    onUninstallSelected()
                }) {
                    Text(stringResource(R.string.uninstall), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showBatchConfirm = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.uninstall_confirm_batch_title, uiState.selectedPackages.size)) },
            text = {
                Text(stringResource(R.string.uninstall_confirm_batch_text, uiState.selectedPackages.size))
            },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            }
        )
    }

    uiState.systemAppPrompt?.let { prompt ->
        SystemAppDialog(
            prompt = prompt,
            onConfirm = onConfirmSystemApp,
            onDismiss = onDismissSystemApp,
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (uiState.isSelectionMode) {
                // Selection mode top bar
                TopAppBar(
                    title = {
                        Text(stringResource(R.string.uninstall_n_selected, uiState.selectedPackages.size))
                    },
                    navigationIcon = {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Rounded.Close, contentDescription = stringResource(R.string.uninstall_cancel_selection))
                        }
                    },
                    actions = {
                        IconButton(onClick = onToggleSelectAll) {
                            Icon(
                                Icons.Rounded.SelectAll,
                                contentDescription = if (uiState.isAllSelected) stringResource(R.string.uninstall_deselect_all) else stringResource(R.string.uninstall_select_all),
                                tint = if (uiState.isAllSelected)
                                    MaterialTheme.colorScheme.primary
                                else
                                    MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        IconButton(onClick = {
                            // Skip the generic confirm dialog when any system app is in the
                            // selection — the system-app dialog below covers confirmation and
                            // method choice in one place, so the user doesn't see two dialogs.
                            val hasSystem = uiState.apps
                                .filter { it.packageName in uiState.selectedPackages }
                                .any { it.isSystemApp }
                            if (hasSystem) onUninstallSelected() else showBatchConfirm = true
                        }) {
                            Icon(
                                Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.uninstall_selected_action),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        navigationIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        actionIconContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    ),
                )
            } else {
                LargeTopAppBar(
                    expandedHeight = 140.dp,
                    title = {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(
                                text = stringResource(R.string.screen_title_uninstall),
                                style = MaterialTheme.typography.headlineMedium,
                            )
                            InstallerModeBadge()
                            Spacer(modifier = Modifier.height(12.dp))

                        }
                    },
                    actions = {
                        IconButton(onClick = onRefresh) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Refresh",
                            )
                        }
                        IconButton(onClick = onOpenLogs) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                contentDescription = stringResource(R.string.uninstall_logs_cd),
                            )
                        }
                    },
                    scrollBehavior = scrollBehavior,
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = MaterialTheme.colorScheme.background,
                        scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                )
            }
        },
        floatingActionButton = {
            if (!uiState.isSelectionMode && !uiState.isLoading) {
                // Tap → filter sheet; long-press → scroll to top. M3's `FloatingActionButton`
                // and clickable `Surface` only expose `onClick`, so we compose a FAB-shaped
                // Surface and attach `combinedClickable` ourselves. Avoids a second FAB that
                // clashed visually with the red `DeleteOutline` on each card.
                val haptic = LocalHapticFeedback.current
                Surface(
                    shape = FloatingActionButtonDefaults.shape,
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shadowElevation = 6.dp,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(FloatingActionButtonDefaults.shape)
                        .combinedClickable(
                            role = Role.Button,
                            onClick = { showFilterSheet = true },
                            onLongClick = {
                                haptic.performHapticFeedback(
                                    HapticFeedbackType.LongPress
                                )
                                coroutineScope.launch { listState.scrollToItem(0) }
                            },
                        ),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.FilterList,
                            contentDescription = stringResource(R.string.uninstall_filter_cd),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            // Search bar
            if (!uiState.isSelectionMode) {
                SearchBar(
                    inputField = {
                        SearchBarDefaults.InputField(
                            query = uiState.searchQuery,
                            onQueryChange = onSearchQueryChanged,
                            onSearch = {},
                            expanded = false,
                            onExpandedChange = {},
                            placeholder = { Text(stringResource(R.string.uninstall_search_hint)) },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Rounded.Search,
                                    contentDescription = null
                                )
                            },
                        )
                    },
                    expanded = false,
                    onExpandedChange = {},
                    windowInsets = WindowInsets(0),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                ) {}
            }

            if (!uiState.isSelectionMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.uninstall_app_count, uiState.filteredApps.size),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    // Short sort summary so user knows current state without opening sheet.
                    Text(
                        text = stringResource(
                            R.string.uninstall_current_sort_summary,
                            stringResource(sortLabelRes(uiState.sortBy)),
                            if (uiState.sortDirection == SortDirection.Asc) "↑" else "↓",
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Re-check usage access when user returns from the Settings screen.
            val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
            androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
                val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                    if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) onRefreshUsageAccess()
                }
                lifecycleOwner.lifecycle.addObserver(observer)
                onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
            }

            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.filteredApps.isEmpty() -> {
                    EmptyStateView(
                        icon = Icons.Rounded.SearchOff,
                        title = stringResource(R.string.uninstall_no_apps_found),
                        subtitle = if (uiState.searchQuery.isNotBlank())
                            stringResource(R.string.uninstall_no_match, uiState.searchQuery)
                        else stringResource(R.string.uninstall_no_user_apps),
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp)
                    )
                }

                else -> {
                    // Any change in sort or filter jumps back to the top. `scrollToItem` is
                    // O(1); `animateScrollToItem` steps through every item and lags hard on
                    // 300+ apps — user already sees the chip flip, the jump doesn't need
                    // animation.
                    LaunchedEffect(
                        uiState.sortBy,
                        uiState.sortDirection,
                        uiState.searchQuery,
                        uiState.showSystemApps,
                    ) {
                        if (listState.firstVisibleItemIndex != 0 ||
                            listState.firstVisibleItemScrollOffset != 0
                        ) {
                            listState.scrollToItem(0)
                        }
                    }
                    LazyColumn(
                        state = listState,
                        // Extra bottom space so the FAB doesn't overlap the last card's
                        // Uninstall button — 56dp FAB + 16dp inset + breathing room.
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(
                            items = uiState.filteredApps,
                            key = { it.packageName }
                        ) { app ->
                            AppCard(
                                app = app,
                                isSelectionMode = uiState.isSelectionMode,
                                isSelected = app.packageName in uiState.selectedPackages,
                                onUninstall = { onUninstall(app.packageName) },
                                onLongClick = { onToggleSelection(app.packageName) },
                                onToggleSelect = { onToggleSelection(app.packageName) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                        item {
                            Spacer(Modifier.height(8.dp))
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSheet(
    sortBy: UninstallSortBy,
    direction: SortDirection,
    showSystemApps: Boolean,
    usageGranted: Boolean,
    onSortChange: (UninstallSortBy) -> Unit,
    onToggleSystemApps: () -> Unit,
    onRequestUsageAccess: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val resource = LocalResources.current

    val sheetState = rememberModalBottomSheetState(
        skipPartiallyExpanded = true,
    )
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.uninstall_filter_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(16.dp))

            Text(
                text = stringResource(R.string.uninstall_filter_sort_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SortChip(UninstallSortBy.Name, sortBy, direction, stringResource(R.string.uninstall_sort_name)) {
                    onSortChange(UninstallSortBy.Name)
                }
                SortChip(UninstallSortBy.Size, sortBy, direction, stringResource(R.string.uninstall_sort_size)) {
                    onSortChange(UninstallSortBy.Size)
                }
                SortChip(UninstallSortBy.InstalledAt, sortBy, direction, stringResource(R.string.uninstall_sort_installed)) {
                    onSortChange(UninstallSortBy.InstalledAt)
                }
                SortChip(UninstallSortBy.LastUsed, sortBy, direction, stringResource(R.string.uninstall_sort_last_used)) {
                    if (!usageGranted) {
                        android.widget.Toast.makeText(
                            context,
                            resource.getString(R.string.uninstall_sort_usage_toast),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                        onRequestUsageAccess()
                    } else {
                        onSortChange(UninstallSortBy.LastUsed)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = stringResource(R.string.uninstall_filter_filter_section),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickableRow { onToggleSystemApps() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.uninstall_show_system_apps),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = stringResource(R.string.uninstall_show_system_apps_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = showSystemApps,
                    onCheckedChange = { onToggleSystemApps() },
                )
            }
        }
    }
}

/**
 * Click wrapper that doesn't require long-click semantics — the row just toggles, no need
 * for `combinedClickable`. Pulled out so we avoid accidentally using combinedClickable in
 * places where a plain clickable is clearer.
 */
@Composable
private fun Modifier.combinedClickableRow(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))

@Composable
private fun sortLabelRes(sortBy: UninstallSortBy): Int = when (sortBy) {
    UninstallSortBy.Name -> R.string.uninstall_sort_name
    UninstallSortBy.Size -> R.string.uninstall_sort_size
    UninstallSortBy.InstalledAt -> R.string.uninstall_sort_installed
    UninstallSortBy.LastUsed -> R.string.uninstall_sort_last_used
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SortChip(
    axis: UninstallSortBy,
    current: UninstallSortBy,
    direction: SortDirection,
    label: String,
    onClick: () -> Unit,
) {
    val selected = axis == current
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = { Text(label) },
        trailingIcon = if (selected) {
            {
                Icon(
                    imageVector = if (direction == SortDirection.Asc)
                        Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
            }
        } else null,
        colors = FilterChipDefaults.filterChipColors(),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCard(
    app: InstalledApp,
    isSelectionMode: Boolean,
    isSelected: Boolean,
    onUninstall: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showConfirmDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            confirmButton = {
                TextButton(onClick = {
                    showConfirmDialog = false
                    onUninstall()
                }) {
                    Text(stringResource(R.string.uninstall), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.uninstall_confirm_single_title, app.appName)) },
            text = {
                Text(stringResource(R.string.uninstall_confirm_single_text, app.appName, app.packageName))
            },
            icon = {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(AppIconData(app.packageName))
                        .build(),
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(MaterialTheme.shapes.medium),
                    error = {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                        )
                    },
                    success = { SubcomposeAsyncImageContent() },
                )
            }
        )
    }

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = {
                    when {
                        isSelectionMode -> onToggleSelect()
                        // Route system apps straight to the ViewModel so the root-aware
                        // method dialog appears — avoids the generic confirm firing first
                        // and the user seeing two dialogs back-to-back.
                        app.isSystemApp -> onUninstall()
                        else -> showConfirmDialog = true
                    }
                },
                onLongClick = onLongClick,
            ),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.elevatedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Selection indicator
            if (isSelectionMode) {
                Icon(
                    imageVector = if (isSelected) Icons.Rounded.CheckCircle
                        else Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    tint = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp),
                )
            }

            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(AppIconData(app.packageName))
                    .build(),
                contentDescription = app.appName,
                modifier = Modifier
                    .size(48.dp)
                    .clip(MaterialTheme.shapes.medium),
                error = {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                },
                success = { SubcomposeAsyncImageContent() },
            )

            // App info
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (app.isSystemApp) {
                        Text(
                            text = stringResource(R.string.uninstall_system_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.tertiaryContainer)
                                .padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }
                }
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                // Version + size on one line — both are "static" identifiers, always known.
                val versionSizeLine = buildList {
                    if (app.versionName.isNotBlank()) add("v${app.versionName}")
                    if (app.sizeBytes > 0) {
                        add(android.text.format.Formatter.formatShortFileSize(context, app.sizeBytes))
                    }
                }.joinToString(" · ")
                if (versionSizeLine.isNotEmpty()) {
                    Text(
                        text = versionSizeLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                // Installed date + last-used time. Last-used is omitted when the user hasn't
                // granted Usage access (we get 0 from UsageStatsManager in that case).
                val dateParts = buildList {
                    if (app.installedAt > 0) {
                        add(stringResource(
                            R.string.uninstall_row_installed,
                            android.text.format.DateUtils.formatDateTime(
                                context,
                                app.installedAt,
                                android.text.format.DateUtils.FORMAT_SHOW_DATE or
                                    android.text.format.DateUtils.FORMAT_ABBREV_MONTH,
                            )
                        ))
                    }
                    if (app.lastUsedAt > 0) {
                        val rel = android.text.format.DateUtils.getRelativeTimeSpanString(
                            app.lastUsedAt,
                            System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS,
                            android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE,
                        ).toString()
                        add(stringResource(R.string.uninstall_row_used, rel))
                    }
                }
                if (dateParts.isNotEmpty()) {
                    Text(
                        text = dateParts.joinToString(" · "),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Delete button (only in normal mode)
            if (!isSelectionMode) {
                FilledTonalIconButton(
                    onClick = {
                        // System apps bypass the normal confirm — let the ViewModel surface
                        // the root-aware dialog directly so the user sees the right options.
                        if (app.isSystemApp) onUninstall() else showConfirmDialog = true
                    },
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.uninstall_app_cd, app.appName),
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

// ── System-app dialog ────────────────────────────────────────────────────────

@Composable
private fun SystemAppDialog(
    prompt: SystemAppPrompt,
    onConfirm: (SystemAppMethod?) -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        is SystemAppPrompt.Single -> SystemAppMethodDialog(
            title = stringResource(R.string.uninstall_system_dialog_title_single),
            warning = stringResource(R.string.uninstall_system_warning_single, prompt.appName),
            allowSkip = false,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is SystemAppPrompt.Batch -> SystemAppMethodDialog(
            title = stringResource(R.string.uninstall_system_dialog_title_batch),
            warning = stringResource(
                R.string.uninstall_system_warning_batch,
                prompt.systemApps.size + prompt.userApps.size,
                prompt.userApps.size,
                prompt.systemApps.size,
            ),
            systemAppsPreview = prompt.systemApps,
            allowSkip = prompt.userApps.isNotEmpty(),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is SystemAppPrompt.PrivilegedRequired -> SystemAppPrivilegedRequiredDialog(
            prompt = prompt,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Single shared implementation for the Single and Batch variants. `allowSkip=true` exposes
 * the third radio option ("Skip system apps") which makes sense only when the user also
 * has regular apps in the selection that can still be uninstalled normally.
 */
@Composable
private fun SystemAppMethodDialog(
    title: String,
    warning: String,
    allowSkip: Boolean,
    onConfirm: (SystemAppMethod?) -> Unit,
    onDismiss: () -> Unit,
    systemAppsPreview: List<Pair<String, String>> = emptyList(),
) {
    // Sealed local type to let the radio group include "Skip" alongside real methods
    // without polluting the shared enum.
    var selection by remember { mutableStateOf<Choice>(Choice.Method(SystemAppMethod.UninstallForUser0)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodyMedium,
                )
                if (systemAppsPreview.isNotEmpty()) {
                    val shown = systemAppsPreview.take(4).joinToString(", ") { it.second }
                    val more = (systemAppsPreview.size - 4).coerceAtLeast(0)
                    Text(
                        text = if (more > 0) "$shown, +$more" else shown,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.uninstall_system_method_header),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                SystemMethodRadio(
                    title = stringResource(R.string.uninstall_system_method_per_user_title),
                    subtitle = stringResource(R.string.uninstall_system_method_per_user_sub),
                    selected = selection == Choice.Method(SystemAppMethod.UninstallForUser0),
                    onClick = { selection = Choice.Method(SystemAppMethod.UninstallForUser0) },
                )
                SystemMethodRadio(
                    title = stringResource(R.string.uninstall_system_method_disable_title),
                    subtitle = stringResource(R.string.uninstall_system_method_disable_sub),
                    selected = selection == Choice.Method(SystemAppMethod.Disable),
                    onClick = { selection = Choice.Method(SystemAppMethod.Disable) },
                )
                if (allowSkip) {
                    SystemMethodRadio(
                        title = stringResource(R.string.uninstall_system_method_skip_title),
                        subtitle = stringResource(R.string.uninstall_system_method_skip_sub),
                        selected = selection == Choice.Skip,
                        onClick = { selection = Choice.Skip },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(when (val c = selection) {
                    is Choice.Method -> c.method
                    Choice.Skip -> null
                })
            }) {
                Text(
                    text = stringResource(R.string.uninstall_system_continue),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

private sealed interface Choice {
    data class Method(val method: SystemAppMethod) : Choice
    data object Skip : Choice
}

@Composable
private fun SystemMethodRadio(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        androidx.compose.material3.RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SystemAppPrivilegedRequiredDialog(
    prompt: SystemAppPrompt.PrivilegedRequired,
    onConfirm: (SystemAppMethod?) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasRegular = prompt.userAppsAvailable.isNotEmpty()
    val body = when {
        prompt.systemApps.size == 1 && !hasRegular ->
            stringResource(R.string.uninstall_system_privileged_required_body_single, prompt.systemApps.first().second)
        hasRegular ->
            stringResource(
                R.string.uninstall_system_privileged_required_body_batch,
                prompt.systemApps.size,
                prompt.userAppsAvailable.size,
            )
        else ->
            stringResource(R.string.uninstall_system_privileged_required_body_batch_only_system, prompt.systemApps.size)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.DeleteOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(stringResource(R.string.uninstall_system_privileged_required_title)) },
        text = { Text(body, style = MaterialTheme.typography.bodyMedium) },
        confirmButton = {
            if (hasRegular) {
                TextButton(onClick = { onConfirm(null) }) {
                    Text(stringResource(R.string.uninstall_system_proceed_user_only))
                }
            } else {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
        dismissButton = if (hasRegular) {
            { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } }
        } else null,
    )
}
