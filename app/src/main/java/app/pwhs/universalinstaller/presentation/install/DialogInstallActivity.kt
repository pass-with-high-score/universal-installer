package app.pwhs.universalinstaller.presentation.install

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pwhs.universalinstaller.IntentHandoff
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.dataStore
import app.pwhs.universalinstaller.presentation.install.dialog.DialogFailedContent
import app.pwhs.universalinstaller.presentation.install.dialog.DialogInstallingContent
import app.pwhs.universalinstaller.presentation.install.dialog.DialogMenuContent
import app.pwhs.universalinstaller.presentation.install.dialog.DialogPrepareContent
import app.pwhs.universalinstaller.presentation.install.dialog.DialogSuccessContent
import app.pwhs.universalinstaller.presentation.install.dialog.InstallRisk
import app.pwhs.universalinstaller.presentation.install.dialog.RiskConfirmDialog
import app.pwhs.universalinstaller.presentation.install.dialog.detectInstallRisks
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.util.LocaleHelper
import app.pwhs.universalinstaller.util.WindowBlurEffect
import app.pwhs.universalinstaller.util.extension.getDisplayName
import org.koin.androidx.viewmodel.ext.android.viewModel
import ru.solrudev.ackpine.splits.ApkSplits.validate
import ru.solrudev.ackpine.splits.SplitPackage.Companion.toSplitPackage
import ru.solrudev.ackpine.splits.ZippedApkSplits
import timber.log.Timber

/**
 * Translucent activity that shows a focused install dialog when an external app (file
 * manager, Chrome, Telegram, share sheet) opens an APK / APKS / XAPK / APKM. This activity
 * owns the install intent filters directly — there is no router activity in front, so the
 * user goes straight from their file picker into the dialog with no flash of our app
 * chrome.
 *
 * Architecturally inspired by InstallerX's `InstallerActivity` pattern (read for approach,
 * not copied — they're GPL):
 * - File-open intent filters live on this activity, not on the launcher activity.
 * - `singleInstance` + `excludeFromRecents` keep this off the recents stack.
 * - `Theme.UniversalInstaller.Dialog` is translucent so the calling app stays visible
 *   behind our scrim.
 * - The dialog content transitions through stages: Loading → Prepare → Menu → Installing → Result.
 *
 * Dismissal paths:
 * - Tap outside the card → dismiss. Detected via stacked `pointerInput` blocks (outer
 *   scrim Box dispatches dismiss; inner Surface consumes taps).
 * - Cancel button → same.
 * - Install button → starts install; dialog shows progress then result.
 */
class DialogInstallActivity : ComponentActivity() {

    private val viewModel: InstallViewModel by viewModel()

    /** Track whether system took us to a confirmation activity. */
    private var wentToSystemConfirm = false

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val incomingUris = collectIncomingUris(intent)
        if (incomingUris.isEmpty()) {
            Timber.w("DialogInstallActivity launched without any content URIs — bailing")
            finish()
            return
        }

        if (incomingUris.size > 1) {
            // Multiple URIs → redirect to full app for batch install
            IntentHandoff.postBatch(incomingUris)
            val targetIntent = Intent(this, InstallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            forwardIncomingUris(intent, targetIntent)
            startActivity(targetIntent)
            finish()
            return
        }

        val incomingUri = incomingUris.first()

        // Start in Loading stage
        viewModel.dialogStartLoading()

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val resource = LocalResources.current
            val context = LocalContext.current

            val obbPickerLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.OpenMultipleDocuments()
            ) { uris ->
                uris.forEach { viewModel.attachObbFile(context, it) }
            }

            // Dispatch parsing once. Keyed on the URI so a config-change recomposition
            // doesn't re-parse — the VM's pendingApkInfo state survives the recomposition.
            LaunchedEffect(incomingUri) {
                runCatching { parseAndPush(context, incomingUri) }.onFailure { e ->
                    Timber.e(e, "Parse failed for $incomingUri")
                    Toast.makeText(
                        context,
                        resource.getString(R.string.install_unsupported_file),
                        Toast.LENGTH_LONG,
                    ).show()
                    finish()
                }
            }

