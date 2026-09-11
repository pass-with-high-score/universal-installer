package app.pwhs.updater.domain.backup

import app.pwhs.updater.domain.model.TrackedApp
import app.pwhs.updater.domain.model.UpdateSourceType
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
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
    val installedVersionRegex: String? = null,
    val installedVersionMatchGroup: String? = null,
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
                installedVersionRegex = app.installedVersionRegex,
                installedVersionMatchGroup = app.installedVersionMatchGroup,
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
                        installedVersionRegex = dto.installedVersionRegex,
                        installedVersionMatchGroup = dto.installedVersionMatchGroup,
                    )
                }
            }
        }.onFailure { Timber.d(it, "Not a standard Universal Installer backup format") }

        // 2. Try parsing Obtainium export format or raw array/map
        val results = mutableListOf<TrackedApp>()
        runCatching {
            val element = json.parseToJsonElement(trimmed)
            val items: Collection<kotlinx.serialization.json.JsonElement>? = when {
                element is kotlinx.serialization.json.JsonArray -> element
                element is JsonObject && element.containsKey("apps") -> {
                    when (val apps = element["apps"]) {
                        is kotlinx.serialization.json.JsonArray -> apps
                        is JsonObject -> apps.values
                        else -> null
                    }
                }
                element is JsonObject && element.containsKey("trackedApps") -> {
                    when (val apps = element["trackedApps"]) {
                        is kotlinx.serialization.json.JsonArray -> apps
                        is JsonObject -> apps.values
                        else -> null
                    }
                }
                element is JsonObject && element.containsKey("tracked_apps") -> {
                    when (val apps = element["tracked_apps"]) {
                        is kotlinx.serialization.json.JsonArray -> apps
                        is JsonObject -> apps.values
                        else -> null
                    }
                }
                element is JsonObject && element.containsKey("app_sources") -> {
                    when (val apps = element["app_sources"]) {
                        is kotlinx.serialization.json.JsonArray -> apps
                        is JsonObject -> apps.values
                        else -> null
                    }
                }
                element is JsonObject && element.values.any { it is JsonObject && (it.containsKey("url") || it.containsKey("source_url") || it.containsKey("id")) } -> {
                    element.values
                }
                else -> null
            }

            items?.forEach { item ->
                runCatching {
                    val obj = item as? JsonObject ?: return@forEach
                    val url = obj["url"]?.jsonPrimitive?.contentOrNull
                        ?: obj["source_url"]?.jsonPrimitive?.contentOrNull
                        ?: obj["sourceUrl"]?.jsonPrimitive?.contentOrNull
                        ?: return@forEach

                    if (url.isBlank()) return@forEach

                    val name = obj["name"]?.jsonPrimitive?.contentOrNull
                        ?: obj["appName"]?.jsonPrimitive?.contentOrNull
                        ?: obj["app_name"]?.jsonPrimitive?.contentOrNull
                        ?: obj["author"]?.jsonPrimitive?.contentOrNull?.let { "$it/${url.substringAfterLast('/')}" }
                        ?: url.substringBefore('?').substringAfterLast('/')

                    val id = obj["id"]?.jsonPrimitive?.contentOrNull
                        ?: obj["package_name"]?.jsonPrimitive?.contentOrNull
                        ?: obj["packageName"]?.jsonPrimitive?.contentOrNull
                        ?: "tracked.${name.lowercase().replace(Regex("[^a-z0-9_]"), "_")}"

                    val additionalSettings: JsonObject? = when (val elem = obj["additionalSettings"]) {
                        is JsonObject -> elem
                        is kotlinx.serialization.json.JsonPrimitive -> {
                            val content = elem.contentOrNull
                            if (!content.isNullOrBlank()) {
                                runCatching { json.parseToJsonElement(content) as? JsonObject }
                                    .recoverCatching {
                                        val sanitized = content.replace(Regex("""\\([^"\\/bfnrtu])"""), """\\\\$1""")
                                        json.parseToJsonElement(sanitized) as? JsonObject
                                    }
                                    .getOrNull()
                            } else null
                        }
                        else -> null
                    }

                    val includePrereleases = obj["include_prereleases"]?.jsonPrimitive?.booleanOrNull
                        ?: obj["includePrereleases"]?.jsonPrimitive?.booleanOrNull
                        ?: additionalSettings?.get("includePrereleases")?.jsonPrimitive?.booleanOrNull
                        ?: false

                    val customFilter = obj["filter"]?.jsonPrimitive?.contentOrNull
                        ?: obj["customRegexFilter"]?.jsonPrimitive?.contentOrNull
                        ?: obj["custom_regex_filter"]?.jsonPrimitive?.contentOrNull
                        ?: additionalSettings?.get("apkFilterRegEx")?.jsonPrimitive?.contentOrNull
                        ?: additionalSettings?.get("customRegexFilter")?.jsonPrimitive?.contentOrNull

                    val versionRegex = obj["version_extract_regex"]?.jsonPrimitive?.contentOrNull
                        ?: obj["versionExtractRegex"]?.jsonPrimitive?.contentOrNull
                        ?: obj["versionRegex"]?.jsonPrimitive?.contentOrNull
                        ?: additionalSettings?.get("versionExtractionRegEx")?.jsonPrimitive?.contentOrNull
                        ?: additionalSettings?.get("versionRegex")?.jsonPrimitive?.contentOrNull

                    val matchGroup = obj["match_group"]?.jsonPrimitive?.contentOrNull
                        ?: obj["matchGroup"]?.jsonPrimitive?.contentOrNull
                        ?: additionalSettings?.get("matchGroupToUse")?.jsonPrimitive?.contentOrNull
                        ?: additionalSettings?.get("matchGroup")?.jsonPrimitive?.contentOrNull

                    val useReleaseTitle = obj["use_release_title"]?.jsonPrimitive?.booleanOrNull
                        ?: obj["useReleaseTitleAsVersion"]?.jsonPrimitive?.booleanOrNull
                        ?: additionalSettings?.get("releaseTitleAsVersion")?.jsonPrimitive?.booleanOrNull
                        ?: false

                    val category = obj["category"]?.jsonPrimitive?.contentOrNull
                        ?: runCatching { obj["categories"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.contentOrNull }.getOrNull()

                    val installedVersionRegex = obj["installed_version_regex"]?.jsonPrimitive?.contentOrNull
                        ?: obj["installedVersionRegex"]?.jsonPrimitive?.contentOrNull
                        ?: obj["installed_version_extract_regex"]?.jsonPrimitive?.contentOrNull
                        ?: obj["installedVersionExtractionRegEx"]?.jsonPrimitive?.contentOrNull
                        ?: additionalSettings?.get("installedVersionExtractionRegEx")?.jsonPrimitive?.contentOrNull
                        ?: additionalSettings?.get("installedVersionRegex")?.jsonPrimitive?.contentOrNull

                    val installedVersionMatchGroup = obj["installed_match_group"]?.jsonPrimitive?.contentOrNull
                        ?: obj["installedMatchGroup"]?.jsonPrimitive?.contentOrNull
                        ?: additionalSettings?.get("installedMatchGroupToUse")?.jsonPrimitive?.contentOrNull
                        ?: additionalSettings?.get("installedMatchGroup")?.jsonPrimitive?.contentOrNull

                    results.add(
                        TrackedApp(
                            packageName = id.trim(),
                            appName = name.trim(),
                            sourceType = UpdateSourceType.fromUrl(url.trim()),
                            sourceUrl = url.trim(),
                            currentVersionName = "Not Installed",
                            currentVersionCode = 0L,
                            includePrereleases = includePrereleases,
                            customRegexFilter = customFilter,
                            versionRegex = versionRegex,
                            matchGroup = matchGroup,
                            useReleaseTitleAsVersion = useReleaseTitle,
                            category = category,
                            installedVersionRegex = installedVersionRegex,
                            installedVersionMatchGroup = installedVersionMatchGroup,
                        )
                    )
                }.onFailure { Timber.w(it, "Skipping malformed app in backup JSON") }
            }
        }.onFailure { Timber.e(it, "Failed to parse backup JSON") }

        return results
    }
}
