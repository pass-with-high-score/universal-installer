package app.pwhs.universalinstaller.presentation.install.components

import android.text.format.Formatter
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** The package already open on the install screen, offered as a one-tap send. */
@Composable
internal fun WatchPendingApkRow(
    apkInfo: ApkInfo,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val iconBitmap by produceState<ImageBitmap?>(initialValue = null, key1 = apkInfo.icon) {
        value = withContext(Dispatchers.IO) { apkInfo.icon?.toBitmap(96, 96)?.asImageBitmap() }
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val icon = iconBitmap
            if (icon != null) {
                Image(bitmap = icon, contentDescription = null, modifier = Modifier.size(40.dp))
            } else {
                Icon(
                    imageVector = Icons.Rounded.Android,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(40.dp),
                )
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = apkInfo.appName,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "v${apkInfo.versionName} · " +
                        Formatter.formatShortFileSize(context, apkInfo.fileSizeBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
                if (apkInfo.isWearOsSupported) {
                    StatusChip(
                        label = stringResource(R.string.watch_chip_wear_os),
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Button(onClick = onSend) { Text(stringResource(R.string.watch_sheet_send)) }
        }
    }
}

@Preview
@Composable
private fun WatchPendingApkRowPreview() {
    UniversalInstallerTheme {
        WatchPendingApkRow(
            apkInfo = ApkInfo(
                appName = "Watch Face Studio",
                packageName = "com.example.wfs",
                versionName = "2.1.0",
                versionCode = 21,
                icon = null,
                minSdkVersion = 30,
                targetSdkVersion = 34,
                fileSizeBytes = 4_200_000,
                permissions = emptyList(),
                isWearOsSupported = true,
            ),
            onSend = {},
        )
    }
}