            // Transition from Loading → Prepare when parse completes
            LaunchedEffect(uiState.pendingApkInfo, uiState.dialogStage) {
                if (uiState.pendingApkInfo != null && uiState.dialogStage == DialogStage.Loading) {
                    viewModel.dialogShowPrepare()
                }
            }

            // Dialog target snapshot — set inside confirmInstall before the install fires.
            // We watch this + the session list to drive Installing → Success/Failed transitions.
            val dialogTarget by viewModel.dialogTarget.collectAsState()

            // Auto-open-after-install pref — read directly from DataStore (no SettingViewModel
            // dependency in this activity). Drives the Success-stage countdown.
            val prefs by context.dataStore.data.collectAsState(initial = null)
            val autoOpenAfterInstall = prefs?.get(PreferencesKeys.AUTO_OPEN_AFTER_INSTALL) ?: false

            // Tracks whether we've actually observed the captured session in the repository.
            // The session is added inside controller.install() AFTER createSession() suspends,
            // so there's a window where dialogTarget is set but the session isn't in the list
            // yet — without this guard we'd misread that as "session removed" and fire Success
            // immediately, dismissing the dialog while ackpine hasn't finished installing.
            var sessionEverSeen by remember(dialogTarget?.sessionId) { mutableStateOf(false) }

            // Resolve Installing → Success / Failed by watching the captured session.
            //   - session in list, error blank        → still Installing (mark as seen)
            //   - session in list, error non-blank   → Failed
            //   - session NOT in list (was there)    → Succeeded
            //   - session NOT in list (never seen)   → not started yet, keep waiting
            // BaseInstallController removes on Succeeded and calls setError() on Failed.
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

            // Risk-gate state — when non-empty we render the consent AlertDialog over the
            // main install Dialog. Confirming proceeds with the install; cancelling returns
            // the user to the Prepare/Menu stage (no install fires).
            var pendingRisks by remember { mutableStateOf<List<InstallRisk>>(emptyList()) }
            val proceedInstall = {
                // Show the Installing stage straight away. dialogTarget arrives a moment
                // later (after createSession suspends), at which point the Installing UI
                // re-renders with the real session data and progress bar.
                viewModel.dialogStartInstalling()
                viewModel.confirmInstall(trackDialogTarget = true)
            }
            val handleInstallTap = {
                val info = uiState.pendingApkInfo
                val risks = if (info != null) detectInstallRisks(info) else emptyList()
                if (risks.isNotEmpty()) {
                    pendingRisks = risks
                } else {
                    proceedInstall()
                }
            }

