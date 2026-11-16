package app.pwhs.core.security

import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * Pregate Security Validator to protect Android devices against the GhostMap vulnerability.
 *
 * GhostMap exploits a discrepancy in AOSP between package parsing and runtime memory mapping (mmap).
 * A crafted ZIP entry with an unsupported compression method (e.g. not Deflate) and an exaggerated
 * uncompressed length tricks the runtime into mapping non-existent file bytes, triggering a SIGBUS
 * in system_server upon reading and locking the device into an endless bootloop.
 *
 * This validator enforces strict PKWARE and Android APK specifications:
 * 1. Central Directory & Local File Header compression methods must strictly be 0 (STORED) or 8 (DEFLATED).
 * 2. For STORED entries, compressedSize must equal uncompressedSize.
 * 3. Declared data boundaries must not exceed the physical file size (no phantom EOF mapping).
 * 4. Compression methods and sizes between Central Directory and Local File Headers must match.
 */
object GhostMapPregate {

    private const val EOCD_SIGNATURE = 0x06054b50
    private const val CD_HEADER_SIGNATURE = 0x02014b50
    private const val LFH_SIGNATURE = 0x04034b50

    const val METHOD_STORED = 0
    const val METHOD_DEFLATED = 8

    sealed interface ValidationResult {
        data object Valid : ValidationResult
        data class Invalid(val reason: String, val entryName: String? = null) : ValidationResult
    }

    class GhostMapSecurityException(
        val reason: String,
        val entryName: String? = null
    ) : SecurityException(if (entryName != null) "$reason [entry: $entryName]" else reason)

    /**
     * Validates an APK file against GhostMap and ZIP structural anomalies.
     * Throws [GhostMapSecurityException] if the file is invalid or dangerous.
     */
    @Throws(GhostMapSecurityException::class)
    fun validateOrThrow(apkFile: File) {
        when (val result = validate(apkFile)) {
            is ValidationResult.Valid -> return
            is ValidationResult.Invalid -> throw GhostMapSecurityException(result.reason, result.entryName)
        }
    }

    /**
     * Inspects the APK file and returns [ValidationResult.Valid] or [ValidationResult.Invalid].
     */
    fun validate(apkFile: File): ValidationResult {
        if (!apkFile.exists() || !apkFile.canRead()) {
            return ValidationResult.Invalid("File does not exist or cannot be read")
        }
        val fileSize = apkFile.length()
        if (fileSize < 22) {
            return ValidationResult.Invalid("File size is smaller than the minimum ZIP header size ($fileSize bytes)")
        }

        return try {
            RandomAccessFile(apkFile, "r").use { raf ->
                validateChannel(raf.channel, fileSize)
            }
        } catch (e: Exception) {
            ValidationResult.Invalid("Failed to inspect ZIP structure: ${e.message ?: e::class.java.simpleName}")
        }
    }

    /**
     * Inspects an APK from a Content [Uri] or File [Uri].
     */
    fun validateUri(context: android.content.Context, uri: android.net.Uri): ValidationResult {
        if (uri.scheme == "file" && !uri.path.isNullOrBlank()) {
            return validate(File(uri.path!!))
        }
        return try {
            val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                ?: return ValidationResult.Invalid("Cannot open file descriptor for $uri")
            pfd.use {
                val size = if (pfd.statSize > 0) pfd.statSize else null
                java.io.FileInputStream(pfd.fileDescriptor).channel.use { channel ->
                    val actualSize = size ?: channel.size()
                    validateChannel(channel, actualSize)
                }
            }
        } catch (e: Exception) {
            ValidationResult.Invalid("Failed to inspect URI: ${e.message ?: e::class.java.simpleName}")
        }
    }

