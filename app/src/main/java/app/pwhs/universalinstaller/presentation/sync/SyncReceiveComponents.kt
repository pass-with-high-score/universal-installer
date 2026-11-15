package app.pwhs.universalinstaller.presentation.sync

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.ui.ApkFileIconData
import app.pwhs.universalinstaller.R
import coil3.compose.SubcomposeAsyncImage
import coil3.request.ImageRequest
import java.io.File

/**
 * Modern item row for shared or received APK packages.
 */
@Composable
fun SyncPackageItemCard(
    file: File,
    modifier: Modifier = Modifier,
    isReceived: Boolean = false,
    onInstall: (() -> Unit)? = null,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                contentAlignment = Alignment.Center,
            ) {
                SubcomposeAsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(ApkFileIconData(file.absolutePath))
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().padding(4.dp),
                    error = {
                        Icon(
                            imageVector = Icons.Rounded.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp),
                        )
                    },
                    loading = {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    },
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = formatPackageSize(context, file.length()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = file.extension.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            if (isReceived && onInstall != null) {
                FilledTonalButton(
                    onClick = onInstall,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Icon(
                        Icons.Rounded.InstallMobile,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.sync_receive_btn_install),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(4.dp))
            }

            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.Outlined.Delete,
                    contentDescription = stringResource(R.string.sync_delete_cd),
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}

/**
 * 3-step illustrated visual guide for sharing files over Wi-Fi.
 */
@Composable
fun SyncSendHowItWorksCard(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.sync_send_how_it_works),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            SyncStepRow(
                number = "1",
                icon = Icons.Rounded.AddCircleOutline,
                text = stringResource(R.string.sync_send_step1),
            )
            SyncStepRow(
                number = "2",
                icon = Icons.Rounded.Wifi,
                text = stringResource(R.string.sync_send_step2),
            )
            SyncStepRow(
                number = "3",
                icon = Icons.Rounded.Devices,
                text = stringResource(R.string.sync_send_step3),
            )
        }
    }
}

/**
 * 3-step illustrated visual guide for receiving files over Wi-Fi.
 */
@Composable
fun SyncReceiveHowItWorksCard(modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = stringResource(R.string.sync_receive_how_it_works),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )

            SyncStepRow(
                number = "1",
                icon = Icons.Rounded.Wifi,
                text = stringResource(R.string.sync_receive_step1),
            )
            SyncStepRow(
                number = "2",
                icon = Icons.Rounded.OpenInBrowser,
                text = stringResource(R.string.sync_receive_step2),
            )
            SyncStepRow(
                number = "3",
                icon = Icons.Rounded.CloudUpload,
                text = stringResource(R.string.sync_receive_step3),
            )
        }
    }
}

@Composable
private fun SyncStepRow(
    number: String,
    icon: ImageVector,
    text: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(28.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )

        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}

/**
 * Clean empty state card with icon, title, message, and optional action button.
 */
@Composable
fun SyncEmptyStateCard(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionButtonText: String? = null,
    onActionClick: (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier.size(56.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(28.dp),
                    )
                }
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )

            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            if (actionButtonText != null && onActionClick != null) {
                Spacer(Modifier.height(4.dp))
                Button(
                    onClick = onActionClick,
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(actionButtonText)
                }
            }
        }
    }
}

fun formatPackageSize(context: Context, bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0
    return when {
        gb >= 1.0 -> context.getString(R.string.file_size_gb, gb)
        mb >= 1.0 -> context.getString(R.string.file_size_mb, mb)
        kb >= 1.0 -> context.getString(R.string.file_size_kb, kb)
        else -> context.getString(R.string.file_size_b, bytes)
    }
}
