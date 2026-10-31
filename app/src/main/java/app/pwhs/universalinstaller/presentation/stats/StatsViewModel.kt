package app.pwhs.universalinstaller.presentation.stats

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.data.local.DownloadHistoryDao
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.data.local.UninstallLogDao
import app.pwhs.universalinstaller.domain.stats.MilestoneCategory
import app.pwhs.universalinstaller.domain.stats.UserStatsSummary
import app.pwhs.universalinstaller.domain.stats.UserStatsTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class StatsViewModel(
    private val application: Application,
    private val installHistoryDao: InstallHistoryDao,
    private val uninstallLogDao: UninstallLogDao,
    private val downloadHistoryDao: DownloadHistoryDao,
) : ViewModel() {

    private val _selectedPeriodDays = MutableStateFlow(7)
    val selectedPeriodDays: StateFlow<Int> = _selectedPeriodDays.asStateFlow()

    private val _selectedCategory = MutableStateFlow<MilestoneCategory?>(null)
    val selectedCategory: StateFlow<MilestoneCategory?> = _selectedCategory.asStateFlow()

    val summary: StateFlow<UserStatsSummary> = combine(
        application.dataStore.data,
        installHistoryDao.getAll(),
        uninstallLogDao.getAll(),
        downloadHistoryDao.getAll(),
    ) { prefs, installs, uninstalls, downloads ->
        UserStatsTracker.buildSummary(
            prefs = prefs,
            installHistory = installs,
            uninstallLogs = uninstalls,
            downloadHistory = downloads,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserStatsSummary(),
    )

    init {
        viewModelScope.launch {
            val installs = runCatching { installHistoryDao.getAll().first() }.getOrDefault(emptyList())
            val uninstalls = runCatching { uninstallLogDao.getAll().first() }.getOrDefault(emptyList())
            UserStatsTracker.syncWithHistoryIfNeeded(application, installs, uninstalls)
        }
    }

    fun selectPeriodDays(days: Int) {
        _selectedPeriodDays.value = days
    }

    fun selectCategory(category: MilestoneCategory?) {
        _selectedCategory.value = category
    }

    fun markMilestonesSeen() {
        viewModelScope.launch {
            UserStatsTracker.markMilestonesSeen(application)
        }
    }
}
