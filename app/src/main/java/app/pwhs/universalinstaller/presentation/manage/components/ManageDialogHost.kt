package app.pwhs.universalinstaller.presentation.manage

import android.text.format.Formatter
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstalledApp
import app.pwhs.universalinstaller.presentation.manage.BatchExtractState
import app.pwhs.universalinstaller.presentation.manage.ExtractState
import app.pwhs.universalinstaller.presentation.install.controller.SystemAppMethod

@Composable
internal fun ManageDialogHost(
    confirmUninstallTarget: InstalledApp?,
    onConfirmUninstall: (String) -> Unit,
    onDismissUninstall: () -> Unit,
    confirmClearDataTarget: InstalledApp?,
    onConfirmClearData: (String, String) -> Unit,
    onDismissClearData: () -> Unit,
    extractState: ExtractState,
    batchExtractState: BatchExtractState,
    showBatchConfirm: Boolean,
    onConfirmBatchUninstall: () -> Unit,
    onDismissBatchConfirm: () -> Unit,
    showBatchClearDataConfirm: Boolean,
    onConfirmBatchClearData: () -> Unit,
    onDismissBatchClearDataConfirm: () -> Unit,
    systemAppPrompt: SystemAppPrompt?,
    onConfirmSystemApp: (SystemAppMethod?) -> Unit,
    onDismissSystemApp: () -> Unit,
    selectedPackagesCount: Int,
) {
    val context = LocalContext.current

    confirmUninstallTarget?.let { target ->
        UninstallConfirmDialog(
            app = target,
            onConfirm = { onConfirmUninstall(target.packageName) },
            onDismiss = onDismissUninstall,
        )
    }

    confirmClearDataTarget?.let { target ->
        AlertDialog(
            onDismissRequest = onDismissClearData,
            icon = {
                Icon(
                    Icons.Rounded.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
            title = {
                Text(stringResource(R.string.manage_clear_data_confirm_title, target.appName))
            },
            text = { Text(stringResource(R.string.manage_clear_data_confirm_message)) },
            confirmButton = {
                TextButton(onClick = {
                    onConfirmClearData(target.packageName, target.appName)
                }) {
                    Text(
                        stringResource(R.string.manage_action_clear_data),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissClearData) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }

    if (extractState is ExtractState.Running) {
        val runningState = extractState
        AlertDialog(
            onDismissRequest = { /* Cannot dismiss, it's running */ },
            title = { Text(stringResource(R.string.extract_progress_title, runningState.appName)) },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (runningState.totalBytes > 0) {
                        val progress = runningState.bytesCopied.toFloat() / runningState.totalBytes.toFloat()
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "${Formatter.formatShortFileSize(context, runningState.bytesCopied)} / ${Formatter.formatShortFileSize(context, runningState.totalBytes)}",
                            style = MaterialTheme.typography.bodySmall
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {}
        )
    }

    (batchExtractState as? BatchExtractState.Running)?.let { batch ->
        AlertDialog(
            onDismissRequest = { /* running — not dismissable */ },
            title = {
                Text(stringResource(R.string.manage_batch_extract_title, batch.completed + 1, batch.total))
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                ) {
                    Text(
                        text = batch.currentName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    val progress = if (batch.totalBytes > 0) {
                        batch.bytesCopied.toFloat() / batch.totalBytes.toFloat()
                    } else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {},
        )
    }

    if (showBatchConfirm) {
        Dialog(onDismissRequest = onDismissBatchConfirm) {
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
                    Box(
                        modifier = Modifier
                            .size(56.dp)
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
                            modifier = Modifier.size(28.dp),
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    Text(
                        text = stringResource(R.string.uninstall_confirm_batch_title, selectedPackagesCount),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(Modifier.height(12.dp))

                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = stringResource(R.string.uninstall_confirm_batch_text, selectedPackagesCount),
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
                            onClick = onDismissBatchConfirm,
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
                            onClick = onConfirmBatchUninstall,
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

    if (showBatchClearDataConfirm) {
        AlertDialog(
            onDismissRequest = onDismissBatchClearDataConfirm,
            confirmButton = {
                TextButton(onClick = onConfirmBatchClearData) {
                    Text(
                        stringResource(R.string.manage_batch_action_clear_data),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = onDismissBatchClearDataConfirm) {
                    Text(stringResource(R.string.cancel))
                }
            },
            title = { Text(stringResource(R.string.manage_batch_clear_data_confirm_title, selectedPackagesCount)) },
            text = { Text(stringResource(R.string.manage_batch_clear_data_confirm_text)) },
            icon = {
                Icon(
                    imageVector = Icons.Rounded.DeleteSweep,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                )
            },
        )
    }

    systemAppPrompt?.let { prompt ->
        SystemAppDialog(
            prompt = prompt,
            onConfirm = onConfirmSystemApp,
            onDismiss = onDismissSystemApp,
        )
    }
}
