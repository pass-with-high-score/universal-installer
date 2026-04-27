package app.pwhs.universalinstaller.presentation.manage

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.automirrored.rounded.ReceiptLong
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Store
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import androidx.core.net.toUri
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.composable.EmptyStateView
import app.pwhs.universalinstaller.presentation.composable.InstallerModeBadge
import app.pwhs.universalinstaller.presentation.install.controller.SystemAppMethod
import app.pwhs.universalinstaller.presentation.manage.logs.UninstallLogsActivity
import app.pwhs.universalinstaller.presentation.manage.permissions.AppPermissionsActivity
import app.pwhs.universalinstaller.presentation.setting.dataStore
import kotlinx.coroutines.flow.map
import app.pwhs.universalinstaller.util.AppIconData
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest




import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel


@Composable
fun ManageScreen(
    modifier: Modifier = Modifier,
    viewModel: ManageViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    val context = LocalContext.current
    UninstallUi(
        modifier = modifier,
        uiState = uiState,
        onSearchQueryChanged = viewModel::onSearchQueryChanged,
        onToggleAppFilter = viewModel::toggleAppFilter,
        onUninstall = viewModel::uninstallApp,
        onExtract = viewModel::extractApp,
        onShare = viewModel::shareApp,
        onForceStop = viewModel::forceStop,
        onSetEnabled = viewModel::setEnabled,
        onClearData = viewModel::clearAllData,
        queryStorage = viewModel::queryStorageStats,
        queryUsage = viewModel::queryUsageBuckets,
        onDismissExtractResult = viewModel::dismissExtractResult,
        onDismissPrivilegedResult = viewModel::dismissPrivilegedActionResult,
        onRefreshPrivileged = viewModel::refreshPrivilegedReady,
        onToggleSelection = viewModel::toggleSelection,
        onClearSelection = viewModel::clearSelection,
        onToggleSelectAll = viewModel::toggleSelectAll,
        onUninstallSelected = viewModel::uninstallSelected,
        onOpenLogs = {
            context.startActivity(Intent(context, UninstallLogsActivity::class.java))
        },
        onOpenBackups = {
            context.startActivity(Intent(context, BackupsActivity::class.java))
        },
        onRefresh = viewModel::refreshApps,
        onSortChange = viewModel::setSort,
        onGroupByChange = viewModel::setGroupBy,
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
private fun UninstallUi(
    modifier: Modifier = Modifier,
    uiState: ManageUiState = ManageUiState(),
    onSearchQueryChanged: (String) -> Unit = {},
    onToggleAppFilter: (AppFilter) -> Unit = {},
    onUninstall: (String) -> Unit = {},
    onExtract: (String, String) -> Unit = { _, _ -> },
    onShare: (String, String) -> Unit = { _, _ -> },
    onForceStop: (String, String) -> Unit = { _, _ -> },
    onSetEnabled: (String, String, Boolean) -> Unit = { _, _, _ -> },
    onClearData: (String, String) -> Unit = { _, _ -> },
    queryStorage: suspend (String) -> StorageBreakdown? = { null },
    queryUsage: suspend (String) -> List<UsageBucket> = { emptyList() },
    onDismissExtractResult: () -> Unit = {},
    onDismissPrivilegedResult: () -> Unit = {},
    onRefreshPrivileged: () -> Unit = {},
    onToggleSelection: (String) -> Unit = {},
    onClearSelection: () -> Unit = {},
    onToggleSelectAll: () -> Unit = {},
    onUninstallSelected: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenBackups: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onSortChange: (UninstallSortBy) -> Unit = {},
    onGroupByChange: (GroupBy) -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
    onRefreshUsageAccess: () -> Unit = {},
    onConfirmSystemApp: (SystemAppMethod?) -> Unit = {},
    onDismissSystemApp: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    var actionTarget by remember { mutableStateOf<InstalledApp?>(null) }
    var confirmUninstallTarget by remember { mutableStateOf<InstalledApp?>(null) }
    var confirmClearDataTarget by remember { mutableStateOf<InstalledApp?>(null) }
    val extractInProgress = uiState.extractState is ExtractState.Running
    // Biometric gate state — flag tracked per-attempt rather than per-target so toggling
    // the Settings switch applies on the next uninstall without re-composing.
    val uninstallGateEnabled by remember(context) {
        context.dataStore.data.map {
            it[app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
                .BIOMETRIC_LOCK_UNINSTALL] ?: false
        }
    }.collectAsState(initial = false)
    val gatedUninstall: (String) -> Unit = { pkg ->
        val activity = context as? androidx.fragment.app.FragmentActivity
        if (activity != null) {
            val name = uiState.apps.firstOrNull { it.packageName == pkg }?.appName ?: pkg
            app.pwhs.universalinstaller.util.BiometricGate.authenticate(
                activity = activity,
                enabled = uninstallGateEnabled,
                title = context.getString(R.string.biometric_uninstall_title),
                subtitle = context.getString(R.string.biometric_uninstall_sub, name),
                onSuccess = { onUninstall(pkg) },
            )
        } else {
            onUninstall(pkg)
        }
    }

    // Action sheet — opens on card tap (when not in selection mode). Adding new actions
    // later is just appending another ActionRow; lifting the dialogs to this level avoided
    // duplicating them inside every card composition.
    actionTarget?.let { target ->
        // Re-probe before each open in case Shizuku permission was just revoked or root
        // shell expired — the cached `privilegedReady` flag may be stale.
        LaunchedEffect(target.packageName) { onRefreshPrivileged() }
        AppActionSheet(
            app = target,
            extractInProgress = extractInProgress,
            privilegedReady = uiState.privilegedReady,
            onOpenApp = {
                actionTarget = null
                launchInstalledApp(context, target.packageName)
            },
            onOpenAppInfo = {
                actionTarget = null
                openAppInfoSettings(context, target.packageName)
            },
            onShare = {
                actionTarget = null
                onShare(target.packageName, target.appName)
            },
            onExtract = {
                actionTarget = null
                onExtract(target.packageName, target.appName)
            },
            onForceStop = {
                actionTarget = null
                onForceStop(target.packageName, target.appName)
            },
            onSetEnabled = { enabled ->
                actionTarget = null
                onSetEnabled(target.packageName, target.appName, enabled)
            },
            onClearData = {
                actionTarget = null
                confirmClearDataTarget = target
            },
            queryStorage = queryStorage,
            queryUsage = queryUsage,
            onUninstall = {
                actionTarget = null
                // System apps bypass the generic confirm — the ViewModel surfaces the
                // root-aware method dialog directly. User apps still get the explicit
                // "Are you sure" guard before destructive action. Biometric gate (when
                // enabled) wraps the system-app path; the user-app path goes through the
                // confirm dialog → its onConfirm calls gatedUninstall.
                if (target.isSystemApp) gatedUninstall(target.packageName)
                else confirmUninstallTarget = target
            },
            onDismiss = { actionTarget = null },
        )
    }

    confirmUninstallTarget?.let { target ->
        UninstallConfirmDialog(
            app = target,
            onConfirm = {
                confirmUninstallTarget = null
                gatedUninstall(target.packageName)
            },
            onDismiss = { confirmUninstallTarget = null },
        )
    }

    confirmClearDataTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { confirmClearDataTarget = null },
            icon = {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(stringResource(R.string.manage_clear_data_confirm_title, target.appName))
            },
            text = { Text(stringResource(R.string.manage_clear_data_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    val t = target
                    confirmClearDataTarget = null
                    onClearData(t.packageName, t.appName)
                }) {
                    Text(
                        stringResource(R.string.manage_action_clear_data),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDataTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    // Privileged-action snackbar (Force stop / Disable / Enable). Lives alongside the
    // extract snackbar — both share the same SnackbarHostState; the system Material
    // queue handles back-to-back messages.
    LaunchedEffect(uiState.privilegedActionResult) {
        val result = uiState.privilegedActionResult ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message = result.message, withDismissAction = true)
        onDismissPrivilegedResult()
    }

    // Drive the post-extract UX from the ExtractState.mode field — Backup shows snackbar
    // with "Open folder", Share fires the system chooser as soon as the cache file lands.
    LaunchedEffect(uiState.extractState) {
        when (val s = uiState.extractState) {
            is ExtractState.Done -> {
                when (s.mode) {
                    ExtractMode.Backup -> {
                        val res = snackbarHostState.showSnackbar(
                            message = context.getString(R.string.extract_done, s.file.name),
                            actionLabel = context.getString(R.string.extract_done_action_open),
                            withDismissAction = true,
                        )
                        if (res == SnackbarResult.ActionPerformed) onOpenBackups()
                    }
                    ExtractMode.Share -> {
                        val launched = launchShareIntent(context, s.file, s.appName)
                        if (!launched) {
                            snackbarHostState.showSnackbar(
                                message = context.getString(
                                    R.string.manage_action_share_failed,
                                    "no app accepts the share",
                                ),
                                withDismissAction = true,
                            )
                        }
                    }
                }
                onDismissExtractResult()
            }
            is ExtractState.Error -> {
                val msg = when (s.mode) {
                    ExtractMode.Share ->
                        context.getString(R.string.manage_action_share_failed, s.message)
                    ExtractMode.Backup ->
                        context.getString(R.string.extract_failed, s.message)
                }
                snackbarHostState.showSnackbar(message = msg, withDismissAction = true)
                onDismissExtractResult()
            }
            else -> Unit
        }
    }
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
            groupBy = uiState.groupBy,
            usageGranted = uiState.usageAccessGranted,
            onSortChange = onSortChange,
            onGroupByChange = onGroupByChange,
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                        IconButton(onClick = onOpenBackups) {
                            Icon(
                                imageVector = Icons.Rounded.Inventory2,
                                contentDescription = stringResource(R.string.extract_action_backups),
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
            // Stats banner — recomputed from filteredApps so it follows the active chips.
            // Hidden in selection mode where the top bar already shows "N selected".
            if (!uiState.isSelectionMode) {
                StatsBanner(apps = uiState.filteredApps)
            }

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
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AppFilter.entries.forEach { filter ->
                        val selected = filter in uiState.appFilter
                        FilterChip(
                            selected = selected,
                            onClick = { onToggleAppFilter(filter) },
                            label = { Text(stringResource(appFilterLabel(filter))) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
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
                        uiState.appFilter,
                    ) {
                        if (listState.firstVisibleItemIndex != 0 ||
                            listState.firstVisibleItemScrollOffset != 0
                        ) {
                            listState.scrollToItem(0)
                        }
                    }
                    val sideloadLabel = stringResource(R.string.manage_group_other)
                    LazyColumn(
                        state = listState,
                        // Extra bottom space so the FAB doesn't overlap the last card's
                        // Uninstall button — 56dp FAB + 16dp inset + breathing room.
                        contentPadding = PaddingValues(
                            start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        if (uiState.groupBy == GroupBy.Installer) {
                            // Group-by-installer view. We compute the bucket label per app
                            // (known store → display name; everything else → sideload), then
                            // sort groups by name so the order is stable across loads.
                            val grouped = uiState.filteredApps.groupBy { app ->
                                resolveInstallerInfo(app.installerPackage, app.packageName)
                                    ?.displayName ?: sideloadLabel
                            }.toSortedMap()
                            grouped.forEach { (groupName, apps) ->
                                stickyHeader(key = "h:$groupName") {
                                    GroupHeader(
                                        title = groupName,
                                        count = apps.size,
                                    )
                                }
                                items(
                                    items = apps,
                                    key = { it.packageName },
                                ) { app ->
                                    AppCard(
                                        app = app,
                                        isSelectionMode = uiState.isSelectionMode,
                                        isSelected = app.packageName in uiState.selectedPackages,
                                        onShowActions = { actionTarget = app },
                                        onLongClick = { onToggleSelection(app.packageName) },
                                        onToggleSelect = { onToggleSelection(app.packageName) },
                                        modifier = Modifier.animateItem(),
                                    )
                                }
                            }
                        } else {
                            items(
                                items = uiState.filteredApps,
                                key = { it.packageName }
                            ) { app ->
                                AppCard(
                                    app = app,
                                    isSelectionMode = uiState.isSelectionMode,
                                    isSelected = app.packageName in uiState.selectedPackages,
                                    onShowActions = { actionTarget = app },
                                    onLongClick = { onToggleSelection(app.packageName) },
                                    onToggleSelect = { onToggleSelection(app.packageName) },
                                    modifier = Modifier.animateItem(),
                                )
                            }
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
    groupBy: GroupBy,
    usageGranted: Boolean,
    onSortChange: (UninstallSortBy) -> Unit,
    onGroupByChange: (GroupBy) -> Unit,
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
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        onGroupByChange(
                            if (groupBy == GroupBy.Installer) GroupBy.None else GroupBy.Installer,
                        )
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.manage_group_label),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = groupBy == GroupBy.Installer,
                    onCheckedChange = {
                        onGroupByChange(if (it) GroupBy.Installer else GroupBy.None)
                    },
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
    onShowActions: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    ElevatedCard(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .combinedClickable(
                onClick = {
                    if (isSelectionMode) onToggleSelect() else onShowActions()
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
                    if (!app.enabled) {
                        // Reuse the error palette so disabled apps stand out the same way
                        // as the destructive Uninstall icon does at the row's trailing edge.
                        Text(
                            text = stringResource(R.string.manage_disabled_badge),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier
                                .clip(MaterialTheme.shapes.small)
                                .background(MaterialTheme.colorScheme.errorContainer)
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

// ── App actions bottom sheet ────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AppActionSheet(
    app: InstalledApp,
    extractInProgress: Boolean,
    privilegedReady: Boolean,
    onOpenApp: () -> Unit,
    onOpenAppInfo: () -> Unit,
    onShare: () -> Unit,
    onExtract: () -> Unit,
    onForceStop: () -> Unit,
    onSetEnabled: (Boolean) -> Unit,
    onClearData: () -> Unit,
    queryStorage: suspend (String) -> StorageBreakdown?,
    queryUsage: suspend (String) -> List<UsageBucket>,
    onUninstall: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // Cheap PM IPC; stable across recompositions so memoize per package.
    val launchable = remember(app.packageName) {
        context.packageManager.getLaunchIntentForPackage(app.packageName) != null
    }
    var storage by remember(app.packageName) { mutableStateOf<StorageBreakdown?>(null) }
    var usage by remember(app.packageName) { mutableStateOf<List<UsageBucket>>(emptyList()) }
    LaunchedEffect(app.packageName) {
        storage = queryStorage(app.packageName)
        usage = queryUsage(app.packageName)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        // Sheet content can grow taller than the viewport once usage chart + storage
        // chips + privileged group + intent rows all render. ModalBottomSheet's body
        // slot doesn't scroll on its own, so wrap in our own scrolling column.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
        // Header: app icon + name + package name. Same shell as the AppCard so users see
        // the in-context selection echoed back to them.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (app.versionName.isNotBlank()) {
                    Text(
                        text = "v${app.versionName}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                if (app.isSystemApp) {
                    Text(
                        text = stringResource(R.string.uninstall_system_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
                if (!app.enabled) {
                    if (app.isSystemApp) Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.manage_disabled_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // Usage chart — renders only when there's at least one bucket with foreground time.
        // Hidden silently when Usage Access isn't granted (queryUsage returns []).
        val totalUsageMs = remember(usage) { usage.sumOf { it.foregroundMillis } }
        if (totalUsageMs > 0L) {
            UsageChart(
                buckets = usage,
                totalMillis = totalUsageMs,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }

        // Storage breakdown — only renders when StorageStatsManager returns data (Usage
        // Access granted + API 26+). Three lightweight chips so the user grasps APK vs
        // Data vs Cache at a glance without a deep dialog.
        storage?.let { s ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                StorageChip(
                    label = stringResource(R.string.manage_storage_app),
                    value = android.text.format.Formatter.formatShortFileSize(context, s.appBytes),
                    weight = 1f,
                )
                StorageChip(
                    label = stringResource(R.string.manage_storage_data),
                    value = android.text.format.Formatter.formatShortFileSize(context, s.dataBytes),
                    weight = 1f,
                )
                StorageChip(
                    label = stringResource(R.string.manage_storage_cache),
                    value = android.text.format.Formatter.formatShortFileSize(context, s.cacheBytes),
                    weight = 1f,
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        if (launchable) {
            ActionRow(
                icon = Icons.AutoMirrored.Rounded.Launch,
                iconTint = MaterialTheme.colorScheme.primary,
                label = stringResource(R.string.manage_action_open_app),
                subtitle = stringResource(R.string.manage_action_open_app_sub, app.appName),
                onClick = onOpenApp,
            )
        }
        // "Open in store" only renders when we have a known installer source. Sideload
        // installs and unknown sources don't get this row — there's nowhere meaningful
        // to send the user.
        val storeInfo = remember(app.installerPackage, app.packageName) {
            resolveInstallerInfo(app.installerPackage, app.packageName)
        }
        storeInfo?.let { info ->
            ActionRow(
                icon = Icons.Rounded.Store,
                iconTint = MaterialTheme.colorScheme.primary,
                label = stringResource(R.string.manage_action_open_in_store, info.displayName),
                subtitle = stringResource(R.string.manage_action_open_in_store_sub),
                onClick = {
                    runCatching {
                        context.startActivity(
                            info.intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                    onDismiss()
                },
            )
        }
        ActionRow(
            icon = Icons.Rounded.Info,
            iconTint = MaterialTheme.colorScheme.primary,
            label = stringResource(R.string.manage_action_app_info),
            subtitle = stringResource(R.string.manage_action_app_info_sub),
            onClick = onOpenAppInfo,
        )
        ActionRow(
            icon = Icons.Rounded.Security,
            iconTint = MaterialTheme.colorScheme.primary,
            label = stringResource(R.string.manage_action_permissions),
            subtitle = stringResource(R.string.manage_action_permissions_sub),
            onClick = {
                val intent = Intent(context, AppPermissionsActivity::class.java)
                    .putExtra(AppPermissionsActivity.EXTRA_PACKAGE_NAME, app.packageName)
                runCatching { context.startActivity(intent) }
                onDismiss()
            },
        )
        ActionRow(
            icon = Icons.Rounded.Share,
            iconTint = MaterialTheme.colorScheme.primary,
            label = stringResource(R.string.manage_action_share),
            subtitle = stringResource(R.string.manage_action_share_sub),
            enabled = !extractInProgress,
            onClick = onShare,
        )
        ActionRow(
            icon = if (app.hasSplits) Icons.Rounded.FolderZip else Icons.Rounded.Inventory2,
            iconTint = MaterialTheme.colorScheme.primary,
            label = stringResource(R.string.extract_action),
            subtitle = if (app.hasSplits) stringResource(R.string.extract_label_split)
                else stringResource(R.string.extract_label_single),
            enabled = !extractInProgress,
            onClick = onExtract,
        )

        // Privileged group — only renders when Shizuku or Root is currently usable.
        // The interactive shell-out actions are riskier than intent fires above, so we
        // visually separate them with a divider + section label so the user notices the
        // jump from "safe" to "powerful".
        if (privilegedReady) {
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Text(
                text = stringResource(R.string.manage_section_advanced),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
            ActionRow(
                icon = Icons.Rounded.Block,
                iconTint = MaterialTheme.colorScheme.tertiary,
                label = stringResource(R.string.manage_action_force_stop),
                subtitle = stringResource(R.string.manage_action_force_stop_sub),
                onClick = onForceStop,
            )
            if (app.enabled) {
                ActionRow(
                    icon = Icons.Rounded.PowerSettingsNew,
                    iconTint = MaterialTheme.colorScheme.tertiary,
                    label = stringResource(R.string.manage_action_disable),
                    subtitle = stringResource(R.string.manage_action_disable_sub),
                    onClick = { onSetEnabled(false) },
                )
            } else {
                ActionRow(
                    icon = Icons.Rounded.PlayCircle,
                    iconTint = MaterialTheme.colorScheme.primary,
                    label = stringResource(R.string.manage_action_enable),
                    subtitle = stringResource(R.string.manage_action_enable_sub),
                    onClick = { onSetEnabled(true) },
                )
            }
            ActionRow(
                icon = Icons.Rounded.DeleteForever,
                iconTint = MaterialTheme.colorScheme.error,
                label = stringResource(R.string.manage_action_clear_data),
                subtitle = stringResource(R.string.manage_action_clear_data_sub),
                onClick = onClearData,
            )
        }

        ActionRow(
            icon = Icons.Rounded.DeleteOutline,
            iconTint = MaterialTheme.colorScheme.error,
            label = stringResource(R.string.uninstall),
            subtitle = stringResource(R.string.uninstall_app_cd, app.appName),
            onClick = onUninstall,
        )

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconTint: androidx.compose.ui.graphics.Color,
    label: String,
    subtitle: String,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) iconTint else iconTint.copy(alpha = 0.4f),
            modifier = Modifier.size(24.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f),
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun UninstallConfirmDialog(
    app: InstalledApp,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(
                    stringResource(R.string.uninstall),
                    color = MaterialTheme.colorScheme.error,
                )
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
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
        },
    )
}

// ── Intent helpers ──────────────────────────────────────────────────────────

private fun launchInstalledApp(context: android.content.Context, packageName: String) {
    val intent = context.packageManager.getLaunchIntentForPackage(packageName) ?: return
    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(intent) }
}

private fun openAppInfoSettings(context: android.content.Context, packageName: String) {
    val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = "package:$packageName".toUri()
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

/**
 * Wraps the cache-extracted APK in a FileProvider URI and fires a SEND chooser. Returns
 * false when no chooser-capable Activity is available so the caller can show feedback.
 */
private fun launchShareIntent(
    context: android.content.Context,
    file: java.io.File,
    appName: String,
): Boolean {
    val uri = androidx.core.content.FileProvider.getUriForFile(
        context,
        "${app.pwhs.universalinstaller.BuildConfig.APPLICATION_ID}.fileprovider",
        file,
    )
    val send = Intent(Intent.ACTION_SEND).apply {
        // Most file managers and chat apps accept the canonical APK MIME — picky targets
        // (e.g. Telegram on some Android versions) sometimes need octet-stream too, but
        // that's a hostile UX trade-off (more apps in the chooser that can't actually
        // handle APKs). Keep it strict.
        type = "application/vnd.android.package-archive"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    val chooser = Intent.createChooser(
        send,
        context.getString(
            app.pwhs.universalinstaller.R.string.manage_action_share_chooser,
            appName,
        ),
    ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
    return runCatching { context.startActivity(chooser); true }.getOrDefault(false)
}

@Suppress("unused")
private fun appFilterLabel(filter: AppFilter): Int = when (filter) {
    AppFilter.User -> R.string.manage_filter_user
    AppFilter.System -> R.string.manage_filter_system
    AppFilter.Disabled -> R.string.manage_filter_disabled
}

/**
 * Compact label/value chip for the storage row. Filled tonal so it reads as informational
 * rather than actionable — these are display-only.
 */
@Composable
private fun androidx.compose.foundation.layout.RowScope.StorageChip(
    label: String,
    value: String,
    weight: Float,
) {
    Column(
        modifier = Modifier
            .weight(weight)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

/**
 * Compact 3-stat summary for the active filter set: count · total size · disabled count.
 * Reads `apps` directly so the banner updates instantly when chips toggle.
 */
@Composable
private fun StatsBanner(apps: List<InstalledApp>) {
    val context = LocalContext.current
    val totalBytes = remember(apps) { apps.sumOf { it.sizeBytes } }
    val disabled = remember(apps) { apps.count { !it.enabled } }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.manage_stats_apps, apps.size),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = "·", color = MaterialTheme.colorScheme.outline)
        Text(
            text = android.text.format.Formatter.formatShortFileSize(context, totalBytes),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (disabled > 0) {
            Text(text = "·", color = MaterialTheme.colorScheme.outline)
            Text(
                text = stringResource(R.string.manage_stats_disabled, disabled),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

@Composable
private fun GroupHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = count.toString(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .clip(MaterialTheme.shapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}

/**
 * 7-bar daily usage chart. Bars are drawn relative to the largest bucket so a single
 * heavy day doesn't squash the rest into invisibility. Today is rightmost; bar opacity
 * dims for zero-time days so they read as "no usage" rather than "missing data".
 */
@Composable
private fun UsageChart(
    buckets: List<UsageBucket>,
    totalMillis: Long,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val maxBucket = remember(buckets) { (buckets.maxOfOrNull { it.foregroundMillis } ?: 0L).coerceAtLeast(1L) }
    val barColor = MaterialTheme.colorScheme.primary
    val emptyColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
    Column(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.manage_usage_title),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = stringResource(
                    R.string.manage_usage_total,
                    formatDuration(context, totalMillis),
                ),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(8.dp))
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
        ) {
            val barCount = buckets.size.coerceAtLeast(1)
            // Total horizontal padding between bars: barCount-1 gaps × 4dp. Remaining
            // space is split evenly so the chart adapts to any sheet width.
            val gapPx = 4.dp.toPx()
            val totalGap = gapPx * (barCount - 1)
            val barWidth = (size.width - totalGap) / barCount
            buckets.forEachIndexed { i, bucket ->
                val ratio = bucket.foregroundMillis.toFloat() / maxBucket
                val barHeight = size.height * ratio
                val x = i * (barWidth + gapPx)
                val y = size.height - barHeight
                drawRoundRect(
                    color = if (bucket.foregroundMillis == 0L) emptyColor else barColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, y),
                    size = androidx.compose.ui.geometry.Size(
                        width = barWidth,
                        height = barHeight.coerceAtLeast(2f),
                    ),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth / 4f),
                )
            }
        }
    }
}

private fun formatDuration(context: android.content.Context, millis: Long): String {
    val totalSec = millis / 1000
    val h = totalSec / 3600
    val m = (totalSec % 3600) / 60
    return when {
        h > 0 -> "${h}h ${m}m"
        m > 0 -> "${m}m"
        else -> "<1m"
    }
}
