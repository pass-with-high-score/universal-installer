package app.pwhs.universalinstaller.presentation.sync

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.SyncOptions

/**
 * Modern Hero Card showing Wi-Fi Server status, IP URL, QR button, and Settings trigger.
 */
@Composable
fun SyncServerHeroCard(
    state: SyncState,
    serverUrl: String?,
    syncOptions: SyncOptions,
    modifier: Modifier = Modifier,
    onToggle: (Boolean) -> Unit,
    onShowQr: () -> Unit,
    onOpenSettings: () -> Unit,
    subtitleOverride: String? = null,
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val isRunning = state == SyncState.RUNNING

    val statusDotColor by animateColorAsState(
        targetValue = when (state) {
            SyncState.RUNNING -> MaterialTheme.colorScheme.primary
            SyncState.STARTING -> MaterialTheme.colorScheme.tertiary
            SyncState.ERROR -> MaterialTheme.colorScheme.error
            SyncState.STOPPED -> MaterialTheme.colorScheme.outline
        },
        label = "statusDotColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Surface(
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Status Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .alpha(if (isRunning) pulseAlpha else 1f)
                            .background(statusDotColor, CircleShape)
                    )
                    Text(
                        text = when (state) {
                            SyncState.RUNNING -> stringResource(R.string.sync_server_status_online)
                            SyncState.STARTING -> stringResource(R.string.sync_server_starting)
                            SyncState.ERROR -> stringResource(R.string.sync_server_error)
                            SyncState.STOPPED -> stringResource(R.string.sync_server_status_offline)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Switch(
                    checked = isRunning || state == SyncState.STARTING,
                    onCheckedChange = onToggle,
                )
            }

            // Subtitle
            Text(
                text = subtitleOverride ?: if (isRunning) {
                    stringResource(R.string.sync_web_access_desc)
                } else {
                    stringResource(R.string.sync_empty_state_message)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // When Server is Running: Display Web URL and Action Chips
            AnimatedVisibility(
                visible = isRunning && serverUrl != null,
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerHigh,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f),
                            ) {
                                Icon(
                                    Icons.Rounded.Language,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    text = serverUrl ?: "",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                IconButton(
                                    onClick = {
                                        serverUrl?.let { url ->
                                            clipboardManager.setText(AnnotatedString(url))
                                            Toast.makeText(
                                                context,
                                                context.getString(R.string.sync_server_url_copied),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.ContentCopy,
                                        contentDescription = stringResource(R.string.sync_server_copy_url),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }

                                IconButton(
                                    onClick = onShowQr,
                                    modifier = Modifier.size(36.dp),
                                ) {
                                    Icon(
                                        Icons.Rounded.QrCode,
                                        contentDescription = stringResource(R.string.sync_server_qr_btn),
                                        modifier = Modifier.size(18.dp),
                                    )
                                }
                            }
                        }
                    }

                    // Metadata Row: Port, PIN, Settings Trigger
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            SuggestionChip(
                                onClick = onOpenSettings,
                                label = { Text("Port: ${syncOptions.serverPort}") },
                            )
                            if (syncOptions.requirePin && syncOptions.pinCode.isNotEmpty()) {
                                SuggestionChip(
                                    onClick = onOpenSettings,
                                    label = { Text("PIN: ${syncOptions.pinCode}") },
                                )
                            }
                        }

                        TextButton(
                            onClick = onOpenSettings,
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) {
                            Icon(
                                Icons.Rounded.Tune,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.sync_server_settings_btn),
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}
