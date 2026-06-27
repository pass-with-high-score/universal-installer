package app.pwhs.universalinstaller.presentation.install

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Error
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.data.local.InstallHistoryEntity
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun HistoryCard(
    entry: InstallHistoryEntity,
    modifier: Modifier = Modifier,
) {
    val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    var expanded by rememberSaveable(entry.id) { mutableStateOf(false) }
    val canExpand = !entry.success && !entry.errorMessage.isNullOrBlank()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable(enabled = canExpand) { expanded = !expanded },
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        ),
        // Flat card — no shadow. A thin outline separates each item instead of elevation,
        // since the flat container colour blends into the screen background.
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Icon
                val iconBitmap = entry.iconPath?.let { path ->
                    try {
                        val file = File(path)
                        if (file.exists()) BitmapFactory.decodeFile(path)?.asImageBitmap() else null
                    } catch (_: Exception) { null }
                }

                if (iconBitmap != null) {
                    Image(
                        bitmap = iconBitmap,
                        contentDescription = entry.appName,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(MaterialTheme.shapes.medium),
                    )
                } else {
                    Icon(
                        imageVector = if (entry.success) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                        contentDescription = null,
                        tint = if (entry.success) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(40.dp),
                    )
                }

                // Info
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = entry.appName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = entry.fileName,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = dateFormat.format(Date(entry.installedAt)),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }

                // Status badge
                Icon(
                    imageVector = if (entry.success) Icons.Rounded.CheckCircle else Icons.Rounded.Error,
                    contentDescription = if (entry.success) stringResource(R.string.status_success) else stringResource(R.string.status_failed),
                    tint = if (entry.success) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(20.dp),
                )
            }

            if (canExpand) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
                ErrorBlock(
                    message = entry.errorMessage.orEmpty(),
                    expanded = expanded,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(entry.errorMessage.orEmpty()))
                        Toast.makeText(
                            context,
                            context.getString(R.string.dialog_failed_copied),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                )
            }
        }
    }
}

@Composable
private fun ErrorBlock(
    message: String,
    expanded: Boolean,
    onCopy: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = stringResource(R.string.logs_reason),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.error,
            )
            Spacer(Modifier.weight(1f))
            IconButton(
                onClick = onCopy,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.ContentCopy,
                    contentDescription = "Copy Error Log",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
            }
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (expanded) stringResource(R.string.logs_collapse_cd) else stringResource(R.string.logs_expand_cd),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .size(18.dp)
                    .rotate(if (expanded) 180f else 0f),
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f))
                .padding(horizontal = 10.dp, vertical = 8.dp),
        ) {
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
