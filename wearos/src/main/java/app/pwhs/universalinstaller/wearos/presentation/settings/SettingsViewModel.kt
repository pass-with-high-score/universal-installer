package app.pwhs.universalinstaller.wearos.presentation.settings

import android.app.Application
import android.content.Intent
import android.provider.Settings
import androidx.core.net.toUri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.util.StorageUtil
import app.pwhs.universalinstaller.wearos.data.WearApkRepository
import app.pwhs.universalinstaller.wearos.data.WearSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val repository: WearApkRepository,
    private val settings: WearSettings,
    application: Application,
) : AndroidViewModel(application) {

    val keepAfterInstall: StateFlow<Boolean> =
        settings.keepAfterInstall.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    private val _queueBytes = MutableStateFlow(0L)
    val queueBytes: StateFlow<Long> = _queueBytes.asStateFlow()

    private val _freeBytes = MutableStateFlow(0L)
    val freeBytes: StateFlow<Long> = _freeBytes.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _queueBytes.value = repository.queueBytes()
            _freeBytes.value = StorageUtil.getStorageStats().freeBytes
        }
    }

    fun setKeepAfterInstall(keep: Boolean) {
        viewModelScope.launch { settings.setKeepAfterInstall(keep) }
    }

    fun clearQueue() {
        viewModelScope.launch {
            repository.clearAll()
            refresh()
        }
    }

    /** The watch hides this behind Settings, so offer it before an install fails rather than after. */
    fun openInstallPermission() {
        val app = getApplication<Application>()
        val intent = Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            "package:${app.packageName}".toUri(),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { app.startActivity(intent) }
    }
}
