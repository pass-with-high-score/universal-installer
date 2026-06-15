package app.pwhs.tv

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.pwhs.tv.presentation.manage.ManageScreen
import app.pwhs.tv.presentation.receive.ReceiveScreen
import app.pwhs.tv.presentation.settings.SettingsScreen

import androidx.compose.animation.core.tween

/**
 * Top-level TV shell: a side Navigation Rail for Install | Manage | Settings.
 * Modern collapsible design that expands on focus.
 */
@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvApp(modifier: Modifier = Modifier) {
    var tab by remember { mutableIntStateOf(0) }
    var isRailFocused by remember { mutableStateOf(false) }
    val railWidth by animateDpAsState(
        targetValue = if (isRailFocused) 240.dp else 88.dp,
        animationSpec = tween(durationMillis = 300),
        label = "railWidth"
    )

    Box(
        modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.fillMaxSize()) {
            // Side Navigation Rail
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(railWidth)
                    .onFocusChanged { isRailFocused = it.hasFocus }
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    Modifier
                        .fillMaxSize()
                        .padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.Start,
                ) {

                    // App Logo / Title
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                                Icon(
                                    painter = painterResource(R.drawable.logo),
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = Color.Unspecified
                                )
                            }
                            AnimatedVisibility(
                                visible = isRailFocused,
                                enter = fadeIn(tween(durationMillis = 300, delayMillis = 100)),
                                exit = fadeOut(tween(durationMillis = 150))
                            ) {
                                Text(
                                    text = stringResource(R.string.app_name),
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                    modifier = Modifier.padding(start = 4.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(32.dp))

                    NavigationItem(
                        selected = tab == 0,
                        expanded = isRailFocused,
                        onClick = { tab = 0 },
                        iconRes = R.drawable.ic_apk_install,
                        label = stringResource(R.string.tv_app_tab_install)
                    )
                    Spacer(Modifier.height(12.dp))
                    NavigationItem(
                        selected = tab == 1,
                        expanded = isRailFocused,
                        onClick = { tab = 1 },
                        iconRes = R.drawable.ic_delete,
                        label = stringResource(R.string.tv_app_tab_manage)
                    )
                    Spacer(Modifier.height(12.dp))
                    NavigationItem(
                        selected = tab == 2,
                        expanded = isRailFocused,
                        onClick = { tab = 2 },
                        iconRes = R.drawable.ic_setting,
                        label = stringResource(R.string.tv_app_tab_settings)
                    )
                }
            }


            // Content Area
            Box(Modifier.weight(1f).fillMaxHeight()) {
                when (tab) {
                    0 -> ReceiveScreen(modifier = Modifier.fillMaxSize())
                    1 -> ManageScreen(modifier = Modifier.fillMaxSize())
                    else -> SettingsScreen(modifier = Modifier.fillMaxSize())
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun NavigationItem(
    selected: Boolean,
    expanded: Boolean,
    onClick: () -> Unit,
    iconRes: Int,
    label: String
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f),
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (expanded) 1f else 0.5f) else Color.Transparent,
            focusedContainerColor = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
            pressedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
        ),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Box(Modifier.width(56.dp), contentAlignment = Alignment.Center) {
                Icon(
                    painter = painterResource(iconRes),
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
            }
            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandHorizontally(),
                exit = fadeOut() + shrinkHorizontally()
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                    modifier = Modifier.padding(start = 16.dp)
                )
            }
        }
    }
}

