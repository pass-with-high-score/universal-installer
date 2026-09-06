package app.pwhs.universalinstaller.presentation.install

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import app.pwhs.core.util.StorageUtil
import app.pwhs.universalinstaller.R
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.core.util.PermissionMonitor
import app.pwhs.universalinstaller.IntentHandoff
import app.pwhs.universalinstaller.domain.model.ExternalOpenMode
import app.pwhs.universalinstaller.domain.model.InstallUiStyle
import app.pwhs.universalinstaller.presentation.install.dialog.DialogInstallContent
import app.pwhs.universalinstaller.presentation.install.dialog.DialogInstallUriHelper
import app.pwhs.universalinstaller.presentation.install.dialog.InsufficientStorageDialog
import app.pwhs.universalinstaller.presentation.install.dialog.HeadlessNotificationInstall
import app.pwhs.universalinstaller.presentation.install.util.SourceFileDeleter
import app.pwhs.universalinstaller.presentation.install.util.CallerAppDetector
import app.pwhs.universalinstaller.domain.manager.AutoApproveApps
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.SecurityLevel
import app.pwhs.universalinstaller.util.LocaleHelper
import app.pwhs.universalinstaller.util.extension.getDisplayName
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import androidx.fragment.app.FragmentActivity
import timber.log.Timber
import java.io.FileNotFoundException
import java.io.IOException

class DialogInstallActivity : FragmentActivity() {

    private val viewModel: InstallViewModel by viewModel()
    private val installNotifier: InstallProgressNotifier by inject()
    private val promptNotifier: InstallPromptNotifier by inject()

