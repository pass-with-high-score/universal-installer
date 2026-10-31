package app.pwhs.universalinstaller.domain.stats

import android.content.Context
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.data.local.DownloadHistoryEntity
import app.pwhs.universalinstaller.data.local.InstallHistoryEntity
import app.pwhs.universalinstaller.data.local.UninstallLogEntity
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import timber.log.Timber
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.roundToInt

object UserStatsTracker {

    // Lifetime counters in DataStore (survive clearing Room history)
    val KEY_SUCCESS_INSTALLS = intPreferencesKey("stats_lifetime_success_installs")
    val KEY_FAILED_INSTALLS = intPreferencesKey("stats_lifetime_failed_installs")
    val KEY_NEW_INSTALLS = intPreferencesKey("stats_lifetime_new_installs")
    val KEY_UPDATES = intPreferencesKey("stats_lifetime_updates")
    val KEY_DOWNGRADES = intPreferencesKey("stats_lifetime_downgrades")
    val KEY_UNINSTALLS = intPreferencesKey("stats_lifetime_uninstalls")

    val KEY_BYTES_INSTALLED = longPreferencesKey("stats_lifetime_bytes_installed")
    val KEY_BYTES_CLEANED = longPreferencesKey("stats_lifetime_bytes_cleaned")
    val KEY_FILES_CLEANED = intPreferencesKey("stats_lifetime_files_cleaned")
    val KEY_SINGLE_APK_COUNT = intPreferencesKey("stats_lifetime_single_apk_count")
    val KEY_SPLIT_BUNDLE_COUNT = intPreferencesKey("stats_lifetime_split_bundle_count")
    val KEY_OBB_INSTALL_COUNT = intPreferencesKey("stats_lifetime_obb_install_count")

    val KEY_MODE_DEFAULT = intPreferencesKey("stats_lifetime_mode_default")
    val KEY_MODE_SHIZUKU = intPreferencesKey("stats_lifetime_mode_shizuku")
    val KEY_MODE_ROOT = intPreferencesKey("stats_lifetime_mode_root")
    val KEY_MODE_DHIZUKU = intPreferencesKey("stats_lifetime_mode_dhizuku")

    val KEY_VT_SCANS = intPreferencesKey("stats_lifetime_vt_scans")
    val KEY_TRACKER_SCANS = intPreferencesKey("stats_lifetime_tracker_scans")
    val KEY_TRACKERS_DETECTED = intPreferencesKey("stats_lifetime_trackers_detected")

    val KEY_ANNOUNCED_MILESTONES = stringSetPreferencesKey("stats_announced_milestones")
    val KEY_UNSEEN_MILESTONES = stringSetPreferencesKey("stats_unseen_milestones")

    // Legacy key from ReviewGate so we never undercount installs recorded before Stats existed
    private val REVIEW_SUCCESSFUL_INSTALLS = intPreferencesKey("review_successful_installs")

    private val _milestoneEvents = MutableSharedFlow<MilestoneId>(extraBufferCapacity = 4)
    val milestoneEvents: SharedFlow<MilestoneId> = _milestoneEvents.asSharedFlow()

    fun isSplitFileName(fileName: String): Boolean {
        val lower = fileName.lowercase(Locale.ROOT)
        return lower.endsWith(".xapk") ||
            lower.endsWith(".apks") ||
            lower.endsWith(".apkm") ||
            lower.endsWith(".zip")
    }

    suspend fun recordInstallResult(
        context: Context,
        fileName: String,
        fileSizeBytes: Long,
        success: Boolean,
        operationType: String?,
        installerMode: String?,
        isSplitBundle: Boolean = isSplitFileName(fileName),
    ) {
        updateAndCheckMilestones(context, emitPopup = true) { prefs ->
            if (success) {
                prefs[KEY_SUCCESS_INSTALLS] = (prefs[KEY_SUCCESS_INSTALLS] ?: 0) + 1
                if (fileSizeBytes > 0L) {
                    prefs[KEY_BYTES_INSTALLED] = (prefs[KEY_BYTES_INSTALLED] ?: 0L) + fileSizeBytes
                }
                when (operationType) {
                    "UPDATE" -> prefs[KEY_UPDATES] = (prefs[KEY_UPDATES] ?: 0) + 1
                    "DOWNGRADE" -> prefs[KEY_DOWNGRADES] = (prefs[KEY_DOWNGRADES] ?: 0) + 1
                    else -> prefs[KEY_NEW_INSTALLS] = (prefs[KEY_NEW_INSTALLS] ?: 0) + 1
                }
                if (isSplitBundle) {
                    prefs[KEY_SPLIT_BUNDLE_COUNT] = (prefs[KEY_SPLIT_BUNDLE_COUNT] ?: 0) + 1
                } else {
                    prefs[KEY_SINGLE_APK_COUNT] = (prefs[KEY_SINGLE_APK_COUNT] ?: 0) + 1
                }
                when (installerMode) {
                    "Shizuku" -> prefs[KEY_MODE_SHIZUKU] = (prefs[KEY_MODE_SHIZUKU] ?: 0) + 1
                    "Root" -> prefs[KEY_MODE_ROOT] = (prefs[KEY_MODE_ROOT] ?: 0) + 1
                    "Dhizuku" -> prefs[KEY_MODE_DHIZUKU] = (prefs[KEY_MODE_DHIZUKU] ?: 0) + 1
                    else -> prefs[KEY_MODE_DEFAULT] = (prefs[KEY_MODE_DEFAULT] ?: 0) + 1
                }
            } else {
                prefs[KEY_FAILED_INSTALLS] = (prefs[KEY_FAILED_INSTALLS] ?: 0) + 1
            }
        }
    }

