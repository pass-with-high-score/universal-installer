package app.pwhs.universalinstaller.presentation.install.controller

import android.content.Context
import android.net.Uri
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.UUID

/**
 * Root-based targeted installer. Shells out to `pm install-create --user <id>` →
 * `pm install-write` (one per APK split) → `pm install-commit`, so users on
 * Root-only setups (no Shizuku) can install into a work profile / secondary user.
 *
 * Counterpart of [ManualTargetedInstaller] which uses the Shizuku binder; together
 * they cover the two privileged paths the app supports.
 */
object RootTargetedInstaller {

    suspend fun install(
        context: Context,
        uris: List<Uri>,
        userId: Int,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            // Stage each input URI into the app's cache dir under a chmod-readable file so
            // the root shell can read it without SELinux drama on content:// URIs.
            val stagingDir = File(context.cacheDir, "root_install_${UUID.randomUUID()}").apply {
                mkdirs()
            }
            val stagedFiles = try {
                uris.mapIndexed { idx, uri ->
                    val out = File(stagingDir, "split_$idx.apk")
                    context.contentResolver.openInputStream(uri).use { input ->
                        requireNotNull(input) { "Failed to open URI $uri" }
                        out.outputStream().use { input.copyTo(it) }
                    }
                    // Make world-readable so root-shell (which may drop to other contexts) can read.
                    out.setReadable(true, false)
                    out
                }.also {
                    stagingDir.setReadable(true, false)
                    stagingDir.setExecutable(true, false)
                }
            } catch (e: Exception) {
                stagingDir.deleteRecursively()
                throw e
            }

            try {
                val sessionId = createSession(userId)
                onProgress(0.1f)

                stagedFiles.forEachIndexed { idx, file ->
                    writeSplit(sessionId, idx, file)
                    onProgress(0.1f + 0.8f * ((idx + 1).toFloat() / stagedFiles.size))
                }

                commitSession(sessionId)
                onProgress(1f)
            } finally {
                stagingDir.deleteRecursively()
            }
        }
    }

    private fun createSession(userId: Int): String {
        // `pm install-create --user <id> -r` → prints "Success: created install session [<id>]"
        val result = Shell.cmd("pm install-create --user $userId -r").exec()
        if (!result.isSuccess) {
            throw RuntimeException("pm install-create failed: ${joinErr(result)}")
        }
        val line = result.out.joinToString("\n")
        val sessionId = extractSessionId(line)
            ?: throw RuntimeException("Could not parse session id from: $line")
        Timber.d("RootTargetedInstaller: created session=$sessionId user=$userId")
        return sessionId
    }

    private fun writeSplit(sessionId: String, index: Int, file: File) {
        val size = file.length()
        val splitName = "split_$index"
        // Older pm versions don't accept a file path as last arg, but Android 9+ does. We
        // bracket with `-S <size>` to be explicit. If a ROM rejects this signature, fall
        // back to a pipe form below.
        val pathArg = file.absolutePath.replace(" ", "\\ ")
        val cmd = "pm install-write -S $size $sessionId $splitName $pathArg"
        var result = Shell.cmd(cmd).exec()
        if (!result.isSuccess) {
            // Fallback: pipe via cat. Works on older pm where path-arg variant fails.
            val pipeCmd = "cat ${file.absolutePath.replace(" ", "\\ ")} | pm install-write -S $size $sessionId $splitName -"
            result = Shell.cmd(pipeCmd).exec()
            if (!result.isSuccess) {
                throw RuntimeException("pm install-write failed for split_$index: ${joinErr(result)}")
            }
        }
    }

    private fun commitSession(sessionId: String) {
        val result = Shell.cmd("pm install-commit $sessionId").exec()
        val stdout = result.out.joinToString("\n")
        if (!result.isSuccess || !stdout.contains("Success", ignoreCase = true)) {
            // pm install-commit on failure prints e.g. "Failure [INSTALL_FAILED_VERSION_DOWNGRADE]"
            throw RuntimeException(stdout.ifBlank { joinErr(result) }.ifBlank { "install-commit failed" })
        }
    }

    private fun extractSessionId(text: String): String? {
        // Sample output: "Success: created install session [1234567]"
        // Anchor on "session" so a stray bracketed number in a warning line above doesn't match.
        val regex = Regex("""session\s*\[(\d+)]""", RegexOption.IGNORE_CASE)
        return regex.find(text)?.groupValues?.getOrNull(1)
    }

    private fun joinErr(result: Shell.Result): String =
        result.err.joinToString("\n").ifBlank { result.out.joinToString("\n") }
}
