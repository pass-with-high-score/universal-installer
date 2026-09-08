package app.pwhs.universalinstaller.presentation.install.dialog

import android.content.Context
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstallUiStyle
import app.pwhs.universalinstaller.presentation.install.DialogStage
import app.pwhs.universalinstaller.presentation.install.DialogTarget
import app.pwhs.universalinstaller.presentation.install.InstallUiState
import app.pwhs.universalinstaller.presentation.install.InstallViewModel
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.util.SystemIntentInstaller
import app.pwhs.universalinstaller.util.WindowBlurEffect
import timber.log.Timber

val FLOATING_SHEET_SHAPE = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SheetChrome(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        BottomSheetDefaults.DragHandle()
        content()
    }
}

@Composable
fun SheetEntryAnimation(enabled: Boolean, content: @Composable () -> Unit) {
    if (!enabled) {
        content()
        return
    }
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically { it } + fadeIn(),
        exit = slideOutVertically { it } + fadeOut(),
    ) {
        content()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogInstallContent(
    uiState: InstallUiState,
    dialogTarget: DialogTarget?,
    uiStyle: InstallUiStyle,
    isApk: Boolean,
    themeMode: ThemeMode,
    dynamicColor: Boolean,
    amoledMode: Boolean,
    themePreset: AppThemePreset,
    autoOpenAfterInstall: Boolean,
    showKeepApkOption: Boolean = false,
    keepApk: Boolean = false,
    onKeepApkChanged: (Boolean) -> Unit = {},
    strictVirusTotalCheck: Boolean,
    canInstallPackages: () -> Boolean,
    viewModel: InstallViewModel,
    onOpenInstallPermissionSettings: () -> Unit,
    onProceedInstall: () -> Unit,
    onDismissAndFinish: () -> Unit,
    onFinishAfterSuccess: (keepApk: Boolean, packageNameToOpen: String?) -> Unit,
    onScanVirusTotal: (Context) -> Unit,
) {
    val context = LocalContext.current
    var pendingRisks by remember { mutableStateOf<List<InstallRisk>>(emptyList()) }

    val handleInstallTap = {
        val info = uiState.pendingApkInfo
        val risks = if (info != null) {
            detectInstallRisks(info, strictVirusTotalCheck)
        } else {
            emptyList()
        }
        when {
            !canInstallPackages() -> viewModel.dialogPermissionRequired()
            risks.isNotEmpty() -> pendingRisks = risks
            else -> onProceedInstall()
        }
    }

    val onObbPickerResult: (List<Uri>) -> Unit = { uris ->
        uris.forEach { viewModel.attachObbFile(context, it) }
    }
    val obbPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenMultipleDocuments(),
        onObbPickerResult,
    )
    val fallbackObbPickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetMultipleContents(),
        onObbPickerResult,
    )
    val safeLaunchObbPicker = {
        val launched = runCatching {
            obbPickerLauncher.launch(arrayOf("*/*"))
        }.isSuccess
        if (!launched) {
            val fallbackLaunched = runCatching {
                fallbackObbPickerLauncher.launch("*/*")
            }.isSuccess
            if (!fallbackLaunched) {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_no_file_picker),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val darkTheme = when (themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    UniversalInstallerTheme(
        darkTheme = darkTheme,
        dynamicColor = dynamicColor,
        amoledMode = amoledMode,
        themePreset = themePreset,
    ) {
        val configuration = LocalConfiguration.current
        val screenHeight = configuration.screenHeightDp.dp
        val maxDialogHeight = screenHeight * 0.8f

        WindowBlurEffect(enabled = true)

        if (pendingRisks.isNotEmpty()) {
            RiskConfirmDialog(
                risks = pendingRisks,
                onConfirm = {
                    pendingRisks = emptyList()
                    onProceedInstall()
                },
                onCancel = {
                    pendingRisks = emptyList()
                    onDismissAndFinish()
                },
                onPrivilegedUninstall = viewModel::uninstallConflictingApp,
                onExistingAppUninstalled = {
                    viewModel.onConflictingAppUninstalled()
                    pendingRisks = pendingRisks.filterNot {
                        it is InstallRisk.SignatureMismatch || it is InstallRisk.Downgrade
                    }
                },
            )
        }

        if (uiState.dialogStage == DialogStage.None) return@UniversalInstallerTheme

        val isSheet = uiStyle == InstallUiStyle.Sheet
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures(onTap = {
                        onDismissAndFinish()
                    })
                },
            contentAlignment = if (isSheet) Alignment.BottomCenter else Alignment.Center,
        ) {
            SheetEntryAnimation(enabled = isSheet) {
                Surface(
                    modifier = (
                        if (isSheet) {
                            Modifier
                                .fillMaxWidth()
                                .navigationBarsPadding()
                                .padding(horizontal = 12.dp, vertical = 12.dp)
                                .heightIn(max = screenHeight * 0.9f)
                        } else {
                            Modifier
                                .padding(24.dp)
                                .widthIn(max = 480.dp)
                                .heightIn(max = maxDialogHeight)
                        }
                    )
                        .pointerInput(Unit) {
                            detectTapGestures(onTap = { /* consume clicks */ })
                        },
                    shape = if (isSheet) FLOATING_SHEET_SHAPE else AlertDialogDefaults.shape,
                    color = AlertDialogDefaults.containerColor,
                    tonalElevation = if (isSheet) BottomSheetDefaults.Elevation else AlertDialogDefaults.TonalElevation,
                    shadowElevation = if (isSheet) 8.dp else 12.dp,
                ) {
                    SheetChrome(enabled = isSheet) {
                        val params = generateDialogParams(
                            uiState = uiState,
                            dialogTarget = dialogTarget,
                            autoOpenAfterInstall = autoOpenAfterInstall,
                            showKeepApkOption = showKeepApkOption,
                            keepApk = keepApk,
                            onKeepApkChanged = onKeepApkChanged,
                            onInstall = handleInstallTap,
                            onCancel = onDismissAndFinish,
                            onMenu = viewModel::dialogShowMenu,
                            onUnblock = viewModel::unblockPackage,
                            onMenuBack = viewModel::dialogBackToPrepare,
                            onGrantInstallPermission = onOpenInstallPermissionSettings,
                            onCheckVirusTotal = { onScanVirusTotal(context) },
                            onRemoveObb = { obb -> viewModel.removeAttachedObb(obb.uri) },
                            onToggleSplit = viewModel::toggleSplit,
                            onAttachObb = safeLaunchObbPicker,
                            onBackground = onDismissAndFinish,
                            onOpenInstalledApp = { pkg, keepApk ->
                                viewModel.getAppLaunchIntent(pkg)?.let { intent ->
                                    intent.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                                    runCatching {
                                        context.startActivity(intent)
                                    }.onFailure { e ->
                                        Timber.e(e, "Failed to launch app %s", pkg)
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.error_cannot_open_app),
                                            Toast.LENGTH_SHORT
                                        ).show()
                                    }
                                }
                                onFinishAfterSuccess(keepApk, pkg)
                            },
                            onCloseAfterResult = { keepApk -> onFinishAfterSuccess(keepApk, null) },
                            onRetry = { viewModel.retryDialogInstall() },
                            onToggleAllUsers = viewModel::setAllUsers,
                            onSelectUserId = { viewModel.setUserId(it) },
                            onSkipParse = if (isApk) {
                                { viewModel.skipParseAndInstallSingle() }
                            } else {
                                null
                            },
                            onFallbackInstall = {
                                val apkUri = dialogTarget?.apkUri
                                if (isApk && apkUri != null) {
                                    val intent = SystemIntentInstaller.createInstallIntent(context, apkUri)
                                    val launched = intent != null && runCatching { context.startActivity(intent) }
                                        .onFailure { Timber.e(it, "Failed to launch system installer fallback") }
                                        .isSuccess
                                    if (!launched) {
                                        Toast.makeText(
                                            context,
                                            context.getString(R.string.dialog_fallback_install_unavailable),
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                    }
                                    onDismissAndFinish()
                                }
                            }.takeIf { isApk && dialogTarget?.apkUri != null },
                        )

                        PositionDialog(
                            modifier = if (uiState.dialogStage != DialogStage.Menu) {
                                Modifier.verticalScroll(rememberScrollState())
                            } else {
                                Modifier
                            },
                            centerIcon = dialogInnerWidget(params.icon),
                            centerTitle = dialogInnerWidget(params.title),
                            centerSubtitle = dialogInnerWidget(params.subtitle),
                            centerText = dialogInnerWidget(params.text),
                            centerContent = dialogInnerWidget(params.content),
                            centerButton = dialogInnerWidget(params.buttons),
                        )
                    }
                }
            }
        }
    }
}
