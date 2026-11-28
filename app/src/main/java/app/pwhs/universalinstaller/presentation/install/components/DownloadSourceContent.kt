package app.pwhs.universalinstaller.presentation.install.components

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.ActiveDownloadItem
import app.pwhs.universalinstaller.presentation.install.ActiveDownloadStatus
import app.pwhs.universalinstaller.presentation.install.DownloadState

/**
 * URL download tab upgraded with a parallel-ready Active Downloads queue UI.
 *
 * Bridges the current single [downloadState] into [ActiveDownloadItem] cards today,
 * while accepting [activeDownloads] + per-item Pause/Resume/Cancel callbacks for
 * seamless migration to multi-URL & segmented (ABDM-style) downloads.
 */
@Composable
internal fun DownloadSourceContent(
    downloadState: DownloadState,
    onStartDownload: (String) -> Unit,
    onCancelDownload: () -> Unit,
    onDismissDownloadError: () -> Unit,
    onOpenHistory: () -> Unit,
    activeDownloads: List<ActiveDownloadItem> = emptyList(),
    onPauseDownload: ((String) -> Unit)? = null,
    onResumeDownload: ((String) -> Unit)? = null,
    onCancelItem: ((String) -> Unit)? = null,
) {
    val context = LocalContext.current
    val resource = LocalResources.current
    var url by rememberSaveable { mutableStateOf("") }
    var lastSubmittedUrl by rememberSaveable { mutableStateOf("") }
    var pendingStart by remember { mutableStateOf(false) }

    LaunchedEffect(downloadState) {
        when (downloadState) {
            is DownloadState.Preparing,
            is DownloadState.Running,
            is DownloadState.Error,
            -> pendingStart = false
            DownloadState.Idle -> Unit
        }
    }

    val error = (downloadState as? DownloadState.Error)?.message
    val isPreparing = pendingStart || downloadState is DownloadState.Preparing
    val isLegacyRunning = downloadState is DownloadState.Running

    val items: List<ActiveDownloadItem> = remember(activeDownloads, downloadState, lastSubmittedUrl) {
        if (activeDownloads.isNotEmpty()) {
            activeDownloads
        } else {
            when (downloadState) {
                is DownloadState.Preparing -> listOf(
                    ActiveDownloadItem(
                        id = "active_preparing",
                        url = lastSubmittedUrl,
                        fileName = extractDisplayFileName(lastSubmittedUrl),
                        status = ActiveDownloadStatus.PREPARING,
                    )
                )
                is DownloadState.Running -> listOf(
                    ActiveDownloadItem(
                        id = downloadState.url,
                        url = downloadState.url,
                        fileName = extractDisplayFileName(downloadState.url),
                        bytesRead = downloadState.bytesRead,
                        totalBytes = downloadState.totalBytes,
                        speedBytesPerSec = downloadState.speedBytesPerSec,
                        etaSeconds = downloadState.etaSeconds,
                        status = ActiveDownloadStatus.DOWNLOADING,
                    )
                )
                else -> emptyList()
            }
        }
    }

    val canQueueMore = !isPreparing || activeDownloads.isNotEmpty()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        // ── Header + Active Count Pill ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = stringResource(R.string.source_download_header),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            AnimatedVisibility(visible = items.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Text(
                        text = stringResource(R.string.source_download_active_section, items.size),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }
        }

        // ── URL Input Field ──
        OutlinedTextField(
            value = url,
            onValueChange = {
                if (error != null) onDismissDownloadError()
                url = it
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 112.dp),
            placeholder = { Text(stringResource(R.string.source_download_hint)) },
            enabled = canQueueMore,
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
                disabledContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                focusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                unfocusedTextColor = MaterialTheme.colorScheme.onPrimaryContainer,
                disabledTextColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f),
                focusedPlaceholderColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                unfocusedPlaceholderColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
        )

        // ── Start / Paste Row ──
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = {
                    val trimmed = url.trim()
                    if (trimmed.isBlank()) return@Button
                    lastSubmittedUrl = trimmed
                    pendingStart = true
                    onStartDownload(trimmed)
                    val accepted = trimmed.startsWith("http://", ignoreCase = true) ||
                        trimmed.startsWith("https://", ignoreCase = true)
                    if (!accepted) {
                        pendingStart = false
                    } else {
                        pendingStart = false
                        url = ""
                    }
                },
                enabled = canQueueMore && url.isNotBlank(),
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = 52.dp),
                shape = RoundedCornerShape(16.dp),
            ) {
                if (isPreparing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                    Spacer(Modifier.width(8.dp))
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Download,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = stringResource(R.string.source_download_start),
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
                enabled = canQueueMore,
                modifier = Modifier.size(52.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentPaste,
                    contentDescription = stringResource(R.string.source_download_paste),
                )
            }
        }

        // ── Parallel Active Downloads Queue ──
        if (items.isNotEmpty()) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items.forEach { item ->
                    ActiveDownloadCard(
                        item = item,
                        onPause = onPauseDownload?.let { cb -> { cb(item.id) } },
                        onResume = onResumeDownload?.let { cb -> { cb(item.id) } },
                        onCancel = {
                            if (onCancelItem != null) onCancelItem(item.id) else onCancelDownload()
                        },
                    )
                }
            }
        }

        Spacer(Modifier.height(2.dp))
        OutlinedButton(
            onClick = onOpenHistory,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 48.dp),
            shape = RoundedCornerShape(16.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.History,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = stringResource(R.string.download_history_open_button),
                style = MaterialTheme.typography.titleSmall,
            )
        }
    }
}

private fun extractDisplayFileName(url: String): String {
    val segment = runCatching { Uri.parse(url).lastPathSegment }.getOrNull()
        ?.substringBefore('?')
        ?.trim()
        .orEmpty()
    return segment.ifBlank { url.ifBlank { "APK" } }
}

private fun pasteFromClipboard(context: Context): String? {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim()
    return text?.takeIf { it.isNotEmpty() }
}
