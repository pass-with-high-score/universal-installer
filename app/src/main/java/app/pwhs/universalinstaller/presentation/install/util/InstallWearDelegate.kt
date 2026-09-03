package app.pwhs.universalinstaller.presentation.install.util

import android.app.Application
import android.net.Uri
import app.pwhs.core.util.WatchAppCheck
import app.pwhs.universalinstaller.presentation.install.ScanState
import app.pwhs.universalinstaller.presentation.install.WatchSendState
import app.pwhs.universalinstaller.presentation.install.wear.WearApkSender
import app.pwhs.universalinstaller.presentation.install.wear.WearTransferService
import app.pwhs.universalinstaller.presentation.install.wear.WearTransferState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Backs the Send-to-Watch sheet: which watch is reachable, which packages are on this device, and
 * the handover to [WearTransferService], which owns the transfer itself.
 */
class InstallWearDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
) {
    val sendState: StateFlow<WatchSendState> = WearTransferState.state

    private val _watchName = MutableStateFlow<String?>(null)
    val watchName: StateFlow<String?> = _watchName.asStateFlow()

    private val _isLookingUpWatch = MutableStateFlow(false)
    val isLookingUpWatch: StateFlow<Boolean> = _isLookingUpWatch.asStateFlow()

    // Kept apart from InstallScanDelegate's scan so opening this sheet cannot pop the other one.
    private val _apkScanState = MutableStateFlow<ScanState>(ScanState.Idle)
    val apkScanState: StateFlow<ScanState> = _apkScanState.asStateFlow()

    private var scanJob: Job? = null
    private var precheckJob: Job? = null

    fun refreshWatch() {
        scope.launch {
            _isLookingUpWatch.value = true
            _watchName.value = WearApkSender.connectedWatchName(application)
            _isLookingUpWatch.value = false
        }
    }

    fun scanForApks(force: Boolean = false) {
        if (!force && _apkScanState.value is ScanState.Ready) return
        scanJob?.cancel()
        _apkScanState.value = ScanState.Scanning
        scanJob = scope.launch {
            _apkScanState.value = InstallScanHelper.performDeviceScan(application)
        }
    }

    fun reportUnsupported(reason: String) {
        WearTransferState.update(WatchSendState.Unsupported(reason))
    }

    /**
     * [declaresWatchFeature] comes from the parsed APK when the caller already has it; `null` means
     * it has to be read off [apkUri] first.
     */
    fun sendToWatch(apkUri: Uri, fileName: String, declaresWatchFeature: Boolean?) {
        if (sendState.value is WatchSendState.Sending) return
        if (declaresWatchFeature != null) {
            startOrConfirm(apkUri, fileName, declaresWatchFeature)
            return
        }

        WearTransferState.update(WatchSendState.CheckingWatch)
        precheckJob = scope.launch {
            startOrConfirm(apkUri, fileName, WatchAppCheck.declaresWatchFeature(application, apkUri))
        }
    }

    fun confirmSendToWatch() {
        val pending = sendState.value as? WatchSendState.ConfirmNotWatchApp ?: return
        WearTransferService.start(application, pending.apkUri, pending.fileName)
    }

    fun cancelWatchSend() {
        precheckJob?.cancel()
        // The confirm prompt is the only state the service never sees, so it has nothing to cancel.
        if (sendState.value is WatchSendState.ConfirmNotWatchApp) {
            WearTransferState.reset()
            return
        }
        WearTransferService.cancel(application)
    }

    fun dismissWatchSend() {
        WearTransferState.reset()
    }

    private fun startOrConfirm(apkUri: Uri, fileName: String, declaresWatchFeature: Boolean) {
        if (declaresWatchFeature) {
            WearTransferService.start(application, apkUri, fileName)
        } else {
            WearTransferState.update(WatchSendState.ConfirmNotWatchApp(apkUri, fileName))
        }
    }
}
