package com.nqmgaming.universalinstaller.data.remote

import com.nqmgaming.universalinstaller.domain.model.VtResult
import com.nqmgaming.universalinstaller.domain.model.VtStatus
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import timber.log.Timber
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

class VirusTotalService(
    private val client: HttpClient,
) {
    /**
     * Compute SHA-256 hash of a file.
     */
    suspend fun computeSha256(file: File): String = withContext(Dispatchers.IO) {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { fis ->
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (fis.read(buffer).also { bytesRead = it } != -1) {
                digest.update(buffer, 0, bytesRead)
            }
        }
        digest.digest().joinToString("") { "%02x".format(it) }
    }

    /**
     * Query VirusTotal API for file analysis by SHA-256 hash.
     */
    suspend fun checkFile(apiKey: String, sha256: String): VtResult = withContext(Dispatchers.IO) {
        try {
            val response = client.get("https://www.virustotal.com/api/v3/files/$sha256") {
                headers {
                    append("x-apikey", apiKey)
                    append("Accept", "application/json")
                }
            }

            when (response.status.value) {
                200 -> {
                    val body = response.bodyAsText()
                    parseVtResponse(body)
                }
                404 -> {
                    VtResult(status = VtStatus.NOT_FOUND)
                }
                else -> {
                    VtResult(
                        status = VtStatus.ERROR,
                        errorMessage = "HTTP ${response.status.value}: ${response.status.description}"
                    )
                }
            }
        } catch (e: Exception) {
            Timber.e(e, "VirusTotal API error")
            VtResult(status = VtStatus.ERROR, errorMessage = e.message ?: "Unknown error")
        }
    }

    private fun parseVtResponse(body: String): VtResult {
        return try {
            val json = JSONObject(body)
            val attributes = json.getJSONObject("data").getJSONObject("attributes")
            val stats = attributes.getJSONObject("last_analysis_stats")

            val malicious = stats.optInt("malicious", 0)
            val suspicious = stats.optInt("suspicious", 0)
            val harmless = stats.optInt("harmless", 0)
            val undetected = stats.optInt("undetected", 0)

            val status = when {
                malicious > 0 -> VtStatus.MALICIOUS
                suspicious > 0 -> VtStatus.SUSPICIOUS
                else -> VtStatus.CLEAN
            }

            VtResult(
                malicious = malicious,
                suspicious = suspicious,
                harmless = harmless,
                undetected = undetected,
                status = status,
            )
        } catch (e: Exception) {
            Timber.e(e, "Error parsing VT response")
            VtResult(status = VtStatus.ERROR, errorMessage = "Parse error: ${e.message}")
        }
    }
}
