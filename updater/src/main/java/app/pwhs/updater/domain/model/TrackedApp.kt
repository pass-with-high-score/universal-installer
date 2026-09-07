package app.pwhs.updater.domain.model

import app.pwhs.updater.domain.matcher.SemVerComparator

data class TrackedApp(
    val packageName: String,
    val appName: String,
    val iconUrl: String? = null,
    val sourceType: UpdateSourceType,
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
    val matchGroup: String? = null,
    val useReleaseTitleAsVersion: Boolean = false,
    val category: String? = null,
    val ignoredVersion: String? = null,
    val eTag: String? = null,
    val availableAssets: List<AssetArtifact> = emptyList(),
) {
    val isInstalled: Boolean
        get() = currentVersionName.isNotBlank() && !currentVersionName.equals("Not Installed", ignoreCase = true)

    val isVersionIgnored: Boolean
        get() = !ignoredVersion.isNullOrBlank() && ignoredVersion.equals(latestVersionName, ignoreCase = true)

    val hasUpdate: Boolean
        get() {
            if (!isInstalled) return false // Fix #130: Uninstalled apps are not updates
            if (latestVersionName.isNullOrBlank()) return false
            if (latestDownloadUrl.isNullOrBlank()) return false // Fix #133: Release without APK cannot be updated
            if (isVersionIgnored) return false
            if (isLatestVersionNameBackedByCurrentVersionCode()) return false
            return SemVerComparator.isNewer(currentVersionName, latestVersionName)
        }

    private fun isLatestVersionNameBackedByCurrentVersionCode(): Boolean {
        if (currentVersionCode <= 0L || latestVersionName.isNullOrBlank()) return false

        val latest = latestVersionName.trim()
        val currentName = currentVersionName.trim()
        val currentCode = currentVersionCode.toString()
        val prefix = "$currentName."

        return latest.startsWith(prefix, ignoreCase = true) &&
            latest.substring(prefix.length) == currentCode
    }
}