    suspend fun recordObbInstall(context: Context) {
        updateAndCheckMilestones(context, emitPopup = true) { prefs ->
            prefs[KEY_OBB_INSTALL_COUNT] = (prefs[KEY_OBB_INSTALL_COUNT] ?: 0) + 1
        }
    }

    suspend fun recordSourceFileDeleted(context: Context, bytesFreed: Long) {
        updateAndCheckMilestones(context, emitPopup = false) { prefs ->
            prefs[KEY_FILES_CLEANED] = (prefs[KEY_FILES_CLEANED] ?: 0) + 1
            if (bytesFreed > 0L) {
                prefs[KEY_BYTES_CLEANED] = (prefs[KEY_BYTES_CLEANED] ?: 0L) + bytesFreed
            }
        }
    }

    suspend fun recordUninstallSuccess(context: Context) {
        updateAndCheckMilestones(context, emitPopup = true) { prefs ->
            prefs[KEY_UNINSTALLS] = (prefs[KEY_UNINSTALLS] ?: 0) + 1
        }
    }

    suspend fun recordVirusTotalScan(context: Context) {
        updateAndCheckMilestones(context, emitPopup = true) { prefs ->
            prefs[KEY_VT_SCANS] = (prefs[KEY_VT_SCANS] ?: 0) + 1
        }
    }

    suspend fun recordTrackerScan(context: Context, detectedTrackersCount: Int) {
        updateAndCheckMilestones(context, emitPopup = true) { prefs ->
            prefs[KEY_TRACKER_SCANS] = (prefs[KEY_TRACKER_SCANS] ?: 0) + 1
            if (detectedTrackersCount > 0) {
                prefs[KEY_TRACKERS_DETECTED] =
                    (prefs[KEY_TRACKERS_DETECTED] ?: 0) + detectedTrackersCount
            }
        }
    }

    suspend fun markMilestonesSeen(context: Context) {
        runCatching {
            context.dataStore.edit { prefs ->
                prefs[KEY_UNSEEN_MILESTONES] = emptySet()
                prefs[SharedPrefsKeys.UNSEEN_MILESTONE_COUNT] = 0
            }
        }.onFailure { Timber.w(it, "Failed to mark milestones seen") }
    }

