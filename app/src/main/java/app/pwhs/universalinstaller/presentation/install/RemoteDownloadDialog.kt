package app.pwhs.universalinstaller.presentation.install

import android.content.ClipboardManager
import android.content.Context
import android.text.format.Formatter
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.getSystemService
import app.pwhs.universalinstaller.R

@Composable
internal fun RemoteDownloadDialog(
    downloadState: DownloadState,
    onDismiss: () -> Unit,
    onDownload: (String) -> Unit,
    onCancelDownload: () -> Unit,
) {
    val context = LocalContext.current
    var url by remember { mutableStateOf("") }
    val isRunning = downloadState is DownloadState.Running
    val error = (downloadState as? DownloadState.Error)?.message

    AlertDialog(
        onDismissRequest = {
            if (!isRunning) onDismiss()
        },
        title = { Text(stringResource(R.string.remote_download_title)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.remote_download_hint)) },
                    singleLine = true,
                    enabled = !isRunning,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Uri,
                        imeAction = ImeAction.Go,
                    ),
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                pasteFromClipboard(context)?.let { url = it }
                                    ?: Toast.makeText(
                                        context,
                                        context.getString(R.string.remote_download_clipboard_empty),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                            },
                            enabled = !isRunning,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = stringResource(R.string.remote_download_paste),
                            )
                        }
                    },
                )

                if (error != null) {
                    Spacer(Modifier.height(12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Error,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.size(6.dp))
                        Text(
                            text = error,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }

                if (downloadState is DownloadState.Running) {
                    Spacer(Modifier.height(12.dp))
                    val percent = downloadState.progressPercent
                    if (percent != null) {
                        LinearProgressIndicator(
                            progress = { percent / 100f },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = progressText(context, downloadState),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            if (isRunning) {
                TextButton(onClick = onCancelDownload) {
                    Text(stringResource(R.string.remote_download_cancel))
                }
            } else {
                Button(
                    onClick = { onDownload(url) },
                    enabled = url.isNotBlank(),
                ) {
                    Text(stringResource(R.string.remote_download_start))
                }
            }
        },
        dismissButton = {
            if (!isRunning) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
            }
        },
    )
}

private fun pasteFromClipboard(context: Context): String? {
    val clipboard = context.getSystemService<ClipboardManager>() ?: return null
    val clip = clipboard.primaryClip ?: return null
    if (clip.itemCount == 0) return null
    val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim()
    return text?.takeIf { it.isNotEmpty() }
}

private fun progressText(context: Context, state: DownloadState.Running): String {
    val readStr = Formatter.formatShortFileSize(context, state.bytesRead)
    return if (state.totalBytes > 0) {
        val totalStr = Formatter.formatShortFileSize(context, state.totalBytes)
        context.getString(
            R.string.remote_download_progress,
            state.progressPercent ?: 0,
            readStr,
            totalStr,
        )
    } else {
        context.getString(R.string.remote_download_progress_unknown, readStr)
    }
}
