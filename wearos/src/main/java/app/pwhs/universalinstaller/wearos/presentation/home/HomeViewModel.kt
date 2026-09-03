package app.pwhs.universalinstaller.wearos.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalinstaller.wearos.data.WearApkInfo
import app.pwhs.universalinstaller.wearos.data.WearApkRepository
import app.pwhs.universalinstaller.wearos.data.WearReceiveProgress
import app.pwhs.universalinstaller.wearos.data.WearReceiveState
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: WearApkRepository,
) : ViewModel() {

    val apks: StateFlow<List<WearApkInfo>> = repository.apks
    val isLoading: StateFlow<Boolean> = repository.isLoading
    val receiveState: StateFlow<WearReceiveState> = WearReceiveProgress.state

    init {
        viewModelScope.launch { repository.refresh() }
    }

    fun delete(id: String) {
        viewModelScope.launch { repository.deleteById(id) }
    }
}