    /**
     * Backfills lifetime counters from existing Room history if Room has larger numbers than
     * DataStore (e.g. for existing users upgrading to the version with Stats).
     */
    suspend fun syncWithHistoryIfNeeded(
        context: Context,
        installHistory: List<InstallHistoryEntity>,
        uninstallLogs: List<UninstallLogEntity>,
    ) {
        runCatching {
            val successEntries = installHistory.filter { it.success }
            val failCount = installHistory.count { !it.success }
            val newCount = successEntries.count { it.operationType == null || it.operationType == "INSTALL" }
            val updateCount = successEntries.count { it.operationType == "UPDATE" }
            val downgradeCount = successEntries.count { it.operationType == "DOWNGRADE" }
            val uninstallSuccessCount = uninstallLogs.count { it.success }
            val bytesInstalled = successEntries.sumOf { max(0L, it.fileSizeBytes) }
            val splitCount = successEntries.count { isSplitFileName(it.fileName) }
            val singleCount = successEntries.size - splitCount
            val shizukuCount = successEntries.count { it.installerMode == "Shizuku" }
            val rootCount = successEntries.count { it.installerMode == "Root" }
            val dhizukuCount = successEntries.count { it.installerMode == "Dhizuku" }
            val defaultCount = successEntries.count {
                it.installerMode == null || it.installerMode == "Default" || it.installerMode == "Manual"
            }

            updateAndCheckMilestones(context, emitPopup = false) { prefs ->
                val reviewInstalls = prefs[REVIEW_SUCCESSFUL_INSTALLS] ?: 0
                prefs[KEY_SUCCESS_INSTALLS] = maxOf(prefs[KEY_SUCCESS_INSTALLS] ?: 0, successEntries.size, reviewInstalls)
                prefs[KEY_FAILED_INSTALLS] = max(prefs[KEY_FAILED_INSTALLS] ?: 0, failCount)
                prefs[KEY_NEW_INSTALLS] = max(prefs[KEY_NEW_INSTALLS] ?: 0, newCount)
                prefs[KEY_UPDATES] = max(prefs[KEY_UPDATES] ?: 0, updateCount)
                prefs[KEY_DOWNGRADES] = max(prefs[KEY_DOWNGRADES] ?: 0, downgradeCount)
                prefs[KEY_UNINSTALLS] = max(prefs[KEY_UNINSTALLS] ?: 0, uninstallSuccessCount)
                prefs[KEY_BYTES_INSTALLED] = max(prefs[KEY_BYTES_INSTALLED] ?: 0L, bytesInstalled)
                prefs[KEY_SPLIT_BUNDLE_COUNT] = max(prefs[KEY_SPLIT_BUNDLE_COUNT] ?: 0, splitCount)
                prefs[KEY_SINGLE_APK_COUNT] = max(prefs[KEY_SINGLE_APK_COUNT] ?: 0, singleCount)
                prefs[KEY_MODE_SHIZUKU] = max(prefs[KEY_MODE_SHIZUKU] ?: 0, shizukuCount)
                prefs[KEY_MODE_ROOT] = max(prefs[KEY_MODE_ROOT] ?: 0, rootCount)
                prefs[KEY_MODE_DHIZUKU] = max(prefs[KEY_MODE_DHIZUKU] ?: 0, dhizukuCount)
                prefs[KEY_MODE_DEFAULT] = max(prefs[KEY_MODE_DEFAULT] ?: 0, defaultCount)
            }
        }.onFailure { Timber.w(it, "Failed to sync stats with history") }
    }

