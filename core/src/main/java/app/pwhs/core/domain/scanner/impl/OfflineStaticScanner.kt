package app.pwhs.core.domain.scanner.impl

import android.content.Context
import android.net.Uri
import app.pwhs.core.domain.scanner.ApkSecurityScanner
import app.pwhs.core.domain.scanner.EngineDetection
import app.pwhs.core.domain.scanner.ScanApkMetadata
import app.pwhs.core.domain.scanner.ScanOptions
import app.pwhs.core.domain.scanner.ScanStep
import app.pwhs.core.domain.scanner.ScannerCapabilities
import app.pwhs.core.domain.scanner.ScannerFinding
import app.pwhs.core.domain.scanner.ScannerProviderId
import app.pwhs.core.domain.scanner.ScannerSeverity
import app.pwhs.core.domain.scanner.ScannerVerdict
import app.pwhs.core.domain.scanner.SecurityScanResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import timber.log.Timber
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

class OfflineStaticScanner(
    private val customRulesStream: (() -> InputStream)? = null,
) : ApkSecurityScanner {

    override val providerId: ScannerProviderId = ScannerProviderId.OFFLINE_STATIC

    override val capabilities: ScannerCapabilities = ScannerCapabilities(
        supportsHashLookup = false,
        supportsBinaryUpload = false,
        requiresNetwork = false,
        requiresApiKey = false,
        maxFileSizeBytes = Long.MAX_VALUE,
        supportsStreamingProgress = true,
    )

    data class TrackerRule(
        val id: Int,
        val name: String,
        val patterns: List<String>,
        val categories: List<String>,
        val website: String?,
    )

    class TrackerTrie {
        class Node {
            val children = HashMap<Char, Node>()
            val rules = mutableListOf<TrackerRule>()
        }
        val root = Node()

        fun insert(pattern: String, rule: TrackerRule) {
            var curr = root
            for (ch in pattern) {
                curr = curr.children.getOrPut(ch) { Node() }
            }
            curr.rules.add(rule)
        }

        inline fun search(className: String, onMatch: (TrackerRule) -> Unit) {
            var curr = root
            val len = className.length
            for (i in 0 until len) {
                val ch = className[i]
                curr = curr.children[ch] ?: return
                if (curr.rules.isNotEmpty()) {
                    val nextIdx = i + 1
                    if (nextIdx == len || className[nextIdx] == '.' || className[nextIdx] == '$') {
                        for (r in curr.rules) onMatch(r)
                    }
                }
            }
        }
    }

    @Volatile
    private var cachedTrie: TrackerTrie? = null

    override suspend fun isConfigured(context: Context): Boolean = true

    override suspend fun lookupHash(
        context: Context,
        sha256: String,
        metadata: ScanApkMetadata,
    ): Result<SecurityScanResult?> = Result.success(null)

    override fun scanApk(
        context: Context,
        uri: Uri,
        fileName: String,
        metadata: ScanApkMetadata,
        options: ScanOptions,
    ): Flow<ScanStep> = flow {
        emit(ScanStep.Analyzing(10, "Analyzing application permissions..."))
        val findings = mutableListOf<ScannerFinding>()

        // 1. Static Permission Risk Evaluation
        findings.addAll(analyzePermissions(metadata.permissions))

        emit(ScanStep.Analyzing(40, "Scanning DEX bytecode for tracking libraries..."))
        // 2. DEX Tracker Detection
        val trie = getOrLoadTrie(context)
        val detectedTrackers = scanDexForTrackers(context, uri, trie)
        for (rule in detectedTrackers) {
            findings.add(
                ScannerFinding(
                    scanner = "offline_static",
                    ruleId = "tracker_${rule.id}",
                    title = rule.name,
                    description = "Embedded third-party tracker: ${rule.name} (${rule.categories.joinToString()})",
                    severity = if (rule.categories.any { it.contains("Ad", ignoreCase = true) }) ScannerSeverity.INFO else ScannerSeverity.LOW,
                    evidence = rule.website ?: rule.patterns.firstOrNull(),
                )
            )
        }

        emit(ScanStep.Analyzing(90, "Evaluating static security score..."))
        val suspiciousCount = findings.count { it.severity >= ScannerSeverity.MEDIUM }
        val verdict = when {
            findings.any { it.severity == ScannerSeverity.HIGH || it.severity == ScannerSeverity.CRITICAL } -> ScannerVerdict.MALICIOUS
            suspiciousCount > 0 || detectedTrackers.size >= 5 -> ScannerVerdict.RISKWARE
            else -> ScannerVerdict.SAFE
        }

        val threatScore = minOf(100, (detectedTrackers.size * 5) + (suspiciousCount * 25))
        val engineDetections = findings.map { f ->
            EngineDetection(
                engineName = f.title,
                category = if (f.severity >= ScannerSeverity.MEDIUM) "suspicious" else "harmless",
                result = f.description,
            )
        }

        val result = SecurityScanResult(
            providerId = ScannerProviderId.OFFLINE_STATIC,
            sha256 = metadata.sha256,
            verdict = verdict,
            riskScore = threatScore,
            findings = findings,
            engineDetections = engineDetections,
            maliciousCount = 0,
            suspiciousCount = suspiciousCount,
            harmlessCount = (70 - findings.size).coerceAtLeast(0),
            undetectedCount = 0,
            totalEngines = 70,
            scannedAtMillis = System.currentTimeMillis(),
        )

        emit(ScanStep.Completed(result))
    }.flowOn(Dispatchers.IO)

    private fun analyzePermissions(permissions: List<String>): List<ScannerFinding> {
        val findings = mutableListOf<ScannerFinding>()
        val sensitive = listOf(
            "android.permission.BIND_ACCESSIBILITY_SERVICE" to "Accessibility Service access can monitor all screen content and input",
            "android.permission.SYSTEM_ALERT_WINDOW" to "Can draw overlays over other applications",
            "android.permission.REQUEST_INSTALL_PACKAGES" to "Can initiate installation of other packages",
            "android.permission.RECEIVE_BOOT_COMPLETED" to "Automatically boots in background on device startup",
        )

        for ((perm, desc) in sensitive) {
            if (permissions.contains(perm)) {
                findings.add(
                    ScannerFinding(
                        scanner = "offline_static",
                        ruleId = "perm_${perm.substringAfterLast('.')}",
                        title = perm.substringAfterLast('.'),
                        description = desc,
                        severity = if (perm.contains("ACCESSIBILITY")) ScannerSeverity.MEDIUM else ScannerSeverity.LOW,
                        evidence = perm,
                    )
                )
            }
        }
        return findings
    }

    private fun getOrLoadTrie(context: Context): TrackerTrie {
        cachedTrie?.let { return it }
        return synchronized(this) {
            cachedTrie ?: run {
                val rules = try {
                    val stream = customRulesStream?.invoke() ?: context.assets.open("exodus_trackers.json")
                    stream.use { parseRulesJson(it) }
                } catch (e: Exception) {
                    Timber.w(e, "OfflineStaticScanner: could not open exodus_trackers.json, proceeding with empty rules")
                    emptyList()
                }
                val trie = TrackerTrie()
                for (r in rules) {
                    for (p in r.patterns) trie.insert(p, r)
                }
                cachedTrie = trie
                trie
            }
        }
    }

    private fun parseRulesJson(stream: InputStream): List<TrackerRule> {
        val jsonText = stream.bufferedReader().use { it.readText() }
        val array = JSONArray(jsonText)
        val list = ArrayList<TrackerRule>(array.length())
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val id = obj.getInt("id")
            val name = obj.getString("name")
            val sig = obj.optString("signature", "")
            val website = if (obj.has("website") && !obj.isNull("website")) obj.getString("website") else null
            val categories = ArrayList<String>()
            val catArr = obj.optJSONArray("categories")
            if (catArr != null) {
                for (c in 0 until catArr.length()) categories.add(catArr.getString(c))
            }
            if (sig.isNotBlank()) {
                val patterns = sig.split('|').map { it.trim().trimEnd('.') }.filter { it.isNotBlank() }
                if (patterns.isNotEmpty()) {
                    list.add(TrackerRule(id, name, patterns, categories, website))
                }
            }
        }
        return list
    }

    private fun scanDexForTrackers(context: Context, uri: Uri, trie: TrackerTrie): List<TrackerRule> {
        val detected = LinkedHashMap<Int, TrackerRule>()
        try {
            if (uri.scheme == "file" && uri.path != null) {
                val file = File(uri.path!!)
                if (file.exists() && file.length() > 0) {
                    ZipFile(file).use { zip ->
                        val entries = zip.entries()
                        while (entries.hasMoreElements()) {
                            val entry = entries.nextElement()
                            if (entry.name.startsWith("classes") && entry.name.endsWith(".dex")) {
                                zip.getInputStream(entry).use { scanDexBytes(it.readBytes(), trie, detected) }
                            }
                        }
                    }
                    return detected.values.toList()
                }
            }
            context.contentResolver.openInputStream(uri)?.use { stream ->
                BufferedInputStream(stream, 64 * 1024).use { bis ->
                    val zis = ZipInputStream(bis)
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.startsWith("classes") && entry.name.endsWith(".dex")) {
                            scanDexBytes(zis.readBytes(), trie, detected)
                        }
                        zis.closeEntry()
                        entry = zis.nextEntry
                    }
                }
            }
        } catch (t: Throwable) {
            Timber.w(t, "OfflineStaticScanner: scanDexForTrackers failed")
        }
        return detected.values.toList()
    }

    private fun scanDexBytes(dexBytes: ByteArray, trie: TrackerTrie, detected: MutableMap<Int, TrackerRule>) {
        if (dexBytes.size < 112) return
        val buffer = ByteBuffer.wrap(dexBytes).order(ByteOrder.LITTLE_ENDIAN)
        if (buffer.get(0) != 'd'.code.toByte() || buffer.get(1) != 'e'.code.toByte() ||
            buffer.get(2) != 'x'.code.toByte() || buffer.get(3) != '\n'.code.toByte()
        ) return

        val stringIdsSize = buffer.getInt(0x38)
        val stringIdsOff = buffer.getInt(0x3C)
        val typeIdsSize = buffer.getInt(0x40)
        val typeIdsOff = buffer.getInt(0x44)
        val classDefsSize = buffer.getInt(0x60)
        val classDefsOff = buffer.getInt(0x64)

        if (classDefsSize <= 0 || classDefsOff <= 0 || classDefsOff >= dexBytes.size) return
        for (i in 0 until classDefsSize) {
            val classDefOffset = classDefsOff + (i * 32)
            if (classDefOffset + 4 > dexBytes.size) break
            val classIdx = buffer.getInt(classDefOffset)
            if (classIdx in 0 until typeIdsSize) {
                val typeOffset = typeIdsOff + (classIdx * 4)
                if (typeOffset + 4 > dexBytes.size) continue
                val descriptorIdx = buffer.getInt(typeOffset)
                if (descriptorIdx in 0 until stringIdsSize) {
                    val stringIdOffset = stringIdsOff + (descriptorIdx * 4)
                    if (stringIdOffset + 4 > dexBytes.size) continue
                    val stringDataOff = buffer.getInt(stringIdOffset)
                    val className = readDexClassString(dexBytes, stringDataOff) ?: continue
                    trie.search(className) { rule ->
                        detected.putIfAbsent(rule.id, rule)
                    }
                }
            }
        }
    }

    private fun readDexClassString(dexBytes: ByteArray, offset: Int): String? {
        if (offset < 0 || offset >= dexBytes.size) return null
        var cursor = offset
        var value = 0
        var shift = 0
        while (cursor < dexBytes.size) {
            val b = dexBytes[cursor++].toInt()
            value = value or ((b and 0x7F) shl shift)
            if ((b and 0x80) == 0) break
            shift += 7
            if (shift >= 35) return null
        }
        val start = cursor
        while (cursor < dexBytes.size && dexBytes[cursor] != 0.toByte()) cursor++
        val length = cursor - start
        if (length <= 2) return null
        if (dexBytes[start] == 'L'.code.toByte() && dexBytes[cursor - 1] == ';'.code.toByte()) {
            val chars = CharArray(length - 2)
            for (i in 0 until (length - 2)) {
                val b = dexBytes[start + 1 + i].toInt().toChar()
                chars[i] = if (b == '/') '.' else b
            }
            return String(chars)
        }
        return null
    }
}
