package app.pwhs.universalinstaller.presentation.manage

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.util.AppIconData
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest

data class AppActionItem(
    val icon: ImageVector,
    val iconTint: Color,
    val label: String,
    val enabled: Boolean = true,
    val onClick: () -> Unit,
)

@Composable
fun AppActionHeader(
    app: InstalledApp,
    context: Context,
    isBlocked: Boolean = false,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SubcomposeAsyncImage(
                model = ImageRequest.Builder(context)
                    .data(AppIconData(app.packageName))
                    .build(),
                contentDescription = app.appName,
                modifier = Modifier
                    .size(52.dp)
                    .clip(MaterialTheme.shapes.medium),
                error = {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        modifier = Modifier.size(52.dp),
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                    )
                },
                success = { SubcomposeAsyncImageContent() },
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                val versionSizeLine = buildList {
                    if (app.versionName.isNotBlank()) add("v${app.versionName}")
                    if (app.sizeBytes > 0) {
                        add(android.text.format.Formatter.formatShortFileSize(context, app.sizeBytes))
                    }
                }.joinToString(" · ")
                if (versionSizeLine.isNotBlank()) {
                    Text(
                        text = versionSizeLine,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Dates line: Installed date + update date + last-used time
        val dateParts = buildList {
            if (app.installedAt > 0) {
                add(
                    stringResource(
                        R.string.uninstall_row_installed,
                        android.text.format.DateUtils.formatDateTime(
                            context,
                            app.installedAt,
                            android.text.format.DateUtils.FORMAT_SHOW_DATE or
                                android.text.format.DateUtils.FORMAT_ABBREV_MONTH,
                        )
                    )
                )
            }
            if (app.lastUpdatedAt > 0 && app.lastUpdatedAt != app.installedAt) {
                add(
                    stringResource(
                        R.string.uninstall_row_updated,
                        android.text.format.DateUtils.formatDateTime(
                            context,
                            app.lastUpdatedAt,
                            android.text.format.DateUtils.FORMAT_SHOW_DATE or
                                android.text.format.DateUtils.FORMAT_ABBREV_MONTH,
                        )
                    )
                )
            }
            if (app.lastUsedAt > 0) {
                val rel = android.text.format.DateUtils.getRelativeTimeSpanString(
                    app.lastUsedAt,
                    System.currentTimeMillis(),
                    android.text.format.DateUtils.MINUTE_IN_MILLIS,
                    android.text.format.DateUtils.FORMAT_ABBREV_RELATIVE,
                ).toString()
                add(stringResource(R.string.uninstall_row_used, rel))
            }
        }
        if (dateParts.isNotEmpty()) {
            Text(
                text = dateParts.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // Status Badges row
        val hasBadges = isBlocked || app.isSystemApp || app.hasSplits || !app.enabled || app.isAndroidAutoSupported
        if (hasBadges) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isBlocked) {
                    Text(
                        text = stringResource(R.string.manage_blocked_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (app.isSystemApp) {
                    Text(
                        text = stringResource(R.string.uninstall_system_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.tertiaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (app.hasSplits) {
                    Text(
                        text = stringResource(R.string.manage_split_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (!app.enabled) {
                    Text(
                        text = stringResource(R.string.manage_disabled_badge),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
                if (app.isAndroidAutoSupported) {
                    Text(
                        text = "Android Auto",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier
                            .clip(MaterialTheme.shapes.small)
                            .background(MaterialTheme.colorScheme.primaryContainer)
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun AppActionGrid(
    items: List<AppActionItem>,
    columns: Int = 4,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        val rows = items.chunked(columns)
        for (rowItems in rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
            ) {
                for (item in rowItems) {
                    ActionGridCell(
                        item = item,
                        modifier = Modifier.weight(1f),
                    )
                }
                val remaining = columns - rowItems.size
                if (remaining > 0) {
                    repeat(remaining) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ActionGridCell(
    item: AppActionItem,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = item.enabled, onClick = item.onClick)
            .padding(vertical = 10.dp, horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = if (item.enabled) {
                MaterialTheme.colorScheme.surfaceVariant
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            },
            modifier = Modifier.size(48.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.label,
                    tint = if (item.enabled) {
                        item.iconTint
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                    },
                    modifier = Modifier.size(24.dp),
                )
            }
        }
        Text(
            text = item.label,
            style = MaterialTheme.typography.labelSmall,
            color = if (item.enabled) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            },
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 14.sp,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 28.dp),
        )
    }
}

@Composable
fun UninstallConfirmDialog(
    app: InstalledApp,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(AppIconData(app.packageName))
                        .build(),
                    contentDescription = app.appName,
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape),
                    error = {
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(
                                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f),
                                    shape = CircleShape,
                                ),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                    },
                    success = { SubcomposeAsyncImageContent() },
                )

                Spacer(Modifier.height(14.dp))

                Text(
                    text = app.appName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )

                Spacer(Modifier.height(4.dp))

                Text(
                    text = app.packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )

                val sizeText = if (app.sizeBytes > 0) {
                    android.text.format.Formatter.formatShortFileSize(context, app.sizeBytes)
                } else null
                val versionText = app.versionName.takeIf { it.isNotBlank() }?.let { "v$it" }
                val infoDetails = listOfNotNull(versionText, sizeText).joinToString(" • ")

                if (infoDetails.isNotBlank()) {
                    Spacer(Modifier.height(8.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.7f),
                    ) {
                        Text(
                            text = infoDetails,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = stringResource(R.string.uninstall_confirm_single_text, app.appName, app.packageName),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(14.dp),
                    )
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onDismiss,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.cancel),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    Button(
                        onClick = onConfirm,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.uninstall),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}
