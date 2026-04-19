package app.pwhs.universalinstaller.presentation.install

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.FolderZip
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.platform.LocalResources
import androidx.core.content.getSystemService
import app.pwhs.universalinstaller.R

enum class SourceTab { Local, Download }

@Composable
internal fun SourcePicker(
    selectedTab: SourceTab,
    onTabChange: (SourceTab) -> Unit,
    isParsing: Boolean,
    downloadState: DownloadState,
    onFindAutomatic: () -> Unit,
    onBrowsePackages: () -> Unit,
    onBrowseAll: () -> Unit,
    onStartDownload: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onDismissDownloadError: () -> Unit,
    onOpenDownloadHistory: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        SourceTabRow(
            selected = selectedTab,
            onSelect = onTabChange,
        )
        Spacer(Modifier.height(20.dp))
        AnimatedContent(
            targetState = selectedTab,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "source_tab",
        ) { tab ->
            when (tab) {
                SourceTab.Local -> LocalSourceContent(
                    isParsing = isParsing,
                    onFindAutomatic = onFindAutomatic,
                    onBrowsePackages = onBrowsePackages,
                    onBrowseAll = onBrowseAll,
                )
                SourceTab.Download -> DownloadSourceContent(
                    downloadState = downloadState,
                    onStartDownload = onStartDownload,
                    onCancelDownload = onCancelDownload,
                    onDismissDownloadError = onDismissDownloadError,
                    onOpenHistory = onOpenDownloadHistory,
                )
            }
        }
    }
}

@Composable
private fun SourceTabRow(
    selected: SourceTab,
    onSelect: (SourceTab) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SourceTabPill(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.Storage,
            label = stringResource(R.string.source_tab_local),
            selected = selected == SourceTab.Local,
            onClick = { onSelect(SourceTab.Local) },
        )
        SourceTabPill(
            modifier = Modifier.weight(1f),
            icon = Icons.Rounded.CloudDownload,
            label = stringResource(R.string.source_tab_download),
            selected = selected == SourceTab.Download,
            onClick = { onSelect(SourceTab.Download) },
        )
    }
}

@Composable
private fun SourceTabPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        FilledTonalButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.filledTonalButtonColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            ),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = modifier.heightIn(min = 52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            ),
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(Modifier.size(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LocalSourceContent(
    isParsing: Boolean,
    onFindAutomatic: () -> Unit,
    onBrowsePackages: () -> Unit,
    onBrowseAll: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.source_local_header),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        if (isParsing) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                )
                Text(
                    text = stringResource(R.string.file_picker_parsing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        LocalSourceAction(
            icon = Icons.Rounded.Search,
            title = stringResource(R.string.source_action_find_automatic),
            subtitle = stringResource(R.string.source_action_find_automatic_sub),
            enabled = !isParsing,
            onClick = onFindAutomatic,
        )
        LocalSourceAction(
            icon = Icons.Rounded.FolderZip,
            title = stringResource(R.string.source_action_browse_packages),
            subtitle = stringResource(R.string.source_action_browse_packages_sub),
            enabled = !isParsing,
            onClick = onBrowsePackages,
        )
        LocalSourceAction(
            icon = Icons.Rounded.FolderOpen,
            title = stringResource(R.string.source_action_browse_all),
            subtitle = stringResource(R.string.source_action_browse_all_sub),
            enabled = !isParsing,
            onClick = onBrowseAll,
        )
    }
}

/**
 * List-style action row: leading icon · title over subtitle · chevron. Left-aligned so the
 * varying label lengths stay visually anchored — earlier center-wrapping looked ragged when
 * subtitles (e.g. the extension list) wrapped to a second line.
 */
@Composable
private fun LocalSourceAction(
    icon: ImageVector,
    title: String,
    subtitle: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = 64.dp),
        enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            horizontal = 16.dp, vertical = 12.dp,
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.size(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.Rounded.ChevronRight,
                contentDescription = null,
                modifier = Modifier.size(20.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DownloadSourceContent(
    downloadState: DownloadState,
    onStartDownload: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onDismissDownloadError: () -> Unit,
    onOpenHistory: () -> Unit,
) {
    val context = LocalContext.current
    val resource = LocalResources.current
    var url by rememberSaveable { mutableStateOf("") }
    val running = downloadState as? DownloadState.Running
    val error = (downloadState as? DownloadState.Error)?.message
    val isRunning = running != null

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(R.string.source_download_header),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        OutlinedTextField(
            value = url,
            onValueChange = {
                if (error != null) onDismissDownloadError()
                url = it
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 140.dp),
            placeholder = { Text(stringResource(R.string.source_download_hint)) },
            enabled = !isRunning,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Uri,
                imeAction = ImeAction.Go,
            ),
            isError = error != null,
            supportingText = error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer,
                focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
                disabledIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            ),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    if (isRunning) onCancelDownload() else onStartDownload(url)
                },
                enabled = isRunning || url.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 56.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                Text(
                    text = if (isRunning) stringResource(R.string.remote_download_cancel)
                           else stringResource(R.string.source_download_start),
                    style = MaterialTheme.typography.titleSmall,
                )
            }
            IconButton(
                onClick = {
                    pasteFromClipboard(context)?.let { url = it }
                        ?: Toast.makeText(
                            context,
                            resource.getString(R.string.remote_download_clipboard_empty),
                            Toast.LENGTH_SHORT,
                        ).show()
                },
                enabled = !isRunning,
                modifier = Modifier.size(56.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentPaste,
                    contentDescription = stringResource(R.string.source_download_paste),
                )
            }
        }
        if (running != null) {
            val percent = running.progressPercent
            if (percent != null) {
                LinearProgressIndicator(
                    progress = { percent / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        }

        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onOpenHistory,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(
                text = stringResource(R.string.download_history_open_button),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

private fun pasteFromClipboard(context: Context): String? {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim()
    return text?.takeIf { it.isNotEmpty() }
}

