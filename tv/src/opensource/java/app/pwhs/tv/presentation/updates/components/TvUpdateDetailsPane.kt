package app.pwhs.tv.presentation.updates.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.OutlinedButtonDefaults
import androidx.tv.material3.Text
import app.pwhs.tv.R
import app.pwhs.tv.rememberAppIcon
import app.pwhs.updater.domain.model.TrackedApp
import coil3.compose.AsyncImage

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvUpdateDetailsPane(
    app: TrackedApp?,
    isChecking: Boolean,
    isDownloading: Boolean,
    onUpdateOrInstall: (TrackedApp) -> Unit,
    onCheckUpdate: (TrackedApp) -> Unit,
    onIgnoreVersion: (TrackedApp) -> Unit,
    onRemove: (TrackedApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (app == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = stringResource(R.string.tv_updates_empty),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val localIcon = rememberAppIcon(app.packageName, sizePx = 256)
    val notesScrollState = rememberScrollState()

    Column(
        modifier = modifier
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 40.dp, vertical = 40.dp),
    ) {
        // App Header: Icon + Name + Package Name
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    localIcon != null -> {
                        Image(
                            bitmap = localIcon,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    !app.iconUrl.isNullOrBlank() -> {
                        AsyncImage(
                            model = app.iconUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    else -> {
                        Image(
                            painter = painterResource(R.drawable.ic_apk_install),
                            contentDescription = null,
                            modifier = Modifier.size(36.dp),
                        )
                    }
                }
            }

            Spacer(Modifier.width(20.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Spacer(Modifier.height(20.dp))

        // Metadata Pills Row (Installed, Latest, Source, Status)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            MetadataPill(
                label = stringResource(R.string.tv_updates_detail_current),
                value = if (app.isInstalled) "v${app.currentVersionName}" else stringResource(R.string.tv_updates_status_not_installed),
                modifier = Modifier.weight(1f),
            )
            MetadataPill(
                label = stringResource(R.string.tv_updates_detail_latest),
                value = app.latestVersionName?.let { "v$it" } ?: "—",
                highlight = app.hasUpdate,
                modifier = Modifier.weight(1f),
            )
            MetadataPill(
                label = stringResource(R.string.tv_updates_detail_source),
                value = app.sourceType.name,
                modifier = Modifier.weight(1f),
            )
            MetadataPill(
                label = stringResource(R.string.tv_updates_detail_status),
                value = when {
                    app.hasUpdate -> stringResource(R.string.tv_updates_status_update_available)
                    !app.isInstalled -> stringResource(R.string.tv_updates_status_not_installed)
                    app.isVersionIgnored -> stringResource(R.string.tv_updates_status_ignored)
                    else -> stringResource(R.string.tv_updates_status_up_to_date)
                },
                highlight = app.hasUpdate,
                modifier = Modifier.weight(1.2f),
            )
        }

        Spacer(Modifier.height(20.dp))

        // Action Buttons (Organized in 2 rows for TV D-pad visibility)
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Row 1: Primary actions (Update/Install + Check)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val primaryLabel = when {
                    app.hasUpdate -> stringResource(R.string.tv_updates_btn_update)
                    !app.isInstalled -> stringResource(R.string.tv_updates_btn_install)
                    else -> stringResource(R.string.tv_updates_btn_update)
                }
                val btnShape = RoundedCornerShape(12.dp)

                Button(
                    onClick = { onUpdateOrInstall(app) },
                    enabled = !isDownloading && (app.hasUpdate || !app.isInstalled || !app.latestDownloadUrl.isNullOrBlank()),
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(btnShape),
                    shape = ButtonDefaults.shape(btnShape),
                    colors = ButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        focusedContentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (app.hasUpdate) Icons.Rounded.Download else Icons.Rounded.CheckCircle,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = primaryLabel,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }

                OutlinedButton(
                    onClick = { onCheckUpdate(app) },
                    enabled = !isChecking,
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(btnShape),
                    shape = OutlinedButtonDefaults.shape(btnShape),
                    colors = OutlinedButtonDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                        focusedContentColor = MaterialTheme.colorScheme.surface,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.tv_updates_btn_check),
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 1,
                        )
                    }
                }
            }

            // Row 2: Secondary / Destructive actions (Ignore + Delete/Remove)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val btnShape = RoundedCornerShape(12.dp)

                if (app.hasUpdate && !app.latestVersionName.isNullOrBlank()) {
                    OutlinedButton(
                        onClick = { onIgnoreVersion(app) },
                        modifier = Modifier
                            .weight(1f)
                            .height(44.dp)
                            .clip(btnShape),
                        shape = OutlinedButtonDefaults.shape(btnShape),
                        colors = OutlinedButtonDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                            focusedContentColor = MaterialTheme.colorScheme.surface,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.VisibilityOff,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.tv_updates_btn_ignore),
                                style = MaterialTheme.typography.labelMedium,
                                maxLines = 1,
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = { onRemove(app) },
                    modifier = Modifier
                        .weight(1f)
                        .height(44.dp)
                        .clip(btnShape),
                    shape = OutlinedButtonDefaults.shape(btnShape),
                    colors = OutlinedButtonDefaults.colors(
                        containerColor = MaterialTheme.colorScheme.error.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.error,
                        focusedContainerColor = MaterialTheme.colorScheme.error,
                        focusedContentColor = MaterialTheme.colorScheme.onError,
                    ),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.tv_updates_btn_remove),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(20.dp))

        // Release Notes Section (scrollable inside card, does not shift header)
        Text(
            text = stringResource(R.string.tv_updates_release_notes),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
        )

        Spacer(Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                .padding(16.dp),
        ) {
            val notes = app.releaseNotes?.trim()
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(notesScrollState),
            ) {
                Text(
                    text = if (!notes.isNullOrBlank()) notes else stringResource(R.string.tv_updates_no_release_notes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (!notes.isNullOrBlank()) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun MetadataPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlight: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (highlight) Color(0xFF1B5E20).copy(alpha = 0.35f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (highlight) FontWeight.Bold else FontWeight.Medium,
                color = if (highlight) Color(0xFF81C784) else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
