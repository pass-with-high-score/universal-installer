package app.pwhs.universalinstaller.wearos.presentation.home

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.Icon
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.data.WearApkInfo
import app.pwhs.universalinstaller.wearos.data.WearReceiveState
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun HomeScreen(
    onApkClick: (String) -> Unit,
    onMoreClick: () -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val apks by viewModel.apks.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val receiveState by viewModel.receiveState.collectAsState()
    val storage by viewModel.storage.collectAsState()
    val queueBytes by viewModel.queueBytes.collectAsState()
    HomeScreenContent(
        apks = apks,
        isLoading = isLoading,
        receiveState = receiveState,
        queueBytes = queueBytes,
        freeBytes = storage.freeBytes,
        usedFraction = storage.progress,
        onApkClick = onApkClick,
        onMoreClick = onMoreClick,
        onDelete = viewModel::delete,
    )
}

@Composable
fun HomeScreenContent(
    apks: List<WearApkInfo>,
    isLoading: Boolean,
    receiveState: WearReceiveState,
    queueBytes: Long,
    freeBytes: Long,
    usedFraction: Float,
    onApkClick: (String) -> Unit,
    onMoreClick: () -> Unit,
    onDelete: (String) -> Unit,
) {
    AppScaffold {
        val listState = rememberTransformingLazyColumnState()
        val spec = rememberTransformationSpec()

        ScreenScaffold(
            scrollState = listState,
            // A persistent destination rather than a list item: the queue can be long and Manage
            // must not sit behind it.
            edgeButton = {
                EdgeButton(onClick = onMoreClick) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more_horiz),
                        contentDescription = stringResource(R.string.more_title),
                    )
                }
            },
        ) { contentPadding ->
            TransformingLazyColumn(contentPadding = contentPadding, state = listState) {
                item {
                    ListHeader(
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    ) {
                        Text(text = stringResource(R.string.home_title))
                    }
                }

                item {
                    StorageSummary(
                        queueBytes = queueBytes,
                        freeBytes = freeBytes,
                        usedFraction = usedFraction,
                        modifier = Modifier.transformedHeight(this, spec),
                    )
                }

                if (receiveState !is WearReceiveState.Idle) {
                    item {
                        ReceivingCard(
                            state = receiveState,
                            modifier = Modifier.transformedHeight(this, spec),
                        )
                    }
                }

                items(apks.size, key = { apks[it].id }) { index ->
                    val apk = apks[index]
                    ApkListItem(
                        info = apk,
                        onClick = { onApkClick(apk.id) },
                        onDelete = { onDelete(apk.id) },
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }

                if (apks.isEmpty() && receiveState is WearReceiveState.Idle) {
                    item {
                        if (isLoading) {
                            Text(
                                text = stringResource(R.string.loading),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, spec),
                            )
                        } else {
                            HomeEmpty(modifier = Modifier.transformedHeight(this, spec))
                        }
                    }
                }
            }
        }
    }
}

private fun previewApk(
    id: String = "sample.apk",
    appName: String = "Watch Face Studio",
    declaresWatchFeature: Boolean = true,
    installedVersionCode: Long? = null,
) = WearApkInfo(
    id = id, fileName = id, appName = appName, packageName = "com.sample.app",
    versionName = "2.1.0", versionCode = 210, minSdk = 30, isBundle = false,
    sizeBytes = 15_000_000L, cachedFilePath = "/nowhere/$id",
    declaresWatchFeature = declaresWatchFeature, installedVersionCode = installedVersionCode,
)

@WearPreviewDevices
@Composable
private fun HomeScreenPreview() {
    UniversalInstallerTheme {
        HomeScreenContent(
            apks = listOf(
                previewApk(),
                previewApk(id = "b.apk", appName = "Instagram", declaresWatchFeature = false),
                previewApk(id = "c.apk", appName = "Tiles Demo", installedVersionCode = 210),
            ),
            isLoading = false,
            receiveState = WearReceiveState.Idle,
            queueBytes = 160_000_000L,
            freeBytes = 5_300_000_000L,
            usedFraction = 0.34f,
            onApkClick = {},
            onMoreClick = {},
            onDelete = {},
        )
    }
}

@WearPreviewDevices
@Composable
private fun HomeScreenReceivingPreview() {
    UniversalInstallerTheme {
        HomeScreenContent(
            apks = listOf(previewApk()),
            isLoading = false,
            receiveState = WearReceiveState.Receiving("watchface.apk", 4_000_000, 12_000_000),
            queueBytes = 160_000_000L,
            freeBytes = 5_300_000_000L,
            usedFraction = 0.34f,
            onApkClick = {},
            onMoreClick = {},
            onDelete = {},
        )
    }
}

@WearPreviewDevices
@Composable
private fun HomeScreenEmptyPreview() {
    UniversalInstallerTheme {
        HomeScreenContent(emptyList(), false, WearReceiveState.Idle, 0L, 5_300_000_000L, 0.34f, {}, {}, {})
    }
}

@WearPreviewDevices
@Composable
private fun HomeScreenLoadingPreview() {
    UniversalInstallerTheme {
        HomeScreenContent(emptyList(), true, WearReceiveState.Idle, 0L, 5_300_000_000L, 0.34f, {}, {}, {})
    }
}
