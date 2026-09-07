package app.pwhs.updater.domain.provider

import app.pwhs.updater.domain.model.AssetArtifact
import app.pwhs.updater.domain.model.ReleaseDetails
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import timber.log.Timber

/**
 * Update source provider for GitHub Releases using GitHub REST API v3.
 */
class GitHubReleaseProvider(
    private val client: HttpClient = HttpClient(CIO),
) : UpdateSourceProvider {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val packageExtensions = setOf(".apk", ".apks", ".xapk", ".apkm")

    override fun canHandle(url: String): Boolean {
        val lower = url.lowercase().trim()
        return lower.contains("github.com") && extractRepoPath(url) != null
    }

    override suspend fun fetchLatestRelease(
        url: String,
        includePrereleases: Boolean,
        eTag: String?,
        apiToken: String?,
    ): Result<ReleaseDetails?> = withContext(Dispatchers.IO) {
        val repoPath = extractRepoPath(url)
            ?: return@withContext Result.failure(IllegalArgumentException("Invalid GitHub repository URL: $url"))

        try {
            val apiUrl = "https://api.github.com/repos/$repoPath/releases?per_page=20"

            val response = client.get(apiUrl) {
                header("Accept", "application/vnd.github.v3+json")
                header("User-Agent", "UniversalInstaller-AppUpdater")
                if (!eTag.isNullOrBlank()) {
                    header("If-None-Match", eTag)
                }
                if (!apiToken.isNullOrBlank()) {
                    header("Authorization", "Bearer $apiToken")
                }
            }

            if (response.status == HttpStatusCode.NotModified) {
                return@withContext Result.success(null)
            }

            if (response.status != HttpStatusCode.OK) {
                return@withContext Result.failure(
                    RuntimeException("GitHub API returned error: ${response.status.value} ${response.status.description}")
                )
            }

            val responseBody = response.bodyAsText()
            val newETag = response.headers["ETag"]

            val array = json.parseToJsonElement(responseBody).jsonArray
            if (array.isEmpty()) return@withContext Result.failure(NoSuchElementException("No releases found"))

            val candidates = array.mapNotNull { it as? JsonObject }.filter { obj ->
                val isDraft = obj["draft"]?.jsonPrimitive?.booleanOrNull ?: false
                !isDraft
            }

            val targetRelease = if (includePrereleases) {
                candidates.firstOrNull { hasPackageAsset(it) }
                    ?: candidates.firstOrNull()
                    ?: array.first().jsonObject
            } else {
                candidates.firstOrNull { obj ->
                    val isPrerelease = obj["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
                    !isPrerelease && hasPackageAsset(obj)
                } ?: candidates.firstOrNull { obj ->
                    val isPrerelease = obj["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
                    !isPrerelease
                } ?: array.first().jsonObject
            }

            val details = parseReleaseObject(targetRelease, newETag)
            Result.success(details)
        } catch (e: Exception) {
            Timber.e(e, "Failed to fetch release from GitHub for $url")
            Result.failure(e)
        }
    }

    private fun hasPackageAsset(obj: JsonObject): Boolean {
        val assets = obj["assets"]?.jsonArray ?: return false
        return assets.any { item ->
            val name = (item as? JsonObject)?.get("name")?.jsonPrimitive?.content ?: ""
            packageExtensions.any { ext -> name.endsWith(ext, ignoreCase = true) }
        }
    }

    private fun parseReleaseObject(obj: JsonObject, eTag: String?): ReleaseDetails {
        val tagName = obj["tag_name"]?.jsonPrimitive?.content ?: ""
        val title = obj["name"]?.jsonPrimitive?.content
        val body = obj["body"]?.jsonPrimitive?.content
        val isPrerelease = obj["prerelease"]?.jsonPrimitive?.booleanOrNull ?: false
        val publishedAtStr = obj["published_at"]?.jsonPrimitive?.content

        val assets = obj["assets"]?.jsonArray?.mapNotNull { item ->
            val assetObj = item.jsonObject
            val name = assetObj["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val downloadUrl = assetObj["browser_download_url"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val size = assetObj["size"]?.jsonPrimitive?.longOrNull ?: 0L
            val contentType = assetObj["content_type"]?.jsonPrimitive?.content
            AssetArtifact(
                name = name,
                downloadUrl = downloadUrl,
                sizeBytes = size,
                contentType = contentType,
            )
        } ?: emptyList()

        val avatarUrl = obj["author"]?.jsonObject?.get("avatar_url")?.jsonPrimitive?.content
        val cleanVersion = extractCleanVersion(tagName)

        return ReleaseDetails(
            tagName = tagName,
            versionName = cleanVersion,
            title = title,
            releaseNotes = body,
            publishedAt = parseIsoDate(publishedAtStr),
            isPrerelease = isPrerelease,
            assets = assets,
            iconUrl = avatarUrl,
            eTag = eTag,
        )
    }

    companion object {
        fun extractRepoPath(url: String): String? {
            val clean = url.trim()
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .removePrefix("github.com/")

            val parts = clean.split("/").filter { it.isNotBlank() }
            if (parts.size >= 2) {
                val owner = parts[0]
                val repo = parts[1].removeSuffix(".git")
                return "$owner/$repo"
            }
            return null
        }

        fun extractCleanVersion(tag: String): String {
            return tag.trim()
                .removePrefix("v")
                .removePrefix("V")
                .removePrefix("release-")
                .removePrefix("release_")
                .removePrefix("app-")
                .removePrefix("v.")
        }

        private fun parseIsoDate(iso: String?): Long? {
            if (iso.isNullOrBlank()) return null
            return runCatching {
                java.time.Instant.parse(iso).toEpochMilli()
            }.getOrNull()
        }
    }
}
