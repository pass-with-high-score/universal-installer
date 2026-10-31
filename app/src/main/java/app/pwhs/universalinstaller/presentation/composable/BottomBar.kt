package app.pwhs.universalinstaller.presentation.composable

import android.app.Activity
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.InstallMobile
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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.pwhs.core.R as CoreR
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.install.InstallActivity
import app.pwhs.universalinstaller.presentation.manage.ManageActivity
import app.pwhs.universalinstaller.presentation.setting.SettingActivity
import app.pwhs.universalinstaller.presentation.stats.StatsActivity
import app.pwhs.universalinstaller.util.extension.disableSceneTransition

enum class BottomBarItem(
    val activityClass: Class<*>?,
    val label: Int,
    val icon: ImageVector,
) {
    Install(
        activityClass = InstallActivity::class.java,
        label = CoreR.string.nav_install,
        icon = Icons.Rounded.InstallMobile,
    ),
    Updates(
        activityClass = runCatching { Class.forName("app.pwhs.updater.presentation.UpdatesActivity") }.getOrNull(),
        label = CoreR.string.nav_updates,
        icon = Icons.Rounded.RocketLaunch,
    ),
    Manage(
        activityClass = ManageActivity::class.java,
        label = CoreR.string.nav_manage,
        icon = Icons.Rounded.Apps,
    ),
    Stats(
        activityClass = StatsActivity::class.java,
        label = CoreR.string.nav_stats,
        icon = Icons.Rounded.QueryStats,
    ),
    Settings(
        activityClass = SettingActivity::class.java,
        label = CoreR.string.nav_settings,
        icon = Icons.Rounded.Settings,
    );
}

@Composable
fun BottomBar(
    currentTab: BottomBarItem,
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

    val updateCount by produceState(initialValue = 0) {
        runCatching {
            val repoClass = Class.forName("app.pwhs.updater.data.repo.AppUpdateRepository")
            val repo = org.koin.java.KoinJavaComponent.get<Any>(repoClass)
            val method = repoClass.getMethod("getUpdateCount")
            @Suppress("UNCHECKED_CAST")
            val flow = method.invoke(repo) as? kotlinx.coroutines.flow.Flow<Int>
            flow?.collect { value = it }
        }
    }

    val unseenMilestoneCount by produceState(initialValue = 0, context) {
        runCatching {
            context.dataStore.data.collect { prefs ->
                value = prefs[SharedPrefsKeys.UNSEEN_MILESTONE_COUNT] ?: 0
            }
        }
    }

    val destinations = remember {
        BottomBarItem.entries.filter { it.activityClass != null }
    }

    val isAmoled = colors.background == Color.Black && colors.surface == Color.Black

    NavigationBar(
        containerColor = if (isAmoled) Color.Black else NavigationBarDefaults.containerColor,
    ) {
        destinations.forEach { destination ->
            val isSelected = currentTab == destination
            NavigationBarItem(
                selected = isSelected,
                colors = itemColors,
                onClick = {
                    if (!isSelected && destination.activityClass != null) {
                        val intent = Intent(context, destination.activityClass).apply {
                            flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
                        }
                        context.startActivity(intent)
                        (context as? Activity)?.disableSceneTransition()
                    }
                },
                icon = {
                    val badgeCount = when (destination) {
                        BottomBarItem.Updates -> updateCount
                        BottomBarItem.Stats -> unseenMilestoneCount
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
                                contentDescription = stringResource(destination.label),
                            )
                        }
                    } else {
                        Icon(
                            imageVector = destination.icon,
                            contentDescription = stringResource(destination.label),
                        )
                    }
                },
                label = { Text(stringResource(destination.label)) },
            )
        }
    }
}