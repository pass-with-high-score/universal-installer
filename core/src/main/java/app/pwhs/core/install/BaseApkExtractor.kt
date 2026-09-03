package app.pwhs.core.install

import android.content.Context
import android.net.Uri
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.ZippedApkSplits
import java.io.File

/**
 * PackageManager can only parse a real APK, so anything that reads a package's manifest has to
 * unwrap a split bundle first. Callers that get a temp file back own deleting it.
 */
object BaseApkExtractor {

    /**
     * A path PackageManager can parse: [apkPath] itself for a plain APK, or a freshly extracted
     * copy of the bundle's base APK. Null when the bundle holds no base split or cannot be read.
     */
    fun pathForParsing(context: Context, apkPath: String, prefix: String): Extracted? {
        val file = File(apkPath)
        if (!file.name.isBundleFileName()) return Extracted(apkPath, temp = null)

        return runCatching {
            val apks = ZippedApkSplits.getApksForUri(Uri.fromFile(file), context)
            val baseUri = try {
                apks.firstOrNull { it is Apk.Base }?.uri
            } finally {
                apks.close()
            } ?: return null

            val temp = File(context.cacheDir, "${prefix}_${System.currentTimeMillis()}.apk")
            context.contentResolver.openInputStream(baseUri)?.use { input ->
                temp.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            Extracted(temp.absolutePath, temp)
        }.getOrNull()
    }

    /** [temp] is non-null only when a copy was made and the caller must delete it. */
    data class Extracted(val path: String, val temp: File?)
}
