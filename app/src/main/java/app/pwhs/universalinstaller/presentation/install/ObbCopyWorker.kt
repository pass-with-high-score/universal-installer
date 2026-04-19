package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import androidx.core.net.toUri
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import timber.log.Timber
import java.io.IOException
import java.util.UUID
import java.util.zip.ZipInputStream

/**
 * Foreground worker that performs the OBB copy after an APK install succeeds. Outlives the
 * Activity — user can close the app and the copy continues, with progress shown on the
 * notification shade.
 *
 * The strategy (direct / Shizuku / SAF) is decided by the ViewModel before enqueueing;
 * the worker just executes what it's told. SAF-grant prompts stay in the UI layer because
 * a background worker can't launch picker activities.
 */
class ObbCopyWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val appName = inputData.getString(KEY_APP_NAME).orEmpty()
        val notif = ObbCopyNotifier.buildProgress(applicationContext, appName, 0L, -1L)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                ObbCopyNotifier.FOREGROUND_ID,
                notif,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
            )
        } else {
            ForegroundInfo(ObbCopyNotifier.FOREGROUND_ID, notif)
        }
    }

    override suspend fun doWork(): Result {
        val strategy = inputData.getString(KEY_STRATEGY) ?: return failWith("Missing strategy")
        val packageName = inputData.getString(KEY_PACKAGE_NAME) ?: return failWith("Missing package")
        val appName = inputData.getString(KEY_APP_NAME).orEmpty()
        val sourceUri = inputData.getString(KEY_SOURCE_URI)?.toUri()
        val entryPaths = inputData.getStringArray(KEY_ENTRY_PATHS) ?: emptyArray()
        val entrySizes = inputData.getLongArray(KEY_ENTRY_SIZES) ?: LongArray(0)
        val attachedUris = inputData.getStringArray(KEY_ATTACHED_URIS) ?: emptyArray()
        val attachedNames = inputData.getStringArray(KEY_ATTACHED_NAMES) ?: emptyArray()
        val attachedSizes = inputData.getLongArray(KEY_ATTACHED_SIZES) ?: LongArray(0)
        val treeUri = inputData.getString(KEY_TREE_URI)?.toUri()

        val entries = entryPaths.mapIndexed { i, path ->
            ObbEntry(
                entryPath = path,
                fileName = path.substringAfterLast('/'),
                sizeBytes = entrySizes.getOrNull(i) ?: 0L,
            )
        }
        val attached = attachedUris.mapIndexed { i, uriStr ->
            AttachedObb(
                uri = uriStr.toUri(),
                fileName = attachedNames.getOrNull(i) ?: uriStr.substringAfterLast('/'),
                sizeBytes = attachedSizes.getOrNull(i) ?: 0L,
            )
        }

        val combinedTotal = entries.sumOf { it.sizeBytes.coerceAtLeast(0L) } +
            attached.sumOf { it.sizeBytes.coerceAtLeast(0L) }

        // Initial foreground set — WorkManager promotes us to a foreground service so the OS
        // doesn't kill us if the user backgrounds the app mid-copy.
        setForeground(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ForegroundInfo(
                    ObbCopyNotifier.FOREGROUND_ID,
                    ObbCopyNotifier.buildProgress(applicationContext, appName, 0L, combinedTotal),
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                )
            } else {
                ForegroundInfo(
                    ObbCopyNotifier.FOREGROUND_ID,
                    ObbCopyNotifier.buildProgress(applicationContext, appName, 0L, combinedTotal),
                )
            }
        )

        var bytesSoFar = 0L
        var totalCopied = 0
        // Throttle progress reports — setForeground + setProgress are binder IPC each; hitting
        // them every 64 KB on a multi-GB OBB was making the copy 5-10× slower than raw I/O.
        var lastReportMs = 0L
        val progressThrottleMs = 500L

        val onBytes: suspend (Long) -> Unit = { bytes ->
            val now = android.os.SystemClock.elapsedRealtime()
            val atEnd = combinedTotal in 1..bytes
            if (atEnd || now - lastReportMs >= progressThrottleMs) {
                lastReportMs = now
                val percent = if (combinedTotal > 0) {
                    ((bytes * 100L) / combinedTotal).toInt().coerceIn(0, 100)
                } else 0
                setProgress(
                    workDataOf(
                        KEY_PROGRESS_BYTES to bytes,
                        KEY_PROGRESS_TOTAL to combinedTotal,
                        KEY_PROGRESS_PERCENT to percent,
                    )
                )
                setForeground(
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        ForegroundInfo(
                            ObbCopyNotifier.FOREGROUND_ID,
                            ObbCopyNotifier.buildProgress(applicationContext, appName, bytes, combinedTotal),
                            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
                        )
                    } else {
                        ForegroundInfo(
                            ObbCopyNotifier.FOREGROUND_ID,
                            ObbCopyNotifier.buildProgress(applicationContext, appName, bytes, combinedTotal),
                        )
                    }
                )
            }
        }

        return try {
            // --- Bundle-embedded OBBs (re-stream from the original zip) ---
            if (entries.isNotEmpty() && sourceUri != null) {
                val entryPathSet = entries.map { it.entryPath }.toSet()
                applicationContext.contentResolver.openInputStream(sourceUri)?.buffered()?.use { input ->
                    ZipInputStream(input).use { zis ->
                        while (true) {
                            val entry = zis.nextEntry ?: break
                            if (entry.isDirectory || entry.name !in entryPathSet) {
                                zis.closeEntry(); continue
                            }
                            val fileName = entry.name.substringAfterLast('/')
                            val beforeBytes = bytesSoFar
                            val result = writeOne(strategy, packageName, treeUri, fileName, zis) { fileBytes ->
                                onBytes(beforeBytes + fileBytes)
                            }
                            if (result.isFailure) return reportFailure(appName, result.exceptionOrNull())
                            totalCopied++
                            val size = entries.find { it.entryPath == entry.name }?.sizeBytes ?: 0L
                            bytesSoFar += size.coerceAtLeast(0L)
                            zis.closeEntry()
                        }
                    }
                } ?: return reportFailure(appName, IOException("Cannot open source"))
            }

            // --- Attached standalone OBBs ---
            for (obb in attached) {
                val beforeBytes = bytesSoFar
                val input = applicationContext.contentResolver.openInputStream(obb.uri)
                    ?: return reportFailure(appName, IOException("Cannot open ${obb.fileName}"))
                val result = input.use {
                    writeOne(strategy, packageName, treeUri, obb.fileName, it) { fileBytes ->
                        onBytes(beforeBytes + fileBytes)
                    }
                }
                if (result.isFailure) return reportFailure(appName, result.exceptionOrNull())
                totalCopied++
                bytesSoFar = beforeBytes + obb.sizeBytes.coerceAtLeast(0L)
            }

            showDoneNotification(appName, totalCopied)
            Result.success(workDataOf(KEY_RESULT_FILE_COUNT to totalCopied))
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            reportFailure(appName, t)
        }
    }

    private suspend fun writeOne(
        strategy: String,
        packageName: String,
        treeUri: Uri?,
        fileName: String,
        input: java.io.InputStream,
        onBytes: suspend (Long) -> Unit,
    ): kotlin.Result<Unit> = when (strategy) {
        STRATEGY_SHIZUKU -> ShizukuObbWriter.copy(
            input = input,
            packageName = packageName,
            fileName = fileName,
            onBytesProgress = { onBytesBlocking(onBytes, it) },
        )
        STRATEGY_SAF -> {
            if (treeUri == null) kotlin.Result.failure(IOException("Missing tree URI for SAF"))
            else SafObbWriter.copy(
                context = applicationContext,
                input = input,
                treeUri = treeUri,
                fileName = fileName,
                onBytesProgress = { onBytesBlocking(onBytes, it) },
            )
        }
        STRATEGY_DIRECT -> directWrite(packageName, fileName, input, onBytes)
        else -> kotlin.Result.failure(IOException("Unknown strategy: $strategy"))
    }

    /**
     * Writer callbacks fire on every `os.write` (once per 1 MB buffer) — we throttle here
     * to avoid paying `runBlocking` overhead for every chunk. Only cross into the worker's
     * suspend scope ~twice a second; the final update catches the tail.
     */
    @Volatile private var lastCallbackMs = 0L

    private fun onBytesBlocking(sink: suspend (Long) -> Unit, bytes: Long) {
        val now = android.os.SystemClock.elapsedRealtime()
        if (now - lastCallbackMs < 500L) return
        lastCallbackMs = now
        kotlinx.coroutines.runBlocking { sink(bytes) }
    }

    private suspend fun directWrite(
        packageName: String,
        fileName: String,
        input: java.io.InputStream,
        onBytes: suspend (Long) -> Unit,
    ): kotlin.Result<Unit> = try {
        val dir = java.io.File(
            android.os.Environment.getExternalStorageDirectory(),
            "Android/obb/$packageName",
        )
        if (!dir.exists() && !dir.mkdirs()) {
            kotlin.Result.failure(IOException("Cannot create $dir"))
        } else {
            val out = java.io.File(dir, fileName)
            out.outputStream().buffered(BUFFER_BYTES).use { os ->
                val buf = ByteArray(BUFFER_BYTES)
                var total = 0L
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    os.write(buf, 0, n)
                    total += n
                    onBytes(total)
                }
            }
            kotlin.Result.success(Unit)
        }
    } catch (t: Throwable) {
        Timber.e(t, "Direct OBB write failed")
        kotlin.Result.failure(t)
    }

    private fun showDoneNotification(appName: String, count: Int) {
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? android.app.NotificationManager ?: return
        nm.notify(
            ObbCopyNotifier.FOREGROUND_ID + 1,
            ObbCopyNotifier.buildDone(applicationContext, appName, count),
        )
    }

    private fun reportFailure(appName: String, t: Throwable?): Result {
        val message = t?.message ?: "OBB copy failed"
        Timber.e(t, "OBB worker failed: $message")
        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE)
            as? android.app.NotificationManager
        nm?.notify(
            ObbCopyNotifier.FOREGROUND_ID + 1,
            ObbCopyNotifier.buildFailed(applicationContext, appName, message),
        )
        return Result.failure(workDataOf(KEY_RESULT_ERROR to message))
    }

    private fun failWith(msg: String): Result {
        Timber.e("OBB worker config error: $msg")
        return Result.failure(workDataOf(KEY_RESULT_ERROR to msg))
    }

    companion object {
        private const val BUFFER_BYTES = 1 * 1024 * 1024  // 1 MB — fewer IPC round-trips over Shizuku binder

        const val WORK_NAME_PREFIX = "obb_copy_"
        const val WORK_TAG = "obb_copy"

        const val KEY_STRATEGY = "strategy"
        const val KEY_PACKAGE_NAME = "packageName"
        const val KEY_APP_NAME = "appName"
        const val KEY_SOURCE_URI = "sourceUri"
        const val KEY_ENTRY_PATHS = "entryPaths"
        const val KEY_ENTRY_SIZES = "entrySizes"
        const val KEY_ATTACHED_URIS = "attachedUris"
        const val KEY_ATTACHED_NAMES = "attachedNames"
        const val KEY_ATTACHED_SIZES = "attachedSizes"
        const val KEY_TREE_URI = "treeUri"

        const val KEY_PROGRESS_BYTES = "progressBytes"
        const val KEY_PROGRESS_TOTAL = "progressTotal"
        const val KEY_PROGRESS_PERCENT = "progressPercent"
        const val KEY_RESULT_FILE_COUNT = "fileCount"
        const val KEY_RESULT_ERROR = "error"

        const val STRATEGY_DIRECT = "DIRECT"
        const val STRATEGY_SHIZUKU = "SHIZUKU"
        const val STRATEGY_SAF = "SAF"

        fun buildInputData(
            strategy: String,
            packageName: String,
            appName: String,
            sourceUri: Uri?,
            entries: List<ObbEntry>,
            attached: List<AttachedObb>,
            treeUri: Uri?,
        ): Data = Data.Builder()
            .putString(KEY_STRATEGY, strategy)
            .putString(KEY_PACKAGE_NAME, packageName)
            .putString(KEY_APP_NAME, appName)
            .apply {
                if (sourceUri != null) putString(KEY_SOURCE_URI, sourceUri.toString())
                if (treeUri != null) putString(KEY_TREE_URI, treeUri.toString())
                if (entries.isNotEmpty()) {
                    putStringArray(KEY_ENTRY_PATHS, entries.map { it.entryPath }.toTypedArray())
                    putLongArray(KEY_ENTRY_SIZES, entries.map { it.sizeBytes }.toLongArray())
                }
                if (attached.isNotEmpty()) {
                    putStringArray(KEY_ATTACHED_URIS, attached.map { it.uri.toString() }.toTypedArray())
                    putStringArray(KEY_ATTACHED_NAMES, attached.map { it.fileName }.toTypedArray())
                    putLongArray(KEY_ATTACHED_SIZES, attached.map { it.sizeBytes }.toLongArray())
                }
            }
            .build()

        fun buildRequest(workName: String, data: Data): androidx.work.OneTimeWorkRequest =
            OneTimeWorkRequestBuilder<ObbCopyWorker>()
                .setInputData(data)
                .addTag(WORK_TAG)
                .addTag(workName)
                .build()

        fun workNameFor(id: UUID) = "$WORK_NAME_PREFIX$id"
    }
}
