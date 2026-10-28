package app.pwhs.universalinstaller.presentation.install.dialog

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.datastore.preferences.core.Preferences
import app.pwhs.universalinstaller.presentation.install.InstallViewModel
import app.pwhs.core.util.PermissionMonitor
import app.pwhs.core.util.PermissionSettingsHelper
import app.pwhs.universalinstaller.presentation.install.util.InstallSessionManager
import app.pwhs.core.data.local.dataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.io.FileNotFoundException
import java.io.IOException

object DialogInstallActivityHelper {

    fun reportParseProblem(cause: Throwable, viewModel: InstallViewModel) {
        val unreadable = cause is SecurityException ||
            cause is FileNotFoundException ||
            cause is IOException
        val detail = cause.message.orEmpty()
        if (unreadable) {
            viewModel.dialogReadFailed(detail)
        } else {
            viewModel.dialogParseFailed(detail)
        }
    }

    fun canInstallPackages(
        context: Context,
        prefs: Preferences?,
        profileId: String? = null,
    ): Boolean = !InstallSessionManager.requiresInstallPermission(context, prefs, profileId) ||
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
        context.packageManager.canRequestPackageInstalls()

    fun openInstallPermissionSettings(activity: Activity) {
        PermissionMonitor.start(activity) { activity.packageManager.canRequestPackageInstalls() }
        PermissionSettingsHelper.openInstallPermission(activity)
    }

    suspend fun readInstallUiStyle(context: Context): app.pwhs.universalinstaller.domain.model.InstallUiStyle =
        runCatching {
            val prefs = context.dataStore.data.first()
            app.pwhs.universalinstaller.domain.model.InstallUiStyle.from(
                prefs[app.pwhs.universalinstaller.presentation.setting.PreferencesKeys.INSTALL_UI_STYLE]
            )
        }.getOrDefault(app.pwhs.universalinstaller.domain.model.InstallUiStyle.Dialog)

    suspend fun readExternalOpenMode(context: Context): app.pwhs.universalinstaller.domain.model.ExternalOpenMode =
        runCatching {
            val prefs = context.dataStore.data.first()
            app.pwhs.universalinstaller.domain.model.ExternalOpenMode.from(
                prefs[app.pwhs.universalinstaller.presentation.setting.PreferencesKeys.EXTERNAL_OPEN_MODE]
            )
        }.getOrDefault(app.pwhs.universalinstaller.domain.model.ExternalOpenMode.Dialog)

    fun handleNewIntent(
        activity: Activity,
        intent: Intent,
        viewModel: InstallViewModel,
        scope: kotlinx.coroutines.CoroutineScope,
    ) {
        val uris = DialogInstallUriHelper.collectIncomingUris(intent)
        if (uris.isEmpty()) return

        val hasWebUrl = uris.any { it.scheme == "http" || it.scheme == "https" }
        if (uris.size > 1 && !hasWebUrl) {
            app.pwhs.universalinstaller.IntentHandoff.postBatch(uris)
            val targetIntent = Intent(activity, app.pwhs.universalinstaller.presentation.install.InstallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            DialogInstallUriHelper.forwardIncomingUris(intent, targetIntent)
            activity.startActivity(targetIntent)
            activity.finish()
            return
        }

        val uri = if (hasWebUrl) {
            uris.first { it.scheme == "http" || it.scheme == "https" }
        } else {
            uris.first()
        }
        viewModel.dismissPendingInstall()
        viewModel.dialogStartLoading()
        if (uri.scheme == "http" || uri.scheme == "https") {
            viewModel.startDialogNetworkDownload(activity, uri) { file, fileName ->
                scope.launch {
                    DialogInstallUriHelper.parseAndPushFile(activity, file, fileName, viewModel)
                }
            }
        } else {
            scope.launch {
                runCatching {
                    DialogInstallUriHelper.parseAndPush(activity, uri, viewModel)
                }.onFailure { e ->
                    timber.log.Timber.e(e, "Parse failed for new intent $uri")
                    reportParseProblem(e, viewModel)
                }
            }
        }
    }

    fun handleDismiss(
        activity: Activity,
        viewModel: InstallViewModel,
        dialogTarget: app.pwhs.universalinstaller.presentation.install.DialogTarget?,
        dialogStage: app.pwhs.universalinstaller.presentation.install.DialogStage,
        promptNotifier: app.pwhs.universalinstaller.presentation.install.InstallPromptNotifier,
        installNotifier: app.pwhs.universalinstaller.presentation.install.InstallProgressNotifier,
        scope: kotlinx.coroutines.CoroutineScope,
        finishAfterSuccess: (Boolean, String?) -> Unit,
    ) {
        when {
            dialogStage == app.pwhs.universalinstaller.presentation.install.DialogStage.Success -> {
                finishAfterSuccess(false, null)
            }
            dialogStage is app.pwhs.universalinstaller.presentation.install.DialogStage.Installing -> {
                scope.launch {
                    val target = dialogTarget ?: withTimeoutOrNull(5000L) {
                        viewModel.dialogTarget.filterNotNull().first()
                    }
                    if (target != null) {
                        installNotifier.track(
                            sessionId = target.sessionId,
                            packageName = target.packageName,
                            appName = target.appName,
                            iconPath = target.iconPath,
                        )
                    }
                    android.widget.Toast.makeText(
                        activity,
                        activity.getString(app.pwhs.universalinstaller.R.string.install_running_in_background),
                        android.widget.Toast.LENGTH_SHORT,
                    ).show()
                    activity.finish()
                }
            }
            dialogStage == app.pwhs.universalinstaller.presentation.install.DialogStage.Prepare ||
            dialogStage is app.pwhs.universalinstaller.presentation.install.DialogStage.Menu ||
            viewModel.uiState.value.pendingApkInfo != null -> {
                scope.launch {
                    val canPost = promptNotifier.canPost()
                    val entry = if (canPost) viewModel.stashPendingInstall() else null
                    if (entry != null && promptNotifier.prompt(entry)) {
                        activity.finish()
                    } else {
                        viewModel.dismissPendingInstall()
                        viewModel.dialogClose()
                        viewModel.clearDialogTarget()
                        activity.finish()
                    }
                }
            }
            else -> {
                val isDownloading = viewModel.uiState.value.dialogDownloadProgress != null
                if (!isDownloading) {
                    viewModel.dismissPendingInstall()
                    viewModel.dialogClose()
                    viewModel.clearDialogTarget()
                }
                activity.finish()
            }
        }
    }
}
