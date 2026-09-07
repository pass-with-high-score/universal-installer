package app.pwhs.universalinstaller.presentation.manage

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.manage.AppCard
import app.pwhs.universalinstaller.presentation.manage.GroupBy
import app.pwhs.universalinstaller.presentation.manage.GroupHeader
import app.pwhs.universalinstaller.presentation.manage.resolveInstallerInfo

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
internal fun AppListLayout(
    filteredApps: List<InstalledApp>,
    isSelectionMode: Boolean,
    selectedPackages: Set<String>,
    blockedPackages: Set<String>,
    groupBy: GroupBy,
    isRefreshing: Boolean,
    listState: LazyListState,
    onRefresh: () -> Unit,
    onShowActions: (InstalledApp) -> Unit,
    onToggleSelection: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val sideloadLabel = stringResource(R.string.manage_group_other)
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = modifier
    ) {
        LazyColumn(
            state = listState,
            // Extra bottom space so the FAB doesn't overlap the last card's
            // Uninstall button — 56dp FAB + 16dp inset + breathing room.
            contentPadding = PaddingValues(
                start = 16.dp, end = 16.dp, top = 4.dp, bottom = 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (groupBy == GroupBy.Installer) {
                // Group-by-installer view. We compute the bucket label per app
                // (known store → display name; everything else → sideload), then
                // sort groups by name so the order is stable across loads.
                val grouped = filteredApps.groupBy { app ->
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
                            isSelectionMode = isSelectionMode,
                            isSelected = app.packageName in selectedPackages,
                            isBlocked = app.packageName in blockedPackages,
                            onShowActions = { onShowActions(app) },
                            onLongClick = { onToggleSelection(app.packageName) },
                            onToggleSelect = { onToggleSelection(app.packageName) },
                            modifier = Modifier.animateItem(),
                        )
                    }
                }
            } else {
                items(
                    items = filteredApps,
                    key = { it.packageName }
                ) { app ->
                    AppCard(
                        app = app,
                        isSelectionMode = isSelectionMode,
                        isSelected = app.packageName in selectedPackages,
                        isBlocked = app.packageName in blockedPackages,
                        onShowActions = { onShowActions(app) },
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
