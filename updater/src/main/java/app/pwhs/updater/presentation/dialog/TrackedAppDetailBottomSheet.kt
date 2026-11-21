package app.pwhs.updater.presentation.dialog

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import app.pwhs.core.util.openUriSafely
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.R
import app.pwhs.core.ui.theme.LocalExtendedColors
import app.pwhs.core.ui.theme.Spacing
import app.pwhs.updater.domain.model.TrackedApp
import app.pwhs.updater.presentation.component.AppIconView

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackedAppDetailBottomSheet(
    app: TrackedApp,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onUpdateApp: (TrackedApp) -> Unit,
    onDeleteApp: (TrackedApp) -> Unit,
    onCheckUpdate: (TrackedApp) -> Unit,
    onDownloadAndInstall: (TrackedApp) -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        TrackedAppDetailContent(
            app = app,
            onDismiss = onDismiss,
            onUpdateApp = onUpdateApp,
            onDeleteApp = onDeleteApp,
            onCheckUpdate = onCheckUpdate,
            onDownloadAndInstall = onDownloadAndInstall,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackedAppDetailContent(
    app: TrackedApp,
    onDismiss: () -> Unit,
    onUpdateApp: (TrackedApp) -> Unit,
    onDeleteApp: (TrackedApp) -> Unit,
    onCheckUpdate: (TrackedApp) -> Unit,
    onDownloadAndInstall: (TrackedApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    var activeTab by rememberSaveable(app.packageName) { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.L)
            .verticalScroll(rememberScrollState()),
    ) {
            // Header
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
                    size = 56.dp,
                )
                Spacer(modifier = Modifier.width(Spacing.M))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.appName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.basicMarquee(),
                    )
                    Text(
                        text = app.packageName,
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
                            text = "New: ${app.latestVersionName}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = Spacing.S, vertical = Spacing.XS),
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.M))

            // Version info banner
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(Spacing.M),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column {
                        Text("Installed", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(app.currentVersionName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text("Latest", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(app.latestVersionName ?: "N/A", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(Spacing.M))

            // Compact 2-tab switcher so Overview stays concise while power-user Settings stay 1 tap away.
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    shape = SegmentedButtonDefaults.itemShape(index = 0, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                ) {
                    Text(
                        text = "Overview",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                SegmentedButton(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    shape = SegmentedButtonDefaults.itemShape(index = 1, count = 2),
                    icon = {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    },
                ) {
                    Text(
                        text = stringResource(R.string.nav_settings),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            Spacer(modifier = Modifier.height(Spacing.M))

            if (activeTab == 0) {
                // Quick App Actions Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.S),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (app.isInstalled) {
                        FilledTonalButton(
                            onClick = {
                                runCatching {
                                    val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                                    if (intent != null) context.startActivity(intent)
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = MaterialTheme.shapes.medium,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.XS))
                            Text("Open", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            context.openUriSafely(app.sourceUrl)
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.XS))
                        Text("Source", maxLines = 1, softWrap = false, overflow = TextOverflow.Ellipsis)
                    }

                    OutlinedButton(
                        onClick = { onCheckUpdate(app) },
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Check update", modifier = Modifier.size(18.dp))
                    }

                    OutlinedButton(
                        onClick = {
                            runCatching {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_TEXT, app.sourceUrl)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, "Share App Link"))
                            }
                        },
                        shape = MaterialTheme.shapes.medium,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = "Share", modifier = Modifier.size(18.dp))
                    }
                }

                if ((app.hasUpdate || !app.isInstalled) && !app.latestDownloadUrl.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(Spacing.M))
                    Button(
                        onClick = {
                            onDismiss()
                            onDownloadAndInstall(app)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            if (app.isInstalled) Icons.Rounded.SystemUpdate else Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(Spacing.S))
                        Text(if (app.isInstalled) "Download & Install Update" else stringResource(R.string.updates_card_btn_install))
                    }

                    if (app.availableAssets.size > 1) {
                        Spacer(modifier = Modifier.height(Spacing.S))
                        OutlinedButton(
                            onClick = {
                                onDismiss()
                                onDownloadAndInstall(app)
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.medium,
                        ) {
                            Icon(Icons.Rounded.FolderZip, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(Spacing.S))
                            Text(stringResource(R.string.updates_detail_choose_asset_btn, app.availableAssets.size))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.M))

                // Changelog / Release notes
                Text(
                    text = stringResource(R.string.updates_card_changelog_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.height(Spacing.S))
                val changelogScrollState = rememberScrollState()
                Surface(
                    shape = MaterialTheme.shapes.medium,
                    color = MaterialTheme.colorScheme.surfaceContainerLowest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 96.dp, max = 360.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .verticalScroll(changelogScrollState)
                            .verticalScrollbar(changelogScrollState)
                            .padding(Spacing.M),
                    ) {
                        Text(
                            text = app.releaseNotes?.takeIf { it.isNotBlank() } ?: "No release notes provided for this version.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.M))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(Spacing.S))

                // Ignore version / Delete app actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (app.isVersionIgnored) {
                        TextButton(onClick = {
                            onUpdateApp(app.copy(ignoredVersion = null))
                        }) {
                            Text("Unignore Version")
                        }
                    } else if (app.hasUpdate && app.latestVersionName != null) {
                        TextButton(onClick = {
                            onUpdateApp(app.copy(ignoredVersion = app.latestVersionName))
                        }) {
                            Icon(Icons.Rounded.Block, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(Spacing.XS))
                            Text("Ignore ${app.latestVersionName}")
                        }
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }

                    TextButton(
                        onClick = {
                            onDismiss()
                            onDeleteApp(app)
                        },
                    ) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Spacer(modifier = Modifier.width(Spacing.XS))
                        Text("Delete Tracking", color = MaterialTheme.colorScheme.error)
                    }
                }
            } else {
                // Per-App Settings Section
                TrackedAppConfigurationSection(
                    app = app,
                    onSaveConfig = onUpdateApp,
                )
            }

            Spacer(modifier = Modifier.height(24.dp))
    }
}
