package app.pwhs.core.install

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.ApkSplits.validate
import ru.solrudev.ackpine.splits.CloseableSequence
import ru.solrudev.ackpine.splits.SplitPackage.Companion.toSplitPackage
import ru.solrudev.ackpine.splits.ZippedApkSplits
import ru.solrudev.ackpine.splits.get
import java.io.File
import java.io.InputStream
import java.util.zip.ZipInputStream
import kotlin.coroutines.resume

/**
 * Installs an APK (or a split bundle) via [PackageInstaller] — the only install path that
 * works on Android TV, where there is no installer `VIEW` intent and no SAF picker.
 *
 * The commit reports back through a [PendingIntent] broadcast. A non-privileged installer
 * first receives [PackageInstaller.STATUS_PENDING_USER_ACTION] carrying a confirm Intent
 * (the system package-installer UI, D-pad navigable on TV); after the user accepts, the
 * same IntentSender receives the terminal status.
 *
 * Bundles (.apks/.xapk/.apkm/.zip) are unzipped in memory and every contained `.apk` is
 * written into one session, so split apps install in a single transaction.
 */
class ApkInstaller(private val context: Context) {

    sealed interface Result {
        data object Success : Result
        data class Failure(val message: String) : Result
    }

    private companion object {
        const val ACTION = "app.pwhs.core.install.STATUS"
    }

    /** Convenience for installing a staged file (e.g. an upload received over LAN). */
    suspend fun install(source: File): Result =
        install(
            Uri.fromFile(source),
            isBundle = source.extension.lowercase() in BUNDLE_EXTS,
            totalBytes = source.length(),
        )

    /**
     * Install from a content/file [uri]. [isBundle] true unzips split APKs into one session.
     * [totalBytes] (the source's on-disk size, `-1` if unknown) drives [onProgress], which is
     * invoked with cumulative bytes written and the total on the calling (IO) thread — TV shows
     * this as a determinate bar while the session is being written.
     * Suspends until the install reaches a terminal state (the user-action confirm screen is
     * launched mid-flow). Safe to call off the main thread.
     */
    suspend fun install(
        uri: Uri,
        isBundle: Boolean,
        totalBytes: Long = -1L,
        onProgress: ((written: Long, total: Long) -> Unit)? = null,
    ): Result {
        val pm = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
        val sessionId = pm.createSession(params)
        val progress = onProgress?.let { Progress(totalBytes, it) }
        try {
            pm.openSession(sessionId).use { session ->
                if (isBundle) {
                    writeBundle(session, uri, progress)
                } else {
                    openInput(uri).use { writeEntry(session, "base.apk", it, -1L, progress) }
                }
                return commitAndAwait(session, sessionId)
            }
        } catch (t: Throwable) {
            runCatching { pm.abandonSession(sessionId) }
            return Result.Failure(t.message ?: t::class.java.simpleName)
        }
    }

    /** Accumulates bytes written across every entry and relays them to the caller's callback. */
    private class Progress(val total: Long, val emit: (Long, Long) -> Unit) {
        private var written = 0L
        fun add(bytes: Int) {
            written += bytes
            emit(written, total)
        }
    }

    private fun openInput(uri: Uri): InputStream =
        context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Cannot open $uri")