    /**
     * Inspects a [FileChannel] representing an APK archive.
     */
    fun validateChannel(channel: FileChannel, fileSize: Long): ValidationResult {
        // 1. Locate End of Central Directory (EOCD)
        val eocdOffset = findEocdOffset(channel, fileSize)
            ?: return ValidationResult.Invalid("EOCD record not found (not a valid ZIP archive)")

        val eocdBuffer = ByteBuffer.allocate(22).order(ByteOrder.LITTLE_ENDIAN)
        channel.position(eocdOffset)
        channel.read(eocdBuffer)
        eocdBuffer.flip()

        if (eocdBuffer.remaining() < 22) {
            return ValidationResult.Invalid("Corrupted EOCD buffer")
        }

        val totalEntries = eocdBuffer.getShort(10).toInt() and 0xFFFF
        val cdSize = eocdBuffer.getInt(12).toLong() and 0xFFFFFFFFL
        val cdOffset = eocdBuffer.getInt(16).toLong() and 0xFFFFFFFFL

        if (cdOffset + cdSize > fileSize) {
            return ValidationResult.Invalid("Central Directory bounds ($cdOffset + $cdSize) exceed file size ($fileSize)")
        }

        // 2. Parse Central Directory headers
        val cdBytes = ByteBuffer.allocate(cdSize.toInt()).order(ByteOrder.LITTLE_ENDIAN)
        channel.position(cdOffset)
        channel.read(cdBytes)
        cdBytes.flip()

        for (i in 0 until totalEntries) {
            if (cdBytes.remaining() < 46) {
                return ValidationResult.Invalid("Truncated Central Directory header at index $i")
            }

            val sig = cdBytes.int
            if (sig != CD_HEADER_SIGNATURE) {
                return ValidationResult.Invalid("Invalid Central Directory signature at entry $i: 0x${Integer.toHexString(sig)}")
            }

            cdBytes.short // version made by
            cdBytes.short // version needed
            cdBytes.short // general purpose bit flag
            val method = cdBytes.short.toInt() and 0xFFFF
            cdBytes.short // last mod time
            cdBytes.short // last mod date
            cdBytes.int   // crc-32
            val compressedSize = cdBytes.int.toLong() and 0xFFFFFFFFL
            val uncompressedSize = cdBytes.int.toLong() and 0xFFFFFFFFL
            val fileNameLen = cdBytes.short.toInt() and 0xFFFF
            val extraLen = cdBytes.short.toInt() and 0xFFFF
            val commentLen = cdBytes.short.toInt() and 0xFFFF
            cdBytes.short // disk number start
            cdBytes.short // internal file attributes
            cdBytes.int   // external file attributes
            val lfhOffset = cdBytes.int.toLong() and 0xFFFFFFFFL

            if (cdBytes.remaining() < fileNameLen) {
                return ValidationResult.Invalid("Truncated file name in Central Directory entry $i")
            }

            val fileNameBytes = ByteArray(fileNameLen)
            cdBytes.get(fileNameBytes)
            val fileName = String(fileNameBytes, Charsets.UTF_8)

            // Skip extra and comment
            if (cdBytes.remaining() < extraLen + commentLen) {
                return ValidationResult.Invalid("Truncated extra or comment field", fileName)
            }
            cdBytes.position(cdBytes.position() + extraLen + commentLen)

            // RULE 1: Compression method MUST strictly be STORED (0) or DEFLATED (8)
            if (method != METHOD_STORED && method != METHOD_DEFLATED) {
                return ValidationResult.Invalid(
                    "GhostMap threat: Unsupported compression method $method (expected STORED(0) or DEFLATED(8))",
                    fileName
                )
            }

            // RULE 2: STORED entries must have compressedSize == uncompressedSize
            if (method == METHOD_STORED && compressedSize != uncompressedSize) {
                return ValidationResult.Invalid(
                    "GhostMap threat: STORED entry has compressedSize ($compressedSize) != uncompressedSize ($uncompressedSize)",
                    fileName
                )
            }

            // RULE 3: LFH header + payload must not exceed physical file size
            if (lfhOffset + 30 > fileSize) {
                return ValidationResult.Invalid("Local File Header offset exceeds file size", fileName)
            }

            val lfhResult = validateLocalFileHeader(channel, lfhOffset, method, compressedSize, uncompressedSize, fileName, fileSize)
            if (lfhResult is ValidationResult.Invalid) {
                return lfhResult
            }
        }

        return ValidationResult.Valid
    }

