@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
package app.pwhs.universalinstaller.presentation.manage



import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import app.pwhs.core.presentation.adaptive.AdaptiveTwoPane
import app.pwhs.core.presentation.adaptive.LocalAdaptiveLayoutInfo
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
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.FilterList
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.PowerSettingsNew
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Stop
import app.pwhs.universalinstaller.util.AndroidAutoCompat
import androidx.compose.material.icons.rounded.CloudUpload
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Store
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.activity.compose.BackHandler
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.composable.UniversalSearchBar
import app.pwhs.universalinstaller.presentation.composable.EmptyStateView
import app.pwhs.universalinstaller.presentation.composable.ShimmerBox
import app.pwhs.universalinstaller.presentation.composable.InstallerModeBadge
import app.pwhs.universalinstaller.presentation.install.controller.SystemAppMethod
import app.pwhs.universalinstaller.presentation.manage.logs.UninstallLogsActivity
import app.pwhs.universalinstaller.presentation.manage.permissions.AppPermissionsActivity
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.universalinstaller.util.AppIconData
import app.pwhs.universalinstaller.util.BiometricGate
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import app.pwhs.universalinstaller.util.extension.getDisplayName
import org.koin.androidx.compose.koinViewModel


@Composable
internal fun UninstallUi(
    modifier: Modifier = Modifier,
    uiState: ManageUiState = ManageUiState(),
    onSearchQueryChanged: (String) -> Unit = {},
    onToggleAppFilter: (AppFilter) -> Unit = {},
    onOpenAppPrivileged: (String, String) -> Unit = { _, _ -> },
    onUninstall: (String) -> Unit = {},
    onBlockPackage: (String) -> Unit = {},
    blockedPackages: Set<String> = emptySet(),
    onExtract: (String, String) -> Unit = { _, _ -> },
    onShare: (String, String) -> Unit = { _, _ -> },
    onReinstall: (String, String) -> Unit = { _, _ -> },
    onInstallToSecureFolder: (String, String, Int) -> Unit = { _, _, _ -> },
    onCheckVirusTotal: (InstalledApp) -> Unit = {},
    onAddToServer: (String, String) -> Unit = { _, _ -> },
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
    onForceStopSelected: () -> Unit = {},
    onDisableSelected: () -> Unit = {},
    onClearDataSelected: () -> Unit = {},
    onExtractSelected: () -> Unit = {},
    onDismissBatchExtractResult: () -> Unit = {},
    onOpenLogs: () -> Unit = {},
    onOpenBackups: () -> Unit = {},
    onRefresh: () -> Unit = {},
    onSortChange: (UninstallSortBy) -> Unit = {},
    onGroupByChange: (GroupBy) -> Unit = {},
    onResetFilters: () -> Unit = {},
    onRequestUsageAccess: () -> Unit = {},
    onRefreshUsageAccess: () -> Unit = {},
    onConfirmSystemApp: (SystemAppMethod?) -> Unit = {},
    onDismissSystemApp: () -> Unit = {},
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showFilterSheet by remember { mutableStateOf(false) }
    var showBatchConfirm by remember { mutableStateOf(false) }
    // Selection-mode overflow menu (privileged batch actions) + its destructive confirm.
    var showBatchMenu by remember { mutableStateOf(false) }
    var showBatchClearDataConfirm by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val resource = LocalResources.current
    var actionTarget by remember { mutableStateOf<InstalledApp?>(null) }
    val storedPrefs by context.dataStore.data.collectAsState(initial = null)
    val vtDisabled = storedPrefs?.get(SharedPrefsKeys.DISABLE_VIRUSTOTAL) ?: false
    var confirmUninstallTarget by remember { mutableStateOf<InstalledApp?>(null) }

    BackHandler(enabled = uiState.isSelectionMode) {
        onClearSelection()
    }
    var confirmClearDataTarget by remember { mutableStateOf<InstalledApp?>(null) }
    val extractInProgress = uiState.extractState is ExtractState.Running
    // Biometric gate state — flag tracked per-attempt rather than per-target so toggling
    // the Settings switch applies on the next uninstall without re-composing.
    val uninstallGate = app.pwhs.universalinstaller.presentation.install.util.rememberUninstallSecurityGate(context)
    val gatedUninstall: (String) -> Unit = { pkg ->
        val name = uiState.apps.firstOrNull { it.packageName == pkg }?.appName ?: pkg
        uninstallGate(pkg, name, onUninstall)
    }

    // Action sheet — opens on card tap (when not in selection mode). Adding new actions
    // later is just appending another ActionRow; lifting the dialogs to this level avoided
    // duplicating them inside every card composition.

    app.pwhs.universalinstaller.presentation.manage.ManageDialogHost(
        confirmUninstallTarget = confirmUninstallTarget,
        onConfirmUninstall = { targetPkg ->
            confirmUninstallTarget = null
            gatedUninstall(targetPkg)
        },
        onDismissUninstall = { confirmUninstallTarget = null },
        confirmClearDataTarget = confirmClearDataTarget,
        onConfirmClearData = { pkg, name ->
            confirmClearDataTarget = null
            onClearData(pkg, name)
        },
        onDismissClearData = { confirmClearDataTarget = null },
        extractState = uiState.extractState,
        batchExtractState = uiState.batchExtractState,
        showBatchConfirm = showBatchConfirm,
        onConfirmBatchUninstall = {
            showBatchConfirm = false
            onUninstallSelected()
        },
        onDismissBatchConfirm = { showBatchConfirm = false },
        showBatchClearDataConfirm = showBatchClearDataConfirm,
        onConfirmBatchClearData = {
            showBatchClearDataConfirm = false
            onClearDataSelected()
        },
        onDismissBatchClearDataConfirm = { showBatchClearDataConfirm = false },
        systemAppPrompt = uiState.systemAppPrompt,
        onConfirmSystemApp = onConfirmSystemApp,
        onDismissSystemApp = onDismissSystemApp,
        selectedPackagesCount = uiState.selectedPackages.size,
    )
    val adaptiveInfo = LocalAdaptiveLayoutInfo.current
    val useTwoPane = adaptiveInfo.useTwoPane

    val renderActionSheet: @Composable (InstalledApp, Boolean) -> Unit = { target, asInline ->
        // Re-probe before each open in case Shizuku permission was just revoked or root
        // shell expired — the cached `privilegedReady` flag may be stale.
        LaunchedEffect(target.packageName) { onRefreshPrivileged() }
        AppActionSheet(
            app = target,
            extractInProgress = extractInProgress,
            privilegedReady = uiState.privilegedReady,
            onOpenApp = {
                if (!asInline) actionTarget = null
                if (uiState.privilegedReady) {
                    onOpenAppPrivileged(target.packageName, target.appName)
                } else {
                    launchInstalledApp(context, target.packageName)
                }
            },
            onOpenAppInfo = {
                if (!asInline) actionTarget = null
                openAppInfoSettings(context, target.packageName)
            },
            onShare = {
                if (!asInline) actionTarget = null
                onShare(target.packageName, target.appName)
            },
            onReinstall = {
                if (!asInline) actionTarget = null
                onReinstall(target.packageName, target.appName)
            },
            onCheckVirusTotal = {
                if (!asInline) actionTarget = null
                onCheckVirusTotal(target)
            },
            vtDisabled = vtDisabled,
            onExtract = {
                if (!asInline) actionTarget = null
                onExtract(target.packageName, target.appName)
            },
            onAddToServer = {
                if (!asInline) actionTarget = null
                onAddToServer(target.packageName, target.appName)
            },
            onForceStop = {
                if (!asInline) actionTarget = null
                onForceStop(target.packageName, target.appName)
            },
            onSetEnabled = { enabled ->
                if (!asInline) actionTarget = null
                onSetEnabled(target.packageName, target.appName, enabled)
            },
            onClearData = {
                if (!asInline) actionTarget = null
                confirmClearDataTarget = target
            },
            queryStorage = queryStorage,
            queryUsage = queryUsage,
            onUninstall = {
                if (!asInline) actionTarget = null
                if (target.isSystemApp) gatedUninstall(target.packageName)
                else confirmUninstallTarget = target
            },
            onBlockPackage = {
                if (!asInline) actionTarget = null
                onBlockPackage(target.packageName)
            },
            isBlocked = target.packageName in blockedPackages,
            onInstallToSecureFolder = { userId ->
                onInstallToSecureFolder(target.packageName, target.appName, userId)
            },
            asInlinePane = asInline,
            onDismiss = { actionTarget = null },
        )
    }

    if (!useTwoPane) {
        actionTarget?.let { target -> renderActionSheet(target, false) }
    }

    app.pwhs.universalinstaller.presentation.manage.ManageSnackbars(
        batchExtractState = uiState.batchExtractState,
        privilegedActionResult = uiState.privilegedActionResult,
        extractState = uiState.extractState,
        snackbarHostState = snackbarHostState,
        onOpenBackups = onOpenBackups,
        onDismissBatchExtractResult = onDismissBatchExtractResult,
        onDismissPrivilegedResult = onDismissPrivilegedResult,
        onDismissExtractResult = onDismissExtractResult,
    )
    // Search bar visibility — toggled by the top-bar search button. Saved across config
    // changes so a rotation doesn't snap the user out of search. We auto-open it when the
    // VM still holds a query (e.g. process re-creation while searching).
    var searchActive by rememberSaveable { mutableStateOf(uiState.searchQuery.isNotBlank()) }
    val searchFocusRequester = remember { FocusRequester() }
    LaunchedEffect(searchActive) {
        if (searchActive) {
            // requestFocus throws if the node isn't laid out yet — AnimatedVisibility's
            // entrance handles that timing for us, but the very first frame is still racing.
            runCatching { searchFocusRequester.requestFocus() }
        }
    }
    // Lifted so the filter FAB's long-press can drive the list (scroll to top).
    val listState = rememberLazyListState()
    val coroutineScope = androidx.compose.runtime.rememberCoroutineScope()

    if (showFilterSheet) {
        FilterSheet(
            sortBy = uiState.sortBy,
            direction = uiState.sortDirection,
            groupBy = uiState.groupBy,
            appFilter = uiState.appFilter,
            usageGranted = uiState.usageAccessGranted,
            onSortChange = onSortChange,
            onGroupByChange = onGroupByChange,
            onRequestUsageAccess = onRequestUsageAccess,
            onResetFilters = onResetFilters,
            onDismiss = { showFilterSheet = false },
        )
    }

    val listPane: @Composable () -> Unit = {
        Scaffold(
            modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                app.pwhs.universalinstaller.presentation.manage.ManageHeader(
                    isSelectionMode = uiState.isSelectionMode,
                    selectedCount = uiState.selectedPackages.size,
                    isAllSelected = uiState.isAllSelected,
                    searchActive = searchActive,
                    privilegedReady = uiState.privilegedReady,
                    scrollBehavior = scrollBehavior,
                    onClearSelection = onClearSelection,
                    onToggleSelectAll = onToggleSelectAll,
                    onExtractSelected = onExtractSelected,
                    onRequestBatchUninstall = {
                        val hasSystem = uiState.apps
                            .filter { it.packageName in uiState.selectedPackages }
                            .any { it.isSystemApp }
                        if (hasSystem) onUninstallSelected() else showBatchConfirm = true
                    },
                    onForceStopSelected = onForceStopSelected,
                    onDisableSelected = onDisableSelected,
                    onRequestBatchClearData = { showBatchClearDataConfirm = true },
                    onToggleSearch = {
                        if (searchActive) {
                            onSearchQueryChanged("")
                            searchActive = false
                        } else {
                            searchActive = true
                        }
                    },
                    onRefresh = onRefresh,
                    onOpenBackups = onOpenBackups,
                    onOpenLogs = onOpenLogs,
                )
            },
            floatingActionButtonPosition = if (adaptiveInfo.useNavigationRail) FabPosition.Start else FabPosition.End,
            floatingActionButton = {
                if (!uiState.isSelectionMode && !uiState.isLoading) {
                    val haptic = LocalHapticFeedback.current
                    Surface(
                        shape = FloatingActionButtonDefaults.shape,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        shadowElevation = 6.dp,
                        modifier = Modifier
                            .then(if (adaptiveInfo.useNavigationRail) Modifier.navigationBarsPadding() else Modifier)
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
            app.pwhs.universalinstaller.presentation.manage.ManageScreenContent(
                uiState = uiState,
                searchActive = searchActive,
                searchFocusRequester = searchFocusRequester,
                blockedPackages = blockedPackages,
                listState = listState,
                onSearchQueryChanged = onSearchQueryChanged,
                onToggleSearchActive = { searchActive = it },
                onToggleAppFilter = onToggleAppFilter,
                onRefreshUsageAccess = onRefreshUsageAccess,
                onResetFilters = onResetFilters,
                onRefresh = onRefresh,
                onShowActions = { actionTarget = it },
                onToggleSelection = onToggleSelection,
                activePackageName = if (useTwoPane) actionTarget?.packageName else null,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }

    if (useTwoPane) {
        AdaptiveTwoPane(
            firstWeight = 0.52f,
            first = listPane,
            second = {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .statusBarsPadding()
                            .navigationBarsPadding(),
                    ) {
                        val selected = actionTarget
                        if (selected != null) {
                            renderActionSheet(selected, true)
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(32.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                EmptyStateView(
                                    icon = Icons.Rounded.Android,
                                    title = stringResource(app.pwhs.core.R.string.nav_manage),
                                    subtitle = stringResource(R.string.uninstall_search_hint),
                                )
                            }
                        }
                    }
                }
            },
        )
    } else {
        listPane()
    }
}