            UniversalInstallerTheme {
                // Apply background blur on Android 12+
                WindowBlurEffect(blurRadius = 30)

                if (pendingRisks.isNotEmpty()) {
                    RiskConfirmDialog(
                        risks = pendingRisks,
                        onConfirm = {
                            pendingRisks = emptyList()
                            proceedInstall()
                        },
                        onCancel = { pendingRisks = emptyList() },
                    )
                }

                Dialog(
                    onDismissRequest = {
                        viewModel.dismissPendingInstall()
                        viewModel.dialogClose()
                        finish()
                    },
                    properties = DialogProperties(
                        // We handle outside-tap manually below (via pointerInput) so the
                        // scrim fade keeps animating during dismissal — letting the
                        // platform dismiss instantly would be jarring.
                        dismissOnClickOutside = false,
                        dismissOnBackPress = true,
                        usePlatformDefaultWidth = false,
                    ),
                ) {
                    DialogContent(
                        uiState = uiState,
                        dialogTarget = dialogTarget,
                        autoOpenAfterInstall = autoOpenAfterInstall,
                        onInstall = handleInstallTap,
                        onCancel = {
                            viewModel.dismissPendingInstall()
                            viewModel.dialogClose()
                            viewModel.clearDialogTarget()
                            finish()
                        },
                        onMenu = viewModel::dialogShowMenu,
                        onMenuBack = viewModel::dialogBackToPrepare,
                        onCheckVirusTotal = {
                            viewModel.scanVirusTotal(this@DialogInstallActivity)
                        },
                        onRemoveObb = { obb -> viewModel.removeAttachedObb(obb.uri) },
                        onToggleSplit = viewModel::toggleSplit,
                        onAttachObb = { obbPickerLauncher.launch(arrayOf("*/*")) },
                        onBackground = {
                            // Dialog dismisses; install continues in the background, tracked by
                            // the system notification. Clear the target so a subsequent open
                            // doesn't replay Success/Failed for an already-finished session.
                            viewModel.dialogClose()
                            viewModel.clearDialogTarget()
                            finish()
                        },
                        onOpenInstalledApp = { pkg ->
                            viewModel.getAppLaunchIntent(pkg)?.let { startActivity(it) }
                            viewModel.dialogClose()
                            viewModel.clearDialogTarget()
                            finish()
                        },
                        onCloseAfterResult = {
                            viewModel.dialogClose()
                            viewModel.clearDialogTarget()
                            finish()
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uris = collectIncomingUris(intent)
        if (uris.isEmpty()) return

        if (uris.size > 1) {
            // Multiple URIs → redirect to full app for batch install
            IntentHandoff.postBatch(uris)
            val targetIntent = Intent(this, InstallActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            }
            forwardIncomingUris(intent, targetIntent)
            startActivity(targetIntent)
            finish()
            return
        }

        val uri = uris.first()
        viewModel.dismissPendingInstall()
        viewModel.dialogStartLoading()
        val context = this
        // Re-parse new intent
        lifecycleScope.launch {
            runCatching { parseAndPush(context, uri) }.onFailure { e ->
                Timber.e(e, "Parse failed for new intent $uri")
                Toast.makeText(
                    context,
                    getString(R.string.install_unsupported_file),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    /**
     * Re-grant any content URIs we received on [source] to [target] so the install activity's
     * task can read them. Required because we launch [target] with `NEW_TASK | CLEAR_TASK` and
     * `finish()` ourselves — the original grant only covered DialogInstallActivity's task.
     */
    private fun forwardIncomingUris(source: Intent?, target: Intent) {
        if (source == null) return
        val uris = collectIncomingUris(source)
        if (uris.isEmpty()) return
        if (uris.size == 1) {
            target.data = uris.first()
        } else {
            val clip = ClipData.newRawUri("", uris.first())
            for (i in 1 until uris.size) {
                clip.addItem(ClipData.Item(uris[i]))
            }
            target.clipData = clip
        }
        target.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    override fun onStop() {
        super.onStop()
        // Don't auto-finish if we went to system's install confirm dialog.
        // This prevents the dialog from disappearing when system shows confirmation.
    }

    /**
     * Parse the incoming URI through the same SplitPackage pipeline InstallScreen uses,
     * then hand to the shared VM so the install logic (split picker, VT scan, OBB) works
     * identically to the full-screen flow.
     */
    private fun parseAndPush(context: Context, uri: Uri) {
        val displayName = context.contentResolver.getDisplayName(uri)
        val mime = context.contentResolver.getType(uri)?.lowercase()
        val ext = displayName.substringAfterLast('.', "").lowercase()
        val isApkMime = mime == "application/vnd.android.package-archive"
        val splitProvider = when {
            isApkMime || ext == "apk" -> SingletonApkSequence(uri, context).toSplitPackage()
            ext in setOf("apks", "xapk", "apkm", "zip") ->
                ZippedApkSplits.getApksForUri(uri, context)
                    .validate()
                    .toSplitPackage()
                    .filterCompatible(context)
            else -> SingletonApkSequence(uri, context).toSplitPackage()
        }
        viewModel.parseApkInfo(context, uri, splitProvider, displayName)
    }

    /**
     * Pull all installable URIs off the launch intent. If multiple URIs are found,
     * onCreate/onNewIntent will redirect to the full app's batch flow.
     */
    private fun collectIncomingUris(source: Intent?): List<Uri> {
        if (source == null) return emptyList()
        val out = mutableListOf<Uri>()

        // 1. Data URI (VIEW / INSTALL_PACKAGE)
        source.data?.takeIf { it.scheme == "content" || it.scheme == "file" }?.let(out::add)

        // 2. EXTRA_STREAM (SEND / SEND_MULTIPLE)
        @Suppress("DEPRECATION")
        when (source.action) {
            Intent.ACTION_SEND ->
                (source.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let(out::add)
            Intent.ACTION_SEND_MULTIPLE ->
                source.getParcelableArrayListExtra<Uri>(Intent.EXTRA_STREAM)
                    ?.filterNotNull()
                    ?.let(out::addAll)
        }

        // 3. ClipData (Alternative for some file managers)
        source.clipData?.let { clip ->
            for (i in 0 until clip.itemCount) {
                val u = clip.getItemAt(i).uri ?: continue
                if (u.scheme == "content" || u.scheme == "file") out.add(u)
            }
        }

        return out.distinct()
    }
}

/**
 * The visual layer of the install dialog. Uses AnimatedContent to smoothly transition
 * between stages (Loading → Prepare → Menu). Install triggers finish() immediately.
 */
@Composable
private fun DialogContent(
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
) {
    // The scrim Box swallows tap events that fall outside the card, mapping them to a
    // cancel. The inner Surface's own pointerInput consumes its events so they never
    // bubble up to the scrim's gesture detector.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { onCancel() })
            },
        contentAlignment = Alignment.Center,
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 480.dp)
                .heightIn(max = 640.dp)
                .windowInsetsPadding(WindowInsets.systemBars)
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .pointerInput(Unit) {
                    // Empty handler — its presence consumes taps so they never reach the
                    // scrim's outside-tap detector.
                    detectTapGestures(onTap = { /* swallow */ })
                }
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow,
                    ),
                ),
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
            shadowElevation = 12.dp,
        ) {
            AnimatedContent(
                targetState = uiState.dialogStage,
                transitionSpec = {
                    (fadeIn(tween(250)) + slideInVertically { it / 8 })
                        .togetherWith(fadeOut(tween(200)) + slideOutVertically { -it / 8 })
                        .using(SizeTransform(clip = false))
                },
                label = "DialogStageTransition",
            ) { stage ->
                when (stage) {
                    DialogStage.Loading -> {
                        LoadingContent()
                    }

                    DialogStage.Prepare -> {
                        val info = uiState.pendingApkInfo
                        if (info != null) {
                            DialogPrepareContent(
                                apkInfo = info,
                                installedVersionName = info.installedVersionName,
                                installedVersionCode = info.installedVersionCode,
                                onInstall = onInstall,
                                onMenu = onMenu,
                                onCancel = onCancel,
                            )
                        } else {
                            LoadingContent()
                        }
                    }

                    DialogStage.Menu -> {
                        val info = uiState.pendingApkInfo
                        if (info != null) {
                            DialogMenuContent(
                                apkInfo = info,
                                attachedObbFiles = uiState.attachedObbFiles,
                                onBack = onMenuBack,
                                onInstall = onInstall,
                                onCheckVirusTotal = onCheckVirusTotal,
                                onRemoveObb = onRemoveObb,
                                onToggleSplit = onToggleSplit,
                                onAttachObb = onAttachObb,
                            )
                        } else {
                            LoadingContent()
                        }
                    }

                    DialogStage.Installing -> {
                        if (dialogTarget != null) {
                            val sp = uiState.sessionsProgress.find { it.id == dialogTarget.sessionId }
                            val fraction = sp?.let {
                                if (it.progressMax > 0) it.currentProgress.toFloat() / it.progressMax else null
                            }
                            DialogInstallingContent(
                                target = dialogTarget,
                                progressFraction = fraction,
                                onBackground = onBackground,
                            )
                        } else {
                            LoadingContent()
                        }
                    }

                    DialogStage.Success -> {
                        if (dialogTarget != null) {
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
                        } else {
                            LoadingContent()
                        }
                    }

                    is DialogStage.Failed -> {
                        DialogFailedContent(
                            target = dialogTarget,
                            errorMessage = stage.errorMessage,
                            onClose = onCloseAfterResult,
                        )
                    }

                    DialogStage.None -> {
                        Spacer(modifier = Modifier.size(0.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun LoadingContent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircularProgressIndicator(
            modifier = Modifier.size(40.dp),
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp,
        )
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.dialog_loading_text),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
