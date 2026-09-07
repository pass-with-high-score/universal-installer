package app.pwhs.universalinstaller.presentation.install.components

import android.content.Context
import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.core.ui.ApkFileIconData
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.FoundPackageFile
import app.pwhs.universalinstaller.presentation.install.InstallState
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import java.io.File
import java.text.DateFormat
import java.util.Date

@Composable
internal fun FoundRow(
    file: FoundPackageFile,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val bg = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh
    else MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = bg,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onToggle),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.size(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                SubcomposeAsyncImage(
                    model = coil3.request.ImageRequest.Builder(context)
                        .data(ApkFileIconData(file.path))
                        .build(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    error = {
                        Icon(
                            imageVector = Icons.Rounded.Android,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                    },
                    success = { SubcomposeAsyncImageContent() }
                )
            }
            Spacer(Modifier.size(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val sizeStr = Formatter.formatShortFileSize(context, file.sizeBytes)
                val dateStr = DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(file.modifiedMillis))
                Text(
                    text = "${file.extension.uppercase()} · $sizeStr · $dateStr",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val displaySourcePath = file.originalPath ?: file.path
                val sourceDir = remember(displaySourcePath) { formatSourceDirectory(displaySourcePath, context) }
                if (sourceDir.isNotBlank()) {
                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Folder,
                            contentDescription = null,
                            modifier = Modifier.size(13.dp),
                            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        )
                        Text(
                            text = sourceDir,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                FoundFileChips(file)
            }
            Spacer(Modifier.size(10.dp))
            Icon(
                imageVector = if (selected) Icons.Rounded.CheckBox
                else Icons.Rounded.CheckBoxOutlineBlank,
                contentDescription = null,
                tint = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

internal fun formatSourceDirectory(path: String, context: Context): String {
    val file = File(path)
    val parent = file.parentFile ?: return ""
    val parentPath = parent.absolutePath

    val storagePrefixes = listOf(
        "/storage/emulated/0/",
        "/sdcard/",
        "/storage/self/primary/"
    )
    for (prefix in storagePrefixes) {
        if (parentPath.startsWith(prefix, ignoreCase = true)) {
            val rel = parentPath.removePrefix(prefix).trim('/')
            return if (rel.isEmpty()) context.getString(R.string.source_folder_root) else rel
        }
    }

    val sdCardMatch = Regex("^/storage/([^/]+)/(.*)").find(parentPath)
    if (sdCardMatch != null) {
        val rel = sdCardMatch.groupValues[2].trim('/')
        val sdLabel = context.getString(R.string.source_folder_sdcard)
        return if (rel.isEmpty()) sdLabel else "$sdLabel · $rel"
    }

    return parent.name.ifBlank { parentPath }
}

@Composable
private fun FoundFileChips(file: FoundPackageFile) {
    val isArchive = file.extension in setOf("xapk", "apks", "apkm")
    val showStateChip = file.installState != InstallState.Unknown
    val showAaChip = file.isAndroidAutoSupported

    if (!isArchive && !showStateChip && !showAaChip) return

    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showAaChip) {
            StatusChip(
                label = stringResource(R.string.aa_compatibility_ok),
                container = MaterialTheme.colorScheme.primaryContainer,
                content = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
        if (isArchive) {
            StatusChip(
                label = stringResource(R.string.found_chip_split),
                container = MaterialTheme.colorScheme.tertiaryContainer,
                content = MaterialTheme.colorScheme.onTertiaryContainer,
            )
        }
        if (showStateChip) {
            val (labelRes, container, contentColor) = when (file.installState) {
                InstallState.NotInstalled -> Triple(
                    R.string.found_chip_new,
                    MaterialTheme.colorScheme.secondaryContainer,
                    MaterialTheme.colorScheme.onSecondaryContainer,
                )
                InstallState.Newer -> Triple(
                    R.string.found_chip_update,
                    MaterialTheme.colorScheme.primaryContainer,
                    MaterialTheme.colorScheme.onPrimaryContainer,
                )
                InstallState.SameVersion -> Triple(
                    R.string.found_chip_installed,
                    MaterialTheme.colorScheme.surfaceContainerHighest,
                    MaterialTheme.colorScheme.onSurfaceVariant,
                )
                InstallState.Older -> Triple(
                    R.string.found_chip_older,
                    MaterialTheme.colorScheme.errorContainer,
                    MaterialTheme.colorScheme.onErrorContainer,
                )
                InstallState.Unknown -> return@Row
            }
            StatusChip(
                label = stringResource(labelRes),
                container = container,
                content = contentColor,
            )
        }
    }
}
