package app.pwhs.universalinstaller.presentation.install.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AbiOverrideUtilTest {

    @Test
    fun `sanitize accepts valid Android ABIs and trims whitespace`() {
        assertEquals("armeabi-v7a", AbiOverrideUtil.sanitize("armeabi-v7a"))
        assertEquals("arm64-v8a", AbiOverrideUtil.sanitize("  arm64-v8a  "))
        assertEquals("x86_64", AbiOverrideUtil.sanitize("x86_64"))
        assertEquals("x86", AbiOverrideUtil.sanitize("x86"))
        assertEquals("armeabi", AbiOverrideUtil.sanitize("armeabi"))
    }

    @Test
    fun `sanitize rejects blank unknown and shell injection strings`() {
        assertNull(AbiOverrideUtil.sanitize(null))
        assertNull(AbiOverrideUtil.sanitize(""))
        assertNull(AbiOverrideUtil.sanitize("   "))
        assertNull(AbiOverrideUtil.sanitize("mips64"))
        assertNull(AbiOverrideUtil.sanitize("armeabi-v7a; rm -rf /"))
        assertNull(AbiOverrideUtil.sanitize("arm64-v8a && id"))
    }

    @Test
    fun `selectableAbis puts device ABIs first and deduplicates APK ABIs`() {
        val deviceAbis = listOf("arm64-v8a", "armeabi-v7a")
        val apkAbis = listOf("armeabi-v7a", "x86_64", "invalid-abi")
        assertEquals(
            listOf("arm64-v8a", "armeabi-v7a", "x86_64"),
            AbiOverrideUtil.selectableAbis(apkAbis, deviceAbis),
        )
    }

    @Test
    fun `isSupportedByDevice and isDeviceSingleAbi detect 64-bit-only devices`() {
        val singleAbiDevice = listOf("arm64-v8a")
        assertTrue(AbiOverrideUtil.isDeviceSingleAbi(singleAbiDevice))
        assertTrue(AbiOverrideUtil.isSupportedByDevice("arm64-v8a", singleAbiDevice))
        assertFalse(AbiOverrideUtil.isSupportedByDevice("armeabi-v7a", singleAbiDevice))

        val dualAbiDevice = listOf("arm64-v8a", "armeabi-v7a")
        assertFalse(AbiOverrideUtil.isDeviceSingleAbi(dualAbiDevice))
        assertTrue(AbiOverrideUtil.isSupportedByDevice("armeabi-v7a", dualAbiDevice))
    }
}
