package app.pwhs.tv.presentation.settings.components

import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Save
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.OutlinedButton
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import app.pwhs.core.presentation.components.VirusTotalDailyQuotaBadge
import app.pwhs.core.presentation.components.VirusTotalUnlimitedOptionsSection
import app.pwhs.tv.R
import app.pwhs.tv.ui.components.QrCode

private const val VT_API_KEY_URL = "https://www.virustotal.com/gui/my-apikey"

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvVirusTotalKeyDialog(
    initialKey: String,
    onSaveKey: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    var keyText by remember(initialKey) { mutableStateOf(initialKey) }
    var isInputFocused by remember { mutableStateOf(false) }

    val pasteFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        pasteFocusRequester.requestFocus()
    }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            modifier = Modifier.widthIn(min = 600.dp, max = 740.dp),
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(28.dp),
            ) {
                // Title
                Text(
                    text = stringResource(R.string.tv_settings_virustotal_dialog_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )

                Spacer(Modifier.height(14.dp))

                VirusTotalDailyQuotaBadge()

                Spacer(Modifier.height(12.dp))

                // Content row: QR Code on left, instructions & field on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(24.dp),
                ) {
                    // QR Code block
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.width(140.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(130.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color.White)
                                .padding(6.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            QrCode(
                                data = VT_API_KEY_URL,
                                modifier = Modifier.size(118.dp),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = stringResource(R.string.tv_settings_scan_qr_hint),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                        )
                    }

                    // Key input and details
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.tv_settings_virustotal_dialog_desc),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )

                        Spacer(Modifier.height(12.dp))

                        // TextField
                        BasicTextField(
                            value = keyText,
                            onValueChange = { keyText = it.trim() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                                .onFocusChanged { isInputFocused = it.isFocused }
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                .border(
                                    width = if (isInputFocused) 2.dp else 1.dp,
                                    color = if (isInputFocused) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.border,
                                    shape = RoundedCornerShape(10.dp),
                                )
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(
                                onDone = {
                                    onSaveKey(keyText.trim())
                                    onDismiss()
                                }
                            ),
                            decorationBox = { innerTextField ->
                                if (keyText.isEmpty()) {
                                    Text(
                                        text = stringResource(R.string.tv_settings_virustotal_dialog_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                    )
                                }
                                innerTextField()
                            },
                        )
                    }
                }

                Spacer(Modifier.height(24.dp))

                // Action Buttons Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    // Paste from clipboard
                    Button(
                        onClick = {
                            val clipMgr = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                            val clip = clipMgr?.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
                            if (!clip.isNullOrBlank()) {
                                keyText = clip
                                Toast.makeText(context, context.getString(app.pwhs.core.R.string.onboarding_virustotal_key_saved), Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .focusRequester(pasteFocusRequester),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.ContentPaste, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.tv_settings_virustotal_dialog_paste))
                        }
                    }

                    // Save
                    Button(
                        onClick = {
                            onSaveKey(keyText.trim())
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.colors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        ),
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.tv_settings_virustotal_dialog_save), fontWeight = FontWeight.Bold)
                        }
                    }

                    // Clear
                    if (keyText.isNotEmpty()) {
                        OutlinedButton(
                            onClick = {
                                keyText = ""
                                onSaveKey("")
                            },
                            modifier = Modifier.weight(0.8f),
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text(stringResource(R.string.tv_settings_virustotal_dialog_clear))
                            }
                        }
                    }

                    // Close
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(0.8f),
                    ) {
                        Text(stringResource(R.string.tv_settings_virustotal_dialog_close))
                    }
                }
            }
        }
    }
}
