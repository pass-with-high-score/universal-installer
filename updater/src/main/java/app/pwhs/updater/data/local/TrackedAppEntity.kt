package app.pwhs.updater.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import app.pwhs.updater.domain.model.TrackedApp
import app.pwhs.updater.domain.model.UpdateSourceType

@Entity(tableName = "tracked_apps")
data class TrackedAppEntity(
    @PrimaryKey val packageName: String,
    val appName: String,
    val iconUrl: String? = null,
    val sourceType: String,
    val sourceUrl: String,
    val currentVersionName: String,
    val currentVersionCode: Long,
    val latestVersionName: String? = null,
    val latestVersionCode: Long? = null,
    val latestReleaseTag: String? = null,
    val latestDownloadUrl: String? = null,
    val releaseNotes: String? = null,
    val publishedAt: Long? = null,
    val lastCheckedAt: Long = 0L,
    val includePrereleases: Boolean = false,
    val customRegexFilter: String? = null,
    val versionRegex: String? = null,
    val category: String? = null,
    val ignoredVersion: String? = null,
    val eTag: String? = null,
) {
    fun toDomain(): TrackedApp = TrackedApp(
        packageName = packageName,
        appName = appName,
        iconUrl = iconUrl,
        sourceType = runCatching { UpdateSourceType.valueOf(sourceType) }.getOrDefault(UpdateSourceType.UNKNOWN),
        sourceUrl = sourceUrl,
        currentVersionName = currentVersionName,
        currentVersionCode = currentVersionCode,
        latestVersionName = latestVersionName,
        latestVersionCode = latestVersionCode,
        latestReleaseTag = latestReleaseTag,
        latestDownloadUrl = latestDownloadUrl,
        releaseNotes = releaseNotes,
        publishedAt = publishedAt,
        lastCheckedAt = lastCheckedAt,
        includePrereleases = includePrereleases,
        customRegexFilter = customRegexFilter,
        versionRegex = versionRegex,
        category = category,
        ignoredVersion = ignoredVersion,
        eTag = eTag,
    )

    companion object {
        fun fromDomain(domain: TrackedApp): TrackedAppEntity = TrackedAppEntity(
            packageName = domain.packageName,
            appName = domain.appName,
            iconUrl = domain.iconUrl,
            sourceType = domain.sourceType.name,
            sourceUrl = domain.sourceUrl,
            currentVersionName = domain.currentVersionName,
            currentVersionCode = domain.currentVersionCode,
            latestVersionName = domain.latestVersionName,
            latestVersionCode = domain.latestVersionCode,
            latestReleaseTag = domain.latestReleaseTag,
            latestDownloadUrl = domain.latestDownloadUrl,
            releaseNotes = domain.releaseNotes,
            publishedAt = domain.publishedAt,
            lastCheckedAt = domain.lastCheckedAt,
            includePrereleases = domain.includePrereleases,
            customRegexFilter = domain.customRegexFilter,
            versionRegex = domain.versionRegex,
            category = domain.category,
            ignoredVersion = domain.ignoredVersion,
            eTag = domain.eTag,
        )
    }
}
