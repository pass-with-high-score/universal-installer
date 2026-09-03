package app.pwhs.universalinstaller.presentation.install.util

import android.app.Application
import android.net.Uri
import app.pwhs.universalinstaller.presentation.install.WatchSendState
import app.pwhs.universalinstaller.presentation.install.wear.WearApkSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Delegate managing APK transmission from phone to paired Wear OS watch.
 */
class InstallWearDelegate(
    private val application: Application,
    private val scope: CoroutineScope,
) {
    private val _watchSendState = MutableStateFlow<WatchSendState>(WatchSendState.Idle)
    val watchSendState: StateFlow<WatchSendState> = _watchSendState.asStateFlow()

    private val _watchAvailable = MutableStateFlow(false)
    val watchAvailable: StateFlow<Boolean> = _watchAvailable.asStateFlow()

    fun refreshWatchAvailability() {
        scope.launch {
            _watchAvailable.value = WearApkSender.isWatchAvailable(application)
        }
    }

    fun reportUnsupported(reason: String) {
        _watchSendState.value = WatchSendState.Unsupported(reason)
    }

    fun sendToWatch(apkUri: Uri, fileName: String) {
        if (_watchSendState.value is WatchSendState.Sending) return
        scope.launch {
            _watchSendState.value = WatchSendState.CheckingWatch
            val result = WearApkSender.send(
                context = application,
                apkUri = apkUri,
                fileName = fileName,
                onProgress = { progress ->
                    _watchSendState.value = WatchSendState.Sending(progress)
                },
            )
            _watchSendState.value = when (result) {
                is WearApkSender.SendResult.Success -> WatchSendState.Success
                is WearApkSender.SendResult.NoWatchFound -> WatchSendState.NoWatch
                is WearApkSender.SendResult.Unsupported -> WatchSendState.Unsupported(result.reason)
                is WearApkSender.SendResult.Error -> WatchSendState.Error(result.message)
            }
        }
    }

    fun dismissWatchSend() {
        _watchSendState.value = WatchSendState.Idle
    }
}