    companion object {
        const val EXTRA_PENDING_ID = "pending_install_id"
        const val EXTRA_PENDING_SHOW_UI = "pending_install_show_ui"
        private const val SESSION_ID_TIMEOUT_MS = 3_000L
        private const val LOG = "NotifInstall"
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun reportParseProblem(cause: Throwable) {
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

    private fun canInstallPackages(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
            packageManager.canRequestPackageInstalls()

    private fun openInstallPermissionSettings() {
        PermissionMonitor.start(this) { packageManager.canRequestPackageInstalls() }
        startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:$packageName"),
            ),
        )
    }

    private var skipInitialParse = false
    private val forceDialogUi = MutableStateFlow(false)

    private fun fallbackToDialog() {
        forceDialogUi.value = true
    }

    private fun installFromNotificationAction() {
        Timber.i("$LOG: firing install from the notification action")
        viewModel.confirmInstall(trackDialogTarget = true)
        lifecycleScope.launch {
            val target = withTimeoutOrNull(SESSION_ID_TIMEOUT_MS) {
                viewModel.dialogTarget.filterNotNull().first()
            }
            if (target != null) {
                installNotifier.track(
                    sessionId = target.sessionId,
                    packageName = target.packageName,
                    appName = target.appName,
                    iconPath = target.iconPath,
                )
            } else {
                Timber.w("No session id within ${SESSION_ID_TIMEOUT_MS}ms — install continues untracked")
            }
            viewModel.clearDialogTarget()
            finish()
        }
    }

    private suspend fun readInstallUiStyle(): InstallUiStyle = runCatching {
        InstallUiStyle.from(dataStore.data.first()[PreferencesKeys.INSTALL_UI_STYLE])
    }.getOrDefault(InstallUiStyle.Dialog)

    private suspend fun readExternalOpenMode(): ExternalOpenMode = runCatching {
        ExternalOpenMode.from(dataStore.data.first()[PreferencesKeys.EXTERNAL_OPEN_MODE])
    }.getOrDefault(ExternalOpenMode.Dialog)

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()

        val pendingId = intent.getStringExtra(EXTRA_PENDING_ID)
        var restoredEntry: PendingInstallStore.Entry? = null
        if (pendingId != null) {
            val entry = PendingInstallStore.consume(pendingId)
            promptNotifier.cancel(pendingId)
            Timber.i("$LOG: resuming $pendingId, found=${entry != null}")
            if (entry == null) {
                Timber.w("Install prompt $pendingId is stale — parse no longer in memory")
                finish()
                return
            }
            viewModel.restorePendingInstall(entry)
            if (!intent.getBooleanExtra(EXTRA_PENDING_SHOW_UI, false)) {
                installFromNotificationAction()
                return
            }
            restoredEntry = entry
        }

        val incomingUris = restoredEntry?.let { listOf(it.apkUris.first()) }
            ?: DialogInstallUriHelper.collectIncomingUris(intent)
        if (incomingUris.isEmpty()) {
            Timber.w("DialogInstallActivity launched without any content URIs — bailing")
            finish()
            return
        }

        if (incomingUris.size > 1) {
            IntentHandoff.postBatch(incomingUris)
            val targetIntent = Intent(this, InstallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            DialogInstallUriHelper.forwardIncomingUris(intent, targetIntent)
            startActivity(targetIntent)
            finish()
            return
        }

        val incomingUri = incomingUris.first()
        viewModel.dialogStartLoading()
        skipInitialParse = restoredEntry != null

        // Detect which app triggered this install intent.
        val callerPackage = CallerAppDetector.detectCallerPackage(this, intent)

        setContent {
            val mode by produceState<ExternalOpenMode?>(null) { value = readExternalOpenMode() }
            val forcedToDialog by forceDialogUi.collectAsState()
            val uiStyle by produceState(InstallUiStyle.Dialog) { value = readInstallUiStyle() }
            val resolvedMode = mode
            if (resolvedMode == null && !forcedToDialog) return@setContent

            val context = this@DialogInstallActivity
            val cancelAndFinish = {
                viewModel.dismissPendingInstall()
                viewModel.dialogClose()
                viewModel.clearDialogTarget()
                finish()
            }
            val securityGate = app.pwhs.universalinstaller.presentation.install.util.rememberInstallSecurityGate(
                context = context,
                onCancel = cancelAndFinish,
            )

            if (!forcedToDialog && restoredEntry == null && resolvedMode != ExternalOpenMode.Dialog && !securityGate.isPinRequired) {
                HeadlessNotificationInstall(
                    mode = resolvedMode!!,
                    uri = incomingUri,
                    viewModel = viewModel,
                    promptNotifier = promptNotifier,
                    callerPackage = callerPackage,
                    onFallbackToDialog = { skip ->
                        skipInitialParse = skip
                        fallbackToDialog()
                    },
                    onInstallFromNotificationAction = ::installFromNotificationAction,
                    onFinish = ::finish,
                )
                return@setContent
            }

            val uiState by viewModel.uiState.collectAsState()
            val isApk = remember(incomingUri) {
                val displayName = context.contentResolver.getDisplayName(incomingUri)
                val ext = displayName.substringAfterLast('.', "").lowercase()
                ext == "apk"
            }

            LaunchedEffect(incomingUri) {
                if (skipInitialParse) return@LaunchedEffect
                if (incomingUri.scheme == "http" || incomingUri.scheme == "https") {
                    viewModel.startDialogNetworkDownload(context, incomingUri) { file, fileName ->
                        lifecycleScope.launch {
                            DialogInstallUriHelper.parseAndPushFile(context, file, fileName, viewModel)
                        }
                    }
                } else {
                    runCatching {
                        DialogInstallUriHelper.parseAndPush(context, incomingUri, viewModel)
                    }.onFailure { e ->
                        Timber.e(e, "Parse failed for $incomingUri")
                        reportParseProblem(e)
                    }
                }
            }

            val dialogTarget by viewModel.dialogTarget.collectAsState()
            val prefs by context.dataStore.data.collectAsState(initial = null)
            val autoOpenAfterInstall = prefs?.get(PreferencesKeys.AUTO_OPEN_AFTER_INSTALL) ?: false
            val autoConfirmExternalInstall = prefs?.get(PreferencesKeys.AUTO_CONFIRM_EXTERNAL_INSTALL) ?: false
            val isCallerAutoApproved = AutoApproveApps.isAutoApproved(prefs, callerPackage)
            val deleteApkAfterInstall = prefs?.get(PreferencesKeys.DELETE_APK_AFTER_INSTALL) ?: false
            var keepApk by remember(dialogTarget?.sessionId) { mutableStateOf(false) }
            val strictVirusTotalCheck = SecurityLevel.from(
                stored = prefs?.get(PreferencesKeys.SECURITY_LEVEL),
                legacyStrict = prefs?.get(PreferencesKeys.STRICT_VIRUSTOTAL_CHECK) ?: false,
            ) == SecurityLevel.Strict

            var sessionEverSeen by remember(dialogTarget?.sessionId) { mutableStateOf(false) }

            LaunchedEffect(dialogTarget, uiState.sessions, uiState.dialogStage) {
                val target = dialogTarget ?: return@LaunchedEffect
                if (uiState.dialogStage !is DialogStage.Installing) return@LaunchedEffect
                val session = uiState.sessions.find { it.id == target.sessionId }
                if (session != null) {
                    sessionEverSeen = true
                    val msg = session.error.resolve(this@DialogInstallActivity)
                    if (msg.isNotBlank()) {
                        viewModel.dialogInstallFailed(msg)
                    }
                } else if (sessionEverSeen) {
                    viewModel.dialogInstallSuccess()
                }
            }

            val proceedInstall = {
                val apkSize = uiState.pendingApkInfo?.fileSizeBytes ?: 0L
                if (!StorageUtil.hasSufficientStorage(apkSize)) {
                    viewModel.showStorageWarning(apkSize)
                } else {
                    securityGate.authenticate {
                        viewModel.dialogStartInstalling()
                        viewModel.confirmInstall(trackDialogTarget = true, keepApk = keepApk)
                    }
                }
            }

            LaunchedEffect(uiState.pendingApkInfo, uiState.dialogStage) {
                if (uiState.pendingApkInfo != null && uiState.dialogStage == DialogStage.Loading) {
                    viewModel.dialogShowPrepare()
                }
            }

            val finishAfterSuccess: (Boolean, String?) -> Unit = { keepApk, _ ->
                val target = dialogTarget
                lifecycleScope.launch {
                    if (target?.deleteAfterInstall == true && !keepApk) {
                        target.apkUri?.let { SourceFileDeleter.deleteSourceFileAndWarn(context, it) }
                    }
                    viewModel.dialogClose()
                    viewModel.clearDialogTarget()
                    finish()
                }
            }

            LaunchedEffect(uiState.dialogStage, autoConfirmExternalInstall, isCallerAutoApproved, autoOpenAfterInstall) {
                val shouldAutoInstall = (autoConfirmExternalInstall || isCallerAutoApproved) && !securityGate.isPinRequired
                if (uiState.dialogStage == DialogStage.Prepare && shouldAutoInstall) {
                    Timber.i("Auto-approving install: autoConfirm=$autoConfirmExternalInstall, callerApproved=$isCallerAutoApproved (caller=$callerPackage)")
                    proceedInstall()
                } else if (uiState.dialogStage == DialogStage.Success && shouldAutoInstall) {
                    if (autoOpenAfterInstall) {
                        dialogTarget?.packageName?.takeIf { it.isNotBlank() }?.let { pkg ->
                            viewModel.getAppLaunchIntent(pkg)?.let { intent ->
                                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching {
                                    context.startActivity(intent)
                                }.onFailure { e ->
                                    Timber.e(e, "Failed to auto-launch app %s", pkg)
                                    Toast.makeText(
                                        context,
                                        context.getString(R.string.error_cannot_open_app),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                }
                            }
                        }
                    }
                    finishAfterSuccess(false, null)
                }
            }

            val handoffInstall = {
                val t = dialogTarget
                val stage = uiState.dialogStage
                if (t != null && (stage is DialogStage.Installing || stage is DialogStage.None)) {
                    installNotifier.track(
                        sessionId = t.sessionId,
                        packageName = t.packageName,
                        appName = t.appName,
                        iconPath = t.iconPath,
                    )
                }
            }

            val dismissAndFinish = {
                if (uiState.dialogStage == DialogStage.Success) {
                    finishAfterSuccess(false, null)
                } else {
                    handoffInstall()
                    val isDownloading = uiState.dialogDownloadProgress != null
                    if (!isDownloading) {
                        viewModel.dismissPendingInstall()
                        viewModel.dialogClose()
                        viewModel.clearDialogTarget()
                    }
                    finish()
                }
            }

            BackHandler {
                dismissAndFinish()
            }

            val themeModeName = prefs?.get(stringPreferencesKey("theme_mode")) ?: ThemeMode.System.name
            val themeMode = ThemeMode.entries.find { it.name == themeModeName } ?: ThemeMode.System
            val dynamicColor = prefs?.get(booleanPreferencesKey("dynamic_color")) ?: false
            val amoledMode = prefs?.get(booleanPreferencesKey("amoled_mode")) ?: false
            val presetName = prefs?.get(stringPreferencesKey("theme_preset")) ?: AppThemePreset.Orange.name
            val themePreset = AppThemePreset.entries.find { it.name == presetName } ?: AppThemePreset.Orange

            DialogInstallContent(
                uiState = uiState,
                dialogTarget = dialogTarget,
                uiStyle = uiStyle,
                isApk = isApk,
                themeMode = themeMode,
                dynamicColor = dynamicColor,
                amoledMode = amoledMode,
                themePreset = themePreset,
                autoOpenAfterInstall = autoOpenAfterInstall,
                showKeepApkOption = deleteApkAfterInstall,
                keepApk = keepApk,
                onKeepApkChanged = { keepApk = it },
                strictVirusTotalCheck = strictVirusTotalCheck,
                canInstallPackages = ::canInstallPackages,
                viewModel = viewModel,
                onOpenInstallPermissionSettings = ::openInstallPermissionSettings,
                onProceedInstall = proceedInstall,
                onDismissAndFinish = dismissAndFinish,
                onFinishAfterSuccess = finishAfterSuccess,
                onScanVirusTotal = { viewModel.scanVirusTotal(it) },
            )

            val storageWarning by viewModel.storageWarningInfo.collectAsState()
            storageWarning?.let { warning ->
                InsufficientStorageDialog(
                    warningInfo = warning,
                    onDismiss = viewModel::dismissStorageWarning,
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val uris = DialogInstallUriHelper.collectIncomingUris(intent)
        if (uris.isEmpty()) return

        if (uris.size > 1) {
            IntentHandoff.postBatch(uris)
            val targetIntent = Intent(this, InstallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            DialogInstallUriHelper.forwardIncomingUris(intent, targetIntent)
            startActivity(targetIntent)
            finish()
            return
        }

        val uri = uris.first()
        viewModel.dismissPendingInstall()
        viewModel.dialogStartLoading()
        val context = this
        if (uri.scheme == "http" || uri.scheme == "https") {
            viewModel.startDialogNetworkDownload(context, uri) { file, fileName ->
                lifecycleScope.launch {
                    DialogInstallUriHelper.parseAndPushFile(context, file, fileName, viewModel)
                }
            }
        } else {
            lifecycleScope.launch {
                runCatching {
                    DialogInstallUriHelper.parseAndPush(context, uri, viewModel)
                }.onFailure { e ->
                    Timber.e(e, "Parse failed for new intent $uri")
                    reportParseProblem(e)
                }
            }
        }
    }

    override fun finish() {
        super.finish()
        finishAndRemoveTask()
    }
}
