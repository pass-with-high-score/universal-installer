package app.pwhs.universalinstaller.presentation.composable

import android.app.Activity
import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailDefaults
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.presentation.adaptive.LocalAdaptiveLayoutInfo
import app.pwhs.core.util.InstallationSourceHelper
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.util.extension.disableSceneTransition

/**
 * Top-level navigation chrome that adapts between:
 * - Compact (< 600dp, phone / Fold cover screen): Bottom [NavigationBar]
 * - Medium & Expanded (>= 600dp, unfolded Fold / tablet / split-screen): Left [NavigationRail]
 */
@Composable
fun AdaptiveNavScaffold(
    currentTab: BottomBarItem,
    content: @Composable () -> Unit,
) {
    val adaptiveInfo = LocalAdaptiveLayoutInfo.current

    if (!adaptiveInfo.useNavigationRail) {
        Scaffold(
            bottomBar = { BottomBar(currentTab) },
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding()),
            ) {
                content()
            }
        }
    } else {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
        ) {
            AppNavigationRail(currentTab = currentTab)
            VerticalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                content()
            }
        }
    }
}

@Composable
private fun AppNavigationRail(
    currentTab: BottomBarItem,
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

    val isStoreUser = remember(context) {
        InstallationSourceHelper.isInstalledFromStore(
            context,
            isStoreFlavor = BuildConfig.FLAVOR == "play",
        )
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

    val hiddenTabs by produceState(initialValue = HiddenTabsCache.current, context) {
        runCatching {
            context.dataStore.data.collect { prefs ->
                val tabs = prefs[app.pwhs.universalinstaller.presentation.setting.PreferencesKeys.HIDDEN_BOTTOM_TABS] ?: emptySet()
                HiddenTabsCache.current = tabs
                value = tabs
            }
        }
    }

    val destinations = remember(hiddenTabs, currentTab) {
        BottomBarItem.entries.filter {
            it.activityClass != null && (it.isAlwaysVisible || it == currentTab || it.name !in hiddenTabs)
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
                val isSelected = currentTab == destination
                NavigationRailItem(
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
                                },
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
                    label = {
                        Text(
                            text = stringResource(destination.label),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    },
                )
            }
        }
    }
}