    fun buildSummary(
        prefs: Preferences,
        installHistory: List<InstallHistoryEntity>,
        uninstallLogs: List<UninstallLogEntity>,
        downloadHistory: List<DownloadHistoryEntity>,
    ): UserStatsSummary {
        val successEntries = installHistory.filter { it.success }
        val failEntries = installHistory.filter { !it.success }

        val reviewInstalls = prefs[REVIEW_SUCCESSFUL_INSTALLS] ?: 0
        val totalSuccess = maxOf(prefs[KEY_SUCCESS_INSTALLS] ?: 0, successEntries.size, reviewInstalls)
        val totalFailed = max(prefs[KEY_FAILED_INSTALLS] ?: 0, failEntries.size)
        val totalAttempts = totalSuccess + totalFailed
        val successRate = if (totalAttempts > 0) {
            ((totalSuccess.toDouble() / totalAttempts.toDouble()) * 100.0).roundToInt()
        } else {
            100
        }

        val histNew = successEntries.count { it.operationType == null || it.operationType == "INSTALL" }
        val histUpdate = successEntries.count { it.operationType == "UPDATE" }
        val histDowngrade = successEntries.count { it.operationType == "DOWNGRADE" }

        val newInstalls = max(prefs[KEY_NEW_INSTALLS] ?: 0, histNew)
        val updates = max(prefs[KEY_UPDATES] ?: 0, histUpdate)
        val downgrades = max(prefs[KEY_DOWNGRADES] ?: 0, histDowngrade)
        val totalUninstalls = max(prefs[KEY_UNINSTALLS] ?: 0, uninstallLogs.count { it.success })

        val histBytes = successEntries.sumOf { max(0L, it.fileSizeBytes) }
        val totalBytesInstalled = max(prefs[KEY_BYTES_INSTALLED] ?: 0L, histBytes)
        val totalBytesCleaned = prefs[KEY_BYTES_CLEANED] ?: 0L
        val totalFilesCleaned = prefs[KEY_FILES_CLEANED] ?: 0

        val histSplit = successEntries.count { isSplitFileName(it.fileName) }
        val histSingle = successEntries.size - histSplit
        val splitBundleCount = max(prefs[KEY_SPLIT_BUNDLE_COUNT] ?: 0, histSplit)
        val singleApkCount = max(prefs[KEY_SINGLE_APK_COUNT] ?: 0, histSingle)
        val obbInstallCount = prefs[KEY_OBB_INSTALL_COUNT] ?: 0

        val shizukuCount = max(
            prefs[KEY_MODE_SHIZUKU] ?: 0,
            successEntries.count { it.installerMode == "Shizuku" },
        )
        val rootCount = max(
            prefs[KEY_MODE_ROOT] ?: 0,
            successEntries.count { it.installerMode == "Root" },
        )
        val dhizukuCount = max(
            prefs[KEY_MODE_DHIZUKU] ?: 0,
            successEntries.count { it.installerMode == "Dhizuku" },
        )
        val defaultCount = max(
            prefs[KEY_MODE_DEFAULT] ?: 0,
            successEntries.count {
                it.installerMode == null || it.installerMode == "Default" || it.installerMode == "Manual"
            },
        )

        val vtScans = prefs[KEY_VT_SCANS] ?: 0
        val trackerScans = prefs[KEY_TRACKER_SCANS] ?: 0
        val trackersDetected = prefs[KEY_TRACKERS_DETECTED] ?: 0

        val unseenSet = prefs[KEY_UNSEEN_MILESTONES].orEmpty()
        val privilegedTotal = (shizukuCount + rootCount + dhizukuCount).toLong()
        val securityScansTotal = (vtScans + trackerScans).toLong()

        val milestones = MilestoneId.entries.map { id ->
            val currentVal = when (id) {
                MilestoneId.FIRST_INSTALL,
                MilestoneId.TEN_INSTALLS,
                MilestoneId.FIFTY_INSTALLS,
                MilestoneId.HUNDRED_INSTALLS -> totalSuccess.toLong()
                MilestoneId.SPLIT_BUNDLE -> splitBundleCount.toLong()
                MilestoneId.OBB_MASTER -> obbInstallCount.toLong()
                MilestoneId.GIGABYTE_CLUB,
                MilestoneId.TEN_GIGABYTES -> totalBytesInstalled
                MilestoneId.PRIVILEGED_USER -> privilegedTotal
                MilestoneId.TIME_TRAVELER -> downgrades.toLong()
                MilestoneId.DEBLOAT_WARRIOR -> totalUninstalls.toLong()
                MilestoneId.SECURITY_GUARDIAN -> securityScansTotal
            }
            MilestoneProgress(
                id = id,
                currentValue = currentVal,
                targetValue = id.targetValue,
                isUnlocked = currentVal >= id.targetValue,
                isNewlyUnlocked = id.name in unseenSet,
            )
        }

        val unlockedCount = milestones.count { it.isUnlocked }
        val (rank, nextRank) = UserRank.forUnlockedCount(unlockedCount)

        val last7Days = buildDailyStats(installHistory, days = 7)
        val last30Days = buildDailyStats(installHistory, days = 30)
        val busiestDay = last30Days.maxOfOrNull { it.totalCount } ?: 0

        val topApps = successEntries
            .filter { it.packageName.isNotBlank() }
            .groupBy { it.packageName }
            .map { (pkg, entries) ->
                val latest = entries.maxByOrNull { it.installedAt } ?: entries.first()
                TopAppStat(
                    packageName = pkg,
                    appName = latest.appName.ifBlank { pkg },
                    iconPath = entries.firstNotNullOfOrNull { it.iconPath },
                    totalInstalls = entries.size,
                    updateCount = entries.count { it.operationType == "UPDATE" },
                )
            }
            .sortedWith(compareByDescending<TopAppStat> { it.totalInstalls }.thenByDescending { it.updateCount })
            .take(5)

        return UserStatsSummary(
            totalSuccessInstalls = totalSuccess,
            totalFailedInstalls = totalFailed,
            successRatePercent = successRate,
            newInstallCount = newInstalls,
            updateCount = updates,
            downgradeCount = downgrades,
            totalUninstalls = totalUninstalls,
            totalDownloads = downloadHistory.size,
            totalBytesInstalled = totalBytesInstalled,
            totalBytesCleaned = totalBytesCleaned,
            totalFilesCleaned = totalFilesCleaned,
            singleApkCount = singleApkCount,
            splitBundleCount = splitBundleCount,
            obbInstallCount = obbInstallCount,
            defaultModeCount = defaultCount,
            shizukuModeCount = shizukuCount,
            rootModeCount = rootCount,
            dhizukuModeCount = dhizukuCount,
            virusTotalScans = vtScans,
            trackerScans = trackerScans,
            trackersDetectedCount = trackersDetected,
            last7DaysActivity = last7Days,
            last30DaysActivity = last30Days,
            busiestDayCount = busiestDay,
            topUpdatedApps = topApps,
            rank = rank,
            nextRank = nextRank,
            milestones = milestones,
            unlockedCount = unlockedCount,
            totalMilestones = MilestoneId.entries.size,
        )
    }

