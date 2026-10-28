package app.pwhs.tv.presentation.manage.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.pwhs.core.domain.InstalledApp
import app.pwhs.tv.R
import app.pwhs.tv.TvCardBorder
import app.pwhs.tv.TvNoScale
import app.pwhs.tv.formatSize
import app.pwhs.tv.rememberAppIcon

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun AppStatusBadge(
    text: String,
    containerColor: Color,
    contentColor: Color
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(containerColor)
            .padding(horizontal = 6.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = contentColor
        )
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun AppListRow(
    app: InstalledApp,
    isSelected: Boolean,
    focusRequester: FocusRequester?,
    onFocus: () -> Unit
) {
    val context = LocalContext.current
    val icon = rememberAppIcon(app.packageName, sizePx = 120)
    val shape = RoundedCornerShape(12.dp)
    var isFocused by remember { mutableStateOf(false) }

    Surface(
        onClick = { /* Detail pane updates on focus */ },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onFocus()
            },
        scale = TvNoScale,
        border = TvCardBorder,
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else Color.Transparent,
            focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = if (isSelected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            focusedContentColor = MaterialTheme.colorScheme.onPrimaryContainer
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f)
                    ),
                contentAlignment = Alignment.Center
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(4.dp)
                    )
                } else {
                    Text(
                        text = app.appName.take(1).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = if (isFocused || isSelected) FontWeight.Bold else FontWeight.SemiBold,
                        color = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                        else if (isSelected) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    if (!app.enabled) {
                        AppStatusBadge(
                            text = stringResource(R.string.tv_manage_filter_disabled),
                            containerColor = if (isFocused) MaterialTheme.colorScheme.error.copy(alpha = 0.25f)
                            else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            contentColor = if (isFocused) MaterialTheme.colorScheme.error
                            else MaterialTheme.colorScheme.onErrorContainer
                        )
                    } else if (app.isSystemApp) {
                        AppStatusBadge(
                            text = stringResource(R.string.tv_manage_filter_system),
                            containerColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.15f)
                            else MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Subtitle metadata: Version • Size • PackageName
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val sizeStr = if (app.sizeBytes > 0) formatSize(context, app.sizeBytes) else ""
                    val versionStr = if (app.versionName.isNotBlank()) "v${app.versionName}" else ""
                    val subColor = if (isFocused) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    else MaterialTheme.colorScheme.onSurfaceVariant

                    val metaList = buildList {
                        if (versionStr.isNotBlank()) add(versionStr)
                        if (sizeStr.isNotBlank()) add(sizeStr)
                    }

                    if (metaList.isNotEmpty()) {
                        Text(
                            text = metaList.joinToString(" • "),
                            style = MaterialTheme.typography.bodySmall,
                            color = subColor,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = subColor.copy(alpha = 0.5f)
                        )
                    }

                    Text(
                        text = app.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = subColor.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }
        }
    }
}

@Composable
fun LoadingRow() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ShimmerBox(modifier = Modifier.size(44.dp), shape = RoundedCornerShape(10.dp))
        Spacer(Modifier.width(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ShimmerBox(modifier = Modifier.width(160.dp).height(18.dp), shape = RoundedCornerShape(4.dp))
            ShimmerBox(modifier = Modifier.width(100.dp).height(14.dp), shape = RoundedCornerShape(4.dp))
        }
    }
}
