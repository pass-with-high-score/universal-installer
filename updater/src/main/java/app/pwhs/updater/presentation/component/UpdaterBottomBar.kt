package app.pwhs.updater.presentation.component

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.MoveToInbox
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.R as CoreR
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.InstallationSourceHelper
import app.pwhs.core.util.disableSceneTransition

private object UpdaterHiddenTabsCache {
    @Volatile
    var current: Set<String> = emptySet()
}

private enum class UpdaterBottomNavDestination(
    val tabKey: String,
    val targetActivityClassName: String?,
    val labelRes: Int,
    val icon: ImageVector,
    val isAlwaysVisible: Boolean = false,
) {
    INSTALL(
        tabKey = "Install",
        targetActivityClassName = "app.pwhs.universalinstaller.presentation.install.InstallActivity",
        labelRes = CoreR.string.nav_install,
        icon = Icons.Rounded.MoveToInbox,
        isAlwaysVisible = true,
    ),
    UPDATES(
        tabKey = "Updates",
        targetActivityClassName = null,
        labelRes = CoreR.string.nav_updates,
        icon = Icons.Rounded.RocketLaunch,
        isAlwaysVisible = true,
    ),
    MANAGE(
        tabKey = "Manage",
        targetActivityClassName = "app.pwhs.universalinstaller.presentation.manage.ManageActivity",
        labelRes = CoreR.string.nav_manage,
        icon = Icons.Rounded.Apps,
    ),
    STATS(
        tabKey = "Stats",
        targetActivityClassName = "app.pwhs.universalinstaller.presentation.stats.StatsActivity",
        labelRes = CoreR.string.nav_stats,
        icon = Icons.Rounded.QueryStats,
    ),
    SETTINGS(
        tabKey = "Settings",
        targetActivityClassName = "app.pwhs.universalinstaller.presentation.setting.SettingActivity",
        labelRes = CoreR.string.nav_settings,
        icon = Icons.Rounded.Settings,
        isAlwaysVisible = true,
    ),
}

@Composable
fun UpdaterBottomBar(
    updateCount: Int = 0,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = colors.onPrimaryContainer,
        selectedTextColor = colors.primary,
        indicatorColor = colors.primaryContainer,
        unselectedIconColor = colors.onSurfaceVariant,
        unselectedTextColor = colors.onSurfaceVariant,
    )

    val isStoreUser = remember(context) {
        InstallationSourceHelper.isInstalledFromStore(context)
    }

    val unseenMilestoneCount by produceState(initialValue = 0, context, isStoreUser) {
        if (!isStoreUser) {
            value = 0
            return@produceState
        }
        runCatching {
            context.dataStore.data.collect { prefs ->
                value = prefs[SharedPrefsKeys.UNSEEN_MILESTONE_COUNT] ?: 0
            }
        }
    }

    val hiddenTabs by produceState(initialValue = UpdaterHiddenTabsCache.current, context) {
        runCatching {
            context.dataStore.data.collect { prefs ->
                val tabs = prefs[SharedPrefsKeys.HIDDEN_BOTTOM_TABS] ?: emptySet()
                UpdaterHiddenTabsCache.current = tabs
                value = tabs
            }
        }
    }

    val destinations = remember(hiddenTabs) {
        UpdaterBottomNavDestination.entries.filter {
            it.isAlwaysVisible || it.tabKey !in hiddenTabs
        }
    }

    NavigationBar {
        destinations.forEach { destination ->
            val isSelected = destination == UpdaterBottomNavDestination.UPDATES
            NavigationBarItem(
                selected = isSelected,
                colors = itemColors,
                onClick = {
                    if (!isSelected && destination.targetActivityClassName != null) {
                        val intent = Intent().setClassName(context.packageName, destination.targetActivityClassName).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
                        }
                        context.startActivity(intent)
                        (context as? Activity)?.disableSceneTransition()
                    }
                },
                icon = {
                    val badgeCount = when (destination) {
                        UpdaterBottomNavDestination.UPDATES -> updateCount
                        UpdaterBottomNavDestination.STATS -> unseenMilestoneCount
                        else -> 0
                    }
                    if (badgeCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                                }
                            }
                        ) {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes),
                            )
                        }
                    } else {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.labelRes),
                        )
                    }
                },
                label = {
                    Text(
                        text = stringResource(destination.labelRes),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
            )
        }
    }
}

@Composable
fun UpdaterNavigationRail(
    updateCount: Int = 0,
) {
    val context = LocalContext.current
    val colors = MaterialTheme.colorScheme
    val itemColors = NavigationRailItemDefaults.colors(
        selectedIconColor = colors.onPrimaryContainer,
        selectedTextColor = colors.primary,
        indicatorColor = colors.primaryContainer,
        unselectedIconColor = colors.onSurfaceVariant,
        unselectedTextColor = colors.onSurfaceVariant,
    )

    val isStoreUser = remember(context) {
        InstallationSourceHelper.isInstalledFromStore(context)
    }

    val unseenMilestoneCount by produceState(initialValue = 0, context, isStoreUser) {
        if (!isStoreUser) {
            value = 0
            return@produceState
        }
        runCatching {
            context.dataStore.data.collect { prefs ->
                value = prefs[SharedPrefsKeys.UNSEEN_MILESTONE_COUNT] ?: 0
            }
        }
    }

    val hiddenTabs by produceState(initialValue = UpdaterHiddenTabsCache.current, context) {
        runCatching {
            context.dataStore.data.collect { prefs ->
                val tabs = prefs[SharedPrefsKeys.HIDDEN_BOTTOM_TABS] ?: emptySet()
                UpdaterHiddenTabsCache.current = tabs
                value = tabs
            }
        }
    }

    val destinations = remember(hiddenTabs) {
        UpdaterBottomNavDestination.entries.filter {
            it.isAlwaysVisible || it.tabKey !in hiddenTabs
        }
    }

    val isAmoled = colors.background == Color.Black && colors.surface == Color.Black

    NavigationRail(
        containerColor = if (isAmoled) Color.Black else NavigationRailDefaults.ContainerColor,
        windowInsets = NavigationRailDefaults.windowInsets,
    ) {
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterVertically),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            destinations.forEach { destination ->
                val isSelected = destination == UpdaterBottomNavDestination.UPDATES
                NavigationRailItem(
                    selected = isSelected,
                    colors = itemColors,
                    onClick = {
                        if (!isSelected && destination.targetActivityClassName != null) {
                            val intent = Intent().setClassName(context.packageName, destination.targetActivityClassName).apply {
                                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
                            }
                            context.startActivity(intent)
                            (context as? Activity)?.disableSceneTransition()
                        }
                    },
                    icon = {
                        val badgeCount = when (destination) {
                            UpdaterBottomNavDestination.UPDATES -> updateCount
                            UpdaterBottomNavDestination.STATS -> unseenMilestoneCount
                            else -> 0
                        }
                        if (badgeCount > 0) {
                            BadgedBox(
                                badge = {
                                    Badge {
                                        Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                                    }
                                },
                            ) {
                                Icon(
                                    imageVector = destination.icon,
                                    contentDescription = stringResource(destination.labelRes),
                                )
                            }
                        } else {
                            Icon(
                                imageVector = destination.icon,
                                contentDescription = stringResource(destination.labelRes),
                            )
                        }
                    },
                    label = {
                        Text(
                            text = stringResource(destination.labelRes),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}
