package app.pwhs.universalinstaller.presentation.install.dialog

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.domain.manager.AutoApproveApps
import app.pwhs.universalinstaller.domain.model.ExternalOpenMode
import app.pwhs.universalinstaller.presentation.install.InstallPromptNotifier
import app.pwhs.universalinstaller.presentation.install.InstallViewModel
import app.pwhs.universalinstaller.presentation.install.PendingInstallStore
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.VtStatus
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.SecurityLevel
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import timber.log.Timber

private const val PARSE_TIMEOUT_MS = 30_000L
private const val LOG = "NotifInstall"

@Composable
fun HeadlessNotificationInstall(
    mode: ExternalOpenMode,
    uri: Uri,
    viewModel: InstallViewModel,
    promptNotifier: InstallPromptNotifier,
    callerPackage: String? = null,
    onFallbackToDialog: (skipParse: Boolean) -> Unit,
    onInstallFromNotificationAction: () -> Unit,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    LaunchedEffect(uri) {
        Timber.i("$LOG: mode=$mode, caller=$callerPackage, parsing $uri")
        runCatching {
            DialogInstallUriHelper.parseAndPush(context, uri, viewModel)
        }.onFailure { e ->
            Timber.e(e, "$LOG: parse threw - giving up")
            onFinish()
            return@LaunchedEffect
        }

        val apkInfo = withTimeoutOrNull(PARSE_TIMEOUT_MS) {
            viewModel.uiState.map { it.pendingApkInfo }.filterNotNull().first()
        }
        if (apkInfo == null) {
            Timber.w("$LOG: no parse result within ${PARSE_TIMEOUT_MS}ms - falling back to the dialog")
            onFallbackToDialog(true)
            return@LaunchedEffect
        }

        Timber.i("$LOG: parsed ${apkInfo.packageName}, ${apkInfo.splitEntries.size} split(s)")

        val prefs = runCatching { context.dataStore.data.first() }.getOrNull()
        val strictVirusTotal = SecurityLevel.from(
            prefs?.get(PreferencesKeys.SECURITY_LEVEL)
        ) == SecurityLevel.Strict
        val blockOnTrackers = prefs?.get(PreferencesKeys.AUTO_APPROVE_BLOCK_TRACKERS) ?: false

        // If VirusTotal hash lookup or tracker scanning is in progress, give it a brief window to complete so security flags aren't bypassed
        var currentApkInfo: ApkInfo = apkInfo
        if (currentApkInfo.vtResult?.status == VtStatus.SCANNING || (blockOnTrackers && currentApkInfo.isScanningTrackers)) {
            val updated = withTimeoutOrNull(2500L) {
                viewModel.uiState.map { it.pendingApkInfo }.filterNotNull().first {
                    it.vtResult?.status != VtStatus.SCANNING && (!blockOnTrackers || !it.isScanningTrackers)
                }
            }
            if (updated != null) {
                currentApkInfo = updated
            }
        }

        val risks = detectInstallRisks(currentApkInfo, strictVirusTotal, blockOnTrackers)
        val hasVtFlags = currentApkInfo.vtResult?.let { it.malicious > 0 || it.suspicious > 0 } == true
        val isCallerAutoApproved = AutoApproveApps.isAutoApproved(prefs, callerPackage)

        if ((mode == ExternalOpenMode.AutoNotification || isCallerAutoApproved) && risks.isEmpty() && !hasVtFlags) {
            Timber.i("$LOG: auto mode/approved, no risks - installing without asking (caller=$callerPackage, mode=$mode)")
            onInstallFromNotificationAction()
            return@LaunchedEffect
        }

        if (risks.isNotEmpty()) {
            Timber.i("$LOG: ${risks.size} risk(s) - asking rather than auto-installing")
        }
        val canPost = promptNotifier.canPost()
        if (!canPost) Timber.w("$LOG: notifications unavailable")
        val entry = if (canPost) viewModel.stashPendingInstall() else null
        if (canPost && entry == null) {
            Timber.w("$LOG: nothing to stash - staging the APKs must have failed")
        }
        if (entry == null || !promptNotifier.prompt(entry)) {
            Timber.w("$LOG: falling back to the dialog")
            entry?.let {
                viewModel.restorePendingInstall(it)
                PendingInstallStore.consume(it.id)
            }
            onFallbackToDialog(entry != null)
            return@LaunchedEffect
        }
        Timber.i("$LOG: prompt ${entry.id} posted for ${entry.packageName}, ${entry.apkUris.size} staged uri(s)")
        onFinish()
    }
}
