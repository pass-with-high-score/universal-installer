package app.pwhs.universalinstaller.wearos.presentation.manage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
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
import app.pwhs.core.domain.InstalledApp
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.presentation.component.ApkIcon
import app.pwhs.universalinstaller.wearos.presentation.home.formatSize
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme

/** One installed package. Swipe left to hand it to the system uninstaller. */
@Composable
fun InstalledAppRow(
    app: InstalledApp,
    sourceDir: String?,
    onUninstall: () -> Unit,
    modifier: Modifier = Modifier,
    transformation: SurfaceTransformation? = null,
) {
    SwipeToReveal(
        primaryAction = {
            PrimaryActionButton(
                onClick = onUninstall,
                icon = { Icon(painterResource(R.drawable.ic_delete), contentDescription = null) },
                text = { Text(stringResource(R.string.uninstall)) },
            )
        },
        onSwipePrimaryAction = onUninstall,
        modifier = modifier,
    ) {
        TitleCard(
            onClick = onUninstall,
            title = {
                Text(text = app.appName, maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            modifier = Modifier.fillMaxWidth(),
            transformation = transformation,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (sourceDir != null) ApkIcon(apkPath = sourceDir, size = 24.dp)
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "v${app.versionName}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatSize(app.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

private fun previewApp() = InstalledApp(
    packageName = "com.example.watchface",
    appName = "Watch Face Studio",
    versionName = "2.1.0",
    isSystemApp = false,
    sizeBytes = 15_000_000L,
)

@WearPreviewDevices
@Composable
private fun InstalledAppRowPreview() {
    UniversalInstallerTheme { InstalledAppRow(previewApp(), null, {}) }
}
