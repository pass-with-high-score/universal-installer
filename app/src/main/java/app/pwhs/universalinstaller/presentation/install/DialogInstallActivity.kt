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
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.lifecycle.lifecycleScope
import app.pwhs.universalinstaller.presentation.install.util.InstallSessionManager
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.domain.AppThemePreset
import app.pwhs.core.domain.ThemeMode
import app.pwhs.core.util.PermissionMonitor
import app.pwhs.universalinstaller.IntentHandoff
import app.pwhs.universalinstaller.domain.model.ExternalOpenMode
import app.pwhs.universalinstaller.domain.model.InstallUiStyle
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.presentation.install.dialog.detectInstallRisks
import app.pwhs.universalinstaller.presentation.install.dialog.DialogInstallContent
import app.pwhs.universalinstaller.presentation.install.dialog.DialogInstallUriHelper
import app.pwhs.universalinstaller.presentation.install.dialog.InsufficientStorageDialog
import app.pwhs.universalinstaller.presentation.install.dialog.HeadlessNotificationInstall
import app.pwhs.universalinstaller.presentation.install.dialog.InstallRisk
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
import app.pwhs.universalinstaller.presentation.install.dialog.DialogInstallActivityHelper
import app.pwhs.universalinstaller.presentation.install.dialog.DialogInstallFallbackDialog
import app.pwhs.universalinstaller.domain.manager.ProfileManager
import app.pwhs.universalinstaller.util.DhizukuCompat
import app.pwhs.universalinstaller.util.MicroGCompat
import app.pwhs.universalinstaller.util.ShizukuServices
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
        private const val SESSION_ID_TIMEOUT_MS = 10_000L
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

    private fun reportParseProblem(cause: Throwable) =
        DialogInstallActivityHelper.reportParseProblem(cause, viewModel)

    private fun canInstallPackages(
        prefs: Preferences?,
        profileId: String? = null,
    ): Boolean = DialogInstallActivityHelper.canInstallPackages(this, prefs, profileId)

    private fun openInstallPermissionSettings() =
        DialogInstallActivityHelper.openInstallPermissionSettings(this)

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

    private suspend fun readInstallUiStyle(): InstallUiStyle =
        DialogInstallActivityHelper.readInstallUiStyle(this)

    private suspend fun readExternalOpenMode(): ExternalOpenMode =
        DialogInstallActivityHelper.readExternalOpenMode(this)

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

        val hasWebUrl = incomingUris.any { it.scheme == "http" || it.scheme == "https" }
        if (incomingUris.size > 1 && !hasWebUrl) {
            IntentHandoff.postBatch(incomingUris)
            val targetIntent = Intent(this, InstallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            DialogInstallUriHelper.forwardIncomingUris(intent, targetIntent)
            startActivity(targetIntent)
            finish()
            return
        }

        val incomingUri = if (hasWebUrl) {
            incomingUris.first { it.scheme == "http" || it.scheme == "https" }
        } else {
            incomingUris.first()
        }
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
            val blockOnTrackers = prefs?.get(PreferencesKeys.AUTO_APPROVE_BLOCK_TRACKERS) ?: false
            var autoBlockedRisks by remember { mutableStateOf<List<InstallRisk>>(emptyList()) }
            var keepApk by remember(dialogTarget?.sessionId) { mutableStateOf(false) }
            val vtDisabledForAutoInstall = prefs?.get(PreferencesKeys.DISABLE_VIRUSTOTAL) ?: false
            val strictVirusTotalCheck = !(prefs?.get(PreferencesKeys.DISABLE_VIRUSTOTAL) ?: false) &&
                SecurityLevel.from(
                stored = prefs?.get(PreferencesKeys.SECURITY_LEVEL),
                legacyStrict = prefs?.get(PreferencesKeys.STRICT_VIRUSTOTAL_CHECK) ?: false,
            ) == SecurityLevel.Strict

            val isRetry by viewModel.isRetry.collectAsState()
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

            var pendingFallbackBackend by remember { mutableStateOf<String?>(null) }

            val proceedInstall = {
                val apkSize = uiState.pendingApkInfo?.fileSizeBytes ?: 0L
                if (!StorageUtil.hasSufficientStorage(apkSize)) {
                    viewModel.showStorageWarning(apkSize)
                } else {
                    val profiles = ProfileManager.parseProfiles(prefs?.get(PreferencesKeys.INSTALLER_PROFILES))
                    val profile = profiles.find { it.id == uiState.selectedProfileId }
                    val configuredBackend = profile?.preferredBackend ?: when {
                        prefs?.get(PreferencesKeys.USE_SHIZUKU) == true -> "Shizuku"
                        prefs?.get(PreferencesKeys.USE_ROOT) == true -> "Root"
                        prefs?.get(PreferencesKeys.USE_DHIZUKU) == true -> "Dhizuku"
                        prefs?.get(PreferencesKeys.USE_CUSTOM_AUTHORIZER) == true -> "Custom"
                        prefs?.get(PreferencesKeys.USE_MICROG) == true -> "MicroG"
                        else -> "Default"
                    }
                    val isBackendReady = when (configuredBackend) {
                        "Shizuku" -> InstallSessionManager.isShizukuReadyForInstall(context)
                        "Dhizuku" -> DhizukuCompat.isReady(context)
                        "MicroG" -> MicroGCompat.isAvailable(context)
                        else -> true
                    }
                    if (configuredBackend != "Default" && !isBackendReady) {
                        pendingFallbackBackend = configuredBackend
                    } else {
                        securityGate.authenticate {
                            viewModel.dialogStartInstalling()
                            viewModel.confirmInstall(trackDialogTarget = true, keepApk = keepApk)
                        }
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
                target?.sessionId?.let { installNotifier.untrack(it) }
                lifecycleScope.launch {
                    if (target?.deleteAfterInstall == true && !keepApk) {
                        target.apkUri?.let { SourceFileDeleter.deleteSourceFileAndWarn(context, it) }
                    }
                    viewModel.dismissPendingInstall()
                    viewModel.dialogClose()
                    viewModel.clearDialogTarget()
                    finish()
                }
            }

            LaunchedEffect(uiState.dialogStage, autoConfirmExternalInstall, isCallerAutoApproved, autoOpenAfterInstall, vtDisabledForAutoInstall, uiState.pendingApkInfo) {
                val apkInfo = uiState.pendingApkInfo
                val vtDisabled = vtDisabledForAutoInstall
                // Risks are a pure function of the APK info and the settings, so refresh the
                // remembered blocked list whenever the effect re-runs (e.g. the VirusTotal
                // toggle changed in another window): VT risks drop when disabled and return
                // when re-enabled. The list stays empty until a block actually happened.
                if (autoBlockedRisks.isNotEmpty() && apkInfo != null) {
                    autoBlockedRisks = detectInstallRisks(
                        apkInfo,
                        strictVirusTotalCheck,
                        blockOnTrackers,
                        vtDisabled,
                    )
                }
                // With VirusTotal disabled a stale SCANNING/verdict from an earlier session
                // must not delay auto-install or block it with raw VirusTotal counts.
                val isScanning = (!vtDisabled && apkInfo?.vtResult?.status == VtStatus.SCANNING) ||
                    (blockOnTrackers && apkInfo?.isScanningTrackers == true)
                if (isScanning) return@LaunchedEffect

                val risks = if (apkInfo != null) detectInstallRisks(
                    apkInfo,
                    strictVirusTotalCheck,
                    blockOnTrackers,
                    vtDisabled,
                ) else emptyList()
                val hasVtFlags = !vtDisabled && apkInfo?.vtResult?.let { it.malicious > 0 || it.suspicious > 0 } == true
                val hasSecurityFlags = risks.isNotEmpty() || hasVtFlags
                val shouldAutoInstall = (autoConfirmExternalInstall || isCallerAutoApproved) && !securityGate.isPinRequired && !hasSecurityFlags && !isRetry
                if (hasSecurityFlags && (autoConfirmExternalInstall || isCallerAutoApproved)) {
                    Timber.w("Auto-approve install blocked: security risks/VT flags present ($risks, vt=${apkInfo?.vtResult?.status})")
                    if (uiState.dialogStage == DialogStage.Prepare && autoBlockedRisks.isEmpty()) {
                        autoBlockedRisks = risks
                    }
                }
                if (uiState.dialogStage == DialogStage.Prepare && shouldAutoInstall) {
                    Timber.i("Auto-approving install: autoConfirm=$autoConfirmExternalInstall, callerApproved=$isCallerAutoApproved (caller=$callerPackage)")
                    proceedInstall()
                } else if (uiState.dialogStage == DialogStage.Success && (autoConfirmExternalInstall || isCallerAutoApproved) && !securityGate.isPinRequired) {
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

            val dismissAndFinish = {
                DialogInstallActivityHelper.handleDismiss(
                    activity = this,
                    viewModel = viewModel,
                    dialogTarget = dialogTarget,
                    dialogStage = uiState.dialogStage,
                    promptNotifier = promptNotifier,
                    installNotifier = installNotifier,
                    scope = lifecycleScope,
                    finishAfterSuccess = finishAfterSuccess,
                )
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
                vtDisabled = prefs?.get(PreferencesKeys.DISABLE_VIRUSTOTAL) ?: false,
                blockOnTrackers = blockOnTrackers,
                autoBlockedRisks = autoBlockedRisks,
                canInstallPackages = { canInstallPackages(prefs, uiState.selectedProfileId) },
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

            pendingFallbackBackend?.let { backendName ->
                DialogInstallFallbackDialog(
                    backendName = backendName,
                    onContinueWithDefault = {
                        pendingFallbackBackend = null
                        securityGate.authenticate {
                            viewModel.dialogStartInstalling()
                            viewModel.confirmInstall(trackDialogTarget = true, keepApk = keepApk)
                        }
                    },
                    onOpenApp = if (backendName == "Shizuku") {
                        {
                            pendingFallbackBackend = null
                            context.packageManager.getLaunchIntentForPackage(ShizukuServices.SHIZUKU_PACKAGE)?.let {
                                it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(it) }
                            }
                        }
                    } else null,
                    onDismiss = {
                        pendingFallbackBackend = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        DialogInstallActivityHelper.handleNewIntent(this, intent, viewModel, lifecycleScope)
    }

    override fun finish() {
        super.finish()
        if (viewModel.uiState.value.dialogStage !is DialogStage.Installing && viewModel.dialogTarget.value == null) {
            finishAndRemoveTask()
        }
    }
}
