package app.pwhs.universalinstaller.presentation.install

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import app.pwhs.universalinstaller.base.BaseActivity
import app.pwhs.universalinstaller.review.AppReview
import app.pwhs.universalinstaller.review.ReviewGate
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import app.pwhs.universalinstaller.presentation.composable.BottomBar
import app.pwhs.universalinstaller.presentation.composable.BottomBarItem
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import org.koin.androidx.viewmodel.ext.android.viewModel
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.bridge.AntiSplitBridge
import app.pwhs.universalinstaller.domain.stats.MilestoneId
import app.pwhs.universalinstaller.domain.stats.UserStatsTracker
import app.pwhs.universalinstaller.presentation.composable.AntiSplitPromptDialog
import app.pwhs.universalinstaller.presentation.stats.StatsActivity
import app.pwhs.universalinstaller.presentation.stats.components.MilestoneUnlockBanner
import app.pwhs.universalinstaller.util.extension.disableSceneTransition
import kotlinx.coroutines.delay
import ru.solrudev.ackpine.splits.SplitPackage.Companion.toSplitPackage

class InstallActivity : BaseActivity() {

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* granted or not — uninstall flow works either way, notifications just won't show */ }

    private var showAntiSplitPrompt by mutableStateOf(false)

    val antiSplitLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val mergeResult = AntiSplitBridge.parseResult(result.resultCode, result.data)
            val outputUri = mergeResult?.outputUri
            if (outputUri != null) {
                val fileName = mergeResult.outputPath?.substringAfterLast('/')
                    ?: "${mergeResult.packageName ?: "app"}_merged.apk"
                val splitPackage = SingletonApkSequence(outputUri, this).toSplitPackage()
                viewModel.parseApkInfo(this, outputUri, splitPackage, fileName)
                Toast.makeText(this, getString(R.string.antisplit_merge_success), Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun requestMergeExternalFile(fileUri: Uri, autoStart: Boolean = false) {
        val intent = AntiSplitBridge.createMergeFileIntent(fileUri, autoStart = autoStart)
        if (!AntiSplitBridge.canResolveIntent(this, intent)) {
            showAntiSplitPrompt = true
            return
        }
        runCatching {
            antiSplitLauncher.launch(intent)
        }.onFailure {
            showAntiSplitPrompt = true
        }
    }

    fun requestMergeInstalledPackage(packageName: String, autoStart: Boolean = false) {
        val intent = AntiSplitBridge.createMergeInstalledAppIntent(packageName, autoStart = autoStart)
        if (!AntiSplitBridge.canResolveIntent(this, intent)) {
            showAntiSplitPrompt = true
            return
        }
        runCatching {
            antiSplitLauncher.launch(intent)
        }.onFailure {
            showAntiSplitPrompt = true
        }
    }

    private fun maybeRequestNotificationPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    private val viewModel: InstallViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeRequestNotificationPermission()
        observeReviewOpportunities()
        setContentWithTheme {
            var unlockedMilestone by remember { mutableStateOf<MilestoneId?>(null) }

            LaunchedEffect(Unit) {
                UserStatsTracker.milestoneEvents.collect { milestone ->
                    unlockedMilestone = milestone
                    delay(6_000L)
                    if (unlockedMilestone == milestone) {
                        unlockedMilestone = null
                    }
                }
            }

            Scaffold(
                bottomBar = { BottomBar(BottomBarItem.Install) }
            ) { innerPadding ->
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())) {
                    InstallScreen(
                        viewModel = viewModel,
                        onMergeSplits = { uri -> requestMergeExternalFile(uri) },
                    )
                    MilestoneUnlockBanner(
                        milestone = unlockedMilestone,
                        onViewClick = {
                            unlockedMilestone = null
                            val intent = Intent(this@InstallActivity, StatsActivity::class.java).apply {
                                putExtra(StatsActivity.EXTRA_OPEN_MILESTONES, true)
                                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
                            }
                            startActivity(intent)
                            disableSceneTransition()
                        },
                        onDismiss = { unlockedMilestone = null },
                        modifier = Modifier.align(Alignment.BottomCenter),
                    )
                }
            }
            if (showAntiSplitPrompt) {
                AntiSplitPromptDialog(
                    onDismiss = { showAntiSplitPrompt = false },
                    onDownload = { AntiSplitBridge.openDownloadPage(this@InstallActivity) },
                )
            }
        }
    }

    /**
     * Asks for a review after an install has worked, once the screen has gone quiet.
     *
     * This screen, and not [DialogInstallActivity], is the only place the ask belongs. That one
     * shows the word "Success" and then finishes: the user is mid-flow in whatever app handed us
     * the APK, and the sheet would be torn down with the activity — a wasted ask, and Play only
     * grants a few.
     *
     * [Lifecycle.State.RESUMED] is what keeps an ask from landing on a screen that is merely in
     * the back stack, and cancels the wait outright if the user walks away.
     */
    private fun observeReviewOpportunities() {
        if (!AppReview.isAvailable) return
        lifecycleScope.launch {
            // Pre-warm only when an ask is already allowed. requestReviewFlow is network-backed,
            // and the quiet moment below is not long enough to fetch it in.
            if (ReviewGate.isEligible(this@InstallActivity)) AppReview.prepare()
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                ReviewGate.opportunities.collect {
                    // The success arrives while its session card is still on screen, and a batch
                    // may have more installs queued behind it. Wait for all of that to settle
                    // rather than talking over it; give up on this ask if it never does.
                    val settled = withTimeoutOrNull(QUIET_TIMEOUT_MS) {
                        viewModel.uiState.first {
                            it.sessions.isEmpty() &&
                                it.pendingApkInfo == null &&
                                it.dialogStage == DialogStage.None
                        }
                    }
                    if (settled == null) return@collect
                    AppReview.launch(this@InstallActivity)
                    val installs = ReviewGate.getSuccessfulInstallCount(this@InstallActivity)
                    ReviewGate.recordPrompted(this@InstallActivity)
                    Telemetry.feature(TelemetryEvents.FEATURE_REVIEW_PROMPT)
                    app.pwhs.core.telemetry.AnalyticsHelper.logReviewPromptTriggered(
                        triggerReason = app.pwhs.core.telemetry.TelemetryEvents.TRIGGER_INSTALL_SUCCESS_MILESTONE,
                        totalSuccessfulInstalls = installs
                    )
                }
            }
        }
    }

    private companion object {
        /** Long enough for a batch to finish, short enough that the ask stays connected to it. */
        const val QUIET_TIMEOUT_MS = 30_000L
    }
}
