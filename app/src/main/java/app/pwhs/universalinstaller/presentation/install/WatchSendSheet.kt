package app.pwhs.universalinstaller.presentation.install

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.presentation.install.components.WatchApkPickerBody
import app.pwhs.universalinstaller.presentation.install.components.WatchPendingApkRow
import app.pwhs.universalinstaller.presentation.install.components.WatchStatusRow
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme

/**
 * Single entry point for sending a package to the watch: what it would go to, what can be sent,
 * and an escape hatch to the file picker. The transfer itself is reported by [WatchSendDialog].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchSendSheet(
    watchName: String?,
    isLookingUpWatch: Boolean,
    pendingApk: ApkInfo?,
    scanState: ScanState,
    onRefreshWatch: () -> Unit,
    onRescan: () -> Unit,
    onGrantPermission: () -> Unit,
    onSendPending: () -> Unit,
    onSendFound: (FoundPackageFile) -> Unit,
    onPickFile: () -> Unit,
    onDismiss: () -> Unit,
) {
    var wearOnly by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.watch_send_title),
                style = MaterialTheme.typography.headlineSmall,
            )

            WatchStatusRow(
                watchName = watchName,
                isLooking = isLookingUpWatch,
                onRetry = onRefreshWatch,
            )

            if (pendingApk != null) {
                SectionLabel(stringResource(R.string.watch_sheet_current))
                WatchPendingApkRow(apkInfo = pendingApk, onSend = onSendPending)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                SectionLabel(
                    text = stringResource(R.string.watch_sheet_on_device),
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = wearOnly,
                    onClick = { wearOnly = !wearOnly },
                    label = { Text(stringResource(R.string.watch_chip_wear_os)) },
                )
                IconButton(onClick = onRescan) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = stringResource(R.string.find_auto_rescan),
                    )
                }
            }

            WatchApkPickerBody(
                scanState = scanState,
                wearOnly = wearOnly,
                onGrantPermission = onGrantPermission,
                onSendFound = onSendFound,
            )

            TextButton(onClick = onPickFile, modifier = Modifier.fillMaxWidth()) {
                Icon(imageVector = Icons.Rounded.FolderOpen, contentDescription = null)
                Text(
                    text = stringResource(R.string.watch_sheet_pick_file),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

private val previewFiles = listOf(
    FoundPackageFile(
        path = "/sdcard/Download/watchface.apk",
        name = "watchface.apk",
        sizeBytes = 4_200_000,
        modifiedMillis = 0L,
        extension = "apk",
        isWearOsSupported = true,
    ),
    FoundPackageFile(
        path = "/sdcard/Download/instagram.apk",
        name = "instagram.apk",
        sizeBytes = 92_000_000,
        modifiedMillis = 0L,
        extension = "apk",
    ),
)

@Preview
@Composable
private fun WatchSendSheetReadyPreview() {
    UniversalInstallerTheme {
        WatchSendSheet(
            watchName = "Pixel Watch 3",
            isLookingUpWatch = false,
            pendingApk = null,
            scanState = ScanState.Ready(previewFiles),
            onRefreshWatch = {}, onRescan = {}, onGrantPermission = {},
            onSendPending = {}, onSendFound = {}, onPickFile = {}, onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun WatchSendSheetNoWatchPreview() {
    UniversalInstallerTheme {
        WatchSendSheet(
            watchName = null,
            isLookingUpWatch = false,
            pendingApk = null,
            scanState = ScanState.Scanning,
            onRefreshWatch = {}, onRescan = {}, onGrantPermission = {},
            onSendPending = {}, onSendFound = {}, onPickFile = {}, onDismiss = {},
        )
    }
}

@Preview
@Composable
private fun WatchSendSheetPermissionPreview() {
    UniversalInstallerTheme {
        WatchSendSheet(
            watchName = "Pixel Watch 3",
            isLookingUpWatch = false,
            pendingApk = null,
            scanState = ScanState.PermissionNeeded,
            onRefreshWatch = {}, onRescan = {}, onGrantPermission = {},
            onSendPending = {}, onSendFound = {}, onPickFile = {}, onDismiss = {},
        )
    }
}
