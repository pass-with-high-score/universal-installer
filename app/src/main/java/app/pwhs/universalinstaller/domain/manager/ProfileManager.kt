package app.pwhs.universalinstaller.domain.manager

import app.pwhs.universalinstaller.domain.model.InstallerProfile
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object ProfileManager {
    private val json = Json { ignoreUnknownKeys = true }

    fun parseProfiles(serialized: String?): List<InstallerProfile> {
        if (serialized.isNullOrBlank()) return emptyList()
        return try {
            json.decodeFromString(serialized)
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun serializeProfiles(profiles: List<InstallerProfile>): String {
        return json.encodeToString(profiles)
    }

    fun parseMapping(serialized: String?): Map<String, String> {
        if (serialized.isNullOrBlank()) return emptyMap()
        return try {
            json.decodeFromString(serialized)
        } catch (e: Exception) {
            emptyMap()
        }
    }

    fun serializeMapping(mapping: Map<String, String>): String {
        return json.encodeToString(mapping)
    }
}
