package app.pwhs.universalinstaller.util

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import timber.log.Timber

/**
 * Answers whether an APK can update the copy already installed, or whether Android will reject it
 * because the two were signed with different keys.
 *
 * Worth doing before the install rather than after: a signature mismatch is the one install failure
 * no option can bypass, and the platform only reports it as a generic "conflict" once the session
 * has already failed.
 */
object SignatureCheck {

    /** Flag to add to `getPackageArchiveInfo` so [isMismatch] has certificates to compare. */
    val archiveFlag: Int
        @Suppress("DEPRECATION")
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            PackageManager.GET_SIGNATURES
        }

    /**
     * - `false` — the APK can update [packageName] (or nothing is installed under that name).
     * - `true` — signed with a different key; the install will fail until the old app is removed.
     * - `null` — **couldn't tell.** Callers must treat this as "no warning", never as a mismatch.
     *   Certificate extraction fails on real devices (see the `getPackageArchiveInfo` fallback in
     *   InstallViewModel), and a false alarm on every install is far worse than a missed one.
     */
    fun isMismatch(context: Context, packageName: String, archive: PackageInfo?): Boolean? {
        if (packageName.isBlank() || archive == null) return null
        val pm = context.packageManager
        // Nothing installed under this name — no update, so nothing to mismatch with.
        if (!isInstalled(pm, packageName)) return false

        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                val signers = archive.signingInfo?.apkContentsSigners
                if (signers.isNullOrEmpty()) return null
                // Ask the platform rather than comparing certificate bytes ourselves: a developer
                // who rotated their signing key has a new cert that the installed package still
                // accepts, and a raw set comparison would flag every one of those as a mismatch.
                val accepted = signers.any { signature ->
                    pm.hasSigningCertificate(
                        packageName,
                        signature.toByteArray(),
                        PackageManager.CERT_INPUT_RAW_X509,
                    )
                }
                !accepted
            } else {
                @Suppress("DEPRECATION")
                val archiveSigs = archive.signatures?.map { it.toCharsString() }?.toSet()
                @Suppress("DEPRECATION")
                val installedSigs = pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
                    .signatures?.map { it.toCharsString() }?.toSet()
                if (archiveSigs.isNullOrEmpty() || installedSigs.isNullOrEmpty()) return null
                // No rotation support before API 28 anyway, so an exact comparison is the answer.
                archiveSigs != installedSigs
            }
        } catch (e: Exception) {
            Timber.w(e, "Could not compare signatures for $packageName")
            null
        }
    }

    /** Whether anything is installed under [packageName] right now. */
    fun isInstalled(context: Context, packageName: String): Boolean =
        packageName.isNotBlank() && isInstalled(context.packageManager, packageName)

    /** Computes the lowercase hex SHA-256 hash of the first signing certificate in [archive]. */
    fun extractCertSha256(archive: PackageInfo?): String {
        return try {
            val signers = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                archive?.signingInfo?.apkContentsSigners
            } else {
                @Suppress("DEPRECATION")
                archive?.signatures
            }
            val rawCert = signers?.firstOrNull()?.toByteArray() ?: return ""
            val digest = java.security.MessageDigest.getInstance("SHA-256")
            digest.digest(rawCert).joinToString("") { "%02x".format(it) }
        } catch (e: Exception) {
            Timber.w(e, "Could not extract cert SHA-256")
            ""
        }
    }

    private fun isInstalled(pm: PackageManager, packageName: String): Boolean = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
        } else {
            @Suppress("DEPRECATION") pm.getPackageInfo(packageName, 0)
        }
        true
    } catch (_: PackageManager.NameNotFoundException) {
        false
    }
}
