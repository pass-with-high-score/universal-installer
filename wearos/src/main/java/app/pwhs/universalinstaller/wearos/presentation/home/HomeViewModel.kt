package app.pwhs.universalinstaller.wearos.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.util.StorageStats
import app.pwhs.core.util.StorageUtil
import app.pwhs.universalinstaller.wearos.data.WearApkInfo
import app.pwhs.universalinstaller.wearos.data.WearApkRepository
import app.pwhs.universalinstaller.wearos.data.WearReceiveProgress
import app.pwhs.universalinstaller.wearos.data.WearReceiveState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: WearApkRepository,
) : ViewModel() {

    val apks: StateFlow<List<WearApkInfo>> = repository.apks
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val receiveState: StateFlow<WearReceiveState> = WearReceiveProgress.state

    private val _storage = MutableStateFlow(StorageStats(0L, 0L, 0L, 0f))
    val storage: StateFlow<StorageStats> = _storage.asStateFlow()

    private val _queueBytes = MutableStateFlow(0L)
    val queueBytes: StateFlow<Long> = _queueBytes.asStateFlow()

    init {
        viewModelScope.launch {
            repository.refresh()
            refreshStorage()
        }
    }

    fun delete(id: String) {
        viewModelScope.launch {
            repository.deleteById(id)
            refreshStorage()
        }
    }

    private suspend fun refreshStorage() {
        _queueBytes.value = repository.queueBytes()
        _storage.value = StorageUtil.getStorageStats()
    }
}
