package app.pwhs.universalinstaller.wearos.presentation.settings

import android.text.format.Formatter
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.wear.compose.foundation.lazy.TransformingLazyColumn
import androidx.wear.compose.foundation.lazy.rememberTransformingLazyColumnState
import androidx.wear.compose.material3.AlertDialog
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.SurfaceTransformation
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.lazy.rememberTransformationSpec
import androidx.wear.compose.material3.lazy.transformedHeight
import androidx.wear.compose.ui.tooling.preview.WearPreviewDevices
import app.pwhs.universalinstaller.wearos.R
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun SettingsScreen(
    onAboutClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onAccentClick: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val keep by viewModel.keepAfterInstall.collectAsState()
    val accent by viewModel.accent.collectAsState()
    val languageTag by viewModel.languageTag.collectAsState()
    val languageLabel = if (languageTag == AppLocale.SYSTEM) {
        stringResource(R.string.settings_language_system)
    } else {
        AppLocale.displayName(languageTag)
    }
    val queueBytes by viewModel.queueBytes.collectAsState()
    val freeBytes by viewModel.freeBytes.collectAsState()
    SettingsScreenContent(
        keepAfterInstall = keep,
        queueBytes = queueBytes,
        freeBytes = freeBytes,
        onKeepChange = viewModel::setKeepAfterInstall,
        onClearQueue = viewModel::clearQueue,
        onOpenInstallPermission = viewModel::openInstallPermission,
        onAboutClick = onAboutClick,
        onLanguageClick = onLanguageClick,
        onAccentClick = onAccentClick,
        languageLabel = languageLabel,
        accentLabel = stringResource(accent.labelRes()),
    )
}

@Composable
fun SettingsScreenContent(
    keepAfterInstall: Boolean,
    queueBytes: Long,
    freeBytes: Long,
    onKeepChange: (Boolean) -> Unit,
    onClearQueue: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onAboutClick: () -> Unit,
    onLanguageClick: () -> Unit,
    onAccentClick: () -> Unit,
    languageLabel: String,
    accentLabel: String,
) {
    val context = LocalContext.current
    var confirmClear by remember { mutableStateOf(false) }

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
                        Text(stringResource(R.string.settings_title))
                    }
                }

                item {
                    SettingRow(
                        title = stringResource(R.string.settings_storage),
                        summary = stringResource(
                            R.string.settings_storage_summary,
                            Formatter.formatShortFileSize(context, queueBytes),
                            Formatter.formatShortFileSize(context, freeBytes),
                        ),
                        onClick = { confirmClear = true },
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }

                item {
                    SwitchButton(
                        checked = keepAfterInstall,
                        onCheckedChange = onKeepChange,
                        label = { Text(stringResource(R.string.settings_keep_after_install)) },
                        secondaryLabel = {
                            Text(
                                stringResource(
                                    if (keepAfterInstall) R.string.settings_keep_after_install_on
                                    else R.string.settings_keep_after_install_off
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }

                item {
                    SettingRow(
                        title = stringResource(R.string.settings_language),
                        summary = languageLabel,
                        onClick = onLanguageClick,
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }

                item {
                    SettingRow(
                        title = stringResource(R.string.settings_accent),
                        summary = accentLabel,
                        onClick = onAccentClick,
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }

                item {
                    SettingRow(
                        title = stringResource(R.string.settings_install_permission),
                        summary = stringResource(R.string.settings_install_permission_summary),
                        onClick = onOpenInstallPermission,
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }

                item {
                    SettingRow(
                        title = stringResource(R.string.settings_about),
                        summary = stringResource(R.string.settings_about_summary),
                        onClick = onAboutClick,
                        modifier = Modifier.transformedHeight(this, spec),
                        transformation = SurfaceTransformation(spec),
                    )
                }
            }
        }
    }

    AlertDialog(
        visible = confirmClear,
        onDismissRequest = { confirmClear = false },
        title = { Text(stringResource(R.string.settings_clear_queue_confirm)) },
        confirmButton = {
            Button(
                onClick = {
                    confirmClear = false
                    onClearQueue()
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                ),
            ) { Text(stringResource(R.string.settings_clear_queue)) }
        },
        dismissButton = {
            Button(onClick = { confirmClear = false }) {
                Text(stringResource(R.string.cancel))
            }
        },
    )
}

@WearPreviewDevices
@Composable
private fun SettingsScreenPreview() {
    UniversalInstallerTheme {
        SettingsScreenContent(false, 125_179_933L, 2_400_000_000L, {}, {}, {}, {}, {}, {}, "Tiếng Việt", "Orange")
    }
}

@WearPreviewDevices
@Composable
private fun SettingsScreenKeepOnPreview() {
    UniversalInstallerTheme {
        SettingsScreenContent(true, 0L, 2_400_000_000L, {}, {}, {}, {}, {}, {}, "Watch language", "Blue")
    }
}
