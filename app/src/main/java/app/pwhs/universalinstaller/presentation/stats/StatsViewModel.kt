package app.pwhs.universalinstaller.presentation.stats

import android.app.Application
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.network.VirusTotalKeyManager
import app.pwhs.core.telemetry.AnalyticsHelper
import app.pwhs.universalinstaller.data.local.DownloadHistoryDao
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.data.local.UninstallLogDao
import app.pwhs.universalinstaller.domain.pro.MilestoneRewardTier
import app.pwhs.universalinstaller.domain.pro.ProTrialManager
import app.pwhs.universalinstaller.domain.stats.MilestoneCategory
import app.pwhs.universalinstaller.domain.stats.UserStatsSummary
import app.pwhs.universalinstaller.domain.stats.UserStatsTracker
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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

    private val _claimingTierId = MutableStateFlow<String?>(null)
    val claimingTierId: StateFlow<String?> = _claimingTierId.asStateFlow()

    private val _milestoneRewardEnabled = MutableStateFlow(
        VirusTotalKeyManager.isMilestoneProTrialEnabled()
    )
    val milestoneRewardEnabled: StateFlow<Boolean> = _milestoneRewardEnabled.asStateFlow()

    val claimedRewardTiers: StateFlow<Set<String>> = application.dataStore.data
        .map { it[SharedPrefsKeys.PRO_TRIAL_CLAIMED_MILESTONES].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptySet())

    val isPurchasedPro: StateFlow<Boolean> = application.dataStore.data
        .map { it[SharedPrefsKeys.IS_PRO_USER] ?: false }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val baseRewardHours: Int
        get() = VirusTotalKeyManager.getMilestoneProTrialHours().coerceIn(1, 72)

    fun claimRewardTier(tier: MilestoneRewardTier) {
        if (_claimingTierId.value != null) return
        viewModelScope.launch {
            _claimingTierId.value = tier.tierId
            try {
                ProTrialManager.claimRewardTier(
                    context = application,
                    tier = tier,
                    unlockedCount = summary.value.unlockedCount,
                )
            } finally {
                _claimingTierId.value = null
            }
        }
    }

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
        viewModelScope.launch {
            runCatching { VirusTotalKeyManager.ensureRemoteConfigFetched() }
            _milestoneRewardEnabled.value = VirusTotalKeyManager.isMilestoneProTrialEnabled()
        }
    }

    fun selectPeriodDays(days: Int) {
        if (_selectedPeriodDays.value != days) {
            _selectedPeriodDays.value = days
            AnalyticsHelper.logStatsPeriodChanged(days)
        }
    }

    fun selectCategory(category: MilestoneCategory?) {
        if (_selectedCategory.value != category) {
            _selectedCategory.value = category
            AnalyticsHelper.logStatsCategoryChanged(category?.name ?: "all")
        }
    }

    fun markMilestonesSeen() {
        viewModelScope.launch {
            UserStatsTracker.markMilestonesSeen(application)
        }
    }
}
