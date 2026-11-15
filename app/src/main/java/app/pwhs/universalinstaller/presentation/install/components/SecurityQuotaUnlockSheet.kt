package app.pwhs.universalinstaller.presentation.install.components

import android.content.ClipboardManager
import android.content.Intent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.getSystemService
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.virustotal.VirusTotalSettingActivity
import kotlinx.coroutines.launch

private const val VT_API_KEY_GUIDE_URL = "https://www.virustotal.com/gui/my-apikey"

/**
 * BottomSheet displaying AppShield daily quota status and unlock options
 * (personal VirusTotal API key input or Universal Installer Pro upgrade).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityQuotaUnlockSheet(
    onDismiss: () -> Unit,
    onKeySaved: () -> Unit,
    onUpgradeProClick: (() -> Unit)?,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var isEnteringKey by remember { mutableStateOf(false) }

    val prefs by context.dataStore.data.collectAsState(initial = null)
    val savedKey = prefs?.get(SharedPrefsKeys.VIRUSTOTAL_API_KEY).orEmpty()
    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }
    val trimmed = keyInput.trim()
    val isValidLength = trimmed.isEmpty() || trimmed.length == 64

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (!isEnteringKey) {
                Text(
                    text = stringResource(R.string.quota_unlock_sheet_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                AppShieldDailyQuotaBadge()
                SecurityQuotaExhaustedSection(
                    onAddKey = { isEnteringKey = true },
                    onGetKey = { uriHandler.openUri(VT_API_KEY_GUIDE_URL) },
                    onUpgradeProClick = onUpgradeProClick?.let { upgrade ->
                        {
                            onDismiss()
                            upgrade()
                        }
                    },
                )
            } else {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    IconButton(onClick = { isEnteringKey = false }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.logs_back_cd),
                        )
                    }
                    Text(
                        text = stringResource(R.string.quota_unlock_vt_key_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = stringResource(R.string.quota_unlock_vt_key_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text(stringResource(R.string.quota_unlock_key_label)) },
                    singleLine = true,
                    isError = !isValidLength,
                    supportingText = if (!isValidLength) {
                        {
                            Text(
                                stringResource(
                                    R.string.quota_unlock_key_length_error,
                                    trimmed.length,
                                )
                            )
                        }
                    } else null,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService<ClipboardManager>()
                                val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!text.isNullOrBlank()) {
                                    keyInput = text
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = stringResource(R.string.source_download_paste),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { uriHandler.openUri(VT_API_KEY_GUIDE_URL) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.quota_unlock_get_free_key),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(
                        onClick = {
                            onDismiss()
                            context.startActivity(Intent(context, VirusTotalSettingActivity::class.java))
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.quota_unlock_advanced_settings),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Button(
                    enabled = trimmed.length == 64,
                    onClick = {
                        scope.launch {
                            context.dataStore.edit { p ->
                                p[SharedPrefsKeys.VIRUSTOTAL_API_KEY] = trimmed
                                p[SharedPrefsKeys.SECURITY_AUTO_FALLBACK] = true
                            }
                            onKeySaved()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.quota_unlock_btn_save_and_scan))
                }
            }
        }
    }
}

/**
 * Quick inline dialog for pasting/saving a personal VirusTotal API key without leaving the install sheet.
 */
@Composable
fun VirusTotalQuickKeyDialog(
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val uriHandler = LocalUriHandler.current
    val scope = rememberCoroutineScope()
    val prefs by context.dataStore.data.collectAsState(initial = null)
    val savedKey = prefs?.get(SharedPrefsKeys.VIRUSTOTAL_API_KEY).orEmpty()
    var keyInput by remember(savedKey) { mutableStateOf(savedKey) }
    val trimmed = keyInput.trim()
    val isValidLength = trimmed.isEmpty() || trimmed.length == 64

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Rounded.Key,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        },
        title = {
            Text(
                text = stringResource(R.string.quota_unlock_vt_key_title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.quota_unlock_vt_key_desc),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = keyInput,
                    onValueChange = { keyInput = it },
                    label = { Text(stringResource(R.string.quota_unlock_key_label)) },
                    singleLine = true,
                    isError = !isValidLength,
                    supportingText = if (!isValidLength) {
                        {
                            Text(
                                stringResource(
                                    R.string.quota_unlock_key_length_error,
                                    trimmed.length,
                                )
                            )
                        }
                    } else null,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService<ClipboardManager>()
                                val text = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                                if (!text.isNullOrBlank()) {
                                    keyInput = text
                                }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ContentPaste,
                                contentDescription = stringResource(R.string.source_download_paste),
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    TextButton(
                        onClick = { uriHandler.openUri(VT_API_KEY_GUIDE_URL) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.quota_unlock_get_free_key),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    TextButton(
                        onClick = {
                            onDismiss()
                            context.startActivity(Intent(context, VirusTotalSettingActivity::class.java))
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = stringResource(R.string.quota_unlock_advanced_settings),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = trimmed.length == 64,
                onClick = {
                    scope.launch {
                        context.dataStore.edit { p ->
                            p[SharedPrefsKeys.VIRUSTOTAL_API_KEY] = trimmed
                            p[SharedPrefsKeys.SECURITY_AUTO_FALLBACK] = true
                        }
                        onSaved()
                    }
                },
            ) {
                Text(stringResource(R.string.quota_unlock_btn_save_and_scan))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(android.R.string.cancel))
            }
        },
    )
}
