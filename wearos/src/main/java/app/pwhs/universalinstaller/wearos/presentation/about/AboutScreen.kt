package app.pwhs.universalinstaller.wearos.presentation.about

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.net.toUri
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.presentation.component.ApkIcon
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val pm = context.packageManager
    val info = runCatching {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(context.packageName, 0)
        }
    }.getOrNull()

    AboutScreenContent(
        appName = stringResource(R.string.app_name),
        version = info?.let { "${it.versionName} (${it.longVersionCode})" }.orEmpty(),
        packageName = context.packageName,
        // The installed APK doubles as the icon source, the same way Manage renders its rows.
        sourceDir = info?.applicationInfo?.sourceDir,
        onRate = { openStoreListing(context) },
    )
}

/**
 * Wear ships its own Play client, so the market: URI lands on this app's listing. The https form
 * is the fallback for a watch without Play; if neither resolves there is nowhere to send anyone.
 */
private fun openStoreListing(context: android.content.Context) {
    val id = context.packageName
    val targets = listOf("market://details?id=$id", "https://play.google.com/store/apps/details?id=$id")
    for (url in targets) {
        val intent = Intent(Intent.ACTION_VIEW, url.toUri())
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (runCatching { context.startActivity(intent) }.isSuccess) return
    }
}

@Composable
fun AboutScreenContent(
    appName: String,
    version: String,
    packageName: String,
    sourceDir: String?,
    onRate: () -> Unit,
) {
    AppScaffold {
        val listState = rememberTransformingLazyColumnState()
        val spec = rememberTransformationSpec()

        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .transformedHeight(this, spec),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        if (sourceDir != null) ApkIcon(apkPath = sourceDir, size = 48.dp)
                        Text(
                            text = appName,
                            style = MaterialTheme.typography.titleMedium,
                            textAlign = TextAlign.Center,
                        )
                        Label(stringResource(R.string.about_version), version)
                        Label(stringResource(R.string.about_package), packageName)
                    }
                }

                item {
                    Button(
                        onClick = onRate,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp)
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text(stringResource(R.string.about_rate))
                    }
                }
            }
        }
    }
}

@Composable
private fun Label(caption: String, value: String) {
    Text(
        text = caption,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.padding(top = 6.dp),
    )
    Text(
        text = value,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        maxLines = 2,
        overflow = TextOverflow.MiddleEllipsis,
    )
}

@WearPreviewDevices
@Composable
private fun AboutScreenPreview() {
    UniversalInstallerTheme {
        AboutScreenContent(
            appName = "Universal Installer",
            version = "1.12.0 (1035)",
            packageName = "app.pwhs.universalinstaller",
            sourceDir = null,
            onRate = {},
        )
    }
}
