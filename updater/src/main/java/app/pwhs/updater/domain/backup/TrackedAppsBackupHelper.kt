package app.pwhs.updater.domain.backup

import app.pwhs.updater.domain.model.TrackedApp
import app.pwhs.updater.domain.model.UpdateSourceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import timber.log.Timber

@Serializable
data class TrackedAppBackupDto(
    val packageName: String,
    val appName: String,
    val sourceUrl: String,
    val sourceType: String,
    val includePrereleases: Boolean = false,
    val customRegexFilter: String? = null,
    val versionRegex: String? = null,
    val matchGroup: String? = null,
    val useReleaseTitleAsVersion: Boolean = false,
    val category: String? = null,
)

@Serializable
data class UniversalInstallerBackupContainer(
    val version: Int = 1,
    val exportedAt: Long = System.currentTimeMillis(),
    val apps: List<TrackedAppBackupDto>,
)

object TrackedAppsBackupHelper {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        prettyPrint = true
    }

    fun exportToJson(apps: List<TrackedApp>): String {
        val dtos = apps.map { app ->
            TrackedAppBackupDto(
                packageName = app.packageName,
                appName = app.appName,
                sourceUrl = app.sourceUrl,
                sourceType = app.sourceType.name,
                includePrereleases = app.includePrereleases,
                customRegexFilter = app.customRegexFilter,
                versionRegex = app.versionRegex,
                matchGroup = app.matchGroup,
                useReleaseTitleAsVersion = app.useReleaseTitleAsVersion,
                category = app.category,
            )
        }
        val container = UniversalInstallerBackupContainer(
            apps = dtos,
        )
        return json.encodeToString(UniversalInstallerBackupContainer.serializer(), container)
    }

    fun importFromJson(jsonString: String): List<TrackedApp> {
        val trimmed = jsonString.trim()
        if (trimmed.isEmpty()) return emptyList()

        // 1. Try parsing Universal Installer container
        runCatching {
            val container = json.decodeFromString(UniversalInstallerBackupContainer.serializer(), trimmed)
            if (container.apps.isNotEmpty()) {
                return container.apps.map { dto ->
                    TrackedApp(
                        packageName = dto.packageName,
                        appName = dto.appName,
                        sourceType = runCatching { UpdateSourceType.valueOf(dto.sourceType) }.getOrDefault(UpdateSourceType.fromUrl(dto.sourceUrl)),
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
            }
        }.onFailure { Timber.d(it, "Not a standard Universal Installer backup format") }

        // 2. Try parsing Obtainium export format or raw array
        val results = mutableListOf<TrackedApp>()
        runCatching {
            val element = json.parseToJsonElement(trimmed)
            val appArray = when {
                element is JsonObject && element.containsKey("apps") -> element["apps"]?.jsonArray
                element is JsonObject && element.containsKey("trackedApps") -> element["trackedApps"]?.jsonArray
                element is JsonObject && element.containsKey("app_sources") -> element["app_sources"]?.jsonArray
                element is kotlinx.serialization.json.JsonArray -> element
                else -> null
            }


            appArray?.forEach { item ->
                val obj = item.jsonObject
                val url = obj["url"]?.jsonPrimitive?.content
                    ?: obj["source_url"]?.jsonPrimitive?.content
                    ?: return@forEach

                val name = obj["name"]?.jsonPrimitive?.content
                    ?: obj["author"]?.jsonPrimitive?.content?.let { "$it/${url.substringAfterLast('/')}" }
                    ?: url.substringBefore('?').substringAfterLast('/')

                val id = obj["id"]?.jsonPrimitive?.content
                    ?: obj["package_name"]?.jsonPrimitive?.content
                    ?: "tracked.${name.lowercase().replace(Regex("[^a-z0-9_]"), "_")}"

                val additionalSettings = obj["additionalSettings"]?.jsonObject

                val includePrereleases = obj["include_prereleases"]?.jsonPrimitive?.booleanOrNull
                    ?: obj["includePrereleases"]?.jsonPrimitive?.booleanOrNull
                    ?: additionalSettings?.get("includePrereleases")?.jsonPrimitive?.booleanOrNull
                    ?: false

                val customFilter = obj["filter"]?.jsonPrimitive?.content
                    ?: obj["customRegexFilter"]?.jsonPrimitive?.content
                    ?: additionalSettings?.get("apkFilterRegEx")?.jsonPrimitive?.content
                    ?: additionalSettings?.get("customRegexFilter")?.jsonPrimitive?.content

                val versionRegex = obj["version_extract_regex"]?.jsonPrimitive?.content
                    ?: obj["versionExtractRegex"]?.jsonPrimitive?.content
                    ?: obj["versionRegex"]?.jsonPrimitive?.content
                    ?: additionalSettings?.get("versionExtractionRegEx")?.jsonPrimitive?.content
                    ?: additionalSettings?.get("versionRegex")?.jsonPrimitive?.content

                val matchGroup = obj["match_group"]?.jsonPrimitive?.content
                    ?: obj["matchGroup"]?.jsonPrimitive?.content
                    ?: additionalSettings?.get("matchGroupToUse")?.jsonPrimitive?.content
                    ?: additionalSettings?.get("matchGroup")?.jsonPrimitive?.content

                val useReleaseTitle = obj["use_release_title"]?.jsonPrimitive?.booleanOrNull
                    ?: obj["useReleaseTitleAsVersion"]?.jsonPrimitive?.booleanOrNull
                    ?: additionalSettings?.get("releaseTitleAsVersion")?.jsonPrimitive?.booleanOrNull
                    ?: false

                val category = obj["category"]?.jsonPrimitive?.content
                    ?: obj["categories"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.content

                results.add(
                    TrackedApp(
                        packageName = id,
                        appName = name,
                        sourceType = UpdateSourceType.fromUrl(url),
                        sourceUrl = url,
                        currentVersionName = "Not Installed",
                        currentVersionCode = 0L,
                        includePrereleases = includePrereleases,
                        customRegexFilter = customFilter,
                        versionRegex = versionRegex,
                        matchGroup = matchGroup,
                        useReleaseTitleAsVersion = useReleaseTitle,
                        category = category,
                    )
                )
            }
        }.onFailure { Timber.e(it, "Failed to parse backup JSON") }

        return results
    }
}
