package app.pwhs.updater.presentation.component

import android.app.Activity
import android.content.Intent
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import app.pwhs.core.R as CoreR
import app.pwhs.core.util.disableSceneTransition

private enum class UpdaterBottomNavDestination(
    val targetActivityClassName: String?,
    val labelRes: Int,
    val icon: ImageVector,
) {
    INSTALL(
        targetActivityClassName = "app.pwhs.universalinstaller.presentation.install.InstallActivity",
        labelRes = CoreR.string.nav_install,
        icon = Icons.Rounded.InstallMobile,
    ),
    UPDATES(
        targetActivityClassName = null,
        labelRes = CoreR.string.nav_updates,
        icon = Icons.Rounded.RocketLaunch,
    ),
    MANAGE(
        targetActivityClassName = "app.pwhs.universalinstaller.presentation.manage.ManageActivity",
        labelRes = CoreR.string.nav_manage,
        icon = Icons.Rounded.Apps,
    ),
    SETTINGS(
        targetActivityClassName = "app.pwhs.universalinstaller.presentation.setting.SettingActivity",
        labelRes = CoreR.string.nav_settings,
        icon = Icons.Rounded.Settings,
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

    NavigationBar {
        UpdaterBottomNavDestination.entries.forEach { destination ->
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
                    if (destination == UpdaterBottomNavDestination.UPDATES && updateCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge {
                                    Text(if (updateCount > 99) "99+" else updateCount.toString())
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
                label = { Text(stringResource(destination.labelRes)) },
            )
        }
    }
}
