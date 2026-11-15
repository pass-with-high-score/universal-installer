package app.pwhs.core.install

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class ZipPackageInspectorTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private fun createZip(fileName: String, entries: Map<String, ByteArray>): File {
        val file = tempFolder.newFile(fileName)
        ZipOutputStream(FileOutputStream(file)).use { zos ->
            for ((name, content) in entries) {
                zos.putNextEntry(ZipEntry(name))
                zos.write(content)
                zos.closeEntry()
            }
        }
        return file
    }

    @Test
    fun `inspect returns NotAPackage when zip has no android files`() {
        val zip = createZip("test.zip", mapOf(
            "document.txt" to "hello world".toByteArray(),
            "subfolder/photo.png" to byteArrayOf(1, 2, 3)
        ))

        val result = ZipPackageInspector.inspect(zip)
        assertTrue(result.type is ZipPackageType.NotAPackage)
        assertFalse(result.isInstallable)
        assertFalse(result.hasRootManifest)
        assertEquals(0, result.apkEntries.size)
    }

    @Test
    fun `inspect returns SingleApk when zip has root AndroidManifest`() {
        val zip = createZip("app.zip", mapOf(
            "AndroidManifest.xml" to byteArrayOf(0x03, 0x00, 0x08, 0x00),
            "classes.dex" to byteArrayOf(0x64, 0x65, 0x78),
            "resources.arsc" to byteArrayOf(1, 2, 3)
        ))

        val result = ZipPackageInspector.inspect(zip)
        assertTrue(result.type is ZipPackageType.SingleApk)
        assertTrue(result.isInstallable)
        assertTrue(result.hasRootManifest)
        assertEquals(0, result.apkEntries.size)
    }

    @Test
    fun `inspect returns SplitBundle when zip contains nested apks`() {
        val zip = createZip("splits.zip", mapOf(
            "base.apk" to byteArrayOf(0x50, 0x4B, 0x03, 0x04),
            "split_config.arm64_v8a.apk" to byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        ))

        val result = ZipPackageInspector.inspect(zip)
        assertTrue(result.type is ZipPackageType.SplitBundle)
        assertTrue(result.isInstallable)
        assertFalse(result.hasRootManifest)
        assertEquals(2, result.apkEntries.size)
    }

    @Test
    fun `inspect returns SplitBundle with package metadata when manifest json is present`() {
        val manifestJson = """
            {
                "xapk_version": 2,
                "package_name": "com.example.testapp",
                "name": "Test App",
                "version_code": "100",
                "version_name": "1.0.0",
                "split_apks": [
                    {"file": "base.apk", "id": "base"}
                ]
            }
        """.trimIndent()

        val zip = createZip("app.xapk.zip", mapOf(
            "manifest.json" to manifestJson.toByteArray(Charsets.UTF_8),
            "base.apk" to byteArrayOf(0x50, 0x4B, 0x03, 0x04)
        ))

        val result = ZipPackageInspector.inspect(zip)
        assertTrue(result.type is ZipPackageType.SplitBundle)
        assertTrue(result.isInstallable)
        assertEquals("com.example.testapp", result.manifestPackageName)
        assertEquals("1.0.0", result.manifestVersionName)
        assertEquals(100L, result.manifestVersionCode)
    }

    @Test
    fun `inspect handles empty or corrupt zip gracefully`() {
        val emptyZip = tempFolder.newFile("empty.zip")
        val result = ZipPackageInspector.inspect(emptyZip)
        assertTrue(result.type is ZipPackageType.NotAPackage)
        assertFalse(result.isInstallable)
    }
}
