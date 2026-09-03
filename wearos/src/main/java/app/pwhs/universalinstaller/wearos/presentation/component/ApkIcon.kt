package app.pwhs.universalinstaller.wearos.presentation.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.core.ui.ApkFileIconData
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme
import coil3.compose.SubcomposeAsyncImage
import coil3.compose.SubcomposeAsyncImageContent
import coil3.request.ImageRequest

/** The package's own launcher icon, read straight out of the cached archive. */
@Composable
fun ApkIcon(
    apkPath: String,
    modifier: Modifier = Modifier,
    size: androidx.compose.ui.unit.Dp = 32.dp,
) {
    val context = LocalContext.current
    Box(modifier = modifier.size(size), contentAlignment = Alignment.Center) {
        SubcomposeAsyncImage(
            model = ImageRequest.Builder(context).data(ApkFileIconData(apkPath)).build(),
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            error = { FallbackIcon() },
            loading = { FallbackIcon() },
            success = { SubcomposeAsyncImageContent() },
        )
    }
}

@Composable
private fun FallbackIcon() {
    Icon(
        painter = androidx.compose.ui.res.painterResource(R.drawable.ic_notification),
        contentDescription = null,
        tint = MaterialTheme.colorScheme.primary,
    )
}

@WearPreviewDevices
@Composable
private fun ApkIconPreview() {
    UniversalInstallerTheme { ApkIcon(apkPath = "/nowhere/sample.apk") }
}
