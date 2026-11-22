package app.pwhs.universalinstaller.presentation.setting.installer

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.RocketLaunch
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import app.pwhs.core.presentation.adaptive.MaxWidthContainer
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.composable.InstallPriorityBottomSheet
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.setting.components.InstallOptionsBottomSheet
import app.pwhs.universalinstaller.presentation.setting.components.SettingsCardGroup
import app.pwhs.universalinstaller.presentation.setting.components.SettingsNavRow
import app.pwhs.universalinstaller.presentation.setting.components.SettingsSwitchRow
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallerSettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()
    val useDhizuku by viewModel.useDhizuku.collectAsState()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var showInstallOptionsSheet by remember { mutableStateOf(false) }
    var showInstallPrioritySheet by remember { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.updateShizukuState()
                viewModel.refreshDhizukuState()
                viewModel.refreshRootState()
                viewModel.updateDefaultInstallerStatus()
                viewModel.updateDefaultUninstallerStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { resId ->
            Toast.makeText(context, context.getString(resId), Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            LargeTopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.setting_installer_settings_title),
                        style = MaterialTheme.typography.headlineMedium,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { (context as? Activity)?.finish() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.back_cd),
                        )
                    }
                },
                scrollBehavior = scrollBehavior,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
    ) { innerPadding ->
        MaxWidthContainer(
            maxWidth = 720.dp,
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                item {
                    val installingItems = buildList<@Composable () -> Unit> {
                        add {
                            SettingsNavRow(
                                title = stringResource(R.string.setting_install_priority_title),
                                subtitle = stringResource(R.string.setting_install_priority_subtitle),
                                icon = Icons.Rounded.Tune,
                                onClick = { showInstallPrioritySheet = true },
                            )
                        }
                        if (uiState.rootSupported && uiState.useRoot && uiState.rootState == RootState.DENIED) {
                            add {
                                SettingsNavRow(
                                    title = stringResource(R.string.setting_retry_root),
                                    icon = Icons.Rounded.RocketLaunch,
                                    onClick = viewModel::retryRoot,
                                )
                            }
                        }
                        add {
                            SettingsNavRow(
                                title = stringResource(R.string.setting_section_install_options),
                                subtitle = stringResource(R.string.setting_install_options_subtitle),
                                icon = Icons.Rounded.AdminPanelSettings,
                                onClick = { showInstallOptionsSheet = true },
                            )
                        }
                    }
                    SettingsCardGroup(
                        title = stringResource(R.string.setting_group_installing),
                        items = installingItems,
                    )
                }

                item {
                    SettingsCardGroup(
                        title = stringResource(R.string.setting_group_after_install),
                        items = listOf(
                            {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.setting_delete_apk_title),
                                    icon = Icons.Rounded.DeleteOutline,
                                    checked = uiState.deleteApkAfterInstall,
                                    onCheckedChange = viewModel::setDeleteApkAfterInstall,
                                )
                            },
                            {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.setting_auto_open_title),
                                    subtitle = stringResource(R.string.setting_auto_open_subtitle),
                                    icon = Icons.Rounded.OpenInNew,
                                    checked = uiState.autoOpenAfterInstall,
                                    onCheckedChange = viewModel::setAutoOpenAfterInstall,
                                )
                            },
                        ),
                    )
                }

                item {
                    SettingsCardGroup(
                        title = stringResource(R.string.setting_group_defaults),
                        items = listOf(
                            {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.setting_default_installer_title),
                                    subtitle = stringResource(R.string.setting_default_installer_subtitle),
                                    icon = Icons.Rounded.InstallMobile,
                                    checked = uiState.isDefaultInstaller,
                                    onCheckedChange = viewModel::toggleDefaultInstaller,
                                )
                            },
                            {
                                SettingsSwitchRow(
                                    title = stringResource(R.string.setting_default_uninstaller_title),
                                    subtitle = stringResource(R.string.setting_default_uninstaller_subtitle),
                                    icon = Icons.Rounded.DeleteSweep,
                                    checked = uiState.isDefaultUninstaller,
                                    onCheckedChange = viewModel::toggleDefaultUninstaller,
                                )
                            },
                        ),
                    )
                }
            }
        }
    }

    if (showInstallOptionsSheet) {
        InstallOptionsBottomSheet(
            uiState = uiState,
            useDhizuku = useDhizuku,
            onDismiss = { showInstallOptionsSheet = false },
            onPrivilegedOptionChanged = viewModel::setPrivilegedOption,
            onInstallerPackageChanged = viewModel::setInstallerPackageName,
            onShizukuOptionChanged = viewModel::setShizukuOption,
        )
    }

    if (showInstallPrioritySheet) {
        InstallPriorityBottomSheet(
            onDismissRequest = { showInstallPrioritySheet = false },
        )
    }
}
