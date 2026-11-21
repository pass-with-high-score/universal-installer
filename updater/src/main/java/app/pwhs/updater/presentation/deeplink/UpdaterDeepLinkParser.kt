package app.pwhs.updater.presentation.deeplink

import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import timber.log.Timber
import java.net.URLDecoder

sealed interface UpdaterDeepLinkAction {
    data object OpenList : UpdaterDeepLinkAction
    data object CheckAllUpdates : UpdaterDeepLinkAction
    data class OpenAppDetail(val packageName: String) : UpdaterDeepLinkAction
    data class PreFillAddApp(
        val url: String,
        val targetPackageName: String? = null,
        val category: String? = null,
        val includePrereleases: Boolean = false,
    ) : UpdaterDeepLinkAction
}

/**
 * Parses incoming Intents for [app.pwhs.updater.presentation.UpdatesActivity]:
 * - `universalinstaller://updates` (/add, /app, /check)
 * - `obtainium://add/<url>` & `obtainium://app/<json>`
 * - `https://apps.obtainium.imranr.dev/redirect?r=...`
 * - Direct release/package links from GitHub, GitLab, Codeberg, F-Droid, IzzyOnDroid
 * - `ACTION_SEND` (`text/plain`) shared URLs from browsers or repo apps
 */
object UpdaterDeepLinkParser {

