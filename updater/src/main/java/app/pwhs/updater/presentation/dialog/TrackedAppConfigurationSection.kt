package app.pwhs.updater.presentation.dialog

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import app.pwhs.core.R
import app.pwhs.core.ui.theme.Spacing
import app.pwhs.updater.domain.matcher.VersionParser
import app.pwhs.updater.domain.model.TrackedApp

@Composable
fun TrackedAppConfigurationSection(
    app: TrackedApp,
    onSaveConfig: (TrackedApp) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current

    var includePrereleases by remember(app) { mutableStateOf(app.includePrereleases) }
    var customRegex by remember(app) { mutableStateOf(app.customRegexFilter.orEmpty()) }
    var versionRegex by remember(app) { mutableStateOf(app.versionRegex.orEmpty()) }
    var matchGroup by remember(app) { mutableStateOf(app.matchGroup.orEmpty()) }
    var useReleaseTitleAsVersion by remember(app) { mutableStateOf(app.useReleaseTitleAsVersion) }
    var category by remember(app) { mutableStateOf(app.category.orEmpty()) }
    var installedVersionRegex by remember(app) { mutableStateOf(app.installedVersionRegex.orEmpty()) }
    var installedVersionMatchGroup by remember(app) { mutableStateOf(app.installedVersionMatchGroup.orEmpty()) }

    val regexError = remember(versionRegex) { VersionParser.validateRegex(versionRegex) }
    val installedRegexError = remember(installedVersionRegex) { VersionParser.validateRegex(installedVersionRegex) }

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

    val previewInstalledVersion = remember(app, installedVersionRegex, installedVersionMatchGroup) {
        if (installedVersionRegex.isBlank() || !app.isInstalled) null
        else {
            VersionParser.extractVersion(
                tagName = app.currentVersionName,
                releaseTitle = null,
                defaultVersion = app.currentVersionName,
                versionRegex = installedVersionRegex.trim(),
                matchGroup = installedVersionMatchGroup.trim().takeIf { it.isNotBlank() },
                useReleaseTitleAsVersion = false,
            )
        }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = stringResource(R.string.updates_config_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            TextButton(
                enabled = regexError == null && installedRegexError == null,
                onClick = {
                    val trimmedVersionRegex = versionRegex.trim().takeIf { it.isNotBlank() }
                    val trimmedMatchGroup = matchGroup.trim().takeIf { it.isNotBlank() }
                    val trimmedInstalledVersionRegex = installedVersionRegex.trim().takeIf { it.isNotBlank() }
                    val trimmedInstalledMatchGroup = installedVersionMatchGroup.trim().takeIf { it.isNotBlank() }

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
                        installedVersionRegex = trimmedInstalledVersionRegex,
                        installedVersionMatchGroup = trimmedInstalledMatchGroup,
                    )
                    onSaveConfig(updated)
                    Toast.makeText(
                        context,
                        context.getString(R.string.updates_config_saved),
                        Toast.LENGTH_SHORT,
                    ).show()
                },
            ) {
                Text(stringResource(R.string.updates_config_save_btn))
            }
        }

        Spacer(modifier = Modifier.height(Spacing.S))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(stringResource(R.string.updates_config_include_prereleases), style = MaterialTheme.typography.bodyMedium)
            Switch(checked = includePrereleases, onCheckedChange = { includePrereleases = it })
        }

        Spacer(modifier = Modifier.height(Spacing.S))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.updates_config_use_release_title), style = MaterialTheme.typography.bodyMedium)
                Text(
                    stringResource(R.string.updates_config_use_release_title_desc),
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
            label = { Text(stringResource(R.string.updates_config_asset_regex_label)) },
            placeholder = { Text(stringResource(R.string.updates_config_asset_regex_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )

        Spacer(modifier = Modifier.height(Spacing.S))

        OutlinedTextField(
            value = versionRegex,
            onValueChange = { versionRegex = it },
            label = { Text(stringResource(R.string.updates_config_version_regex_label)) },
            placeholder = { Text(stringResource(R.string.updates_config_version_regex_placeholder)) },
            isError = regexError != null,
            supportingText = {
                if (regexError != null) {
                    Text(regexError, color = MaterialTheme.colorScheme.error)
                } else if (previewVersion != null) {
                    Text(
                        stringResource(R.string.updates_config_preview_extracted, previewVersion),
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
            label = { Text(stringResource(R.string.updates_config_match_group_label)) },
            placeholder = { Text(stringResource(R.string.updates_config_match_group_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )

        Spacer(modifier = Modifier.height(Spacing.S))

        // Issue #142: Installed Version Extractor Regex
        OutlinedTextField(
            value = installedVersionRegex,
            onValueChange = { installedVersionRegex = it },
            label = { Text(stringResource(R.string.updates_installed_version_regex_label)) },
            placeholder = { Text(stringResource(R.string.updates_installed_version_regex_placeholder)) },
            isError = installedRegexError != null,
            supportingText = {
                if (installedRegexError != null) {
                    Text(installedRegexError, color = MaterialTheme.colorScheme.error)
                } else if (previewInstalledVersion != null) {
                    Text(
                        stringResource(R.string.updates_preview_installed_extracted, previewInstalledVersion),
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
            value = installedVersionMatchGroup,
            onValueChange = { installedVersionMatchGroup = it },
            label = { Text(stringResource(R.string.updates_installed_match_group_label)) },
            placeholder = { Text(stringResource(R.string.updates_config_installed_match_group_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )

        Spacer(modifier = Modifier.height(Spacing.S))

        OutlinedTextField(
            value = category,
            onValueChange = { category = it },
            label = { Text(stringResource(R.string.updates_config_category_label)) },
            placeholder = { Text(stringResource(R.string.updates_config_category_placeholder)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
        )
    }
}
