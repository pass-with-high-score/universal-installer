package app.pwhs.universalinstaller.presentation.composable

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavHostController
import app.pwhs.universalinstaller.R
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.generated.destinations.InstallScreenDestination
import com.ramcosta.composedestinations.generated.destinations.SettingScreenDestination
import com.ramcosta.composedestinations.generated.destinations.UninstallScreenDestination
import com.ramcosta.composedestinations.spec.DirectionDestinationSpec
import com.ramcosta.composedestinations.utils.isRouteOnBackStackAsState
import com.ramcosta.composedestinations.utils.rememberDestinationsNavigator

@Composable
fun BottomBar(
    navController: NavHostController
) {
    val navigator = navController.rememberDestinationsNavigator()
    val colors = MaterialTheme.colorScheme
    val itemColors = NavigationBarItemDefaults.colors(
        selectedIconColor = colors.onPrimaryContainer,
        selectedTextColor = colors.primary,
        indicatorColor = colors.primaryContainer,
        unselectedIconColor = colors.onSurfaceVariant,
        unselectedTextColor = colors.onSurfaceVariant,
    )
    NavigationBar {
        BottomBarItem.entries.forEach { destination ->
            val isCurrentDestOnBackStack by navController.isRouteOnBackStackAsState(destination.direction)
            NavigationBarItem(
                selected = isCurrentDestOnBackStack,
                colors = itemColors,
                onClick = {
                    if (isCurrentDestOnBackStack) {
                        navigator.popBackStack(destination.direction, false)
                    }

                    navigator.navigate(destination.direction) {
                        popUpTo(NavGraphs.root) {
                            saveState = true
                        }

                        launchSingleTop = true
                        restoreState = true
                    }
                },
                icon = {
                    Icon(
                        imageVector = destination.icon,
                        contentDescription = stringResource(destination.label)
                    )
                },
                label = { Text(stringResource(destination.label)) },
            )
        }
    }
}

enum class BottomBarItem(
    val direction: DirectionDestinationSpec,
    val label: Int,
    val icon: ImageVector,
) {
    Install(InstallScreenDestination, R.string.txt_install, Icons.Rounded.InstallMobile),
    Uninstall(UninstallScreenDestination, R.string.txt_uninstall, Icons.Rounded.DeleteOutline),
    Settings(SettingScreenDestination, R.string.txt_setting, Icons.Rounded.Settings)
}