    private suspend fun writeBundle(session: PackageInstaller.Session, bundle: Uri, progress: Progress?) {
        val ackpineWrote = runCatching {
            val splitPackage = ZippedApkSplits.getApksForUri(bundle, context)
                .validate()
                .toSplitPackage()
                .filterCompatible(context)
            val sequence = splitPackage.get()
            val entries = try {
                sequence.toList()
            } finally {
                (sequence as? CloseableSequence<*>)?.close()
            }
            var count = 0
            for (entry in entries) {
                val apk = entry.apk
                val name = if (apk is Apk.Base) "base.apk" else apk.name.substringAfterLast('/')
                context.contentResolver.openInputStream(apk.uri)?.use { input ->
                    writeEntry(session, name, input, apk.size, progress)
                    count++
                }
            }
            count
        }.getOrDefault(0)

        if (ackpineWrote > 0) return

        val tempDir = File(context.cacheDir, "bundle_extract_${System.currentTimeMillis()}").apply { mkdirs() }
        try {
            val entries = mutableListOf<Pair<String, File>>()
            ZipInputStream(openInput(bundle).buffered()).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    val rawName = entry.name.substringAfterLast('/')
                    if (!entry.isDirectory && rawName.endsWith(".apk", ignoreCase = true)) {
                        val tmpFile = File(tempDir, "entry_${entries.size}.apk")
                        tmpFile.outputStream().use { zip.copyTo(it) }
                        entries.add(rawName to tmpFile)
                    }
                    zip.closeEntry()
                    entry = zip.nextEntry
                }
            }
            require(entries.isNotEmpty()) { "No APK entries found in bundle" }

            val baseIndex = entries.indexOfFirst { it.first.equals("base.apk", ignoreCase = true) }
                .let { if (it >= 0) it else 0 }

            var splitIndex = 1
            entries.forEachIndexed { index, (rawName, file) ->
                val sessionEntryName = if (index == baseIndex) {
                    "base.apk"
                } else if (rawName.startsWith("split_", ignoreCase = true)) {
                    rawName
                } else {
                    "split_${splitIndex++}.apk"
                }
                file.inputStream().use { input ->
                    writeEntry(session, sessionEntryName, input, file.length(), progress)
                }
            }
        } finally {
            tempDir.deleteRecursively()
        }
    }

    private fun writeEntry(
        session: PackageInstaller.Session,
        name: String,
        input: InputStream,
        size: Long,
        progress: Progress?,
    ) {
        session.openWrite(name, 0, size).use { out ->
            if (progress == null) {
                input.copyTo(out)
            } else {
                val buffer = ByteArray(64 * 1024)
                var read = input.read(buffer)
                while (read >= 0) {
                    out.write(buffer, 0, read)
                    progress.add(read)
                    read = input.read(buffer)
                }
            }
            session.fsync(out)
        }
    }

    private suspend fun commitAndAwait(
        session: PackageInstaller.Session,
        sessionId: Int,
    ): Result = suspendCancellableCoroutine { cont ->
        val action = "$ACTION.$sessionId"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val status = intent.getIntExtra(
                    PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE
                )
                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirm = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        if (confirm != null) {
                            // Remediate Google Play Intent Redirection:
                            // 1. Strip URI grant flags so private content providers cannot be accessed
                            confirm.removeFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)

                            // 2. Verify target is not an internal component of our own app
                            val resolved = confirm.resolveActivity(context.packageManager)
                            val flags = confirm.flags
                            val isUriPermissionFree = (flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) &&
                                    (flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION == 0)

                            if (resolved != null && resolved.packageName != context.packageName && isUriPermissionFree) {
                                confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                runCatching { context.startActivity(confirm) }
                            }
                        }
                        // not terminal — wait for the follow-up status
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        runCatching { context.unregisterReceiver(this) }
                        if (cont.isActive) cont.resume(Result.Success)
                    }
                    else -> {
                        runCatching { context.unregisterReceiver(this) }
                        val msg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                            ?: "Install failed (status $status)"
                        if (cont.isActive) cont.resume(Result.Failure(msg))
                    }
                }
            }
        }
        ContextCompat.registerReceiver(
            context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED
        )
        cont.invokeOnCancellation { runCatching { context.unregisterReceiver(receiver) } }

        // FLAG_MUTABLE: the system fills in EXTRA_INTENT for the pending-user-action step.
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
        val pi = PendingIntent.getBroadcast(
            context, sessionId, Intent(action).setPackage(context.packageName), flags
        )
        session.commit(pi.intentSender)
    }
}
