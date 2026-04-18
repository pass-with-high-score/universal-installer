package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import kotlin.coroutines.coroutineContext

/**
 * OBB copy via a user-granted SAF tree URI pointing at `Android/obb/<pkg>/`.
 * Used when Shizuku isn't available — the user has to grant access once per package.
 */
object SafObbWriter {

    /**
     * Initial-URI hint for [android.content.Intent.ACTION_OPEN_DOCUMENT_TREE]. Deep-links
     * the system picker into `Android/obb/<pkg>/` so the user doesn't have to navigate.
     * Pre-Android 8 picker ignores the hint — still works, just lands at root.
     */
    fun buildObbTreeHintUri(packageName: String): Uri? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return null
        val encoded = "primary%3AAndroid%2Fobb%2F$packageName"
        return "content://com.android.externalstorage.documents/document/$encoded".toUri()
    }

    suspend fun copy(
        context: Context,
        input: InputStream,
        treeUri: Uri,
        fileName: String,
        onBytesProgress: (Long) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val tree = DocumentFile.fromTreeUri(context, treeUri)
                ?: return@withContext Result.failure(IOException("Tree URI not accessible"))
            // Overwrite if exists — can't truncate a DocumentFile in place cleanly.
            tree.findFile(fileName)?.delete()
            val target = tree.createFile("application/octet-stream", fileName)
                ?: return@withContext Result.failure(IOException("Cannot create $fileName in tree"))
            val os = context.contentResolver.openOutputStream(target.uri)
                ?: return@withContext Result.failure(IOException("Cannot open output stream"))
            var totalBytes = 0L
            try {
                os.buffered().use<java.io.BufferedOutputStream, Unit> { bufOs ->
                    val buf = ByteArray(64 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n <= 0) break
                        bufOs.write(buf, 0, n)
                        totalBytes += n
                        onBytesProgress(totalBytes)
                    }
                    bufOs.flush()
                }
            } catch (ce: CancellationException) {
                runCatching { target.delete() }
                throw ce
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Timber.e(t, "SAF OBB copy failed for $fileName")
            Result.failure(t)
        }
    }

    /**
     * Verify the granted tree URI actually points at `Android/obb/<pkg>/`. Catches the
     * user picking the wrong folder — we don't want to dump OBBs into /Download/ silently.
     */
    fun isTreeForObbOf(treeUri: Uri, packageName: String): Boolean {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull() ?: return false
        // docId looks like "primary:Android/obb/com.foo"
        val expected = "primary:Android/obb/$packageName"
        return docId.equals(expected, ignoreCase = false)
    }
}
