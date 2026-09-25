package app.pwhs.updater.presentation.component

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import app.pwhs.core.ui.component.verticalScrollbar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Label
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.platform.LocalContext
import app.pwhs.core.util.TransferFormatter
import app.pwhs.updater.presentation.AppDownloadProgress
import app.pwhs.updater.presentation.util.InstallerUtils
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.R
import app.pwhs.core.ui.theme.LocalExtendedColors
import app.pwhs.core.ui.theme.Spacing
import app.pwhs.updater.domain.model.TrackedApp

@Composable
fun TrackedAppCard(
    app: TrackedApp,
    downloadInfo: AppDownloadProgress? = null,
    onClick: () -> Unit = {},
    onUpdateClick: () -> Unit,
    onCheckClick: () -> Unit,
    onDeleteClick: () -> Unit,
    onCancelDownloadClick: (() -> Unit)? = null,
    isChecking: Boolean = false,
    onEditCategoryClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val isDownloading = downloadInfo != null
    var expandedNotes by remember { mutableStateOf(false) }

    OutlinedCard(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraLarge,
        colors = CardDefaults.outlinedCardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        border = BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.L),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppIconView(
                    packageName = app.packageName,
                    appName = app.appName,
                    iconUrl = app.iconUrl,
                    sourceUrl = app.sourceUrl,
                    isInstalled = app.isInstalled,
                    size = 44.dp,
                )
                Spacer(modifier = Modifier.width(Spacing.M))
                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = app.appName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .basicMarquee(),
                        )
                        if (!app.category.isNullOrBlank()) {
                            Spacer(modifier = Modifier.width(Spacing.S))
                            Surface(
                                shape = MaterialTheme.shapes.extraSmall,
                                color = MaterialTheme.colorScheme.secondaryContainer,
                                onClick = { onEditCategoryClick?.invoke() },
                                enabled = onEditCategoryClick != null,
                            ) {
                                Text(
                                    text = app.category.orEmpty(),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                )
                            }
                        }
                    }
                    Text(
                        text = app.sourceUrl.removePrefix("https://"),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee(),
                    )
                }

                if (app.hasUpdate) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer,
                    ) {
                        Text(
                            text = stringResource(R.string.updates_card_badge_new, app.latestVersionName.orEmpty()),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                        )
                    }
                } else if (!app.isInstalled) {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Text(
                            text = stringResource(R.string.updates_card_badge_not_installed),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                        )
                    }
                } else {
                    Surface(
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(12.dp),
                                tint = LocalExtendedColors.current.success.takeIf { it != androidx.compose.ui.graphics.Color.Unspecified } ?: MaterialTheme.colorScheme.primary,
                            )
                            Spacer(modifier = Modifier.width(Spacing.XS))
                            Text(
                                text = stringResource(R.string.updates_card_badge_up_to_date),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.M))

            // Versions info
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.updates_card_current_version, app.currentVersionName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (app.latestReleaseTag != null) {
                    Text(
                        text = stringResource(R.string.updates_card_tag, app.latestReleaseTag.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Download Progress Bar
            if (downloadInfo != null) {
                val progress = downloadInfo.progress
                val bytesDownloaded = downloadInfo.bytesDownloaded
                val totalBytes = downloadInfo.totalBytes
                val speedBytesPerSec = downloadInfo.speedBytesPerSec
                val etaSeconds = downloadInfo.etaSeconds

                Spacer(modifier = Modifier.height(Spacing.M))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val sizeText = if (totalBytes > 0) {
                            "${InstallerUtils.formatBytes(bytesDownloaded)} / ${InstallerUtils.formatBytes(totalBytes)}"
                        } else if (bytesDownloaded > 0) {
                            InstallerUtils.formatBytes(bytesDownloaded)
                        } else {
                            stringResource(R.string.updates_card_downloading)
                        }
                        val speedStr = TransferFormatter.formatSpeed(speedBytesPerSec)
                        val leftText = if (speedStr.isNotEmpty()) "$sizeText • $speedStr" else sizeText

                        Text(
                            text = leftText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .basicMarquee(),
                        )

                        val percent = if (totalBytes > 0) (progress * 100).toInt() else null
                        val etaStr = TransferFormatter.formatEta(LocalContext.current, etaSeconds)
                        val rightText = when {
                            percent != null && etaStr != null -> "$percent% ($etaStr)"
                            percent != null -> "$percent%"
                            etaStr != null -> etaStr
                            else -> ""
                        }

                        if (rightText.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(Spacing.S))
                            Text(
                                text = rightText,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(Spacing.XS))
                    if (progress > 0f) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            }

            // Release Notes Expandable Section
            if (!app.releaseNotes.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(Spacing.S))
                Surface(
                    onClick = { expandedNotes = !expandedNotes },
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(Spacing.M)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(R.string.updates_card_changelog_title),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Icon(
                                imageVector = if (expandedNotes) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }

                        AnimatedVisibility(visible = expandedNotes) {
                            val cardNotesScroll = rememberScrollState()
                            Column(
                                modifier = Modifier
                                    .padding(top = Spacing.S)
                                    .fillMaxWidth()
                                    .heightIn(max = 200.dp)
                                    .verticalScroll(cardNotesScroll)
                                    .verticalScrollbar(cardNotesScroll),
                            ) {
                                Text(
                                    text = app.releaseNotes.orEmpty(),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface,
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.M))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End,
            ) {
                if (onEditCategoryClick != null) {
                    IconButton(onClick = onEditCategoryClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Label,
                            contentDescription = "Edit Category",
                        )
                    }
                }

                IconButton(onClick = onDeleteClick) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteOutline,
                        contentDescription = stringResource(R.string.updates_card_btn_delete),
                        tint = MaterialTheme.colorScheme.error,
                    )
                }

                IconButton(
                    onClick = onCheckClick,
                    enabled = !isChecking,
                ) {
                    if (isChecking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = stringResource(R.string.updates_card_btn_check),
                        )
                    }
                }

                if (isDownloading) {
                    Spacer(modifier = Modifier.width(Spacing.S))
                    OutlinedButton(
                        onClick = { onCancelDownloadClick?.invoke() },
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f)),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(android.R.string.cancel))
                    }
                } else if (!app.isInstalled && !app.latestDownloadUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(Spacing.S))
                    Button(
                        onClick = onUpdateClick,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.updates_card_btn_install))
                    }
                } else if (app.hasUpdate && !app.latestDownloadUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.width(Spacing.S))
                    Button(
                        onClick = onUpdateClick,
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.updates_card_btn_update))
                    }
                }
            }
        }
    }
}
