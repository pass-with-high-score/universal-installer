package app.pwhs.core.domain.scanner

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow

/**
 * Common abstraction for security and malware scanning providers.
 *
 * Implementations decouple specific scanning backends (e.g., VirusDetec, VirusTotal,
 * offline static analyzers) from app consumers.
 */
interface ApkSecurityScanner {
    /**
     * Unique identifier for this scanner provider.
     */
    val providerId: ScannerProviderId

    /**
     * Hardware, network, and quota capabilities of this scanner.
     */
    val capabilities: ScannerCapabilities

    /**
     * Verifies if this scanner is ready for execution (e.g. valid credentials,
     * network connectivity, or configuration present).
     */
    suspend fun isConfigured(context: Context): Boolean

    /**
     * Fast-path lookup by SHA-256 hash without uploading binary data.
     *
     * @param context Application or activity context.
     * @param sha256 Precomputed SHA-256 hash of the target APK.
     * @param metadata Auxiliary APK metadata (package name, cert hash, sdk versions).
     * @return [Result.success] with [SecurityScanResult] if cached result exists;
     *         [Result.success] with `null` if the hash is not present in the database (requires upload);
     *         [Result.failure] with an exception if a network/auth error occurred.
     */
    suspend fun lookupHash(
        context: Context,
        sha256: String,
        metadata: ScanApkMetadata,
    ): Result<SecurityScanResult?>

    /**
     * Initiates the complete security scanning pipeline for an APK file.
     *
     * Produces a reactive [Flow] of [ScanStep] updates progressing through hashing,
     * cache lookup, binary upload (if required), and active engine analysis.
     *
     * @param context Application or activity context.
     * @param uri URI pointing to the APK binary (content:// or file://).
     * @param fileName Human-readable name of the file.
     * @param metadata Auxiliary APK metadata extracted from manifest.
     * @param options Execution parameters and timeout/language configurations.
     */
    fun scanApk(
        context: Context,
        uri: Uri,
        fileName: String,
        metadata: ScanApkMetadata,
        options: ScanOptions = ScanOptions(),
    ): Flow<ScanStep>
}
