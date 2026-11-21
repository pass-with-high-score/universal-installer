package app.pwhs.universalinstaller.presentation.manage

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.composable.EmptyStateView
import app.pwhs.universalinstaller.presentation.composable.UniversalSearchBar
import app.pwhs.universalinstaller.presentation.manage.AppFilter
import app.pwhs.universalinstaller.presentation.manage.GroupBy
import app.pwhs.universalinstaller.presentation.manage.ManageUiState
import app.pwhs.universalinstaller.presentation.manage.UninstallSortBy
import app.pwhs.universalinstaller.presentation.manage.appFilterLabel

@Composable
internal fun ManageScreenContent(
    uiState: ManageUiState,
    searchActive: Boolean,
    searchFocusRequester: FocusRequester,
    blockedPackages: Set<String>,
    listState: LazyListState,
    onSearchQueryChanged: (String) -> Unit,
    onToggleSearchActive: (Boolean) -> Unit,
    onToggleAppFilter: (AppFilter) -> Unit,
    onRefreshUsageAccess: () -> Unit,
    onResetFilters: () -> Unit,
    onRefresh: () -> Unit,
    onShowActions: (InstalledApp) -> Unit,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
    activePackageName: String? = null,
) {
    Column(
        modifier = modifier.fillMaxSize()
    ) {
        if (!uiState.isSelectionMode) {
            StatsBanner(
                apps = uiState.filteredApps,
                sortBy = uiState.sortBy,
                direction = uiState.sortDirection,
            )
        }

        UniversalSearchBar(
            query = uiState.searchQuery,
            onQueryChange = onSearchQueryChanged,
            active = !uiState.isSelectionMode && searchActive,
            onActiveChange = onToggleSearchActive,
            placeholder = stringResource(R.string.uninstall_search_hint),
            focusRequester = searchFocusRequester,
        )

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

        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) onRefreshUsageAccess()
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        when {
            uiState.isLoading -> ManageSkeleton()
            uiState.filteredApps.isEmpty() -> {
                val filtersActive = uiState.searchQuery.isNotBlank() ||
                    uiState.appFilter != setOf(AppFilter.User) ||
                    uiState.sortBy != UninstallSortBy.Name ||
                    uiState.groupBy != GroupBy.None
                EmptyStateView(
                    icon = Icons.Rounded.SearchOff,
                    title = stringResource(R.string.uninstall_no_apps_found),
                    subtitle = if (uiState.searchQuery.isNotBlank())
                        stringResource(R.string.uninstall_no_match, uiState.searchQuery)
                    else stringResource(R.string.uninstall_no_user_apps),
                    actionLabel = if (filtersActive) stringResource(R.string.uninstall_clear_filters) else null,
                    onAction = if (filtersActive) {
                        {
                            onSearchQueryChanged("")
                            onResetFilters()
                        }
                    } else null,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp)
                )
            }
            else -> {
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
                AppListLayout(
                    filteredApps = uiState.filteredApps,
                    isSelectionMode = uiState.isSelectionMode,
                    selectedPackages = uiState.selectedPackages,
                    blockedPackages = blockedPackages,
                    groupBy = uiState.groupBy,
                    isRefreshing = uiState.isRefreshing,
                    listState = listState,
                    onRefresh = onRefresh,
                    onShowActions = onShowActions,
                    onToggleSelection = onToggleSelection,
                    activePackageName = activePackageName,
                )
            }
        }
    }
}
