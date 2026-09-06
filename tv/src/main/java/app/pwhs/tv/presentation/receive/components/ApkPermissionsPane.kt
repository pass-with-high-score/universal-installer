package app.pwhs.tv.presentation.receive.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Security
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.focusGroup
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.ExperimentalTvMaterial3Api
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import app.pwhs.tv.R

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun ApkPermissionsPane(
    name: String,
    permissions: List<String>,
    isInstalling: Boolean,
    icon: ImageBitmap?,
    onInstall: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { backFocus.requestFocus() }
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(28.dp)
    ) {
        // Header: App Icon (52dp) + Permissions title with count badge + App Name
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
            } else {
                Box(
                    Modifier
                        .size(52.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = stringResource(R.string.tv_details_section_permissions),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "${permissions.size}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(18.dp))

        // Permissions Container (Fixed height 220dp with TV remote D-pad scrollable LazyColumn)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
                .padding(10.dp)
        ) {
            if (permissions.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Rounded.Security,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = stringResource(R.string.tv_details_permissions_empty),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.tv_details_permissions_empty_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                val pairs = remember(permissions) { permissions.chunked(2) }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .focusRestorer()
                        .focusGroup(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(pairs.size) { index ->
                        val pair = pairs[index]
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            PermissionCard(
                                permission = pair[0],
                                modifier = Modifier.weight(1f)
                            )
                            if (pair.size > 1) {
                                PermissionCard(
                                    permission = pair[1],
                                    modifier = Modifier.weight(1f)
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(22.dp))

        // Action Buttons: Install & Back
        val btnShape = RoundedCornerShape(14.dp)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(
                onClick = onInstall,
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp)
                    .clip(btnShape),
                shape = ButtonDefaults.shape(btnShape),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            ) {
                Text(
                    text = if (isInstalling) stringResource(R.string.tv_receive_installing_plain)
                    else stringResource(R.string.tv_receive_install),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Button(
                onClick = onBack,
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(btnShape)
                    .focusRequester(backFocus),
                shape = ButtonDefaults.shape(btnShape),
                colors = ButtonDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedContainerColor = MaterialTheme.colorScheme.onSurface,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
                )
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.tv_details_action_back),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun PermissionCard(
    permission: String,
    modifier: Modifier = Modifier
) {
    val simpleName = permission.substringAfterLast('.')
    val isSensitive = isSensitivePermission(simpleName)
    val friendlyLabel = friendlyPermissionName(simpleName)
    val shape = RoundedCornerShape(10.dp)

    Surface(
        onClick = { /* Informative permission card */ },
        modifier = modifier
            .height(42.dp)
            .clip(shape),
        shape = ClickableSurfaceDefaults.shape(shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.02f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isSensitive) {
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)
            },
            focusedContainerColor = MaterialTheme.colorScheme.onSurface,
            contentColor = if (isSensitive) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            focusedContentColor = MaterialTheme.colorScheme.inverseOnSurface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = if (isSensitive) Icons.Rounded.Security else Icons.Rounded.CheckCircle,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = friendlyLabel,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSensitive) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private fun isSensitivePermission(name: String): Boolean = when {
    name.contains("LOCATION", ignoreCase = true) -> true
    name.contains("CAMERA", ignoreCase = true) -> true
    name.contains("AUDIO", ignoreCase = true) -> true
    name.contains("STORAGE", ignoreCase = true) -> true
    name.contains("INSTALL_PACKAGES", ignoreCase = true) -> true
    name.contains("SYSTEM_ALERT_WINDOW", ignoreCase = true) -> true
    name.contains("MANAGE_EXTERNAL", ignoreCase = true) -> true
    name.contains("SMS", ignoreCase = true) -> true
    name.contains("CONTACTS", ignoreCase = true) -> true
    name.contains("PHONE", ignoreCase = true) -> true
    else -> false
}

private fun friendlyPermissionName(raw: String): String = when (raw) {
    "INTERNET" -> "Internet"
    "ACCESS_NETWORK_STATE" -> "Network State"
    "ACCESS_WIFI_STATE" -> "Wi-Fi State"
    "CHANGE_WIFI_STATE" -> "Change Wi-Fi"
    "CAMERA" -> "Camera"
    "RECORD_AUDIO" -> "Microphone"
    "ACCESS_FINE_LOCATION" -> "Precise Location"
    "ACCESS_COARSE_LOCATION" -> "Approximate Location"
    "READ_EXTERNAL_STORAGE" -> "Read Storage"
    "WRITE_EXTERNAL_STORAGE" -> "Write Storage"
    "MANAGE_EXTERNAL_STORAGE" -> "Manage Storage"
    "REQUEST_INSTALL_PACKAGES" -> "Install Packages"
    "POST_NOTIFICATIONS" -> "Notifications"
    "WAKE_LOCK" -> "Wake Lock"
    "FOREGROUND_SERVICE" -> "Foreground Service"
    "RECEIVE_BOOT_COMPLETED" -> "Run at Startup"
    "SYSTEM_ALERT_WINDOW" -> "Display Over Apps"
    "VIBRATE" -> "Vibrate"
    "BLUETOOTH" -> "Bluetooth"
    "BLUETOOTH_ADMIN" -> "Bluetooth Admin"
    "BLUETOOTH_CONNECT" -> "Bluetooth Connect"
    "BLUETOOTH_SCAN" -> "Bluetooth Scan"
    "READ_PHONE_STATE" -> "Phone State"
    "MODIFY_AUDIO_SETTINGS" -> "Modify Audio"
    "QUERY_ALL_PACKAGES" -> "Query All Packages"
    else -> raw.replace('_', ' ').lowercase().split(' ').joinToString(" ") { word ->
        word.replaceFirstChar { it.uppercase() }
    }
}
