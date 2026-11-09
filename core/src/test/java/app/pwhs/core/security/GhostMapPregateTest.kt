package app.pwhs.core.security

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.CRC32
import java.util.zip.Deflater

class GhostMapPregateTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun testValidStoredZip_passes() {
        val content = "Valid Stored Content".toByteArray(Charsets.UTF_8)
        val file = createZipFile(
            entryName = "resources.arsc",
            content = content,
            method = GhostMapPregate.METHOD_STORED
        )

        val result = GhostMapPregate.validate(file)
        assertEquals(GhostMapPregate.ValidationResult.Valid, result)

        // validateOrThrow should not throw
        GhostMapPregate.validateOrThrow(file)
    }

    @Test
    fun testValidDeflatedZip_passes() {
        val content = "A long repeated string to be deflated properly. ".repeat(20).toByteArray(Charsets.UTF_8)
        val file = createZipFile(
            entryName = "AndroidManifest.xml",
            content = content,
            method = GhostMapPregate.METHOD_DEFLATED
        )

        val result = GhostMapPregate.validate(file)
        assertEquals(GhostMapPregate.ValidationResult.Valid, result)

        GhostMapPregate.validateOrThrow(file)
    }

    @Test
    fun testGhostMap_unsupportedCompressionMethod_detectedAndBlocked() {
        // Attack vector: method = 2, 3, or 99 (not STORED and not DEFLATED)
        val content = "Malicious Payload".toByteArray(Charsets.UTF_8)
        val file = createZipFile(
            entryName = "resources.arsc",
            content = content,
            method = 3 // Unsupported method (e.g. Reduce)
        )

        val result = GhostMapPregate.validate(file)
        assertTrue(result is GhostMapPregate.ValidationResult.Invalid)
        val invalid = result as GhostMapPregate.ValidationResult.Invalid
        assertTrue(invalid.reason.contains("Unsupported compression method 3"))
        assertEquals("resources.arsc", invalid.entryName)

        val exception = assertThrows(GhostMapPregate.GhostMapSecurityException::class.java) {
            GhostMapPregate.validateOrThrow(file)
        }
        assertTrue(exception.message!!.contains("Unsupported compression method 3"))
    }

    @Test
    fun testGhostMap_storedWithMismatchedSizes_detectedAndBlocked() {
        // Attack vector: method = STORED (0), but declared uncompressedSize is 16MB while actual/compressed is small
        val content = "Small content".toByteArray(Charsets.UTF_8)
        val file = createZipFile(
            entryName = "resources.arsc",
            content = content,
            method = GhostMapPregate.METHOD_STORED,
            overrideUncompressedSize = 16 * 1024 * 1024L // 16 MB fake size
        )

        val result = GhostMapPregate.validate(file)
        assertTrue(result is GhostMapPregate.ValidationResult.Invalid)
        val invalid = result as GhostMapPregate.ValidationResult.Invalid
        assertTrue(invalid.reason.contains("compressedSize") && invalid.reason.contains("uncompressedSize"))
    }

    @Test
    fun testGhostMap_dataOutOfBounds_detectedAndBlocked() {
        // Attack vector: Entry data claims to be huge, exceeding physical file length
        val content = "Normal".toByteArray(Charsets.UTF_8)
        val file = createZipFile(
            entryName = "classes.dex",
            content = content,
            method = GhostMapPregate.METHOD_DEFLATED,
            overrideCompressedSize = 1_000_000L // Exceeds actual file length
        )

        val result = GhostMapPregate.validate(file)
        assertTrue(result is GhostMapPregate.ValidationResult.Invalid)
        val invalid = result as GhostMapPregate.ValidationResult.Invalid
        assertTrue(invalid.reason.contains("extends beyond physical file size") || invalid.reason.contains("bounds"))
    }

    @Test
    fun testGhostMap_lfhMethodMismatch_detectedAndBlocked() {
        // Attack vector: CD says method 0, but LFH says method 8
        val content = "Content".toByteArray(Charsets.UTF_8)
        val file = createZipFile(
            entryName = "res/raw/icon.png",
            content = content,
            method = GhostMapPregate.METHOD_STORED,
            overrideLfhMethod = GhostMapPregate.METHOD_DEFLATED
        )

        val result = GhostMapPregate.validate(file)
        assertTrue(result is GhostMapPregate.ValidationResult.Invalid)
        val invalid = result as GhostMapPregate.ValidationResult.Invalid
        assertTrue(invalid.reason.contains("Method mismatch between CD"))
    }

    @Test
    fun testNonZipFile_gracefulRejection() {
        val file = tempFolder.newFile("corrupted.apk")
        file.writeBytes("This is not a zip file at all".toByteArray(Charsets.UTF_8))

        val result = GhostMapPregate.validate(file)
        assertTrue(result is GhostMapPregate.ValidationResult.Invalid)
        val invalid = result as GhostMapPregate.ValidationResult.Invalid
        assertTrue(invalid.reason.contains("EOCD record not found"))
    }

    // --- Helper to synthesize custom ZIP binaries with exact byte offsets ---
    private fun createZipFile(
        entryName: String,
        content: ByteArray,
        method: Int,
        overrideCompressedSize: Long? = null,
        overrideUncompressedSize: Long? = null,
        overrideLfhMethod: Int? = null
    ): File {
        val nameBytes = entryName.toByteArray(Charsets.UTF_8)

        val payload: ByteArray
        val crcValue: Long
        val actualCompSize: Long
        val actualUncompSize: Long

        if (method == GhostMapPregate.METHOD_DEFLATED) {
            val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
            deflater.setInput(content)
            deflater.finish()
            val deflatedOut = ByteArrayOutputStream()
            val buf = ByteArray(1024)
            while (!deflater.finished()) {
                val count = deflater.deflate(buf)
                deflatedOut.write(buf, 0, count)
            }
            deflater.end()
            payload = deflatedOut.toByteArray()

            val crc = CRC32()
            crc.update(content)
            crcValue = crc.value
            actualCompSize = payload.size.toLong()
            actualUncompSize = content.size.toLong()
        } else {
            payload = content
            val crc = CRC32()
            crc.update(content)
            crcValue = crc.value
            actualCompSize = payload.size.toLong()
            actualUncompSize = payload.size.toLong()
        }

        val compSizeToWrite = overrideCompressedSize ?: actualCompSize
        val uncompSizeToWrite = overrideUncompressedSize ?: actualUncompSize
        val lfhMethodToWrite = overrideLfhMethod ?: method

        val out = ByteArrayOutputStream()

        // 1. Local File Header (LFH) - 30 bytes + name
        val lfh = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
        lfh.putInt(0x04034b50) // signature
        lfh.putShort(20)       // version needed
        lfh.putShort(0)        // flags
        lfh.putShort(lfhMethodToWrite.toShort()) // method
        lfh.putShort(0)        // last mod time
        lfh.putShort(0)        // last mod date
        lfh.putInt(crcValue.toInt())
        lfh.putInt(compSizeToWrite.toInt())
        lfh.putInt(uncompSizeToWrite.toInt())
        lfh.putShort(nameBytes.size.toShort())
        lfh.putShort(0)        // extra length
        out.write(lfh.array())
        out.write(nameBytes)

        // 2. Payload
        val lfhOffset = 0L
        out.write(payload)

        val cdOffset = out.size().toLong()

        // 3. Central Directory Header (CD) - 46 bytes + name
        val cd = ByteBuffer.allocate(46).order(ByteOrder.LITTLE_ENDIAN)
        cd.putInt(0x02014b50) // signature
        cd.putShort(20)       // version made by
        cd.putShort(20)       // version needed
        cd.putShort(0)        // flags
        cd.putShort(method.toShort()) // method in CD
        cd.putShort(0)        // last mod time
        cd.putShort(0)        // last mod date
        cd.putInt(crcValue.toInt())
        cd.putInt(compSizeToWrite.toInt())
        cd.putInt(uncompSizeToWrite.toInt())
        cd.putShort(nameBytes.size.toShort())
        cd.putShort(0)        // extra len
        cd.putShort(0)        // comment len
        cd.putShort(0)        // disk number start
        cd.putShort(0)        // internal attrs
        cd.putInt(0)          // external attrs
        cd.putInt(lfhOffset.toInt())
        out.write(cd.array())
        out.write(nameBytes)

        val cdSize = (out.size() - cdOffset).toInt()

        // 4. End of Central Directory (EOCD) - 22 bytes
        val eocd = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
        eocd.putInt(0x06054b50) // signature
        eocd.putShort(0)        // disk num
        eocd.putShort(0)        // cd start disk
        eocd.putShort(1)        // entries on this disk
        eocd.putShort(1)        // total entries
        eocd.putInt(cdSize)     // cd size
        eocd.putInt(cdOffset.toInt()) // cd offset
        eocd.putShort(0)        // comment len
        out.write(eocd.array())

        val tempFile = tempFolder.newFile("test_${System.nanoTime()}.apk")
        tempFile.writeBytes(out.toByteArray())
        return tempFile
    }
}