    private fun validateLocalFileHeader(
        channel: FileChannel,
        lfhOffset: Long,
        cdMethod: Int,
        cdCompressedSize: Long,
        cdUncompressedSize: Long,
        entryName: String,
        fileSize: Long
    ): ValidationResult {
        val lfhBuffer = ByteBuffer.allocate(30).order(ByteOrder.LITTLE_ENDIAN)
        channel.position(lfhOffset)
        channel.read(lfhBuffer)
        lfhBuffer.flip()

        if (lfhBuffer.remaining() < 30) {
            return ValidationResult.Invalid("Truncated Local File Header", entryName)
        }

        val sig = lfhBuffer.int
        if (sig != LFH_SIGNATURE) {
            return ValidationResult.Invalid("Invalid Local File Header signature", entryName)
        }

        lfhBuffer.short // version needed
        val lfhFlags = lfhBuffer.short.toInt() and 0xFFFF
        val lfhMethod = lfhBuffer.short.toInt() and 0xFFFF
        lfhBuffer.short // last mod time
        lfhBuffer.short // last mod date
        lfhBuffer.int   // crc-32
        val lfhCompressedSize = lfhBuffer.int.toLong() and 0xFFFFFFFFL
        val lfhUncompressedSize = lfhBuffer.int.toLong() and 0xFFFFFFFFL
        val lfhNameLen = lfhBuffer.short.toInt() and 0xFFFF
        val lfhExtraLen = lfhBuffer.short.toInt() and 0xFFFF

        // RULE 4: Method in LFH must match Central Directory
        if (lfhMethod != cdMethod) {
            return ValidationResult.Invalid(
                "GhostMap threat: Method mismatch between CD ($cdMethod) and LFH ($lfhMethod)",
                entryName
            )
        }

        // Data descriptor check (bit 3)
        val hasDataDescriptor = (lfhFlags and 0x08) != 0
        if (!hasDataDescriptor) {
            if (lfhCompressedSize != cdCompressedSize || lfhUncompressedSize != cdUncompressedSize) {
                return ValidationResult.Invalid(
                    "GhostMap threat: Size mismatch between CD (c:$cdCompressedSize, u:$cdUncompressedSize) and LFH (c:$lfhCompressedSize, u:$lfhUncompressedSize)",
                    entryName
                )
            }
        }

        // RULE 5: Total entry footprint must not exceed physical file size
        val dataOffset = lfhOffset + 30 + lfhNameLen + lfhExtraLen
        if (dataOffset + cdCompressedSize > fileSize) {
            return ValidationResult.Invalid(
                "GhostMap threat: Entry data extends beyond physical file size (dataEnd: ${dataOffset + cdCompressedSize}, fileSize: $fileSize)",
                entryName
            )
        }

        return ValidationResult.Valid
    }

    private fun findEocdOffset(channel: FileChannel, fileSize: Long): Long? {
        val maxCommentSize = 65535
        val minEocdSize = 22
        val maxSearch = minOf(fileSize, (maxCommentSize + minEocdSize).toLong())
        val bufferSize = maxSearch.toInt()
        val buffer = ByteBuffer.allocate(bufferSize).order(ByteOrder.LITTLE_ENDIAN)

        val searchStart = fileSize - bufferSize
        channel.position(searchStart)
        channel.read(buffer)
        buffer.flip()

        for (i in bufferSize - minEocdSize downTo 0) {
            if (buffer.getInt(i) == EOCD_SIGNATURE) {
                return searchStart + i
            }
        }
        return null
    }
}
