package app.pwhs.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Text as M3Text
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.vector.ImageVector
import app.pwhs.tv.presentation.manage.ManageScreen
import app.pwhs.tv.presentation.receive.ReceiveScreen
import app.pwhs.tv.presentation.settings.SettingsScreen
import app.pwhs.tv.presentation.updates.TvUpdatesProvider

/** Top-level destinations reachable from the side rail. */
private object TvRoute {
    const val RECEIVE = "receive"
    const val UPDATES = "updates"
    const val MANAGE = "manage"
    const val SETTINGS = "settings"
}

/**
 * Top-level TV shell: a static side Navigation Rail driving a Navigation Compose [NavHost].
 *
 * Tabs are switched with the bottom-nav idiom (`saveState`/`restoreState` + `launchSingleTop`),
 * so a destination's back-stack entry — and its ViewModel — survives while you're on another tab.
 * The heavy data (installed apps, local-APK scan) is loaded once by the ViewModels rather than on
 * every visit, so returning to a tab restores instantly instead of re-querying and flashing a
 * loading state — that reload-on-every-switch is what made navigation feel janky.
 */
@Composable
fun TvApp(modifier: Modifier = Modifier) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route ?: TvRoute.RECEIVE

    val updateCount by produceState(initialValue = 0) {
        if (TvUpdatesProvider.isAvailable) {
            TvUpdatesProvider.getUpdateCountFlow().collect { value = it }
        }
    }

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxSize()) {
            // Static Side Navigation Rail (Icon only)
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(100.dp)
                    .background(MaterialTheme.colorScheme.surface)
                    // Overscan-safe: the rail background bleeds to the edge
                    .padding(start = 32.dp, end = 12.dp, top = 28.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // App Logo
                Box(
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.logo),
                        contentDescription = null,
                        modifier = Modifier.size(32.dp),
                        tint = Color.Unspecified
                    )
                }

                Spacer(Modifier.height(32.dp))

                NavigationItem(
                    selected = currentRoute == TvRoute.RECEIVE,
                    onClick = { navController.switchTab(TvRoute.RECEIVE) },
                    iconRes = R.drawable.ic_apk_install
                )
                if (TvUpdatesProvider.isAvailable) {
                    Spacer(Modifier.height(12.dp))
                    NavigationItem(
                        selected = currentRoute == TvRoute.UPDATES,
                        onClick = { navController.switchTab(TvRoute.UPDATES) },
                        imageVector = Icons.Rounded.RocketLaunch,
                        badgeCount = updateCount,
                    )
                }
                Spacer(Modifier.height(12.dp))
                NavigationItem(
                    selected = currentRoute == TvRoute.MANAGE,
                    onClick = { navController.switchTab(TvRoute.MANAGE) },
                    iconRes = R.drawable.ic_delete
                )
                Spacer(Modifier.height(12.dp))
                NavigationItem(
                    selected = currentRoute == TvRoute.SETTINGS,
                    onClick = { navController.switchTab(TvRoute.SETTINGS) },
                    iconRes = R.drawable.ic_setting
                )
            }

            // Content Area
            Box(Modifier.weight(1f).fillMaxHeight()) {
                NavHost(
                    navController = navController,
                    startDestination = TvRoute.RECEIVE,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    composable(TvRoute.RECEIVE) { ReceiveScreen(modifier = Modifier.fillMaxSize()) }
                    if (TvUpdatesProvider.isAvailable) {
                        composable(TvRoute.UPDATES) { TvUpdatesProvider.UpdatesScreen(modifier = Modifier.fillMaxSize()) }
                    }
                    composable(TvRoute.MANAGE) { ManageScreen(modifier = Modifier.fillMaxSize()) }
                    composable(TvRoute.SETTINGS) { SettingsScreen(modifier = Modifier.fillMaxSize()) }
                }
            }
        }
    }
}

/**
 * Bottom-nav style tab switch: single top-level entry per tab, with the leaving tab's state saved
 * and the entered tab's state restored so it comes back exactly as the user left it.
 */
private fun NavController.switchTab(route: String) {
    if (currentDestination?.route == route) return
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavigationItem(
    selected: Boolean,
    onClick: () -> Unit,
    iconRes: Int? = null,
    imageVector: ImageVector? = null,
    badgeCount: Int = 0,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent,
            focusedContainerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            pressedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp) // Fixed height to make it a square-like button
            .clip(shape)
    ) {
        Box(
            Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (badgeCount > 0) {
                BadgedBox(
                    badge = {
                        Badge {
                            M3Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                        }
                    }
                ) {
                    RenderIcon(iconRes, imageVector)
                }
            } else {
                RenderIcon(iconRes, imageVector)
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun RenderIcon(iconRes: Int?, imageVector: ImageVector?) {
    if (imageVector != null) {
        Icon(
            imageVector = imageVector,
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    } else if (iconRes != null) {
        Icon(
            painter = painterResource(iconRes),
            contentDescription = null,
            modifier = Modifier.size(24.dp)
        )
    }
}
