package app.pwhs.universalinstaller.presentation.composable

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import android.graphics.BitmapFactory
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.domain.model.SessionProgress
import java.io.File

@Composable
fun SessionCard(
    modifier: Modifier = Modifier,
    sessionData: SessionData,
    sessionProgress: SessionProgress?,
    onCancel: () -> Unit,
    onRetry: () -> Unit = {},
    /** Clear a failed session from the list. Without this a failed install had Retry and no way
     *  out at all — the card stayed for good (issue #93). */
    onDismiss: () -> Unit = {},
    onOpenShizuku: (() -> Unit)? = null,
    onFallbackInstall: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val errorText = sessionData.error.resolve(context)
    val hasError = errorText.isNotEmpty()
    val progress = sessionProgress?.let {
        if (it.progressMax > 0) it.currentProgress.toFloat() / it.progressMax.toFloat()
        else 0f
    } ?: 0f
    val isComplete = progress >= 1f && !hasError

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "sessionProgress"
    )

    val containerColor by animateColorAsState(
        targetValue = when {
            hasError -> MaterialTheme.colorScheme.errorContainer
            isComplete -> MaterialTheme.colorScheme.tertiaryContainer
            else -> MaterialTheme.colorScheme.surfaceContainerLow
        },
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "cardColor"
    )

    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.elevatedCardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large,
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            // Header row: icon + name + action button (non-error)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                AppIcon(sessionData, hasError, isComplete)

                Column(modifier = Modifier.weight(1f)) {
                    val displayName = sessionData.appName.ifEmpty { sessionData.name }
                    Text(
                        text = displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (sessionData.appName.isNotEmpty()) {
                        Text(
                            text = sessionData.name,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (!hasError) {
                        Text(
                            text = if (isComplete) stringResource(R.string.session_installed_success)
                            else stringResource(R.string.session_installing_progress, (progress * 100).toInt()),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (isComplete) MaterialTheme.colorScheme.tertiary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }

                // Cancel button (only when installing)
                if (!hasError && sessionData.isCancellable && !isComplete) {
                    FilledTonalButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Rounded.Cancel,
                            contentDescription = stringResource(R.string.cancel),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(stringResource(R.string.cancel), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            // Error section — full width below header
            if (hasError) {
                val parts = errorText.split("\n", limit = 2)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = parts[0],
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.error,
                )
                if (parts.size > 1) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = parts[1],
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (onOpenShizuku != null && app.pwhs.universalinstaller.presentation.install.InstallErrorHelper.shouldSuggestShizuku(errorText)) {
                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = onOpenShizuku,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Terminal,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.install_error_suggest_shizuku_action),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
                if (onFallbackInstall != null) {
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = onFallbackInstall,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                    ) {
                        Text(stringResource(R.string.dialog_failed_fallback_install, "Install via System Installer"))
                    }
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.onErrorContainer,
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.error.copy(alpha = 0.35f),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.session_dismiss),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                    Button(
                        onClick = onRetry,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error,
                            contentColor = MaterialTheme.colorScheme.onError,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            stringResource(R.string.session_retry),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            // Progress bar
            if (!isComplete && !hasError && progress > 0f) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            } else if (!isComplete && !hasError) {
                Spacer(Modifier.height(12.dp))
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(MaterialTheme.shapes.extraSmall),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
            }
        }
    }
}

@Composable
private fun AppIcon(
    sessionData: SessionData,
    hasError: Boolean,
    isComplete: Boolean,
) {
    val iconBitmap = sessionData.iconPath?.let { path ->
        try {
            val file = File(path)
            if (file.exists()) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
        } catch (_: Exception) { null }
    }

    if (iconBitmap != null) {
        Image(
            bitmap = iconBitmap,
            contentDescription = sessionData.appName,
            modifier = Modifier
                .size(40.dp)
                .clip(MaterialTheme.shapes.medium),
        )
    } else {
        Icon(
            imageVector = when {
                hasError -> Icons.Rounded.Error
                isComplete -> Icons.Rounded.CheckCircle
                else -> Icons.AutoMirrored.Rounded.InsertDriveFile
            },
            contentDescription = null,
            tint = when {
                hasError -> MaterialTheme.colorScheme.error
                isComplete -> MaterialTheme.colorScheme.tertiary
                else -> MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.size(40.dp)
        )
    }
}
