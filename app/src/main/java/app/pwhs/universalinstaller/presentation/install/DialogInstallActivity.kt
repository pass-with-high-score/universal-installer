package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import app.pwhs.universalinstaller.util.LocaleHelper
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
 * - The dialog itself is a Compose [Dialog] window so the system handles scrim fade and
 *   we get focus / IME behaviour for free.
 *
 * Dismissal paths:
 * - Tap outside the card → dismiss. Detected via stacked `pointerInput` blocks (outer
 *   scrim Box dispatches dismiss; inner Surface consumes taps).
 * - Cancel button → same.
 * - Install button → confirmInstall(); we keep the dialog visible until ackpine's session
 *   is enqueued, then auto-finish.
 */
class DialogInstallActivity : ComponentActivity() {

    private val viewModel: InstallViewModel by viewModel()

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val incomingUri = collectIncomingUri(intent)
        if (incomingUri == null) {
            Timber.w("DialogInstallActivity launched without a content URI — bailing")
            finish()
            return
        }

        setContent {
            val uiState by viewModel.uiState.collectAsState()
            val context = LocalContext.current

            // Dispatch parsing once. Keyed on the URI so a config-change recomposition
            // doesn't re-parse — the VM's pendingApkInfo state survives the recomposition.
            LaunchedEffect(incomingUri) {
                runCatching { parseAndPush(context, incomingUri) }.onFailure { e ->
                    Timber.e(e, "Parse failed for $incomingUri")
                    Toast.makeText(
                        context,
                        context.getString(R.string.install_unsupported_file),
                        Toast.LENGTH_LONG,
                    ).show()
                    finish()
                }
            }

            // Auto-finish once the install session is enqueued. confirmInstall() clears
            // pendingApkInfo and adds to sessions; from there ackpine's notification
            // tracks progress, so we step out of the way.
            LaunchedEffect(uiState.pendingApkInfo, uiState.sessions.size) {
                if (uiState.pendingApkInfo == null && uiState.sessions.isNotEmpty()) {
                    finish()
                }
            }

            UniversalInstallerTheme {
                Dialog(
                    onDismissRequest = {
                        viewModel.dismissPendingInstall()
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
                        onInstall = viewModel::confirmInstall,
                        onCancel = {
                            viewModel.dismissPendingInstall()
                            finish()
                        },
                        onCheckVirusTotal = {
                            viewModel.scanVirusTotal(this@DialogInstallActivity)
                        },
                        onRemoveObb = { obb -> viewModel.removeAttachedObb(obb.uri) },
                        onToggleSplit = viewModel::toggleSplit,
                    )
                }
            }
        }
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
 * The visual layer of the install dialog. A scrim Box fills the screen behind the
 * Material3 Surface; both consume taps via `pointerInput` so an outside-tap dismisses
 * while taps on the card itself pass through to its child controls.
 */
@androidx.compose.runtime.Composable
private fun DialogContent(
    uiState: InstallUiState,
    onInstall: () -> Unit,
    onCancel: () -> Unit,
    onCheckVirusTotal: () -> Unit,
    onRemoveObb: (AttachedObb) -> Unit,
    onToggleSplit: (Int) -> Unit,
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
            uiState.pendingApkInfo?.let { info ->
                ApkInfoContent(
                    apkInfo = info,
                    onInstall = onInstall,
                    onCancel = onCancel,
                    onCheckVirusTotal = onCheckVirusTotal,
                    attachedObbFiles = uiState.attachedObbFiles,
                    onAttachObb = { /* OBB picker disabled in dialog mode for v1 */ },
                    onRemoveObb = onRemoveObb,
                    onToggleSplit = onToggleSplit,
                )
            }
        }
    }
}
