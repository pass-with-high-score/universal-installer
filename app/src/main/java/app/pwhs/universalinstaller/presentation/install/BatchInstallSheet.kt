package app.pwhs.universalinstaller.presentation.install

import android.net.Uri
import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.ui.state.ToggleableState
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.universalinstaller.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun BatchInstallSheet(
    state: BatchInstallState,
    onDismiss: () -> Unit,
    onToggleEntry: (Uri) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    if (state is BatchInstallState.Idle) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        when (state) {
            is BatchInstallState.Parsing -> ParsingBody(state)
            is BatchInstallState.Ready -> ReadyBody(
                state = state,
                onDismiss = onDismiss,
                onToggleEntry = onToggleEntry,
                onToggleAll = onToggleAll,
                onConfirm = onConfirm,
            )
            BatchInstallState.Idle -> Unit
        }
    }
}

@Composable
private fun ParsingBody(state: BatchInstallState.Parsing) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(16.dp))
        Text(
            text = stringResource(
                R.string.batch_install_parsing, state.processed, state.total
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = {
                if (state.total == 0) 0f else state.processed.toFloat() / state.total
            },
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun ReadyBody(
    state: BatchInstallState.Ready,
    onDismiss: () -> Unit,
    onToggleEntry: (Uri) -> Unit,
    onToggleAll: (Boolean) -> Unit,
    onConfirm: () -> Unit,
) {
    val context = LocalContext.current
    val selectable = state.entries.count { it.parseError == null }
    val selectedCount = state.entries.count { it.selected && it.parseError == null }
    val selectedBytes = state.entries
        .filter { it.selected && it.parseError == null }
        .sumOf { it.apkInfo.fileSizeBytes.coerceAtLeast(0L) }
    val failedCount = state.entries.count { it.parseError != null }
    val toggleState = when {
        selectable == 0 -> ToggleableState.Off
        selectedCount == 0 -> ToggleableState.Off
        selectedCount == selectable -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Header: big title + meta chips
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
        ) {
            Text(
                text = stringResource(R.string.batch_install_title),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = stringResource(
                    R.string.batch_install_summary,
                    state.entries.size,
                    Formatter.formatShortFileSize(context, selectedBytes),
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(12.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Tri-state checkbox reflects the real selection (off / partial / on) rather than
                // previewing the action label — which was reading as "reversed" to users.
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(enabled = selectable > 0) {
                            onToggleAll(toggleState != ToggleableState.On)
                        }
                        .padding(end = 8.dp),
                ) {
                    TriStateCheckbox(
                        state = toggleState,
                        onClick = null,
                        enabled = selectable > 0,
                    )
                    Text(
                        text = "$selectedCount / $selectable",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                if (failedCount > 0) {
                    AssistChip(
                        onClick = {},
                        enabled = false,
                        label = {
                            Text(
                                stringResource(R.string.batch_install_failed_chip, failedCount),
                                color = MaterialTheme.colorScheme.error,
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(AssistChipDefaults.IconSize),
                            )
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))

        // Scrollable entry list — capped so the sheet stays usable on small screens.
        LazyColumn(
            modifier = Modifier.heightIn(max = 420.dp),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(state.entries, key = { it.uri.toString() }) { entry ->
                EntryRow(
                    entry = entry,
                    onToggle = { onToggleEntry(entry.uri) },
                )
            }
        }

        // Bottom action bar — stays visible even when the list scrolls.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 0.dp,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(stringResource(R.string.cancel))
                }
                Button(
                    onClick = onConfirm,
                    enabled = selectedCount > 0,
                    modifier = Modifier.weight(2f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.InstallMobile,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = stringResource(
                            R.string.batch_install_action,
                            selectedCount,
                            Formatter.formatShortFileSize(context, selectedBytes),
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(
    entry: BatchApkEntry,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    val failed = entry.parseError != null
    val warning = entry.conflictLabel != null
    val rowColor = when {
        failed -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f)
        warning -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.35f)
        entry.selected -> MaterialTheme.colorScheme.surfaceContainerHigh
        else -> MaterialTheme.colorScheme.surfaceContainerLow
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !failed) { onToggle() },
        color = rowColor,
        shape = RoundedCornerShape(14.dp),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Leading check / error
            Box(
                modifier = Modifier.size(36.dp),
                contentAlignment = Alignment.Center,
            ) {
                if (failed) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                    )
                } else {
                    Icon(
                        imageVector = if (entry.selected) Icons.Rounded.CheckBox
                                       else Icons.Rounded.CheckBoxOutlineBlank,
                        contentDescription = null,
                        tint = if (entry.selected) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.width(10.dp))

            // App icon
            val icon = entry.apkInfo.icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                if (icon != null) {
                    Image(
                        bitmap = icon.toBitmap(96, 96).asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.size(44.dp),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Android,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                        modifier = Modifier.size(32.dp),
                    )
                }
            }
            Spacer(Modifier.width(12.dp))

            // Text block
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.apkInfo.appName.ifBlank {
                        entry.fileName.substringBeforeLast('.')
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = when {
                        failed -> entry.parseError
                        warning -> "${entry.conflictLabel} · ${entry.apkInfo.packageName}"
                        else -> entry.apkInfo.packageName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = when {
                        failed -> MaterialTheme.colorScheme.error
                        warning -> MaterialTheme.colorScheme.tertiary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                AnimatedVisibility(visible = !failed, enter = fadeIn(), exit = fadeOut()) {
                    val detail = buildList {
                        if (entry.apkInfo.versionName.isNotBlank())
                            add("v${entry.apkInfo.versionName}")
                        if (entry.apkInfo.fileSizeBytes > 0)
                            add(Formatter.formatShortFileSize(context, entry.apkInfo.fileSizeBytes))
                        if (entry.apkInfo.splitCount > 1)
                            add("${entry.apkInfo.splitCount} splits")
                    }.joinToString(" · ")
                    Text(
                        text = detail,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
