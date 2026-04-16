package com.nqmgaming.universalinstaller.presentation.composable

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
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.InsertDriveFile
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import android.graphics.BitmapFactory
import com.nqmgaming.universalinstaller.domain.model.SessionData
import com.nqmgaming.universalinstaller.domain.model.SessionProgress
import java.io.File

@Composable
fun SessionCard(
    sessionData: SessionData,
    sessionProgress: SessionProgress?,
    onCancel: () -> Unit,
    onRetry: () -> Unit = {},
    modifier: Modifier = Modifier,
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
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // App icon or status icon
                AppIcon(sessionData, hasError, isComplete)

                // App name, file name, and status
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
                    Text(
                        text = when {
                            hasError -> errorText
                            isComplete -> "Installed successfully"
                            else -> "${(progress * 100).toInt()}% installing…"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = when {
                            hasError -> MaterialTheme.colorScheme.error
                            isComplete -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                // Cancel / Retry button
                if (hasError) {
                    FilledTonalButton(onClick = onRetry) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = "Retry",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Retry", style = MaterialTheme.typography.labelMedium)
                    }
                } else if (sessionData.isCancellable && !isComplete) {
                    FilledTonalButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Rounded.Cancel,
                            contentDescription = "Cancel",
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Cancel", style = MaterialTheme.typography.labelMedium)
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
                // Indeterminate progress
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
