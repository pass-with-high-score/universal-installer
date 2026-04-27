package app.pwhs.universalinstaller.presentation.install

import android.text.format.Formatter
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CheckBox
import androidx.compose.material.icons.rounded.CheckBoxOutlineBlank
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.FolderOff
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.util.ApkFileIconData
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
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
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
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
            textAlign = TextAlign.Center,
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
            textAlign = TextAlign.Center,
        )
        return
    }

    // Selection lives inside the sheet — no need to hoist to the VM since it's transient
    // UI state that resets every time the sheet opens.
    var selected by remember(files) { mutableStateOf(setOf<String>()) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    val keyboardController = LocalSoftwareKeyboardController.current

    // Filter files based on search query (case-insensitive, matches name or path).
    val filteredFiles = remember(files, searchQuery) {
        if (searchQuery.isBlank()) files
        else {
            val q = searchQuery.trim().lowercase()
            files.filter { f ->
                f.name.lowercase().contains(q) || f.path.lowercase().contains(q)
            }
        }
    }

    val selectedFiles = files.filter { it.path in selected }
    val selectedBytes = selectedFiles.sumOf { it.sizeBytes.coerceAtLeast(0L) }
    val hasSelection = selected.isNotEmpty()

    // Toggle state considers only visible (filtered) files.
    val filteredSelected = filteredFiles.count { it.path in selected }
    val toggleState = when {
        filteredFiles.isEmpty() -> ToggleableState.Off
        filteredSelected == 0 -> ToggleableState.Off
        filteredSelected == filteredFiles.size -> ToggleableState.On
        else -> ToggleableState.Indeterminate
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        // ── Search bar ──────────────────────────────────────
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = {
                Text(
                    text = stringResource(R.string.find_auto_search_hint),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            },
            trailingIcon = {
                AnimatedVisibility(
                    visible = searchQuery.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(
                            imageVector = Icons.Rounded.Clear,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
            ),
            textStyle = MaterialTheme.typography.bodyMedium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { keyboardController?.hide() }),
        )
        Spacer(Modifier.height(8.dp))

        // Select-all row: toggles only the currently visible (filtered) items.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TriStateCheckbox(
                state = toggleState,
                onClick = {
                    val visiblePaths = filteredFiles.map { it.path }.toSet()
                    selected = if (toggleState == ToggleableState.On) {
                        selected - visiblePaths
                    } else {
                        selected + visiblePaths
                    }
                },
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = if (searchQuery.isBlank()) "${selected.size} / ${files.size}"
                       else "${filteredSelected} / ${filteredFiles.size}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
        Spacer(Modifier.height(4.dp))

        if (filteredFiles.isEmpty() && searchQuery.isNotBlank()) {
            // No matches — show empty state.
            Text(
                text = stringResource(R.string.find_auto_no_match, searchQuery),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 32.dp),
                textAlign = TextAlign.Center,
            )
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
            ) {
                items(filteredFiles, key = { it.path }) { file ->
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
                    showDeleteDialog = false
                    selected = emptySet()
                    onDeleteMany(selectedFiles)
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

/**
 * Status chip strip — shown under each file's metadata. Up to two chips:
 *
 * - "Split" for archive bundles (.xapk/.apks/.apkm). Always shown when applicable.
 * - One install-state chip:
 *     New        — package not yet installed (primary tint)
 *     Update     — APK is newer than what's installed (success/primary tint)
 *     Installed  — same versionCode already installed (muted)
 *     Older      — APK older than installed (error tint, downgrade warning)
 *
 * Archives stay InstallState.Unknown (we don't unpack base.apk during a scan), so they
 * only get the Split chip. .apk files get the install-state chip.
 */
@Composable
private fun FoundFileChips(file: FoundPackageFile) {
    val isArchive = file.extension in setOf("xapk", "apks", "apkm")
    val showStateChip = file.installState != InstallState.Unknown

    if (!isArchive && !showStateChip) return

    Row(
        modifier = Modifier.padding(top = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
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

@Composable
private fun StatusChip(
    label: String,
    container: androidx.compose.ui.graphics.Color,
    content: androidx.compose.ui.graphics.Color,
) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
}
