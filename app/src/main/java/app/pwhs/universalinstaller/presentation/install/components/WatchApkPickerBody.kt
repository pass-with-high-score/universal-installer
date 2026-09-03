package app.pwhs.universalinstaller.presentation.install.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.FoundPackageFile
import app.pwhs.universalinstaller.presentation.install.ScanState
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme

/** The scan-state half of the send-to-watch sheet: permission prompt, spinner, or the file list. */
@Composable
internal fun WatchApkPickerBody(
    scanState: ScanState,
    wearOnly: Boolean,
    onGrantPermission: () -> Unit,
    onSendFound: (FoundPackageFile) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (scanState) {
        is ScanState.PermissionNeeded -> Column(
            modifier = modifier,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            MutedText(stringResource(R.string.find_auto_permission_body))
            TextButton(onClick = onGrantPermission) {
                Text(stringResource(R.string.find_auto_grant))
            }
        }

        is ScanState.Ready -> {
            val files = scanState.files.filter { !wearOnly || it.isWearOsSupported }
            if (files.isEmpty()) {
                MutedText(stringResource(R.string.find_auto_empty), modifier)
            } else {
                LazyColumn(
                    modifier = modifier.heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(files, key = { it.path }) { file ->
                        WatchApkRow(file = file, onClick = { onSendFound(file) })
                    }
                }
            }
        }

        else -> Row(
            modifier = modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            MutedText(stringResource(R.string.find_auto_scanning))
        }
    }
}

@Composable
private fun MutedText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
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
private fun WatchApkPickerBodyReadyPreview() {
    UniversalInstallerTheme {
        WatchApkPickerBody(ScanState.Ready(previewFiles), false, {}, {})
    }
}

@Preview
@Composable
private fun WatchApkPickerBodyScanningPreview() {
    UniversalInstallerTheme {
        WatchApkPickerBody(ScanState.Scanning, false, {}, {})
    }
}

@Preview
@Composable
private fun WatchApkPickerBodyPermissionPreview() {
    UniversalInstallerTheme {
        WatchApkPickerBody(ScanState.PermissionNeeded, false, {}, {})
    }
}

@Preview
@Composable
private fun WatchApkPickerBodyEmptyPreview() {
    UniversalInstallerTheme {
        WatchApkPickerBody(ScanState.Ready(emptyList()), true, {}, {})
    }
}
