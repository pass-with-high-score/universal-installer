package app.pwhs.updater.presentation.dialog

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Launch
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FilterAlt
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Launch
import androidx.compose.material.icons.rounded.OpenInBrowser
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import app.pwhs.updater.domain.matcher.VersionParser
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
    val context = LocalContext.current
    var includePrereleases by remember(app) { mutableStateOf(app.includePrereleases) }
    var customRegex by remember(app) { mutableStateOf(app.customRegexFilter.orEmpty()) }
    var versionRegex by remember(app) { mutableStateOf(app.versionRegex.orEmpty()) }
    var matchGroup by remember(app) { mutableStateOf(app.matchGroup.orEmpty()) }
    var useReleaseTitleAsVersion by remember(app) { mutableStateOf(app.useReleaseTitleAsVersion) }
    var category by remember(app) { mutableStateOf(app.category.orEmpty()) }
    var isEditingSettings by remember { mutableStateOf(false) }

    val regexError = remember(versionRegex) { VersionParser.validateRegex(versionRegex) }
    val previewVersion = remember(app, versionRegex, matchGroup, useReleaseTitleAsVersion) {
        if (versionRegex.isBlank()) null
        else {
            VersionParser.extractVersion(
                tagName = app.latestReleaseTag ?: app.currentVersionName,
                releaseTitle = null,
                defaultVersion = app.latestVersionName ?: app.currentVersionName,
                versionRegex = versionRegex.trim(),
                matchGroup = matchGroup.trim().takeIf { it.isNotBlank() },
                useReleaseTitleAsVersion = useReleaseTitleAsVersion,
            )
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
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

            // Quick App Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(Spacing.S),
            ) {
                if (app.isInstalled) {
                    FilledTonalButton(
                        onClick = {
                            val intent = context.packageManager.getLaunchIntentForPackage(app.packageName)
                            if (intent != null) context.startActivity(intent)
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Launch, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(Spacing.XS))
                        Text("Open")
                    }
                }

                OutlinedButton(
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(app.sourceUrl))
                        context.startActivity(browserIntent)
                    },
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(Spacing.XS))
                    Text("Source")
                }

                OutlinedButton(
                    onClick = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_TEXT, app.sourceUrl)
                        }
                        context.startActivity(Intent.createChooser(sendIntent, "Share App Link"))
                    },
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(Icons.Rounded.Share, contentDescription = "Share")
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

            Spacer(modifier = Modifier.height(Spacing.L))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Spacing.M))

            // Changelog / Release notes
            Text(
                text = stringResource(R.string.updates_card_changelog_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Spacer(modifier = Modifier.height(Spacing.S))
            Surface(
                shape = MaterialTheme.shapes.medium,
                color = MaterialTheme.colorScheme.surfaceContainerLowest,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = app.releaseNotes?.takeIf { it.isNotBlank() } ?: "No release notes provided for this version.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(Spacing.M),
                )
            }

            Spacer(modifier = Modifier.height(Spacing.L))
            HorizontalDivider()
            Spacer(modifier = Modifier.height(Spacing.M))

            // Per-App Settings Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "App Configuration",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                TextButton(
                    enabled = regexError == null,
                    onClick = {
                        val trimmedVersionRegex = versionRegex.trim().takeIf { it.isNotBlank() }
                        val trimmedMatchGroup = matchGroup.trim().takeIf { it.isNotBlank() }
                        val updatedLatestVersion = if (app.latestReleaseTag != null) {
                            VersionParser.extractVersion(
                                tagName = app.latestReleaseTag,
                                defaultVersion = app.latestVersionName,
                                versionRegex = trimmedVersionRegex,
                                matchGroup = trimmedMatchGroup,
                                useReleaseTitleAsVersion = useReleaseTitleAsVersion,
                            )
                        } else {
                            app.latestVersionName
                        }
                        val updated = app.copy(
                            includePrereleases = includePrereleases,
                            customRegexFilter = customRegex.trim().takeIf { it.isNotBlank() },
                            versionRegex = trimmedVersionRegex,
                            matchGroup = trimmedMatchGroup,
                            useReleaseTitleAsVersion = useReleaseTitleAsVersion,
                            category = category.trim().takeIf { it.isNotBlank() },
                            latestVersionName = updatedLatestVersion,
                        )
                        onUpdateApp(updated)
                        isEditingSettings = false
                    },
                ) {
                    Text("Save Config")
                }
            }

            Spacer(modifier = Modifier.height(Spacing.S))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text("Include Pre-releases (Beta)", style = MaterialTheme.typography.bodyMedium)
                Switch(checked = includePrereleases, onCheckedChange = { includePrereleases = it })
            }

            Spacer(modifier = Modifier.height(Spacing.S))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Use Release Title as Version", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Extract from title instead of Git tag",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(modifier = Modifier.width(Spacing.S))
                Switch(checked = useReleaseTitleAsVersion, onCheckedChange = { useReleaseTitleAsVersion = it })
            }

            Spacer(modifier = Modifier.height(Spacing.S))

            OutlinedTextField(
                value = customRegex,
                onValueChange = { customRegex = it },
                label = { Text("Asset Regex Filter (Optional)") },
                placeholder = { Text("e.g. .*-arm64.*\\.apk") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(modifier = Modifier.height(Spacing.S))

            OutlinedTextField(
                value = versionRegex,
                onValueChange = { versionRegex = it },
                label = { Text("Version Extraction RegEx (Optional)") },
                placeholder = { Text("e.g. ^NeoPlayer(.+)$ or [0-9.]+") },
                isError = regexError != null,
                supportingText = {
                    if (regexError != null) {
                        Text(regexError, color = MaterialTheme.colorScheme.error)
                    } else if (previewVersion != null) {
                        Text(
                            "Preview extracted: $previewVersion",
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(modifier = Modifier.height(Spacing.S))

            OutlinedTextField(
                value = matchGroup,
                onValueChange = { matchGroup = it },
                label = { Text("Match Group to Use (Optional)") },
                placeholder = { Text("e.g. 1 or \$1 or \$1.\$2 (default: \$1 or \$0)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(modifier = Modifier.height(Spacing.S))

            OutlinedTextField(
                value = category,
                onValueChange = { category = it },
                label = { Text("Category") },
                placeholder = { Text("e.g. Tools, Social, Games") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium,
            )

            Spacer(modifier = Modifier.height(Spacing.M))

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

            Spacer(modifier = Modifier.height(36.dp))
        }
    }
}
