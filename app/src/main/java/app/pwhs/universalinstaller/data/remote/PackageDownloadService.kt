package app.pwhs.universalinstaller.data.remote

import io.ktor.client.HttpClient
import kotlinx.coroutines.CancellationException
import java.io.File

/**
 * Streams a package (APK / APKS / XAPK / APKM / ZIP) from a remote URL into a local file.
 *
 * Uses [SegmentedDownloadEngine] (HTTP `Range` + ABDM-style Dynamic Part Splitting across
 * up to 4 parallel connections) when supported by the server, and falls back transparently
 * to a single stream otherwise. Supports Pause/Resume via [downloadWithSegments].
 */
class PackageDownloadService(private val client: HttpClient) {

    private val engine = SegmentedDownloadEngine(client)

    suspend fun download(
        url: String,
        destination: File,
        onProgress: (bytesRead: Long, totalBytes: Long) -> Unit = { _, _ -> },
    ): Result<DownloadedPackage> = downloadWithSegments(
        url = url,
        destination = destination,
        onProgress = { read, total, _, _ -> onProgress(read, total) },
    )

    suspend fun downloadWithSegments(
        url: String,
        destination: File,
        maxConnections: Int = SegmentedDownloadEngine.DEFAULT_MAX_CONNECTIONS,
        initialSegments: List<DownloadSegment> = emptyList(),
        knownTotalBytes: Long = -1L,
        knownFileName: String? = null,
        shouldKeepPartialOnCancel: () -> Boolean = { false },
        onProgress: (
            bytesRead: Long,
            totalBytes: Long,
            segments: List<DownloadSegment>,
            activeThreads: Int,
        ) -> Unit = { _, _, _, _ -> },
    ): Result<DownloadedPackage> = try {
        Result.success(
            engine.download(
                url = url,
                destination = destination,
                maxConnections = maxConnections,
                initialSegments = initialSegments,
                knownTotalBytes = knownTotalBytes,
                knownFileName = knownFileName,
                shouldKeepPartialOnCancel = shouldKeepPartialOnCancel,
                onProgress = onProgress,
            )
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Result.failure(e)
    }

    data class DownloadedPackage(
        val file: File,
        val fileName: String,
        val totalBytes: Long,
    )
}
