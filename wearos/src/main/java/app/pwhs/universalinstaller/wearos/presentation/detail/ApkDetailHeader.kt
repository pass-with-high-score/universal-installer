package app.pwhs.universalinstaller.wearos.presentation.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.data.WearApkInfo
import app.pwhs.universalinstaller.wearos.presentation.component.ApkBadge
import app.pwhs.universalinstaller.wearos.presentation.component.ApkIcon
import app.pwhs.universalinstaller.wearos.presentation.home.formatSize
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme

/** Identity of the package being installed: icon, name, version and size on one card. */
@Composable
fun ApkDetailHeader(info: WearApkInfo, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        ApkIcon(apkPath = info.cachedFilePath, size = 40.dp)
        Text(
            text = info.appName,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = "v${info.versionName}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = formatSize(info.sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Text(
            text = info.packageName,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.MiddleEllipsis,
        )
        ApkBadge(info = info, modifier = Modifier.padding(top = 2.dp))
    }
}

private fun previewApk(declaresWatchFeature: Boolean = true) = WearApkInfo(
    id = "s.apk", fileName = "s.apk", appName = "Watch Face Studio",
    packageName = "com.example.watchfacestudio.companion", versionName = "1.2.3",
    versionCode = 123, minSdk = 30, isBundle = false, sizeBytes = 10_000_000L,
    cachedFilePath = "/nowhere/s.apk", declaresWatchFeature = declaresWatchFeature,
)

@WearPreviewDevices
@Composable
private fun ApkDetailHeaderPreview() {
    UniversalInstallerTheme { ApkDetailHeader(previewApk()) }
}

@WearPreviewDevices
@Composable
private fun ApkDetailHeaderPhoneAppPreview() {
    UniversalInstallerTheme { ApkDetailHeader(previewApk(declaresWatchFeature = false)) }
}
