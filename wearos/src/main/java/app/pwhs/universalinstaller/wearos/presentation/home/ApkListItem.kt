package app.pwhs.universalinstaller.wearos.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwipeToReveal
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.data.WearApkInfo
import app.pwhs.universalinstaller.wearos.presentation.component.ApkBadge
import app.pwhs.universalinstaller.wearos.presentation.component.ApkIcon
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme

/** One received package. Swipe left to delete without opening it. */
@Composable
fun ApkListItem(
    info: WearApkInfo,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
) {
    SwipeToReveal(
        primaryAction = {
            PrimaryActionButton(
                onClick = onDelete,
                icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                text = { Text(stringResource(R.string.delete)) },
            )
        },
        onSwipePrimaryAction = onDelete,
        modifier = modifier,
    ) {
        TitleCard(
            onClick = onClick,
            title = {
                Text(
                    text = info.appName,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            },
            modifier = Modifier.fillMaxWidth(),
            transformation = transformation,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ApkIcon(apkPath = info.cachedFilePath, size = 24.dp)
                Text(
                    text = "v${info.versionName} · ${formatSize(info.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            ApkBadge(info = info, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

internal fun formatSize(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1.0) "%.1f MB".format(mb) else "${bytes / 1024} KB"
}

private fun previewApk(
    declaresWatchFeature: Boolean = true,
    installedVersionCode: Long? = null,
) = WearApkInfo(
    id = "sample.apk", fileName = "sample.apk", appName = "Watch Face Studio",
    packageName = "com.sample.app", versionName = "2.1.0", versionCode = 210, minSdk = 30,
    isBundle = false, sizeBytes = 15_000_000L, cachedFilePath = "/nowhere/sample.apk",
    declaresWatchFeature = declaresWatchFeature, installedVersionCode = installedVersionCode,
)

@WearPreviewDevices
@Composable
private fun ApkListItemPreview() {
    UniversalInstallerTheme { ApkListItem(previewApk(), {}, {}) }
}

@WearPreviewDevices
@Composable
private fun ApkListItemPhoneAppPreview() {
    UniversalInstallerTheme {
        ApkListItem(previewApk(declaresWatchFeature = false), {}, {})
    }
}

@WearPreviewDevices
@Composable
private fun ApkListItemInstalledPreview() {
    UniversalInstallerTheme {
        ApkListItem(previewApk(installedVersionCode = 210), {}, {})
    }
}