    private fun buildDailyStats(
        history: List<InstallHistoryEntity>,
        days: Int,
    ): List<DailyInstallStat> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val dayFmt = SimpleDateFormat("EEE", Locale.getDefault())
        val dateFmt = SimpleDateFormat("dd/MM", Locale.getDefault())

        val dayBuckets = (days - 1 downTo 0).map { offset ->
            val startCal = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -offset) }
            val startMs = startCal.timeInMillis
            val endMs = startMs + 24L * 60L * 60L * 1000L
            val dayEntries = history.filter { it.installedAt in startMs until endMs }
            DailyInstallStat(
                dayLabel = dayFmt.format(Date(startMs)),
                dateLabel = dateFmt.format(Date(startMs)),
                epochDay = startMs / (24L * 60L * 60L * 1000L),
                successCount = dayEntries.count { it.success },
                failCount = dayEntries.count { !it.success },
            )
        }
        return dayBuckets
    }

    private suspend fun updateAndCheckMilestones(
        context: Context,
        emitPopup: Boolean,
        mutator: (MutablePreferences) -> Unit,
    ) {
        val newlyUnlocked = mutableListOf<MilestoneId>()
        runCatching {
            context.dataStore.edit { prefs ->
                mutator(prefs)
                val announced = prefs[KEY_ANNOUNCED_MILESTONES].orEmpty().toMutableSet()
                val unseen = prefs[KEY_UNSEEN_MILESTONES].orEmpty().toMutableSet()

                val totalSuccess = max(
                    prefs[KEY_SUCCESS_INSTALLS] ?: 0,
                    prefs[REVIEW_SUCCESSFUL_INSTALLS] ?: 0,
                ).toLong()
                val splitCount = (prefs[KEY_SPLIT_BUNDLE_COUNT] ?: 0).toLong()
                val obbCount = (prefs[KEY_OBB_INSTALL_COUNT] ?: 0).toLong()
                val bytesInstalled = prefs[KEY_BYTES_INSTALLED] ?: 0L
                val privilegedCount = (
                    (prefs[KEY_MODE_SHIZUKU] ?: 0) +
                        (prefs[KEY_MODE_ROOT] ?: 0) +
                        (prefs[KEY_MODE_DHIZUKU] ?: 0)
                    ).toLong()
                val downgrades = (prefs[KEY_DOWNGRADES] ?: 0).toLong()
                val uninstalls = (prefs[KEY_UNINSTALLS] ?: 0).toLong()
                val securityScans = ((prefs[KEY_VT_SCANS] ?: 0) + (prefs[KEY_TRACKER_SCANS] ?: 0)).toLong()

                for (id in MilestoneId.entries) {
                    val value = when (id) {
                        MilestoneId.FIRST_INSTALL,
                        MilestoneId.TEN_INSTALLS,
                        MilestoneId.FIFTY_INSTALLS,
                        MilestoneId.HUNDRED_INSTALLS -> totalSuccess
                        MilestoneId.SPLIT_BUNDLE -> splitCount
                        MilestoneId.OBB_MASTER -> obbCount
                        MilestoneId.GIGABYTE_CLUB,
                        MilestoneId.TEN_GIGABYTES -> bytesInstalled
                        MilestoneId.PRIVILEGED_USER -> privilegedCount
                        MilestoneId.TIME_TRAVELER -> downgrades
                        MilestoneId.DEBLOAT_WARRIOR -> uninstalls
                        MilestoneId.SECURITY_GUARDIAN -> securityScans
                    }
                    if (value >= id.targetValue && id.name !in announced) {
                        announced += id.name
                        unseen += id.name
                        newlyUnlocked += id
                    }
                }

                prefs[KEY_ANNOUNCED_MILESTONES] = announced
                prefs[KEY_UNSEEN_MILESTONES] = unseen
                prefs[SharedPrefsKeys.UNSEEN_MILESTONE_COUNT] = unseen.size
            }
        }.onFailure {
            Timber.w(it, "Failed to update user stats")
        }

        if (emitPopup) {
            newlyUnlocked.forEach { milestone ->
                _milestoneEvents.tryEmit(milestone)
            }
        }
    }
}
