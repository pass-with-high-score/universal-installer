package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.net.Uri
import android.os.CancellationSignal
import androidx.core.content.FileProvider
import app.pwhs.universalinstaller.BuildConfig
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.CloseableSequence
import timber.log.Timber
import java.io.File
import kotlin.concurrent.Volatile

class SingletonApkSequence(private val uri: Uri, context: Context) : CloseableSequence<Apk> {

    @Volatile
    override var isClosed: Boolean = false
        private set

    private val applicationContext = context.applicationContext
    private val cancellationSignal = CancellationSignal()

    override fun iterator(): Iterator<Apk> {
        return object : Iterator<Apk> {

            // ackpine's Apk.fromUri resolves the SAF FD to a canonical path and then
            // rejects it via File.isApk (`name.endsWith(".apk")`). Some sources (notably
            // F-Droid sharing via FileProvider) hand us a content URI whose backing file
            // on disk has no `.apk` suffix, so the parse returns null and we surface a
            // misleading "file is corrupt" error. Fall back to copying the bytes into a
            // `.apk`-named cache file and reparse from there.
            private val apk = Apk.fromUri(uri, applicationContext, cancellationSignal)
                ?: parseViaCachedCopy()
            private var isYielded = false

            override fun hasNext(): Boolean {
                return apk != null && !isYielded
            }

            override fun next(): Apk {
                if (!hasNext()) {
                    throw NoSuchElementException()
                }
                isYielded = true
                return apk!!
            }
        }
    }

    private fun parseViaCachedCopy(): Apk? {
        val cached = try {
            val dir = File(applicationContext.cacheDir, "apk_parse_fallback").apply { mkdirs() }
            val file = File(dir, "src_${System.currentTimeMillis()}.apk")
            applicationContext.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            file
        } catch (t: Throwable) {
            Timber.w(t, "SingletonApkSequence fallback copy failed")
            return null
        }
        return try {
            val cachedUri = FileProvider.getUriForFile(
                applicationContext,
                "${BuildConfig.APPLICATION_ID}.fileprovider",
                cached,
            )
            Apk.fromUri(cachedUri, applicationContext, cancellationSignal)
        } catch (t: Throwable) {
            Timber.w(t, "SingletonApkSequence fallback parse failed")
            null
        }
    }

    override fun close() {
        isClosed = true
        cancellationSignal.cancel()
    }
}
