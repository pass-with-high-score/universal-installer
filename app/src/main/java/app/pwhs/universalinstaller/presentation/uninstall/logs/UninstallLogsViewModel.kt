package app.pwhs.universalinstaller.presentation.uninstall.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.universalinstaller.data.local.UninstallLogDao
import app.pwhs.universalinstaller.data.local.UninstallLogEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class UninstallLogsViewModel(
    private val uninstallLogDao: UninstallLogDao,
) : ViewModel() {

    val logs: StateFlow<List<UninstallLogEntity>> = uninstallLogDao.getAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun clearAll() {
        viewModelScope.launch { uninstallLogDao.clearAll() }
    }

    fun deleteById(id: Long) {
        viewModelScope.launch { uninstallLogDao.deleteById(id) }
    }
}
