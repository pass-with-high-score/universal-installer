package app.pwhs.universalinstaller.presentation.install

import android.text.format.Formatter
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
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun FoundApksSheet(
    scanState: ScanState,
    onDismiss: () -> Unit,
    onGrantPermission: () -> Unit,
    onRescan: () -> Unit,
    onPick: (FoundPackageFile) -> Unit,
    onPickMany: (List<FoundPackageFile>) -> Unit,
    onDeleteMany: (List<FoundPackageFile>) -> Unit,
) {
    if (scanState is ScanState.Idle) return
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = MaterialTheme.shapes.extraLarge,
    ) {
        // Intentionally no horizontal padding on the outer Column — each body applies its
        // own so the Ready state's bottom bar can span edge-to-edge.
        Column(modifier = Modifier.fillMaxWidth()) {
            SheetHeader(scanState = scanState, onRescan = onRescan)
            Spacer(Modifier.height(16.dp))
            when (scanState) {
                is ScanState.PermissionNeeded -> PermissionBody(onGrant = onGrantPermission)
                is ScanState.Scanning -> ScanningBody()
                is ScanState.Ready -> ResultsBody(
                    files = scanState.files,
                    onPickOne = onPick,
                    onPickMany = onPickMany,
                    onDeleteMany = onDeleteMany,
                )
                ScanState.Idle -> Unit
            }
        }
    }
}

@Composable
private fun SheetHeader(scanState: ScanState, onRescan: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.find_auto_sheet_title),
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            if (scanState is ScanState.Ready && scanState.files.isNotEmpty()) {
                Text(
                    text = stringResource(R.string.find_auto_count, scanState.files.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (scanState is ScanState.Ready) {
            IconButton(onClick = onRescan) {
                Icon(
                    imageVector = Icons.Rounded.Refresh,
                    contentDescription = stringResource(R.string.find_auto_rescan),
                )
            }
        }
    }
}

@Composable
private fun PermissionBody(onGrant: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            imageVector = Icons.Rounded.FolderOff,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(44.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.find_auto_permission_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = stringResource(R.string.find_auto_permission_body),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = onGrant,
            shape = RoundedCornerShape(16.dp),
        ) {
            Text(stringResource(R.string.find_auto_grant))
        }
    }
}

@Composable
private fun ScanningBody() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator()
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.find_auto_scanning),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ResultsBody(
    files: List<FoundPackageFile>,
    onPickOne: (FoundPackageFile) -> Unit,
    onPickMany: (List<FoundPackageFile>) -> Unit,
    onDeleteMany: (List<FoundPackageFile>) -> Unit,
) {
    val context = LocalContext.current
    if (files.isEmpty()) {
        Text(
            text = stringResource(R.string.find_auto_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 32.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
        return
    }

    // Selection lives inside the sheet — no need to hoist to the VM since it's transient
    // UI state that resets every time the sheet opens.
    var selected by remember(files) { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    val selectedFiles = files.filter { it.path in selected }
    val selectedBytes = selectedFiles.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    val hasSelection = selected.isNotEmpty()
    val toggleState = when (selected.size) {
        0 -> ToggleableState.Off
        files.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // Select-all row: only the TriStateCheckbox is clickable (per request — the whole
        // Row used to be a click target, which felt unintentional). The "N / total" label is
        // plain text beside it.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TriStateCheckbox(
                state = toggleState,
                onClick = {
                    selected = if (toggleState == ToggleableState.On) emptySet()
                               else files.map { it.path }.toSet()
                },
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "${selected.size} / ${files.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))

        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
        ) {
            items(files, key = { it.path }) { file ->
                FoundRow(
                    file = file,
                    selected = file.path in selected,
                    onToggle = {
                        selected = if (file.path in selected) selected - file.path
                                   else selected + file.path
                    },
                )
            }
        }

        // Edge-to-edge action bar: parent Column has no horizontal padding, so this Surface
        // fills the full sheet width. Inner Row adds its own padding.
        Surface(
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.surfaceContainer,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (hasSelection) {
                            "${selected.size} · ${Formatter.formatShortFileSize(context, selectedBytes)}"
                        } else {
                            stringResource(R.string.find_auto_count, files.size)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                FilledTonalIconButton(
                    onClick = { showDeleteDialog = true },
                    enabled = hasSelection,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    ),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = stringResource(R.string.find_auto_delete),
                    )
                }
                Button(
                    onClick = {
                        when (selected.size) {
                            0 -> Unit
                            1 -> onPickOne(selectedFiles.first())
                            else -> onPickMany(selectedFiles)
                        }
                    },
                    enabled = hasSelection,
                    shape = MaterialTheme.shapes.medium,
                    contentPadding = ButtonDefaults.ButtonWithIconContentPadding,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.InstallMobile,
                        contentDescription = null,
                        modifier = Modifier.size(ButtonDefaults.IconSize),
                    )
                    Spacer(Modifier.width(ButtonDefaults.IconSpacing))
                    Text(
                        text = if (selected.size <= 1) {
                            stringResource(R.string.find_auto_install_single)
                        } else {
                            stringResource(R.string.find_auto_install_selected, selected.size)
                        },
                    )
                }
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = {
                Text(stringResource(R.string.find_auto_delete_title, selectedFiles.size))
            },
            text = {
                Text(stringResource(R.string.find_auto_delete_message))
            },
            confirmButton = {
                TextButton(onClick = {
                    val toDelete = selectedFiles
                    showDeleteDialog = false
                    selected = emptySet()
                    onDeleteMany(toDelete)
                }) {
                    Text(
                        text = stringResource(R.string.find_auto_delete_confirm),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@Composable
private fun FoundRow(
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
                Icon(
                    imageVector = Icons.Rounded.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
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
