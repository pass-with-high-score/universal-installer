package app.pwhs.universalinstaller.wearos.presentation.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.data.WearApkInfo
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.wearos.presentation.theme.WearWarning

/**
 * At most one badge per package, worst news first: a phone app is the reason a user gives up on
 * an entry, so it outranks the install-state chips.
 */
@Composable
fun ApkBadge(info: WearApkInfo, modifier: Modifier = Modifier) {
    val labelRes: Int
    val tint: Color
    when {
        !info.declaresWatchFeature -> {
            labelRes = R.string.badge_phone_app
            tint = WearWarning
        }

        info.installedVersionCode == null -> return
        info.versionCode > info.installedVersionCode -> {
            labelRes = R.string.badge_update
            tint = MaterialTheme.colorScheme.primary
        }

        info.versionCode < info.installedVersionCode -> {
            labelRes = R.string.badge_older
            tint = MaterialTheme.colorScheme.error
        }

        else -> {
            labelRes = R.string.badge_installed
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        }
    }

    Text(
        text = stringResource(labelRes),
        style = MaterialTheme.typography.labelSmall,
        color = tint,
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(tint.copy(alpha = 0.16f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    )
}

private fun previewApk(
    declaresWatchFeature: Boolean = true,
    installedVersionCode: Long? = null,
) = WearApkInfo(
    id = "s.apk", fileName = "s.apk", appName = "Sample", packageName = "com.sample",
    versionName = "2.1.0", versionCode = 210, minSdk = 30, isBundle = false,
    sizeBytes = 15_000_000L, cachedFilePath = "", declaresWatchFeature = declaresWatchFeature,
    installedVersionCode = installedVersionCode,
)

@WearPreviewDevices
@Composable
private fun ApkBadgePhoneAppPreview() {
    UniversalInstallerTheme { ApkBadge(previewApk(declaresWatchFeature = false)) }
}

@WearPreviewDevices
@Composable
private fun ApkBadgeUpdatePreview() {
    UniversalInstallerTheme { ApkBadge(previewApk(installedVersionCode = 100)) }
}

@WearPreviewDevices
@Composable
private fun ApkBadgeInstalledPreview() {
    UniversalInstallerTheme { ApkBadge(previewApk(installedVersionCode = 210)) }
}
