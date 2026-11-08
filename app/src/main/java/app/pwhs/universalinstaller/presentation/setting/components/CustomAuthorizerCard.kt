package app.pwhs.universalinstaller.presentation.setting.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.util.CustomShellExecutor
import kotlinx.coroutines.launch

private data class AuthorizerPreset(
    val label: String,
    val command: String,
)

private val PRESETS = listOf(
    AuthorizerPreset("su -c {command}", "su -c {command}"),
    AuthorizerPreset("su", "su"),
    AuthorizerPreset("rish -c {command}", "rish -c {command}"),
    AuthorizerPreset("su 1000", "su 1000"),
    AuthorizerPreset("ksu -c {command}", "ksu -c {command}"),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomAuthorizerCard(
    command: String,
    onCommandChange: (String) -> Unit,
    onTestCommand: suspend (String) -> Result<String>,
    modifier: Modifier = Modifier,
) {
    var showSheet by rememberSaveable { mutableStateOf(false) }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clickable { showSheet = true },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(42.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.setting_custom_authorizer_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = command.ifBlank { PreferencesKeys.DEFAULT_CUSTOM_AUTHORIZER_COMMAND },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            FilledTonalButton(
                onClick = { showSheet = true },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Icon(
                    Icons.Rounded.Tune,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.setting_custom_authorizer_configure),
                    style = MaterialTheme.typography.labelMedium,
                )
            }
        }
    }

    if (showSheet) {
        CustomAuthorizerSheet(
            initialCommand = command,
            onSaveCommand = { newCmd ->
                onCommandChange(newCmd)
                showSheet = false
            },
            onTestCommand = onTestCommand,
            onDismiss = { showSheet = false },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun CustomAuthorizerSheet(
    initialCommand: String,
    onSaveCommand: (String) -> Unit,
    onTestCommand: suspend (String) -> Result<String>,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    var localCommand by rememberSaveable { mutableStateOf(initialCommand) }
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<Result<String>?>(null) }

    val validation = remember(localCommand) { CustomShellExecutor.validateCommand(localCommand) }
    val isError = validation is CustomShellExecutor.ValidationResult.Error
    val errorMessage = when (validation) {
        is CustomShellExecutor.ValidationResult.Error -> when (validation.reason) {
            CustomShellExecutor.ValidationErrorReason.BLANK ->
                stringResource(R.string.setting_custom_authorizer_err_blank)
            CustomShellExecutor.ValidationErrorReason.DANGEROUS_COMMAND ->
                stringResource(R.string.setting_custom_authorizer_err_dangerous, validation.detail)
            CustomShellExecutor.ValidationErrorReason.NOT_AUTHORIZER_BINARY ->
                stringResource(R.string.setting_custom_authorizer_err_not_authorizer, validation.detail)
            CustomShellExecutor.ValidationErrorReason.MISSING_PLACEHOLDER ->
                stringResource(R.string.setting_custom_authorizer_err_missing_placeholder, validation.detail)
        }
        else -> null
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(46.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Rounded.Terminal,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(24.dp),
                        )
                    }
                }
                Spacer(modifier = Modifier.width(14.dp))
                Column {
                    Text(
                        text = stringResource(R.string.setting_custom_authorizer_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                    )
                    Text(
                        text = stringResource(R.string.setting_custom_authorizer_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            // Quick Presets
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.setting_custom_authorizer_presets),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    PRESETS.forEach { preset ->
                        val isSelected = localCommand.trim() == preset.command
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                localCommand = preset.command
                                testResult = null
                            },
                            label = {
                                Text(
                                    text = preset.label,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            },
                        )
                    }
                }
            }

            // Input TextField
            OutlinedTextField(
                value = localCommand,
                onValueChange = {
                    localCommand = it
                    testResult = null
                },
                modifier = Modifier.fillMaxWidth(),
                isError = isError,
                label = { Text(stringResource(R.string.setting_custom_authorizer_cmd_label)) },
                placeholder = { Text(stringResource(R.string.setting_custom_authorizer_cmd_hint)) },
                leadingIcon = {
                    Icon(Icons.Rounded.Terminal, contentDescription = null)
                },
                trailingIcon = {
                    if (localCommand != PreferencesKeys.DEFAULT_CUSTOM_AUTHORIZER_COMMAND) {
                        IconButton(
                            onClick = {
                                localCommand = PreferencesKeys.DEFAULT_CUSTOM_AUTHORIZER_COMMAND
                                testResult = null
                            },
                        ) {
                            Icon(
                                Icons.AutoMirrored.Rounded.RotateLeft,
                                contentDescription = stringResource(R.string.setting_custom_authorizer_reset_default),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                supportingText = {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    } else {
                        Text(
                            text = stringResource(R.string.setting_custom_authorizer_cmd_desc),
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                },
                singleLine = false,
                maxLines = 3,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Ascii,
                ),
                textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            )

            // Test execution button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            isTesting = true
                            testResult = onTestCommand(localCommand)
                            isTesting = false
                        }
                    },
                    enabled = !isTesting && !isError && localCommand.isNotBlank(),
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    } else {
                        Icon(
                            Icons.Rounded.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                    Spacer(modifier = Modifier.size(ButtonDefaults.IconSpacing))
                    Text(stringResource(R.string.setting_custom_authorizer_test))
                }
            }

            // Test Result display
            AnimatedVisibility(visible = testResult != null) {
                testResult?.let { res ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (res.isSuccess) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                            } else {
                                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
                            },
                        ),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (res.isSuccess) {
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.setting_custom_authorizer_test_success,
                                        res.getOrNull().orEmpty(),
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            } else {
                                Icon(
                                    Icons.Rounded.ErrorOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = stringResource(
                                        R.string.setting_custom_authorizer_test_failed,
                                        res.exceptionOrNull()?.message.orEmpty(),
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(start = 10.dp),
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Bar (Cancel / Save)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onDismiss) {
                    Text(stringResource(R.string.cancel))
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { onSaveCommand(localCommand) },
                    enabled = !isError && localCommand.isNotBlank(),
                ) {
                    Text(stringResource(R.string.setting_custom_authorizer_save))
                }
            }
        }
    }
}
