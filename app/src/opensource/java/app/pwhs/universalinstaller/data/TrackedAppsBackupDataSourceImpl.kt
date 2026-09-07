package app.pwhs.universalinstaller.data

import android.content.Context
import app.pwhs.universalinstaller.domain.backup.TrackedAppBackupDto
import app.pwhs.universalinstaller.domain.backup.TrackedAppsBackupDataSource
import app.pwhs.updater.data.local.TrackedAppEntity
import app.pwhs.updater.data.local.UpdaterDatabase
import kotlinx.coroutines.flow.first

class TrackedAppsBackupDataSourceImpl(
    private val context: Context,
) : TrackedAppsBackupDataSource {

    private val dao by lazy {
        UpdaterDatabase.getInstance(context).trackedAppDao()
    }

    override fun isAvailable(): Boolean = true

    override suspend fun getTrackedApps(): List<TrackedAppBackupDto> {
        return runCatching {
            dao.getAllTrackedApps().first().map { entity ->
                TrackedAppBackupDto(
                    packageName = entity.packageName,
                    appName = entity.appName,
                    sourceUrl = entity.sourceUrl,
                    sourceType = entity.sourceType,
                    includePrereleases = entity.includePrereleases,
                    customRegexFilter = entity.customRegexFilter,
                    versionRegex = entity.versionRegex,
                    matchGroup = entity.matchGroup,
                    useReleaseTitleAsVersion = entity.useReleaseTitleAsVersion,
                    category = entity.category,
                )
            }
        }.getOrDefault(emptyList())
    }

    override suspend fun restoreTrackedApps(apps: List<TrackedAppBackupDto>): Int {
        var count = 0
        for (dto in apps) {
            runCatching {
                val existing = dao.getByPackageName(dto.packageName)
                val entity = if (existing != null) {
                    existing.copy(
                        appName = dto.appName,
                        sourceUrl = dto.sourceUrl,
                        sourceType = dto.sourceType,
                        includePrereleases = dto.includePrereleases,
                        customRegexFilter = dto.customRegexFilter,
                        versionRegex = dto.versionRegex,
                        matchGroup = dto.matchGroup,
                        useReleaseTitleAsVersion = dto.useReleaseTitleAsVersion,
                        category = dto.category,
                    )
                } else {
                    TrackedAppEntity(
                        packageName = dto.packageName,
                        appName = dto.appName,
                        sourceType = dto.sourceType,
                        sourceUrl = dto.sourceUrl,
                        currentVersionName = "Not Installed",
                        currentVersionCode = 0L,
                        includePrereleases = dto.includePrereleases,
                        customRegexFilter = dto.customRegexFilter,
                        versionRegex = dto.versionRegex,
                        matchGroup = dto.matchGroup,
                        useReleaseTitleAsVersion = dto.useReleaseTitleAsVersion,
                        category = dto.category,
                    )
                }
                dao.insertOrUpdate(entity)
                count++
            }
        }
        return count
    }
}
