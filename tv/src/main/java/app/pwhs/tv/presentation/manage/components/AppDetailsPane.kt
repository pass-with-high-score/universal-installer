package app.pwhs.tv.presentation.manage.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.ui.text.style.TextOverflow
import app.pwhs.tv.rememberAppIcon
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.SearchOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.pwhs.core.domain.InstalledApp
import app.pwhs.tv.R
import app.pwhs.tv.formatSize

@Composable
fun AppDetailsPane(
    focusedApp: InstalledApp?,
    isLoading: Boolean,
    rootAvailable: Boolean,
    searchQuery: String,
    onOpen: (InstalledApp) -> Unit,
    onForceStop: (InstalledApp) -> Unit,
    onToggleEnabled: (InstalledApp) -> Unit,
    onClearData: (InstalledApp) -> Unit,
    onUninstall: (InstalledApp) -> Unit,
    onExtract: (InstalledApp) -> Unit,
    onOpenSettings: (InstalledApp) -> Unit,
    onClearSearch: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 40.dp, vertical = 28.dp)
    ) {
        val app = focusedApp
        if (app != null && !isLoading) {
            val appIcon = rememberAppIcon(app.packageName, sizePx = 160)

            // ── Fixed Top Header (Never pushed out of view) ─────────────────
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    if (appIcon != null) {
                        Image(
                            bitmap = appIcon,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().padding(6.dp)
                        )
                    } else {
                        Text(
                            text = app.appName.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${stringResource(R.string.tv_manage_version_prefix, app.versionName)} • ${formatSize(context, app.sizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // ── Scrollable Actions & Extra Metadata ─────────────────────────
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (app.isSystemApp || !app.enabled) {
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (app.isSystemApp) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.tv_manage_system_app),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            if (!app.enabled) {
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.errorContainer)
                                        .padding(horizontal = 10.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        stringResource(R.string.tv_manage_status_disabled),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    ActionItem(label = stringResource(R.string.tv_manage_action_open)) {
                        onOpen(app)
                    }
                }

                if (rootAvailable) {
                    item {
                        ActionItem(label = stringResource(R.string.tv_manage_action_force_stop)) {
                            onForceStop(app)
                        }
                    }
                    item {
                        ActionItem(
                            label = stringResource(
                                if (app.enabled) R.string.tv_manage_action_disable
                                else R.string.tv_manage_action_enable
                            )
                        ) {
                            onToggleEnabled(app)
                        }
                    }
                    item {
                        ActionItem(
                            label = stringResource(R.string.tv_manage_action_clear_data),
                            destructive = true,
                        ) {
                            onClearData(app)
                        }
                    }
                }

                item {
                    ActionItem(
                        label = stringResource(R.string.tv_manage_action_uninstall),
                        destructive = true,
                    ) {
                        onUninstall(app)
                    }
                }

                item {
                    ActionItem(label = stringResource(R.string.tv_manage_action_extract)) {
                        onExtract(app)
                    }
                }

                item {
                    ActionItem(label = stringResource(R.string.tv_manage_action_settings)) {
                        onOpenSettings(app)
                    }
                }

                item {
                    Spacer(Modifier.height(32.dp))
                }
            }
        } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when {
                        isLoading -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            ShimmerBox(modifier = Modifier.size(120.dp), shape = RoundedCornerShape(20.dp))
                            Spacer(Modifier.height(24.dp))
                            ShimmerBox(
                                modifier = Modifier
                                    .width(200.dp)
                                    .height(32.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(Modifier.height(8.dp))
                            ShimmerBox(
                                modifier = Modifier
                                    .width(140.dp)
                                    .height(24.dp),
                                shape = RoundedCornerShape(8.dp)
                            )
                        }
                        searchQuery.isNotBlank() -> DetailEmptyState(
                            icon = Icons.Rounded.SearchOff,
                            title = stringResource(R.string.tv_manage_no_matches, searchQuery),
                            actionLabel = stringResource(R.string.tv_manage_clear_search),
                            onAction = onClearSearch,
                        )
                        else -> DetailEmptyState(
                            icon = Icons.Rounded.Apps,
                            title = stringResource(R.string.tv_manage_empty),
                            actionLabel = null,
                            onAction = {},
                        )
                    }
                }
            }
        }
    }

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ActionItem(
    label: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(12.dp)
    val focusContainer = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
    val focusContent = if (destructive) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary
    val restContent = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
    Surface(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp),
        scale = app.pwhs.tv.TvNoScale,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f),
            focusedContainerColor = focusContainer,
            contentColor = restContent,
            focusedContentColor = focusContent
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
fun InfoBlock(label: String, value: String) {
    Column(modifier = Modifier.padding(vertical = 10.dp)) {
        Text(text = label, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun DetailEmptyState(
    icon: ImageVector,
    title: String,
    actionLabel: String?,
    onAction: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(420.dp),
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(20.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleLarge,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null) {
            Spacer(Modifier.height(24.dp))
            val shape = CircleShape
            Button(onClick = onAction, scale = app.pwhs.tv.TvButtonNoScale, shape = ButtonDefaults.shape(shape)) {
                Text(actionLabel)
            }
        }
    }
}
