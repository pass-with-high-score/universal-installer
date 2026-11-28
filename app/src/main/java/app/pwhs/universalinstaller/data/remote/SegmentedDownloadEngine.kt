package app.pwhs.universalinstaller.data.remote

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.plugins.timeout
import io.ktor.client.request.header
import io.ktor.client.request.prepareGet
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentLength
import io.ktor.http.isSuccess
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.coroutineContext

data class DownloadSegment(
    val startByte: Long,
    val currentByte: Long,
    val endByte: Long,
)

/**
 * Multi-connection HTTP download engine inspired by AB Download Manager (ABDM):
 * - Probes server for `Range` support (`GET Range: bytes=0-0` to survive HEAD-blocking CDNs).
 * - Pre-allocates the target file and launches up to [maxConnections] parallel workers writing
 *   directly to disjoint offsets via [RandomAccessFile] (zero merge step, zero write-lock contention).
 * - Implements **Dynamic Part Splitting**: whenever a worker finishes its chunk early, it splits
 *   the largest remaining active chunk in half and immediately helps download the second half.
 * - Supports Pause/Resume by preserving the partial file and resuming from [initialSegments].
 */
internal class SegmentedDownloadEngine(
    private val client: HttpClient,
) {
    private class MutablePart(
        val start: Long,
        @Volatile var current: Long,
        @Volatile var end: Long,
        var assigned: Boolean = false,
    ) {
        val remaining: Long get() = (end - current + 1L).coerceAtLeast(0L)
        val isDone: Boolean get() = current > end
        fun snapshot(): DownloadSegment = DownloadSegment(start, current.coerceAtMost(end + 1L), end)
    }

    private data class ProbeResult(
        val supportsRanges: Boolean,
        val totalBytes: Long,
        val fileName: String?,
    )

    suspend fun download(
        url: String,
        destination: File,
        maxConnections: Int = DEFAULT_MAX_CONNECTIONS,
        initialSegments: List<DownloadSegment> = emptyList(),
        knownTotalBytes: Long = -1L,
        knownFileName: String? = null,
        shouldKeepPartialOnCancel: () -> Boolean = { false },
        onProgress: (bytesRead: Long, totalBytes: Long, segments: List<DownloadSegment>, activeThreads: Int) -> Unit,
    ): PackageDownloadService.DownloadedPackage = withContext(Dispatchers.IO) {
        destination.parentFile?.mkdirs()

        try {
            // Fast resume path when we already know totalBytes and have unfinished segments on disk.
            if (initialSegments.isNotEmpty() && knownTotalBytes > 0L && destination.exists()) {
                val fileName = knownFileName?.takeIf { it.isNotBlank() }
                    ?: extractFallbackFileName(url, destination)
                return@withContext runSegmentedDownload(
                    url = url,
                    destination = destination,
                    totalBytes = knownTotalBytes,
                    fileName = fileName,
                    maxConnections = maxConnections.coerceIn(1, MAX_ALLOWED_CONNECTIONS),
                    initialSegments = initialSegments,
                    onProgress = onProgress,
                )
            }

            val probe = probeServer(url)
            val fileName = probe.fileName?.takeIf { it.isNotBlank() }
                ?: knownFileName?.takeIf { it.isNotBlank() }
                ?: extractFallbackFileName(url, destination)

            val canSplit = probe.supportsRanges &&
                probe.totalBytes >= MIN_SPLIT_FILE_SIZE_BYTES &&
                maxConnections > 1

            if (canSplit) {
                runSegmentedDownload(
                    url = url,
                    destination = destination,
                    totalBytes = probe.totalBytes,
                    fileName = fileName,
                    maxConnections = maxConnections.coerceIn(2, MAX_ALLOWED_CONNECTIONS),
                    initialSegments = emptyList(),
                    onProgress = onProgress,
                )
            } else {
                runSingleStreamDownload(
                    url = url,
                    destination = destination,
                    fallbackFileName = fileName,
                    onProgress = onProgress,
                )
            }
        } catch (e: CancellationException) {
            if (!shouldKeepPartialOnCancel()) {
                runCatching { destination.delete() }
            }
            throw e
        } catch (e: Throwable) {
            if (!shouldKeepPartialOnCancel()) {
                runCatching { destination.delete() }
            }
            throw e
        }
    }

    private suspend fun probeServer(url: String): ProbeResult {
        return runCatching {
            client.prepareGet(url) {
                header(HttpHeaders.Range, "bytes=0-0")
                timeout { requestTimeoutMillis = PROBE_TIMEOUT_MS }
            }.execute { response ->
                if (!response.status.isSuccess()) {
                    return@execute ProbeResult(supportsRanges = false, totalBytes = -1L, fileName = null)
                }
                val fileName = response.headers[HttpHeaders.ContentDisposition]
                    ?.let { parseFileNameFromContentDisposition(it) }
                if (response.status == HttpStatusCode.PartialContent) {
                    val contentRange = response.headers[HttpHeaders.ContentRange].orEmpty()
                    val total = parseTotalFromContentRange(contentRange) ?: -1L
                    ProbeResult(supportsRanges = total > 0L, totalBytes = total, fileName = fileName)
                } else {
                    val acceptRanges = response.headers[HttpHeaders.AcceptRanges]
                        ?.contains("bytes", ignoreCase = true) == true
                    val total = response.contentLength() ?: -1L
                    ProbeResult(supportsRanges = acceptRanges && total > 0L, totalBytes = total, fileName = fileName)
                }
            }
        }.getOrDefault(ProbeResult(supportsRanges = false, totalBytes = -1L, fileName = null))
    }

    private suspend fun runSegmentedDownload(
        url: String,
        destination: File,
        totalBytes: Long,
        fileName: String,
        maxConnections: Int,
        initialSegments: List<DownloadSegment>,
        onProgress: (bytesRead: Long, totalBytes: Long, segments: List<DownloadSegment>, activeThreads: Int) -> Unit,
    ): PackageDownloadService.DownloadedPackage {
        val partsMutex = Mutex()
        val parts = mutableListOf<MutablePart>()

        if (initialSegments.isNotEmpty()) {
            initialSegments.forEach { seg ->
                parts.add(MutablePart(seg.startByte, seg.currentByte, seg.endByte))
            }
        } else {
            RandomAccessFile(destination, "rw").use { it.setLength(totalBytes) }
            val initialCount = minOf(
                maxConnections,
                (totalBytes / MIN_PART_SIZE_BYTES).toInt().coerceAtLeast(1),
            )
            val partSize = totalBytes / initialCount
            for (i in 0 until initialCount) {
                val start = i * partSize
                val end = if (i == initialCount - 1) totalBytes - 1L else (start + partSize - 1L)
                parts.add(MutablePart(start, start, end))
            }
        }

        val initialDownloaded = parts.sumOf { (it.current - it.start).coerceAtLeast(0L) }
        val totalRead = AtomicLong(initialDownloaded)
        val activeWorkers = AtomicInteger(0)
        val lastReportAt = AtomicLong(0L)

        suspend fun emitProgress(force: Boolean = false) {
            val now = System.currentTimeMillis()
            val prev = lastReportAt.get()
            if (!force && now - prev < PROGRESS_THROTTLE_MS) return
            if (!force && !lastReportAt.compareAndSet(prev, now)) return
            if (force) lastReportAt.set(now)
            val snapshot = partsMutex.withLock { parts.map { it.snapshot() } }
            onProgress(
                totalRead.get().coerceAtMost(totalBytes),
                totalBytes,
                snapshot,
                activeWorkers.get().coerceAtLeast(1),
            )
        }

        suspend fun acquireNextPart(): MutablePart? = partsMutex.withLock {
            // 1. Pick any unassigned unfinished part first.
            parts.firstOrNull { !it.assigned && !it.isDone }?.let {
                it.assigned = true
                return@withLock it
            }
            // 2. Dynamic Part Splitting (ABDM): split the largest active remaining part in half.
            val largest = parts
                .filter { it.assigned && it.remaining >= MIN_DYNAMIC_SPLIT_BYTES }
                .maxByOrNull { it.remaining }
                ?: return@withLock null
            val mid = largest.current + (largest.remaining / 2L)
            if (mid <= largest.current || mid >= largest.end) return@withLock null
            val oldEnd = largest.end
            largest.end = mid
            val splitPart = MutablePart(
                start = mid + 1L,
                current = mid + 1L,
                end = oldEnd,
                assigned = true,
            )
            parts.add(splitPart)
            splitPart
        }

        emitProgress(force = true)

        coroutineScope {
            val workers = List(maxConnections) {
                async(Dispatchers.IO) {
                    while (true) {
                        coroutineContext.ensureActive()
                        val part = acquireNextPart() ?: break
                        activeWorkers.incrementAndGet()
                        try {
                            downloadPart(
                                url = url,
                                destination = destination,
                                part = part,
                                totalRead = totalRead,
                                onChunkWritten = { emitProgress(force = false) },
                            )
                        } finally {
                            activeWorkers.decrementAndGet()
                        }
                    }
                }
            }
            workers.awaitAll()
        }

        emitProgress(force = true)
        return PackageDownloadService.DownloadedPackage(
            file = destination,
            fileName = fileName,
            totalBytes = totalRead.get(),
        )
    }

    private suspend fun downloadPart(
        url: String,
        destination: File,
        part: MutablePart,
        totalRead: AtomicLong,
        onChunkWritten: suspend () -> Unit,
    ) {
        var attempt = 0
        var lastError: Throwable? = null
        while (attempt < MAX_PART_RETRIES && !part.isDone) {
            coroutineContext.ensureActive()
            try {
                val rangeStart = part.current
                val rangeEnd = part.end
                if (rangeStart > rangeEnd) return

                client.prepareGet(url) {
                    header(HttpHeaders.Range, "bytes=$rangeStart-$rangeEnd")
                    timeout { requestTimeoutMillis = DOWNLOAD_TIMEOUT_MS }
                }.execute { response ->
                    if (!response.status.isSuccess()) {
                        throw IOException("HTTP ${response.status.value} ${response.status.description}")
                    }
                    val input: InputStream = response.body()
                    RandomAccessFile(destination, "rw").use { raf ->
                        raf.seek(rangeStart)
                        val buffer = ByteArray(BUFFER_SIZE)
                        while (true) {
                            coroutineContext.ensureActive()
                            // Re-read part.end dynamically in case another worker split our range!
                            val remaining = part.end - part.current + 1L
                            if (remaining <= 0L) break
                            val toRead = minOf(buffer.size.toLong(), remaining).toInt()
                            val n = input.read(buffer, 0, toRead)
                            if (n < 0) break
                            if (n > 0) {
                                raf.write(buffer, 0, n)
                                part.current += n
                                totalRead.addAndGet(n.toLong())
                                onChunkWritten()
                            }
                        }
                    }
                }
                return
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                lastError = e
                attempt++
            }
        }
        if (!part.isDone && lastError != null) {
            throw lastError
        }
    }

    private suspend fun runSingleStreamDownload(
        url: String,
        destination: File,
        fallbackFileName: String,
        onProgress: (bytesRead: Long, totalBytes: Long, segments: List<DownloadSegment>, activeThreads: Int) -> Unit,
    ): PackageDownloadService.DownloadedPackage {
        return client.prepareGet(url) {
            timeout { requestTimeoutMillis = DOWNLOAD_TIMEOUT_MS }
        }.execute { response ->
            if (!response.status.isSuccess()) {
                throw IOException("HTTP ${response.status.value} ${response.status.description}")
            }
            val total = response.contentLength() ?: -1L
            val fileName = response.headers[HttpHeaders.ContentDisposition]
                ?.let { parseFileNameFromContentDisposition(it) }
                ?: fallbackFileName
            val input: InputStream = response.body()
            var read = 0L
            var lastEmit = 0L
            destination.outputStream().use { out ->
                val buffer = ByteArray(BUFFER_SIZE)
                var n = input.read(buffer)
                while (n >= 0) {
                    coroutineContext.ensureActive()
                    out.write(buffer, 0, n)
                    read += n
                    val now = System.currentTimeMillis()
                    if (now - lastEmit >= PROGRESS_THROTTLE_MS) {
                        lastEmit = now
                        onProgress(read, total, emptyList(), 1)
                    }
                    n = input.read(buffer)
                }
            }
            onProgress(read, total, emptyList(), 1)
            PackageDownloadService.DownloadedPackage(file = destination, fileName = fileName, totalBytes = read)
        }
    }

    companion object {
        const val DEFAULT_MAX_CONNECTIONS = 4
        private const val MAX_ALLOWED_CONNECTIONS = 8
        private const val MIN_SPLIT_FILE_SIZE_BYTES = 2L * 1024 * 1024 // 2 MB
        private const val MIN_PART_SIZE_BYTES = 1L * 1024 * 1024       // 1 MB
        private const val MIN_DYNAMIC_SPLIT_BYTES = 1L * 1024 * 1024   // 1 MB
        private const val BUFFER_SIZE = 64 * 1024
        private const val PROGRESS_THROTTLE_MS = 100L
        private const val PROBE_TIMEOUT_MS = 15_000L
        private const val DOWNLOAD_TIMEOUT_MS = 30 * 60 * 1000L
        private const val MAX_PART_RETRIES = 2

        internal fun parseTotalFromContentRange(header: String): Long? {
            // Format: "bytes 0-0/123456"
            val slashIdx = header.lastIndexOf('/')
            if (slashIdx < 0 || slashIdx == header.lastIndex) return null
            val totalPart = header.substring(slashIdx + 1).trim()
            return totalPart.toLongOrNull()?.takeIf { it > 0L }
        }

        internal fun parseFileNameFromContentDisposition(header: String): String? {
            val starRegex = Regex("""filename\*\s*=\s*(?:[^']*'[^']*')?([^;\n]+)""", RegexOption.IGNORE_CASE)
            starRegex.find(header)?.groupValues?.get(1)?.let { raw ->
                val trimmed = raw.trim().trim('"')
                runCatching { return java.net.URLDecoder.decode(trimmed, Charsets.UTF_8.name()) }
            }
            val regex = Regex("""filename\s*=\s*"?([^";\n]+)"?""", RegexOption.IGNORE_CASE)
            return regex.find(header)?.groupValues?.get(1)?.trim()
        }

        private fun extractFallbackFileName(url: String, destination: File): String =
            url.substringAfterLast('/').substringBefore('?').ifBlank { destination.name }
    }
}