    private val URL_REGEX = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)

    fun parse(intent: Intent?): UpdaterDeepLinkAction? {
        if (intent == null) return null

        if (intent.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)?.trim().orEmpty()
            val extractedUrl = URL_REGEX.find(sharedText)?.value ?: return null
            return parseWebUrl(Uri.parse(extractedUrl))
                ?: UpdaterDeepLinkAction.PreFillAddApp(url = extractedUrl)
        }

        val uri = intent.data ?: return null
        return parseUri(uri)
    }

    fun parseUri(uri: Uri): UpdaterDeepLinkAction? {
        return when (uri.scheme?.lowercase()) {
            "universalinstaller" -> parseInternalUri(uri)
            "obtainium" -> parseObtainiumUri(uri)
            "http", "https" -> parseWebUrl(uri)
            else -> null
        }
    }

    private fun parseInternalUri(uri: Uri): UpdaterDeepLinkAction? {
        if (uri.host?.lowercase() != "updates") return null
        val segments = uri.pathSegments
        val firstSegment = segments.firstOrNull()?.lowercase()

        return when (firstSegment) {
            null, "" -> UpdaterDeepLinkAction.OpenList
            "check" -> UpdaterDeepLinkAction.CheckAllUpdates
            "app" -> {
                val pkg = uri.getQueryParameter("pkg")
                    ?: segments.getOrNull(1)
                if (!pkg.isNullOrBlank()) {
                    UpdaterDeepLinkAction.OpenAppDetail(pkg.trim())
                } else {
                    UpdaterDeepLinkAction.OpenList
                }
            }
            "add" -> {
                val rawUrl = uri.getQueryParameter("url")?.trim()
                if (!rawUrl.isNullOrBlank()) {
                    UpdaterDeepLinkAction.PreFillAddApp(
                        url = normalizeRepoUrl(rawUrl),
                        targetPackageName = uri.getQueryParameter("pkg")?.trim()?.takeIf { it.isNotBlank() },
                        category = uri.getQueryParameter("category")?.trim()?.takeIf { it.isNotBlank() },
                        includePrereleases = uri.getQueryParameter("prerelease")?.equals("true", ignoreCase = true) == true,
                    )
                } else {
                    UpdaterDeepLinkAction.PreFillAddApp(url = "")
                }
            }
            else -> UpdaterDeepLinkAction.OpenList
        }
    }

    private fun parseObtainiumUri(uri: Uri): UpdaterDeepLinkAction? {
        val host = uri.host?.lowercase() ?: return null
        val rawPath = uri.encodedPath?.removePrefix("/")?.trim().orEmpty()
        if (rawPath.isBlank()) return null

        return when (host) {
            "add" -> {
                val decoded = runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)
                if (decoded.startsWith("http://", ignoreCase = true) || decoded.startsWith("https://", ignoreCase = true)) {
                    UpdaterDeepLinkAction.PreFillAddApp(url = normalizeRepoUrl(decoded))
                } else {
                    null
                }
            }
            "app" -> {
                val decoded = runCatching { URLDecoder.decode(rawPath, "UTF-8") }.getOrDefault(rawPath)
                runCatching {
                    val json = JSONObject(decoded)
                    val url = json.optString("url").trim()
                    if (url.isBlank()) return null
                    val pkg = json.optString("id").trim().takeIf { it.isNotBlank() }
                    val categories = json.optJSONArray("categories")
                    val category = if (categories != null && categories.length() > 0) {
                        categories.optString(0).trim().takeIf { it.isNotBlank() }
                    } else null
                    val additional = json.optString("additionalSettings").takeIf { it.isNotBlank() }
                        ?.let { runCatching { JSONObject(it) }.getOrNull() }
                    val prereleases = additional?.optBoolean("includePrereleases", false) == true
                    UpdaterDeepLinkAction.PreFillAddApp(
                        url = normalizeRepoUrl(url),
                        targetPackageName = pkg,
                        category = category,
                        includePrereleases = prereleases,
                    )
                }.onFailure { Timber.w(it, "Failed to parse obtainium://app JSON payload") }.getOrNull()
            }
            else -> null
        }
    }

    private fun parseWebUrl(uri: Uri): UpdaterDeepLinkAction? {
        val host = uri.host?.lowercase() ?: return null

        // Obtainium web redirect: https://apps.obtainium.imranr.dev/redirect?r=obtainium://...
        if (host == "apps.obtainium.imranr.dev") {
            val redirectTarget = uri.getQueryParameter("r")?.trim() ?: return null
            return parseUri(Uri.parse(redirectTarget))
        }

        val fullUrl = uri.toString()

        // F-Droid: https://f-droid.org/packages/<pkg> or https://f-droid.org/<lang>/packages/<pkg>
        if (host == "f-droid.org" || host == "www.f-droid.org") {
            val segments = uri.pathSegments
            val pkgIndex = segments.indexOf("packages")
            val pkg = if (pkgIndex >= 0) segments.getOrNull(pkgIndex + 1) else null
            return UpdaterDeepLinkAction.PreFillAddApp(
                url = fullUrl,
                targetPackageName = pkg?.takeIf { it.contains('.') },
            )
        }

        // IzzyOnDroid: https://apt.izzysoft.de/fdroid/index/apk/<pkg>
        if (host == "apt.izzysoft.de") {
            val segments = uri.pathSegments
            val apkIndex = segments.indexOf("apk")
            val pkg = if (apkIndex >= 0) segments.getOrNull(apkIndex + 1) else null
            return UpdaterDeepLinkAction.PreFillAddApp(
                url = fullUrl,
                targetPackageName = pkg?.takeIf { it.contains('.') },
            )
        }

        // GitHub / GitLab / Codeberg
        if (host in listOf("github.com", "www.github.com", "gitlab.com", "www.gitlab.com", "codeberg.org", "www.codeberg.org")) {
            return UpdaterDeepLinkAction.PreFillAddApp(url = normalizeRepoUrl(fullUrl))
        }

        return null
    }

    /**
     * Strips trailing `/releases...` or `/-/releases...` suffixes from GitHub / GitLab / Codeberg URLs
     * so the AddAppScreen receives the clean repository URL.
     */
    internal fun normalizeRepoUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim().removeSuffix("/")
        val uri = runCatching { Uri.parse(trimmed) }.getOrNull() ?: return trimmed
        val host = uri.host?.lowercase() ?: return trimmed
        val segments = uri.pathSegments

        return when (host) {
            "github.com", "www.github.com", "codeberg.org", "www.codeberg.org" -> {
                if (segments.size >= 2) {
                    "https://${host.removePrefix("www.")}/${segments[0]}/${segments[1]}"
                } else trimmed
            }
            "gitlab.com", "www.gitlab.com" -> {
                val dashIdx = segments.indexOf("-")
                if (dashIdx >= 2) {
                    val repoPath = segments.take(dashIdx).joinToString("/")
                    "https://gitlab.com/$repoPath"
                } else trimmed
            }
            else -> trimmed
        }
    }
}
