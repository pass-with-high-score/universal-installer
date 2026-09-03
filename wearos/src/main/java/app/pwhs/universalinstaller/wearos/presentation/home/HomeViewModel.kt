package app.pwhs.universalinstaller.wearos.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalinstaller.wearos.data.WearApkInfo
import app.pwhs.universalinstaller.wearos.data.WearApkRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: WearApkRepository,
) : ViewModel() {

    val apks: StateFlow<List<WearApkInfo>> = repository.apks
    val isLoading: StateFlow<Boolean> = repository.isLoading

    init {
        viewModelScope.launch { repository.refresh() }
    }
}
