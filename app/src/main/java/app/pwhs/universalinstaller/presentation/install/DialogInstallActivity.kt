package app.pwhs.universalinstaller.presentation.install

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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.install.dialog.DialogMenuContent
import app.pwhs.universalinstaller.presentation.install.dialog.DialogPrepareContent
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

        val incomingUri = collectIncomingUri(intent)
        if (incomingUri == null) {
            Timber.w("DialogInstallActivity launched without a content URI — bailing")
            finish()
            return
        }

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

            UniversalInstallerTheme {
                // Apply background blur on Android 12+
                WindowBlurEffect(blurRadius = 30)

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
                        onInstall = {
                            // Enqueue the install and finish — notification tracks progress.
                            // confirmInstall() clears pendingApkInfo, so we must finish()
                            // before the next recomposition sees null info.
                            viewModel.confirmInstall()
                            viewModel.dialogClose()
                            finish()
                        },
                        onCancel = {
                            viewModel.dismissPendingInstall()
                            viewModel.dialogClose()
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
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        val uri = collectIncomingUri(intent) ?: return
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
    private suspend fun parseAndPush(context: Context, uri: Uri) {
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
     * Pull the first installable URI off the launch intent. SEND_MULTIPLE picks the first
     * URI for the dialog-mode v1; users with batch needs can launch the full app and use
     * the Install tab's batch flow.
     */
    private fun collectIncomingUri(source: Intent?): Uri? {
        if (source == null) return null
        source.data?.takeIf { it.scheme == "content" || it.scheme == "file" }?.let { return it }
        @Suppress("DEPRECATION")
        (source.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri)?.let { return it }
        val clip = source.clipData ?: return null
        for (i in 0 until clip.itemCount) {
            val u = clip.getItemAt(i).uri ?: continue
            if (u.scheme == "content" || u.scheme == "file") return u
        }
        return null
    }
}

/**
 * The visual layer of the install dialog. Uses AnimatedContent to smoothly transition
 * between stages (Loading → Prepare → Menu). Install triggers finish() immediately.
 */
@Composable
private fun DialogContent(
    uiState: InstallUiState,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onMenu: () -> Unit,
    onMenuBack: () -> Unit,
    onCheckVirusTotal: () -> Unit,
    onRemoveObb: (AttachedObb) -> Unit,
    onToggleSplit: (Int) -> Unit,
    onAttachObb: () -> Unit,
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

                    // Installing/Success/Failed/None: dialog should have finished before
                    // reaching these, but handle gracefully if somehow shown.
                    else -> {
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
