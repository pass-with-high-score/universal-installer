package app.pwhs.universalinstaller.presentation.manage.uninstall

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.util.AppIconData
import coil3.compose.AsyncImage
import coil3.request.ImageRequest

@Composable
fun DialogUninstallSheet(
    appName: String,
    packageName: String,
    versionName: String,
    isSystemApp: Boolean,
    defaultKeepData: Boolean,
    defaultAllUsers: Boolean,
    defaultDeleteSystemApp: Boolean = false,
    isPrivileged: Boolean,
    onCancel: () -> Unit,
    onConfirmUninstall: (keepData: Boolean, allUsers: Boolean, deleteSystemApp: Boolean) -> Unit,
) {
    val context = LocalContext.current
    var keepData by remember { mutableStateOf(defaultKeepData) }
    var allUsers by remember { mutableStateOf(defaultAllUsers) }
    var deleteSystemApp by remember { mutableStateOf(defaultDeleteSystemApp || isSystemApp) }
    var isUninstalling by remember { mutableStateOf(false) }

    var sheetVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { sheetVisible = true }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.5f))
            .pointerInput(Unit) {
                detectTapGestures { onCancel() }
            },
        contentAlignment = Alignment.BottomCenter,
    ) {
        AnimatedVisibility(
            visible = sheetVisible,
            enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
            exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        detectTapGestures { /* consume tap inside sheet */ }
                    },
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 6.dp,
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    // Top drag handle
                    Box(
                        modifier = Modifier
                            .padding(top = 12.dp, bottom = 4.dp)
                            .size(width = 36.dp, height = 4.dp)
                            .background(
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                                shape = RoundedCornerShape(2.dp),
                            ),
                    )

                    // Header: Close icon on left, title in center
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        IconButton(onClick = onCancel, enabled = !isUninstalling) {
                            Icon(
                                imageVector = Icons.Rounded.Close,
                                contentDescription = stringResource(R.string.cancel),
                                tint = MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        Text(
                            text = stringResource(R.string.uninstall_app_title),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                        Spacer(modifier = Modifier.size(48.dp))
                    }

                    Spacer(Modifier.height(12.dp))

                    // App icon
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(AppIconData(packageName))
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(72.dp)
                            .clip(CircleShape),
                        error = rememberVectorPainter(Icons.Rounded.Android),
                        fallback = rememberVectorPainter(Icons.Rounded.Android),
                    )

                    Spacer(Modifier.height(14.dp))

                    // App Name
                    Text(
                        text = appName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )

                    Spacer(Modifier.height(4.dp))

                    // Package Name
                    Text(
                        text = if (versionName.isNotBlank()) "$packageName • v$versionName" else packageName,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )

                    Spacer(Modifier.height(20.dp))

                    // Options Container Card
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    ) {
                        Column(modifier = Modifier.padding(vertical = 4.dp)) {
                            UninstallOptionRow(
                                title = stringResource(R.string.uninstall_option_keep_data),
                                subtitle = stringResource(R.string.uninstall_option_keep_data_desc),
                                checked = keepData,
                                enabled = isPrivileged && !isUninstalling,
                                onToggle = { keepData = !keepData },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            )
                            UninstallOptionRow(
                                title = stringResource(R.string.uninstall_option_all_users),
                                subtitle = stringResource(R.string.uninstall_option_all_users_desc),
                                checked = allUsers,
                                enabled = isPrivileged && !isUninstalling,
                                onToggle = { allUsers = !allUsers },
                            )
                            HorizontalDivider(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f),
                            )
                            UninstallOptionRow(
                                title = stringResource(R.string.uninstall_option_delete_system_app),
                                subtitle = stringResource(R.string.uninstall_option_delete_system_app_desc),
                                checked = deleteSystemApp,
                                enabled = isPrivileged && !isUninstalling,
                                onToggle = { deleteSystemApp = !deleteSystemApp },
                            )
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Action Buttons (Cancel / Uninstall)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .padding(bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Button(
                            onClick = onCancel,
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            enabled = !isUninstalling,
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
                                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.cancel),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }

                        Button(
                            onClick = {
                                if (!isUninstalling) {
                                    isUninstalling = true
                                    onConfirmUninstall(keepData, allUsers, deleteSystemApp)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .height(52.dp),
                            enabled = !isUninstalling,
                            shape = RoundedCornerShape(26.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f),
                                contentColor = MaterialTheme.colorScheme.error,
                            ),
                        ) {
                            if (isUninstalling) {
                                CircularProgressIndicator(
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(22.dp),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            } else {
                                Text(
                                    text = stringResource(R.string.uninstall),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UninstallOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(enabled = enabled, onClick = onToggle)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 16.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                },
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                },
            )
        }

        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = when {
                        !enabled -> MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.6f)
                        checked -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surfaceContainerHighest
                    },
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (checked && enabled) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
