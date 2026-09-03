package app.pwhs.universalinstaller.wearos.presentation.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TitleCard
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.data.WearApkInfo
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onApkClick: (String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val apks by viewModel.apks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    HomeScreenContent(apks = apks, isLoading = isLoading, onApkClick = onApkClick)
}

@Composable
fun HomeScreenContent(
    apks: List<WearApkInfo>,
    isLoading: Boolean,
    onApkClick: (String) -> Unit,
) {
    UniversalInstallerTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            val transformationSpec = rememberTransformationSpec()

            ScreenScaffold(scrollState = listState) { contentPadding ->
                TransformingLazyColumn(
                    contentPadding = contentPadding,
                    state = listState,
                ) {
                    item {
                        ListHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, transformationSpec),
                            transformation = SurfaceTransformation(transformationSpec),
                        ) {
                            Text(text = stringResource(R.string.home_title))
                        }
                    }

                    if (apks.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(
                                    if (isLoading) R.string.loading else R.string.home_empty
                                ),
                                style = MaterialTheme.typography.bodyMedium,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp)
                                    .transformedHeight(this, transformationSpec),
                            )
                        }
                    } else {
                        items(apks.size) { index ->
                            val apk = apks[index]
                            TitleCard(
                                onClick = { onApkClick(apk.id) },
                                title = {
                                    Text(
                                        text = apk.appName,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, transformationSpec),
                                transformation = SurfaceTransformation(transformationSpec),
                            ) {
                                Text(
                                    text = "${apk.versionName} · ${formatSize(apk.sizeBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    val mb = bytes / 1_048_576.0
    return if (mb >= 1.0) "%.1f MB".format(mb) else "${bytes / 1024} KB"
}

private fun previewApk() = WearApkInfo(
    id = "sample.apk",
    fileName = "sample.apk",
    appName = "Sample App",
    packageName = "com.sample.app",
    versionName = "2.1.0",
    versionCode = 210,
    minSdk = 30,
    isBundle = false,
    sizeBytes = 15_000_000L,
    cachedFilePath = "/data/app/sample.apk",
)

@WearPreviewDevices
@Composable
private fun HomeScreenPreview() {
    HomeScreenContent(apks = listOf(previewApk()), isLoading = false, onApkClick = {})
}

@WearPreviewDevices
@Composable
private fun HomeScreenLoadingPreview() {
    HomeScreenContent(apks = emptyList(), isLoading = true, onApkClick = {})
}

@WearPreviewDevices
@Composable
private fun HomeScreenEmptyPreview() {
    HomeScreenContent(apks = emptyList(), isLoading = false, onApkClick = {})
}
