package app.pwhs.core.domain.scanner

import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.net.createTestUri
import app.pwhs.core.domain.scanner.impl.OfflineStaticScanner
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder

class OfflineStaticScannerTest {

    private val context = FakeContext()
    private val fakeUri = createTestUri("content://app.pwhs/test.apk")

    @Test
    fun scanApk_detectsDangerousPermissions() = runBlocking {
        val scanner = OfflineStaticScanner(customRulesStream = { ByteArrayInputStream("[]".toByteArray()) })

        val metadata = ScanApkMetadata(
            sha256 = "test_perm_hash",
            permissions = listOf(
                "android.permission.BIND_ACCESSIBILITY_SERVICE",
                "android.permission.SYSTEM_ALERT_WINDOW",
            ),
        )

        val steps = scanner.scanApk(context, fakeUri, "test.apk", metadata).toList()

        assertTrue(steps.last() is ScanStep.Completed)
        val completed = steps.last() as ScanStep.Completed
        assertEquals(ScannerVerdict.RISKWARE, completed.result.verdict)
        assertEquals(2, completed.result.findings.size)
        assertTrue(completed.result.findings.any { it.ruleId == "perm_BIND_ACCESSIBILITY_SERVICE" && it.severity == ScannerSeverity.MEDIUM })
        assertTrue(completed.result.findings.any { it.ruleId == "perm_SYSTEM_ALERT_WINDOW" && it.severity == ScannerSeverity.LOW })
    }

    @Test
    fun trackerTrie_matchesPrefixAndSubpackages() {
        val trie = OfflineStaticScanner.TrackerTrie()
        val rule = OfflineStaticScanner.TrackerRule(
            id = 42,
            name = "Google Analytics",
            patterns = listOf("com.google.android.gms.analytics"),
            categories = listOf("Analytics"),
            website = "https://analytics.google.com",
        )
        trie.insert("com.google.android.gms.analytics", rule)

        val matches = mutableListOf<OfflineStaticScanner.TrackerRule>()
        trie.search("com.google.android.gms.analytics.Tracker") { matches.add(it) }
        assertEquals(1, matches.size)
        assertEquals("Google Analytics", matches[0].name)

        matches.clear()
        trie.search("com.google.android.gms.analytics\$Inner") { matches.add(it) }
        assertEquals(1, matches.size)

        matches.clear()
        trie.search("com.google.android.gms.other.Class") { matches.add(it) }
        assertTrue(matches.isEmpty())
    }

    @Test
    fun scanApk_cleanApk_returnsSafeVerdict() = runBlocking {
        val scanner = OfflineStaticScanner(customRulesStream = { ByteArrayInputStream("[]".toByteArray()) })
        val metadata = ScanApkMetadata(
            sha256 = "clean_hash",
            permissions = listOf("android.permission.INTERNET"),
        )

        val steps = scanner.scanApk(context, fakeUri, "clean.apk", metadata).toList()

        assertTrue(steps.last() is ScanStep.Completed)
        val completed = steps.last() as ScanStep.Completed
        assertEquals(ScannerVerdict.SAFE, completed.result.verdict)
        assertEquals(0, completed.result.riskScore)
        assertTrue(completed.result.findings.isEmpty())
    }

    @Test
    fun scanApk_missingRules_completesGracefully() = runBlocking {
        // Simulates throwing or missing rules
        val scanner = OfflineStaticScanner(customRulesStream = { throw java.io.FileNotFoundException("assets not found") })
        val metadata = ScanApkMetadata(sha256 = "hash_123")

        val steps = scanner.scanApk(context, fakeUri, "test.apk", metadata).toList()

        assertTrue(steps.last() is ScanStep.Completed)
        val completed = steps.last() as ScanStep.Completed
        assertEquals(ScannerVerdict.SAFE, completed.result.verdict)
    }
}
