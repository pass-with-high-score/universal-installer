package app.pwhs.universalinstaller.presentation.install.components

import android.text.format.Formatter
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.FoundPackageFile
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.util.ApkFileIconData
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent

/** One package file offered as a send target. Tapping it starts the send. */
@Composable
internal fun WatchApkRow(
    file: FoundPackageFile,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(40.dp), contentAlignment = Alignment.Center) {
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
                    success = { SubcomposeAsyncImageContent() },
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${file.extension.uppercase()} · " +
                        Formatter.formatShortFileSize(context, file.sizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (file.isWearOsSupported) {
                    StatusChip(
                        label = stringResource(R.string.watch_chip_wear_os),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Preview
@Composable
private fun WatchApkRowWearPreview() {
    UniversalInstallerTheme {
        WatchApkRow(
            file = FoundPackageFile(
                path = "/sdcard/Download/watchface.apk",
                name = "watchface.apk",
                sizeBytes = 4_200_000,
                modifiedMillis = 0L,
                extension = "apk",
                isWearOsSupported = true,
            ),
            onClick = {},
        )
    }
}

@Preview
@Composable
private fun WatchApkRowPhoneAppPreview() {
    UniversalInstallerTheme {
        WatchApkRow(
            file = FoundPackageFile(
                path = "/sdcard/Download/instagram.apk",
                name = "instagram.apk",
                sizeBytes = 92_000_000,
                modifiedMillis = 0L,
                extension = "apk",
            ),
            onClick = {},
        )
    }
}
