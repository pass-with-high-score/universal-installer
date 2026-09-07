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

                val includePrereleases = obj["include_prereleases"]?.jsonPrimitive?.booleanOrNull
                    ?: obj["includePrereleases"]?.jsonPrimitive?.booleanOrNull
                    ?: false

                val customFilter = obj["filter"]?.jsonPrimitive?.content
                    ?: obj["customRegexFilter"]?.jsonPrimitive?.content

                val versionRegex = obj["version_extract_regex"]?.jsonPrimitive?.content
                    ?: obj["versionExtractRegex"]?.jsonPrimitive?.content
                    ?: obj["versionRegex"]?.jsonPrimitive?.content

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
                        category = category,
                    )
                )
            }
        }.onFailure { Timber.e(it, "Failed to parse backup JSON") }

        return results
    }
}
