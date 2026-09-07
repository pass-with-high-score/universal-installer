package app.pwhs.updater.data.repo

import android.content.Context
import app.pwhs.updater.data.local.TrackedAppDao
import app.pwhs.updater.data.local.TrackedAppEntity
import app.pwhs.updater.domain.matcher.InstalledAppMatcher
import app.pwhs.updater.domain.matcher.SmartAbiMatcher
import app.pwhs.updater.domain.matcher.VersionParser
import app.pwhs.updater.domain.model.TrackedApp
import app.pwhs.updater.domain.provider.GitHubReleaseProvider
import app.pwhs.updater.domain.provider.UpdateSourceProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import timber.log.Timber

class AppUpdateRepositoryImpl(
    private val dao: TrackedAppDao,
    private val context: Context,
    private val providers: List<UpdateSourceProvider> = listOf(GitHubReleaseProvider()),
) : AppUpdateRepository {

    override fun getAllTrackedApps(): Flow<List<TrackedApp>> {
        return dao.getAllTrackedApps().map { list ->
            list.map { entity ->
                val domain = entity.toDomain()
                val installedVer = InstalledAppMatcher.getInstalledVersion(context.packageManager, domain.packageName)
                if (installedVer != null && (installedVer.first != domain.currentVersionName || installedVer.second != domain.currentVersionCode)) {
                    domain.copy(
                        currentVersionName = installedVer.first,
                        currentVersionCode = installedVer.second,
                    )
                } else {
                    domain
                }
            }
        }
    }

    override fun getTrackedApp(packageName: String): Flow<TrackedApp?> {
        return dao.getByPackageNameFlow(packageName).map { it?.toDomain() }
    }

    override fun getUpdateCount(): Flow<Int> {
        return getAllTrackedApps().map { apps -> apps.count { it.hasUpdate } }
    }

    override suspend fun saveTrackedApp(app: TrackedApp) = withContext(Dispatchers.IO) {
        val installedVer = InstalledAppMatcher.getInstalledVersion(context.packageManager, app.packageName)
        val normalizedApp = if (app.latestReleaseTag != null) {
            val effectiveVersion = VersionParser.extractVersion(
                tagName = app.latestReleaseTag,
                defaultVersion = app.latestVersionName,
                versionRegex = app.versionRegex,
                matchGroup = app.matchGroup,
                useReleaseTitleAsVersion = app.useReleaseTitleAsVersion,
            )
            app.copy(latestVersionName = effectiveVersion)
        } else {
            app
        }
        val toSave = if (installedVer != null) {
            normalizedApp.copy(
                currentVersionName = installedVer.first,
                currentVersionCode = installedVer.second,
            )
        } else {
            normalizedApp
        }
        dao.insertOrUpdate(TrackedAppEntity.fromDomain(toSave))
    }

    override suspend fun removeTrackedApp(packageName: String) = withContext(Dispatchers.IO) {
        dao.deleteByPackageName(packageName)
    }

    override suspend fun checkForUpdate(
        packageName: String,
        apiToken: String?,
    ): Result<TrackedApp> = withContext(Dispatchers.IO) {
        val entity = dao.getByPackageName(packageName)
            ?: return@withContext Result.failure(NoSuchElementException("App $packageName not tracked"))

        var currentApp = entity.toDomain()
        val installedVer = InstalledAppMatcher.getInstalledVersion(context.packageManager, currentApp.packageName)
        if (installedVer != null) {
            currentApp = currentApp.copy(
                currentVersionName = installedVer.first,
                currentVersionCode = installedVer.second,
            )
        }

        val provider = providers.firstOrNull { it.canHandle(currentApp.sourceUrl) }
            ?: return@withContext Result.failure(
                IllegalArgumentException("No provider available for URL: ${currentApp.sourceUrl}")
            )

        val releaseResult = provider.fetchLatestRelease(
            url = currentApp.sourceUrl,
            includePrereleases = currentApp.includePrereleases,
            eTag = currentApp.eTag,
            apiToken = apiToken,
        )

        val now = System.currentTimeMillis()

        releaseResult.fold(
            onSuccess = { releaseDetails ->
                if (releaseDetails == null) {
                    val updated = currentApp.copy(lastCheckedAt = now)
                    dao.update(TrackedAppEntity.fromDomain(updated))
                    return@withContext Result.success(updated)
                }

                val bestAsset = SmartAbiMatcher.selectBestAsset(
                    assets = releaseDetails.assets,
                    customFilterRegex = currentApp.customRegexFilter,
                )

                val effectiveVersion = VersionParser.extractVersion(
                    tagName = releaseDetails.tagName,
                    releaseTitle = releaseDetails.title,
                    defaultVersion = releaseDetails.versionName,
                    versionRegex = currentApp.versionRegex,
                    matchGroup = currentApp.matchGroup,
                    useReleaseTitleAsVersion = currentApp.useReleaseTitleAsVersion,
                )

                val packageAssets = releaseDetails.assets.filter { SmartAbiMatcher.isPackageAsset(it.name) }.ifEmpty { releaseDetails.assets }

                val updated = currentApp.copy(
                    latestVersionName = effectiveVersion,
                    latestReleaseTag = releaseDetails.tagName,
                    latestDownloadUrl = bestAsset?.downloadUrl,
                    availableAssets = packageAssets,
                    releaseNotes = releaseDetails.releaseNotes,
                    publishedAt = releaseDetails.publishedAt,
                    lastCheckedAt = now,
                    iconUrl = releaseDetails.iconUrl ?: currentApp.iconUrl,
                    eTag = releaseDetails.eTag,
                )

                dao.update(TrackedAppEntity.fromDomain(updated))
                Result.success(updated)
            },
            onFailure = { error ->
                Timber.e(error, "Failed update check for $packageName")
                val updated = currentApp.copy(lastCheckedAt = now)
                dao.update(TrackedAppEntity.fromDomain(updated))
                Result.failure(error)
            },
        )
    }

    override suspend fun checkAllUpdates(apiToken: String?): List<TrackedApp> = withContext(Dispatchers.IO) {
        val apps = dao.getAllTrackedApps().firstOrNull() ?: emptyList()
        apps.map { entity ->
            async {
                checkForUpdate(entity.packageName, apiToken).getOrNull() ?: entity.toDomain()
            }
        }.awaitAll()
    }
}
