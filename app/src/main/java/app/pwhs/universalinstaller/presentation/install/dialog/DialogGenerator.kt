package app.pwhs.universalinstaller.presentation.install.dialog

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.stringResource
import app.pwhs.universalinstaller.R
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Security
import app.pwhs.universalinstaller.presentation.install.AttachedObb
import app.pwhs.universalinstaller.presentation.install.DialogStage
import app.pwhs.universalinstaller.presentation.install.DialogTarget
import app.pwhs.universalinstaller.presentation.install.InstallUiState

@Composable
fun generateDialogParams(
    uiState: InstallUiState,
    dialogTarget: DialogTarget?,
    autoOpenAfterInstall: Boolean,
    showKeepApkOption: Boolean = false,
    keepApk: Boolean = false,
    onKeepApkChanged: (Boolean) -> Unit = {},
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onMenu: () -> Unit,
    onUnblock: (String) -> Unit = {},
    onMenuBack: () -> Unit,
    onCheckVirusTotal: () -> Unit,
    onRemoveObb: (AttachedObb) -> Unit,
    onToggleSplit: (Int) -> Unit,
    onAttachObb: () -> Unit,
    onBackground: () -> Unit,
    onOpenInstalledApp: (String, Boolean) -> Unit,
    onCloseAfterResult: (Boolean) -> Unit,
    onRetry: (() -> Unit)? = null,
    onToggleAllUsers: (Boolean) -> Unit,
    onSelectUserId: (Int?) -> Unit,
    onSkipParse: (() -> Unit)? = null,
    onFallbackInstall: (() -> Unit)? = null,
    onGrantInstallPermission: () -> Unit = {},
    onMergeSplits: (() -> Unit)? = null,
    uiStyle: app.pwhs.universalinstaller.domain.model.InstallUiStyle = app.pwhs.universalinstaller.domain.model.InstallUiStyle.Dialog,
    onChangeEngine: (() -> Unit)? = null,
    onOpenAdvanced: (() -> Unit)? = null,
    onOpenDetails: (() -> Unit)? = null,
): DialogParams {
    return when (val stage = uiState.dialogStage) {
        DialogStage.Loading -> {
            val downloadProgress = uiState.dialogDownloadProgress
            if (downloadProgress != null) {
                DialogParams(
                    content = DialogInnerParams("downloading") {
                        DialogDownloadingContent(
                            progress = downloadProgress,
                            onBackground = onBackground,
                            onCancel = onCancel,
                        )
                    }
                )
            } else {
                DialogParams(
                    content = DialogInnerParams("loading") {
                        LoadingContent(
                            onSkipParse = onSkipParse,
                            onCancel = onCancel,
                        )
                    }
                )
            }
        }

        DialogStage.Prepare -> {
            val info = uiState.pendingApkInfo
            if (info == null) {
                DialogParams(
                    content = DialogInnerParams("loading") {
                        LoadingContent(
                            onSkipParse = onSkipParse,
                            onCancel = onCancel,
                        )
                    }
                )
            } else {
                DialogParams(
                    icon = DialogInnerParams("icon_${info.packageName}") {
                        AnimatedContent(
                            targetState = info.icon,
                            transitionSpec = {
                                fadeIn(tween(300)) togetherWith fadeOut(tween(150))
                            },
                            label = "IconAnimation",
                        ) { drawable ->
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(16.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                if (drawable != null) {
                                    Image(
                                        bitmap = drawable.toBitmap(128, 128).asImageBitmap(),
                                        contentDescription = info.appName,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Android,
                                        contentDescription = null,
                                        modifier = Modifier.size(64.dp),
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                                    )
                                }
                            }
                        }
                    },
                    title = DialogInnerParams("title_${info.packageName}") {
                        Text(
                            text = info.appName.ifBlank { info.packageName },
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    subtitle = DialogInnerParams("subtitle_${info.packageName}") {
                        Text(
                            text = info.packageName,
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    content = DialogInnerParams("content_${info.packageName}") {
                        DialogPrepareContent(
                            apkInfo = info,
                            installedVersionName = info.installedVersionName,
                            installedVersionCode = info.installedVersionCode,
                            onInstall = onInstall,
                            onMenu = onMenu,
                            onCancel = onCancel,
                            onUnblock = onUnblock,
                            onCheckVirusTotal = onCheckVirusTotal,
                            showKeepApkOption = showKeepApkOption,
                            keepApk = keepApk,
                            onKeepApkChanged = onKeepApkChanged,
                        )
                    }
                )
            }
        }

        is DialogStage.Menu -> {
            val info = uiState.pendingApkInfo
            if (info == null) {
                DialogParams(content = DialogInnerParams("loading") { LoadingContent(onCancel = onCancel) })
            } else {
                DialogParams(
                    title = DialogInnerParams("menu_title") {
                        Text(text = stringResource(R.string.dialog_menu_title))
                    },
                    subtitle = DialogInnerParams("menu_subtitle") {
                        Text(
                            text = info.appName.ifBlank { info.packageName },
                            maxLines = 1,
                            modifier = Modifier.basicMarquee()
                        )
                    },
                    content = DialogInnerParams("menu_content") {
                        DialogMenuContent(
                            apkInfo = info,
                            attachedObbFiles = uiState.attachedObbFiles,
                            allUsers = uiState.allUsers,
                            selectedUserId = uiState.selectedUserId,
                            onBack = onMenuBack,
                            onInstall = onInstall,
                            onCheckVirusTotal = onCheckVirusTotal,
                            onRemoveObb = onRemoveObb,
                            onToggleSplit = onToggleSplit,
                            onAttachObb = onAttachObb,
                            onToggleAllUsers = onToggleAllUsers,
                            onSelectUserId = onSelectUserId,
                            onMergeSplits = onMergeSplits,
                            initialTab = stage.initialTab,
                        )
                    }
                )
            }
        }

        DialogStage.Installing -> {
            if (dialogTarget == null) {
                DialogParams(
                    content = DialogInnerParams("loading") {
                        LoadingContent(
                            onCancel = onCancel,
                        )
                    }
                )
            } else {
                val sp = uiState.sessionsProgress.find { it.id == dialogTarget.sessionId }
                val fraction = sp?.let {
                    if (it.progressMax > 0) it.currentProgress.toFloat() / it.progressMax else null
                }
                DialogParams(
                    content = DialogInnerParams("installing_${dialogTarget.sessionId}") {
                        DialogInstallingContent(
                            target = dialogTarget,
                            progressFraction = fraction,
                            onBackground = onBackground,
                        )
                    }
                )
            }
        }

        DialogStage.Success -> {
            if (dialogTarget == null) {
                DialogParams(content = DialogInnerParams("loading") { LoadingContent(onCancel = onCancel) })
            } else {
                DialogParams(
                    content = DialogInnerParams("success_${dialogTarget.sessionId}") {
                        val context = LocalContext.current
                        var canOpen by remember(dialogTarget.packageName) {
                            mutableStateOf(
                                dialogTarget.packageName.isNotBlank() &&
                                        context.packageManager.getLaunchIntentForPackage(dialogTarget.packageName) != null
                            )
                        }
                        LaunchedEffect(dialogTarget.packageName) {
                            if (!canOpen && dialogTarget.packageName.isNotBlank()) {
                                repeat(10) {
                                    val intent = context.packageManager.getLaunchIntentForPackage(dialogTarget.packageName)
                                    if (intent != null) {
                                        canOpen = true
                                        return@LaunchedEffect
                                    }
                                    kotlinx.coroutines.delay(250)
                                }
                            }
                        }
                        DialogSuccessContent(
                            target = dialogTarget,
                            canOpen = canOpen,
                            autoOpenCountdownStartSeconds = if (autoOpenAfterInstall) 3 else null,
                            showKeepApkOption = dialogTarget.deleteAfterInstall,
                            onOpen = { keepApk -> onOpenInstalledApp(dialogTarget.packageName, keepApk) },
                            onDone = onCloseAfterResult,
                        )
                    }
                )
            }
        }

        is DialogStage.Failed -> {
            val target = dialogTarget ?: DialogTarget(
                sessionId = java.util.UUID.randomUUID(),
                packageName = uiState.pendingApkInfo?.packageName.orEmpty(),
                appName = uiState.pendingApkInfo?.appName.orEmpty(),
                iconPath = null,
                apkUri = null,
            )
            DialogParams(
                content = DialogInnerParams("failed_${target.sessionId}") {
                    DialogFailedContent(
                        target = target,
                        errorMessage = stage.errorMessage,
                        onClose = { onCloseAfterResult(false) },
                        onRetry = onRetry,
                        onFallbackInstall = onFallbackInstall,
                        uiStyle = uiStyle,
                        onChangeEngine = onChangeEngine,
                        onOpenAdvanced = onOpenAdvanced,
                        onOpenDetails = onOpenDetails,
                    )
                }
            )
        }

        is DialogStage.ReadFailed -> DialogParams(
            content = DialogInnerParams("read_failed") {
                DialogProblemContent(
                    icon = Icons.Rounded.ErrorOutline,
                    title = stringResource(R.string.dialog_read_failed_title),
                    explanation = stringResource(R.string.dialog_read_failed_text),
                    detail = stage.reason,
                    actionLabel = onFallbackInstall?.let { stringResource(R.string.dialog_failed_fallback_install, "Install via System Installer") },
                    onAction = onFallbackInstall,
                    onClose = { onCloseAfterResult(false) },
                )
            }
        )

        is DialogStage.ParseFailed -> DialogParams(
            content = DialogInnerParams("parse_failed") {
                DialogProblemContent(
                    icon = Icons.Rounded.ErrorOutline,
                    title = stringResource(R.string.dialog_parse_failed_title),
                    explanation = stringResource(R.string.dialog_parse_failed_text),
                    detail = stage.reason,
                    actionLabel = onFallbackInstall?.let { stringResource(R.string.dialog_failed_fallback_install, "Install via System Installer") },
                    onAction = onFallbackInstall,
                    onClose = { onCloseAfterResult(false) },
                )
            }
        )

        DialogStage.PermissionRequired -> DialogParams(
            content = DialogInnerParams("permission_required") {
                DialogProblemContent(
                    icon = Icons.Rounded.Security,
                    title = stringResource(R.string.dialog_permission_title),
                    explanation = stringResource(R.string.dialog_permission_text),
                    actionLabel = stringResource(R.string.dialog_permission_grant),
                    onAction = onGrantInstallPermission,
                    onClose = onCancel,
                )
            }
        )

        DialogStage.None -> DialogParams()
    }
}
