package app.pwhs.universalinstaller.wearos.presentation.detail

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.data.WearApkInfo
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme
import org.koin.androidx.compose.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun ApkDetailScreen(
    apkId: String,
    onInstallSuccess: () -> Unit,
    onDelete: () -> Unit,
    viewModel: DetailViewModel = koinViewModel(parameters = { parametersOf(apkId) }),
) {
    val apkInfo by viewModel.apkInfo.collectAsState()
    val installState by viewModel.installState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(installState) {
        if (installState is InstallState.Success) onInstallSuccess()
    }

    ApkDetailContent(
        apkInfo = apkInfo,
        installState = installState,
        onInstall = viewModel::install,
        onInstallAnyway = viewModel::installAnyway,
        onOpenSettings = { openUnknownSourcesSettings(context) },
        onDelete = {
            viewModel.delete()
            onDelete()
        },
    )
}

private fun openUnknownSourcesSettings(context: android.content.Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
        "package:${context.packageName}".toUri(),
    )
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    }
}

@Composable
private fun ApkDetailContent(
    apkInfo: WearApkInfo?,
    installState: InstallState,
    onInstall: () -> Unit,
    onInstallAnyway: () -> Unit,
    onOpenSettings: () -> Unit,
    onDelete: () -> Unit,
) {
    UniversalInstallerTheme {
        AppScaffold {
            val listState = rememberTransformingLazyColumnState()
            val spec = rememberTransformationSpec()

            ScreenScaffold(
                scrollState = listState,
                edgeButton = {
                    if (installState is InstallState.Idle && apkInfo != null) {
                        EdgeButton(
                            onClick = onDelete,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.errorContainer,
                                contentColor = MaterialTheme.colorScheme.onErrorContainer,
                            ),
                        ) {
                            Text(stringResource(R.string.delete))
                        }
                    }
                },
            ) { contentPadding ->
                TransformingLazyColumn(
                    contentPadding = contentPadding,
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    item {
                        ListHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .transformedHeight(this, spec),
                            transformation = SurfaceTransformation(spec),
                        ) {
                            Text(
                                text = apkInfo?.appName ?: stringResource(R.string.loading),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }

                    if (installState is InstallState.Installing) {
                        item {
                            if (installState.progress != null) {
                                CircularProgressIndicator(
                                    progress = { installState.progress },
                                    modifier = Modifier.size(48.dp).transformedHeight(this, spec),
                                )
                            } else {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(48.dp).transformedHeight(this, spec),
                                )
                            }
                        }
                    }

                    item {
                        CenteredText(
                            text = installState.detailText(apkInfo),
                            modifier = Modifier.transformedHeight(this, spec),
                        )
                    }

                    val action = installState.action()
                    if (action != null && apkInfo != null) {
                        item {
                            Button(
                                onClick = when (action) {
                                    Action.INSTALL -> onInstall
                                    Action.INSTALL_ANYWAY -> onInstallAnyway
                                    Action.RETRY -> onInstall
                                    Action.OPEN_SETTINGS -> onOpenSettings
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .transformedHeight(this, spec),
                                transformation = SurfaceTransformation(spec),
                            ) {
                                Text(stringResource(action.labelRes))
                            }
                        }
                    }

                    if (installState is InstallState.NeedsUnknownSources) {
                        item {
                            CenteredText(
                                text = stringResource(R.string.unknown_sources_hidden),
                                modifier = Modifier.transformedHeight(this, spec),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CenteredText(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        textAlign = TextAlign.Center,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp),
    )
}

private enum class Action(val labelRes: Int) {
    INSTALL(R.string.install),
    INSTALL_ANYWAY(R.string.install_anyway),
    RETRY(R.string.retry),
    OPEN_SETTINGS(R.string.unknown_sources_open_settings),
}

private fun InstallState.action(): Action? = when (this) {
    is InstallState.Idle -> Action.INSTALL
    is InstallState.Incompatible -> Action.INSTALL_ANYWAY
    is InstallState.Failed -> Action.RETRY
    is InstallState.NeedsUnknownSources -> Action.OPEN_SETTINGS
    else -> null
}

@Composable
private fun InstallState.detailText(apkInfo: WearApkInfo?): String = when (this) {
    is InstallState.Installing -> stringResource(R.string.installing)
    is InstallState.Failed -> "${stringResource(R.string.install_failed)}\n$message"
    is InstallState.NeedsUnknownSources -> stringResource(R.string.unknown_sources_msg)
    is InstallState.Incompatible -> reason
    else -> apkInfo?.let { "${it.packageName}\nv${it.versionName}" }.orEmpty()
}

private fun previewApk() = WearApkInfo(
    id = "sample.apk", fileName = "sample.apk", appName = "Sample App",
    packageName = "com.example.sample", versionName = "1.2.3", versionCode = 123,
    minSdk = 30, isBundle = false, sizeBytes = 10_000_000L, cachedFilePath = "",
)

@WearPreviewDevices
@Composable
private fun ApkDetailIdlePreview() {
    ApkDetailContent(previewApk(), InstallState.Idle, {}, {}, {}, {})
}

@WearPreviewDevices
@Composable
private fun ApkDetailInstallingPreview() {
    ApkDetailContent(previewApk(), InstallState.Installing(0.6f), {}, {}, {}, {})
}

@WearPreviewDevices
@Composable
private fun ApkDetailFailedPreview() {
    ApkDetailContent(previewApk(), InstallState.Failed("INSTALL_FAILED_INVALID_APK"), {}, {}, {}, {})
}

@WearPreviewDevices
@Composable
private fun ApkDetailNeedsUnknownSourcesPreview() {
    ApkDetailContent(previewApk(), InstallState.NeedsUnknownSources, {}, {}, {}, {})
}

@WearPreviewDevices
@Composable
private fun ApkDetailIncompatiblePreview() {
    ApkDetailContent(previewApk(), InstallState.Incompatible("This is a phone app."), {}, {}, {}, {})
}
