package app.pwhs.universalinstaller.presentation.install

import android.content.pm.PackageManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.io.IOException
import java.io.InputStream
import java.lang.reflect.Method
import kotlin.coroutines.coroutineContext

/**
 * OBB copy via Shizuku. Android 11+ blocks `Android/obb/<other-pkg>/` even with
 * MANAGE_EXTERNAL_STORAGE — so for the common case we pipe the OBB bytes through a
 * Shizuku shell process which runs as `shell` UID (pre-granted access to obb dirs).
 */
object ShizukuObbWriter {

    private const val BUFFER_BYTES = 1 * 1024 * 1024

    fun isReady(): Boolean = try {
        Shizuku.pingBinder() &&
            !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (_: Exception) { false }

    suspend fun copy(
        input: InputStream,
        packageName: String,
        fileName: String,
        onBytesProgress: (Long) -> Unit,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val targetDir = "/sdcard/Android/obb/$packageName"
        val targetFile = "$targetDir/$fileName"
        try {
            // Ensure obb/<pkg> dir exists. `mkdir -p` returns 0 whether it existed or not.
            val mkdir = invokeShizukuNewProcess(arrayOf("mkdir", "-p", targetDir), null, null)
            val mkdirExit = mkdir.waitFor()
            if (mkdirExit != 0) {
                return@withContext Result.failure(IOException("mkdir via Shizuku exit=$mkdirExit"))
            }

            // Stream content through `cat > path`. Shell-quote the path defensively
            // (package names can't contain single quotes, but filenames could).
            val cmd = "cat > " + shellSingleQuote(targetFile)
            val process = invokeShizukuNewProcess(arrayOf("sh", "-c", cmd), null, null)
            var totalBytes = 0L
            try {
                process.outputStream.buffered(BUFFER_BYTES).use { os ->
                    val buf = ByteArray(BUFFER_BYTES)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n <= 0) break
                        os.write(buf, 0, n)
                        totalBytes += n
                        onBytesProgress(totalBytes)
                    }
                    os.flush()
                }
            } catch (ce: CancellationException) {
                runCatching { process.destroy() }
                // Best-effort cleanup of partial file via Shizuku too.
                runCatching {
                    invokeShizukuNewProcess(arrayOf("rm", "-f", targetFile), null, null).waitFor()
                }
                throw ce
            }
            val exit = process.waitFor()
            if (exit != 0) {
                runCatching {
                    invokeShizukuNewProcess(arrayOf("rm", "-f", targetFile), null, null).waitFor()
                }
                return@withContext Result.failure(IOException("sh cat exit=$exit"))
            }
            Result.success(Unit)
        } catch (ce: CancellationException) {
            throw ce
        } catch (t: Throwable) {
            Timber.e(t, "Shizuku OBB copy failed: $targetFile")
            Result.failure(t)
        }
    }

    private fun shellSingleQuote(s: String): String =
        "'" + s.replace("'", "'\\''") + "'"

    /**
     * `Shizuku.newProcess` is marked `@hide` from Shizuku 13.x onwards. It's still callable
     * via reflection — that's the documented escape hatch for apps that don't want to set
     * up a bound `UserService` just to exec a shell command. Method is cached on first use.
     */
    @Volatile
    private var cachedNewProcess: Method? = null

    private fun invokeShizukuNewProcess(
        cmd: Array<String>,
        env: Array<String>?,
        dir: String?,
    ): Process {
        val m = cachedNewProcess ?: Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).also { it.isAccessible = true; cachedNewProcess = it }
        return m.invoke(null, cmd, env, dir) as Process
    }
}
