package app.pwhs.universalinstaller.presentation.manage

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.controller.SystemAppMethod
import app.pwhs.universalinstaller.util.AppIconData
import coil3.compose.AsyncImage
import coil3.request.ImageRequest


@Composable
internal fun SystemAppDialog(
    prompt: SystemAppPrompt,
    onConfirm: (SystemAppMethod?) -> Unit,
    onDismiss: () -> Unit,
) {
    when (prompt) {
        is SystemAppPrompt.Single -> SystemAppMethodDialog(
            title = stringResource(R.string.uninstall_system_dialog_title_single),
            warning = stringResource(R.string.uninstall_system_warning_single, prompt.appName),
            singlePackage = prompt.pkg,
            singleAppName = prompt.appName,
            allowSkip = false,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is SystemAppPrompt.Batch -> SystemAppMethodDialog(
            title = stringResource(R.string.uninstall_system_dialog_title_batch),
            warning = stringResource(
                R.string.uninstall_system_warning_batch,
                prompt.systemApps.size + prompt.userApps.size,
                prompt.userApps.size,
                prompt.systemApps.size,
            ),
            systemAppsPreview = prompt.systemApps,
            allowSkip = prompt.userApps.isNotEmpty(),
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
        is SystemAppPrompt.PrivilegedRequired -> SystemAppPrivilegedRequiredDialog(
            prompt = prompt,
            onConfirm = onConfirm,
            onDismiss = onDismiss,
        )
    }
}

/**
 * Modern shared dialog for Single and Batch system app removal.
 */
@Composable
internal fun SystemAppMethodDialog(
    title: String,
    warning: String,
    allowSkip: Boolean,
    onConfirm: (SystemAppMethod?) -> Unit,
    onDismiss: () -> Unit,
    singlePackage: String? = null,
    singleAppName: String? = null,
    systemAppsPreview: List<Pair<String, String>> = emptyList(),
) {
    var selection by remember { mutableStateOf<Choice>(Choice.Method(SystemAppMethod.UninstallForUser0)) }

    Dialog(onDismissRequest = onDismiss) {
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
                if (singlePackage != null) {
                    AsyncImage(
                        model = ImageRequest.Builder(LocalContext.current)
                            .data(AppIconData(singlePackage))
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(CircleShape),
                        error = rememberVectorPainter(Icons.Rounded.Android),
                        fallback = rememberVectorPainter(Icons.Rounded.Android),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = singleAppName ?: singlePackage,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    ) {
                        Text(
                            text = stringResource(R.string.uninstall_system_badge),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                        )
                    }
                } else {
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
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(12.dp))

                Text(
                    text = warning,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                if (systemAppsPreview.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    val shown = systemAppsPreview.take(4).joinToString(", ") { it.second }
                    val more = (systemAppsPreview.size - 4).coerceAtLeast(0)
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (more > 0) "$shown, +$more" else shown,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(10.dp),
                            textAlign = TextAlign.Center,
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.uninstall_system_method_header),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }

                Spacer(Modifier.height(8.dp))

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    SystemMethodOptionCard(
                        title = stringResource(R.string.uninstall_system_method_per_user_title),
                        subtitle = stringResource(R.string.uninstall_system_method_per_user_sub),
                        selected = selection == Choice.Method(SystemAppMethod.UninstallForUser0),
                        onClick = { selection = Choice.Method(SystemAppMethod.UninstallForUser0) },
                    )
                    SystemMethodOptionCard(
                        title = stringResource(R.string.uninstall_system_method_disable_title),
                        subtitle = stringResource(R.string.uninstall_system_method_disable_sub),
                        selected = selection == Choice.Method(SystemAppMethod.Disable),
                        onClick = { selection = Choice.Method(SystemAppMethod.Disable) },
                    )
                    if (allowSkip) {
                        SystemMethodOptionCard(
                            title = stringResource(R.string.uninstall_system_method_skip_title),
                            subtitle = stringResource(R.string.uninstall_system_method_skip_sub),
                            selected = selection == Choice.Skip,
                            onClick = { selection = Choice.Skip },
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onDismiss,
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
                        onClick = {
                            onConfirm(when (val c = selection) {
                                is Choice.Method -> c.method
                                Choice.Skip -> null
                            })
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f),
                            contentColor = MaterialTheme.colorScheme.error,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.uninstall_system_continue),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

private sealed interface Choice {
    data class Method(val method: SystemAppMethod) : Choice
    data object Skip : Choice
}

@Composable
internal fun SystemMethodOptionCard(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = if (selected) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        },
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 12.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHighest,
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                if (selected) {
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
}

@Composable
internal fun SystemAppPrivilegedRequiredDialog(
    prompt: SystemAppPrompt.PrivilegedRequired,
    onConfirm: (SystemAppMethod?) -> Unit,
    onDismiss: () -> Unit,
) {
    val hasRegular = prompt.userAppsAvailable.isNotEmpty()
    val body = when {
        prompt.systemApps.size == 1 && !hasRegular ->
            stringResource(R.string.uninstall_system_privileged_required_body_single, prompt.systemApps.first().second)
        hasRegular ->
            stringResource(
                R.string.uninstall_system_privileged_required_body_batch,
                prompt.systemApps.size,
                prompt.userAppsAvailable.size,
            )
        else ->
            stringResource(R.string.uninstall_system_privileged_required_body_batch_only_system, prompt.systemApps.size)
    }

    Dialog(onDismissRequest = onDismiss) {
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
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp),
                    )
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = stringResource(R.string.uninstall_system_privileged_required_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(10.dp))

                Text(
                    text = body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )

                Spacer(Modifier.height(20.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(
                        onClick = onDismiss,
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

                    if (hasRegular) {
                        Button(
                            onClick = { onConfirm(null) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp),
                            shape = RoundedCornerShape(24.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                contentColor = MaterialTheme.colorScheme.primary,
                            ),
                        ) {
                            Text(
                                text = stringResource(R.string.uninstall_system_proceed_user_only),
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }
    }
}
