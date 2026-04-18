package app.pwhs.universalinstaller.presentation.uninstall

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SearchBar
import androidx.compose.material3.SearchBarDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import app.pwhs.universalinstaller.util.AppIconData
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.composable.EmptyStateView
import app.pwhs.universalinstaller.presentation.composable.InstallerModeBadge
import com.ramcosta.composedestinations.annotation.Destination
import com.ramcosta.composedestinations.annotation.RootGraph
import com.ramcosta.composedestinations.generated.destinations.UninstallLogsScreenDestination
import com.ramcosta.composedestinations.navigation.DestinationsNavigator
import org.koin.androidx.compose.koinViewModel

@Destination<RootGraph>
@Composable
fun UninstallScreen(
    navigator: DestinationsNavigator,
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
        onOpenLogs = { navigator.navigate(UninstallLogsScreenDestination) },
        onSortChange = viewModel::setSort,
        onRequestUsageAccess = {
            // Send user to the system Usage Access settings — we re-check on resume via
            // refreshUsageAccess() and reload the list if it flipped.
            runCatching {
                context.startActivity(
                    android.content.Intent(android.provider.Settings.ACTION_USAGE_ACCESS_SETTINGS)
                        .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                )
            }
        },
        onRefreshUsageAccess = viewModel::refreshUsageAccess,
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
    onSortChange: (UninstallSortBy) -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
    onRefreshUsageAccess: () -> Unit = {},
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showFilterMenu by remember { mutableStateOf(false) }
    var showBatchConfirm by remember { mutableStateOf(false) }

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
                        IconButton(onClick = { showBatchConfirm = true }) {
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
                        }
                    },
                    actions = {
                        IconButton(onClick = onOpenLogs) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ReceiptLong,
                                contentDescription = stringResource(R.string.uninstall_logs_cd),
                            )
                        }
                        Box {
                            IconButton(onClick = { showFilterMenu = true }) {
                                Icon(
                                    imageVector = Icons.Rounded.FilterList,
                                    contentDescription = stringResource(R.string.uninstall_filter_cd)
                                )
                            }
                            DropdownMenu(
                                expanded = showFilterMenu,
                                onDismissRequest = { showFilterMenu = false },
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.uninstall_show_system_apps)) },
                                    onClick = { onToggleSystemApps() },
                                    trailingIcon = {
                                        Switch(
                                            checked = uiState.showSystemApps,
                                            onCheckedChange = { onToggleSystemApps() },
                                        )
                                    },
                                )
                            }
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
                SortRow(
                    sortBy = uiState.sortBy,
                    direction = uiState.sortDirection,
                    usageGranted = uiState.usageAccessGranted,
                    count = uiState.filteredApps.size,
                    onSortChange = onSortChange,
                    onRequestUsageAccess = onRequestUsageAccess,
                )
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
                    val listState = rememberLazyListState()
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
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
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
private fun SortRow(
    sortBy: UninstallSortBy,
    direction: SortDirection,
    usageGranted: Boolean,
    count: Int,
    onSortChange: (UninstallSortBy) -> Unit,
    onRequestUsageAccess: () -> Unit,
) {
    val context = LocalContext.current
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.uninstall_app_count, count),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SortChip(UninstallSortBy.Name, sortBy, direction, stringResource(R.string.uninstall_sort_name), onClick = { onSortChange(UninstallSortBy.Name) })
            SortChip(UninstallSortBy.Size, sortBy, direction, stringResource(R.string.uninstall_sort_size), onClick = { onSortChange(UninstallSortBy.Size) })
            SortChip(UninstallSortBy.InstalledAt, sortBy, direction, stringResource(R.string.uninstall_sort_installed), onClick = { onSortChange(UninstallSortBy.InstalledAt) })
            SortChip(
                axis = UninstallSortBy.LastUsed,
                current = sortBy,
                direction = direction,
                label = stringResource(R.string.uninstall_sort_last_used),
                onClick = {
                    if (!usageGranted) {
                        android.widget.Toast.makeText(
                            context,
                            context.getString(R.string.uninstall_sort_usage_toast),
                            android.widget.Toast.LENGTH_LONG,
                        ).show()
                        onRequestUsageAccess()
                    } else {
                        onSortChange(UninstallSortBy.LastUsed)
                    }
                },
            )
        }
    }
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
                    if (isSelectionMode) onToggleSelect() else showConfirmDialog = true
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
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
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
                    onClick = { showConfirmDialog = true },
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
