package app.pwhs.universalinstaller.domain.stats

import androidx.annotation.StringRes
import app.pwhs.universalinstaller.R

enum class UserRank(
    @StringRes val titleRes: Int,
    @StringRes val subtitleRes: Int,
    val minUnlockedMilestones: Int,
) {
    NEWBIE(
        titleRes = R.string.stats_rank_newbie,
        subtitleRes = R.string.stats_rank_newbie_sub,
        minUnlockedMilestones = 0,
    ),
    TINKERER(
        titleRes = R.string.stats_rank_tinkerer,
        subtitleRes = R.string.stats_rank_tinkerer_sub,
        minUnlockedMilestones = 2,
    ),
    EXPLORER(
        titleRes = R.string.stats_rank_explorer,
        subtitleRes = R.string.stats_rank_explorer_sub,
        minUnlockedMilestones = 5,
    ),
    PRIVILEGED_PRO(
        titleRes = R.string.stats_rank_privileged_pro,
        subtitleRes = R.string.stats_rank_privileged_pro_sub,
        minUnlockedMilestones = 8,
    ),
    SIDELOAD_MASTER(
        titleRes = R.string.stats_rank_sideload_master,
        subtitleRes = R.string.stats_rank_sideload_master_sub,
        minUnlockedMilestones = 11,
    );

    companion object {
        fun forUnlockedCount(count: Int): Pair<UserRank, UserRank?> {
            val current = entries.lastOrNull { count >= it.minUnlockedMilestones } ?: NEWBIE
            val nextIndex = current.ordinal + 1
            val next = entries.getOrNull(nextIndex)
            return current to next
        }
    }
}

enum class MilestoneCategory(@StringRes val labelRes: Int) {
    INSTALL(R.string.stats_category_install),
    STORAGE(R.string.stats_category_storage),
    POWER_USER(R.string.stats_category_power),
    SECURITY(R.string.stats_category_security),
}

enum class MilestoneId(
    val category: MilestoneCategory,
    @StringRes val titleRes: Int,
    @StringRes val descRes: Int,
    val targetValue: Long,
    val isBytes: Boolean = false,
) {
    FIRST_INSTALL(
        category = MilestoneCategory.INSTALL,
        titleRes = R.string.milestone_first_install_title,
        descRes = R.string.milestone_first_install_desc,
        targetValue = 1L,
    ),
    TEN_INSTALLS(
        category = MilestoneCategory.INSTALL,
        titleRes = R.string.milestone_ten_installs_title,
        descRes = R.string.milestone_ten_installs_desc,
        targetValue = 10L,
    ),
    FIFTY_INSTALLS(
        category = MilestoneCategory.INSTALL,
        titleRes = R.string.milestone_fifty_installs_title,
        descRes = R.string.milestone_fifty_installs_desc,
        targetValue = 50L,
    ),
    HUNDRED_INSTALLS(
        category = MilestoneCategory.INSTALL,
        titleRes = R.string.milestone_hundred_installs_title,
        descRes = R.string.milestone_hundred_installs_desc,
        targetValue = 100L,
    ),
    SPLIT_BUNDLE(
        category = MilestoneCategory.STORAGE,
        titleRes = R.string.milestone_split_bundle_title,
        descRes = R.string.milestone_split_bundle_desc,
        targetValue = 1L,
    ),
    OBB_MASTER(
        category = MilestoneCategory.STORAGE,
        titleRes = R.string.milestone_obb_master_title,
        descRes = R.string.milestone_obb_master_desc,
        targetValue = 1L,
    ),
    GIGABYTE_CLUB(
        category = MilestoneCategory.STORAGE,
        titleRes = R.string.milestone_gigabyte_club_title,
        descRes = R.string.milestone_gigabyte_club_desc,
        targetValue = 1024L * 1024L * 1024L,
        isBytes = true,
    ),
    TEN_GIGABYTES(
        category = MilestoneCategory.STORAGE,
        titleRes = R.string.milestone_ten_gigabytes_title,
        descRes = R.string.milestone_ten_gigabytes_desc,
        targetValue = 10L * 1024L * 1024L * 1024L,
        isBytes = true,
    ),
    PRIVILEGED_USER(
        category = MilestoneCategory.POWER_USER,
        titleRes = R.string.milestone_privileged_user_title,
        descRes = R.string.milestone_privileged_user_desc,
        targetValue = 1L,
    ),
    TIME_TRAVELER(
        category = MilestoneCategory.POWER_USER,
        titleRes = R.string.milestone_time_traveler_title,
        descRes = R.string.milestone_time_traveler_desc,
        targetValue = 1L,
    ),
    DEBLOAT_WARRIOR(
        category = MilestoneCategory.POWER_USER,
        titleRes = R.string.milestone_debloat_warrior_title,
        descRes = R.string.milestone_debloat_warrior_desc,
        targetValue = 10L,
    ),
    SECURITY_GUARDIAN(
        category = MilestoneCategory.SECURITY,
        titleRes = R.string.milestone_security_guardian_title,
        descRes = R.string.milestone_security_guardian_desc,
        targetValue = 5L,
    ),
}

data class MilestoneProgress(
    val id: MilestoneId,
    val currentValue: Long,
    val targetValue: Long,
    val isUnlocked: Boolean,
    val isNewlyUnlocked: Boolean,
) {
    val progressFraction: Float
        get() = if (targetValue <= 0L) 1f else (currentValue.toFloat() / targetValue.toFloat()).coerceIn(0f, 1f)
}

data class DailyInstallStat(
    val dayLabel: String,
    val dateLabel: String,
    val epochDay: Long,
    val successCount: Int,
    val failCount: Int,
) {
    val totalCount: Int get() = successCount + failCount
}

data class TopAppStat(
    val packageName: String,
    val appName: String,
    val iconPath: String?,
    val totalInstalls: Int,
    val updateCount: Int,
)

data class UserStatsSummary(
    // 1. Install & Uninstall Overview
    val totalSuccessInstalls: Int = 0,
    val totalFailedInstalls: Int = 0,
    val successRatePercent: Int = 100,
    val newInstallCount: Int = 0,
    val updateCount: Int = 0,
    val downgradeCount: Int = 0,
    val totalUninstalls: Int = 0,
    val totalDownloads: Int = 0,

    // 2. Storage & Formats
    val totalBytesInstalled: Long = 0L,
    val totalBytesCleaned: Long = 0L,
    val totalFilesCleaned: Int = 0,
    val singleApkCount: Int = 0,
    val splitBundleCount: Int = 0,
    val obbInstallCount: Int = 0,

    // 3. Modes & Security
    val defaultModeCount: Int = 0,
    val shizukuModeCount: Int = 0,
    val rootModeCount: Int = 0,
    val dhizukuModeCount: Int = 0,
    val virusTotalScans: Int = 0,
    val trackerScans: Int = 0,
    val trackersDetectedCount: Int = 0,

    // 4. Activity Over Time & Top Apps
    val last7DaysActivity: List<DailyInstallStat> = emptyList(),
    val last30DaysActivity: List<DailyInstallStat> = emptyList(),
    val busiestDayCount: Int = 0,
    val topUpdatedApps: List<TopAppStat> = emptyList(),

    // 5. Milestones & Rank
    val rank: UserRank = UserRank.NEWBIE,
    val nextRank: UserRank? = UserRank.TINKERER,
    val milestones: List<MilestoneProgress> = emptyList(),
    val unlockedCount: Int = 0,
    val totalMilestones: Int = MilestoneId.entries.size,
)
