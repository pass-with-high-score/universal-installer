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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.compose.ui.res.stringResource
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.AttachedObb
import app.pwhs.universalinstaller.presentation.install.DialogStage
import app.pwhs.universalinstaller.presentation.install.DialogTarget
import app.pwhs.universalinstaller.presentation.install.InstallUiState

@Composable
fun generateDialogParams(
    uiState: InstallUiState,
    dialogTarget: DialogTarget?,
    autoOpenAfterInstall: Boolean,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onMenu: () -> Unit,
    onMenuBack: () -> Unit,
    onCheckVirusTotal: () -> Unit,
    onRemoveObb: (AttachedObb) -> Unit,
    onToggleSplit: (Int) -> Unit,
    onAttachObb: () -> Unit,
    onBackground: () -> Unit,
    onOpenInstalledApp: (String) -> Unit,
    onCloseAfterResult: () -> Unit,
    onRetry: () -> Unit,
    onToggleAllUsers: (Boolean) -> Unit,
    onSelectUserId: (Int?) -> Unit,
): DialogParams {
    return when (val stage = uiState.dialogStage) {
        DialogStage.Loading -> {
            DialogParams(
                content = DialogInnerParams("loading") {
                    LoadingContent()
                }
            )
        }

        DialogStage.Prepare -> {
            val info = uiState.pendingApkInfo
            if (info == null) {
                DialogParams(content = DialogInnerParams("loading") { LoadingContent() })
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
                        )
                    }
                )
            }
        }

        DialogStage.Menu -> {
            val info = uiState.pendingApkInfo
            if (info == null) {
                DialogParams(content = DialogInnerParams("loading") { LoadingContent() })
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
                        )
                    }
                )
            }
        }

        DialogStage.Installing -> {
            if (dialogTarget == null) {
                DialogParams(content = DialogInnerParams("loading") { LoadingContent() })
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
                DialogParams(content = DialogInnerParams("loading") { LoadingContent() })
            } else {
                DialogParams(
                    content = DialogInnerParams("success_${dialogTarget.sessionId}") {
                        val context = LocalContext.current
                        val canOpen = remember(dialogTarget.packageName) {
                            dialogTarget.packageName.isNotBlank() &&
                                    context.packageManager.getLaunchIntentForPackage(dialogTarget.packageName) != null
                        }
                        DialogSuccessContent(
                            target = dialogTarget,
                            canOpen = canOpen,
                            autoOpenCountdownStartSeconds = if (autoOpenAfterInstall) 3 else null,
                            onOpen = { onOpenInstalledApp(dialogTarget.packageName) },
                            onDone = onCloseAfterResult,
                        )
                    }
                )
            }
        }

        is DialogStage.Failed -> {
            if (dialogTarget == null) {
                DialogParams(content = DialogInnerParams("loading") { LoadingContent() })
            } else {
                DialogParams(
                    content = DialogInnerParams("failed_${dialogTarget.sessionId}") {
                        DialogFailedContent(
                            target = dialogTarget,
                            errorMessage = stage.errorMessage,
                            onClose = onCloseAfterResult,
                            onRetry = onRetry,
                        )
                    }
                )
            }
        }

        DialogStage.None -> DialogParams()
    }
}
