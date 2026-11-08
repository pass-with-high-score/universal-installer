package app.pwhs.universalinstaller.presentation.setting.backup

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreBackupBottomSheet(
    uiState: BackupUiState,
    onDismiss: () -> Unit,
    onDecryptPasswordChanged: (String) -> Unit,
    onSubmitPassword: () -> Unit,
    onToggleRestoreSettings: (Boolean) -> Unit,
    onToggleRestoreTokens: (Boolean) -> Unit,
    onToggleRestoreTrackedApps: (Boolean) -> Unit,
    onToggleRestoreUninstallLogs: (Boolean) -> Unit,
    onConfirmRestore: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showPassword by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 32.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Rounded.Restore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.backup_restore_sheet_title),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // State A: Encrypted - Ask for password
            if (uiState.pendingEnvelope != null) {
                Text(
                    text = stringResource(R.string.backup_restore_enter_password),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(12.dp))

                OutlinedTextField(
                    value = uiState.decryptPassword,
                    onValueChange = onDecryptPasswordChanged,
                    label = { Text(stringResource(R.string.backup_password_label)) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSubmitPassword() }),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = null,
                            )
                        }
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    isError = uiState.decryptError != null,
                    supportingText = uiState.decryptError?.let { { Text(it) } },
                    modifier = Modifier.fillMaxWidth(),
                )

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = onSubmitPassword,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(
                        text = stringResource(R.string.backup_restore_unlock),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            } else if (uiState.summaryInfo != null) {
                // State B: Decrypted / Plain - Summary & Selective Restore
                val summary = uiState.summaryInfo

                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.backup_restore_summary_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        val dateFormatted = DateFormat.getDateTimeInstance().format(Date(summary.exportedAt))
                        Text(
                            text = "Created: $dateFormatted ${summary.appVersion?.let { "• UI v$it" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 8.dp),
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.backup_restore_items_to_restore),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (summary.hasSettings) {
                    BackupCheckboxRow(
                        icon = Icons.Rounded.Settings,
                        title = stringResource(R.string.backup_include_settings),
                        subtitle = stringResource(R.string.backup_include_settings_sub),
                        checked = uiState.restoreSettings,
                        onCheckedChange = onToggleRestoreSettings,
                    )
                }

                if (summary.hasSourceTokens) {
                    BackupCheckboxRow(
                        icon = Icons.Rounded.Key,
                        title = stringResource(R.string.backup_include_tokens),
                        subtitle = stringResource(R.string.backup_include_tokens_sub),
                        checked = uiState.restoreTokens,
                        onCheckedChange = onToggleRestoreTokens,
                    )
                }

                if (summary.trackedAppsCount > 0) {
                    BackupCheckboxRow(
                        icon = Icons.Rounded.Apps,
                        title = stringResource(R.string.backup_include_tracked_apps),
                        subtitle = "${summary.trackedAppsCount} apps found in backup",
                        checked = uiState.restoreTrackedApps,
                        onCheckedChange = onToggleRestoreTrackedApps,
                    )
                }

                if (summary.uninstallLogsCount > 0) {
                    BackupCheckboxRow(
                        icon = Icons.Rounded.History,
                        title = stringResource(R.string.backup_include_uninstall_logs),
                        subtitle = "${summary.uninstallLogsCount} logs found in backup",
                        checked = uiState.restoreUninstallLogs,
                        onCheckedChange = onToggleRestoreUninstallLogs,
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                Button(
                    onClick = onConfirmRestore,
                    enabled = !uiState.isRestoring && (uiState.restoreSettings || uiState.restoreTokens || uiState.restoreTrackedApps || uiState.restoreUninstallLogs),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    if (uiState.isRestoring) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.5.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.backup_restore_action),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}
