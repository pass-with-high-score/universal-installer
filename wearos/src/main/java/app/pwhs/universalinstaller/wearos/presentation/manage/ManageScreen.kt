package app.pwhs.universalinstaller.wearos.presentation.manage

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.core.domain.InstalledApp
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun ManageScreen(viewModel: ManageViewModel = koinViewModel()) {
    val apps by viewModel.apps.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val includeSystem by viewModel.includeSystem.collectAsState()
    ManageScreenContent(
        apps = apps,
        isLoading = isLoading,
        includeSystem = includeSystem,
        sourceDirOf = viewModel::sourceDirOf,
        onToggleSystem = viewModel::toggleSystemApps,
        onOpen = viewModel::openAppInfo,
        onUninstall = viewModel::uninstall,
    )
}

@Composable
fun ManageScreenContent(
    apps: List<InstalledApp>,
    isLoading: Boolean,
    includeSystem: Boolean,
    sourceDirOf: (String) -> String?,
    onToggleSystem: () -> Unit,
    onOpen: (String) -> Unit,
    onUninstall: (String) -> Unit,
) {
    AppScaffold {
        val listState = rememberTransformingLazyColumnState()
        val spec = rememberTransformationSpec()

        ScreenScaffold(scrollState = listState) { contentPadding ->
            TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text(stringResource(R.string.manage_title))
                    }
                }

                item {
                    Button(
                        onClick = onToggleSystem,
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text(
                            stringResource(
                                if (includeSystem) R.string.manage_hide_system
                                else R.string.manage_show_system
                            )
                        )
                    }
                }

                items(apps.size, key = { apps[it].packageName }) { index ->
                    val app = apps[index]
                    InstalledAppRow(
                        app = app,
                        sourceDir = sourceDirOf(app.packageName),
                        onOpen = { onOpen(app.packageName) },
                        onUninstall = { onUninstall(app.packageName) },
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }

                if (apps.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(
                                if (isLoading) R.string.loading else R.string.manage_empty
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, spec),
                        )
                    }
                }
            }
        }
    }
}

private fun previewApp(name: String, pkg: String, size: Long) = InstalledApp(
    packageName = pkg, appName = name, versionName = "2.1.0",
    isSystemApp = false, sizeBytes = size,
)

@WearPreviewDevices
@Composable
private fun ManageScreenPreview() {
    UniversalInstallerTheme {
        ManageScreenContent(
            apps = listOf(
                previewApp("Watch Face Studio", "com.example.wfs", 15_000_000L),
                previewApp("Sleep Tracker", "com.example.sleep", 8_400_000L),
            ),
            isLoading = false, includeSystem = false,
            sourceDirOf = { null }, onToggleSystem = {}, onOpen = {}, onUninstall = {},
        )
    }
}

@WearPreviewDevices
@Composable
private fun ManageScreenEmptyPreview() {
    UniversalInstallerTheme {
        ManageScreenContent(emptyList(), false, false, { null }, {}, {}, {})
    }
}
