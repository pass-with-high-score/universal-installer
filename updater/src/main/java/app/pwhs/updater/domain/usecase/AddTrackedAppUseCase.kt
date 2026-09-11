package app.pwhs.updater.domain.usecase

import android.content.Context
import android.os.Build
import app.pwhs.updater.domain.matcher.InstalledAppMatchResult
import app.pwhs.updater.domain.matcher.InstalledAppMatcher
import app.pwhs.updater.domain.matcher.SmartAbiMatcher
import app.pwhs.updater.domain.model.TrackedApp
import app.pwhs.updater.domain.model.UpdateSourceType
import app.pwhs.updater.domain.provider.GitHubReleaseProvider
import app.pwhs.updater.domain.provider.UpdateSourceProvider

class AddTrackedAppUseCase(
    private val providers: List<UpdateSourceProvider> = listOf(GitHubReleaseProvider()),
) {

    suspend fun execute(
        context: Context,
        url: String,
        includePrereleases: Boolean = false,
        effectiveToken: String? = null,
        targetPackageName: String? = null,
        category: String? = null,
    ): Result<TrackedApp> = runCatching {
        val provider = providers.firstOrNull { it.canHandle(url) }
            ?: throw IllegalArgumentException("No provider available for URL: $url")

        val releaseResult = provider.fetchLatestRelease(
            url = url,
            includePrereleases = includePrereleases,
            apiToken = effectiveToken,
        )

        val release = releaseResult.getOrNull()
            ?: throw IllegalStateException("Could not fetch release information")

        val bestAsset = SmartAbiMatcher.selectBestAsset(release.assets)
        val rawRepoName = url.substringBefore('?').removeSuffix(".git").substringAfterLast('/')
        val cleanAppName = rawRepoName.split('-', '_', '.').joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
        }

        val pm = context.packageManager
        val matchResult = if (!targetPackageName.isNullOrBlank()) {
            val pkgInfo = runCatching { pm.getPackageInfo(targetPackageName, 0) }.getOrNull()
            if (pkgInfo != null) {
                val vCode: Long = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    pkgInfo.longVersionCode
                } else {
                    @Suppress("DEPRECATION")
                    pkgInfo.versionCode.toLong()
                }
                val appLabel = runCatching {
                    pkgInfo.applicationInfo?.let { pm.getApplicationLabel(it).toString() }
                }.getOrNull() ?: cleanAppName
                InstalledAppMatcher.findMatch(pm, url, appLabel) ?: InstalledAppMatchResult(
                    packageName = targetPackageName,
                    appName = appLabel,
                    versionName = pkgInfo.versionName ?: "1.0",
                    versionCode = vCode,
                )
            } else null
        } else {
            InstalledAppMatcher.findMatch(
                pm = pm,
                repoUrl = url,
                candidateName = cleanAppName,
                assetNames = release.assets.map { it.name },
            )
        }

        val finalPkg = matchResult?.packageName ?: targetPackageName
            ?: "tracked.${rawRepoName.lowercase().replace(Regex("[^a-z0-9_]"), "_")}"
        val finalAppName = matchResult?.appName ?: cleanAppName

        TrackedApp(
            packageName = finalPkg,
            appName = finalAppName,
            iconUrl = release.iconUrl,
            sourceType = UpdateSourceType.fromUrl(url),
            sourceUrl = url,
            currentVersionName = matchResult?.versionName ?: "Not Installed",
            currentVersionCode = matchResult?.versionCode ?: 0L,
            latestVersionName = release.versionName,
            latestReleaseTag = release.tagName,
            latestDownloadUrl = bestAsset?.downloadUrl,
            releaseNotes = release.releaseNotes,
            publishedAt = release.publishedAt,
            lastCheckedAt = System.currentTimeMillis(),
            includePrereleases = includePrereleases,
            category = category?.trim()?.takeIf { it.isNotBlank() },
            eTag = release.eTag,
            availableAssets = release.assets.filter { SmartAbiMatcher.isPackageAsset(it.name) }.ifEmpty { release.assets },
        )
    }
}
