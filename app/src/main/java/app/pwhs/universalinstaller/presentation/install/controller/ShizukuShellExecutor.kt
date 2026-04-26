package app.pwhs.universalinstaller.presentation.install.controller

import android.content.pm.PackageManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber

/**
 * Run privileged `pm` commands via Shizuku's `newProcess()` — same shell UID (2000) that
 * `adb shell` runs as, so per-user uninstall and disable of system apps work without Root.
 *
 * Shizuku v13 moved `newProcess` to private visibility to push callers toward the
 * UserService/AIDL pattern. A UserService would take ~100 LOC for a one-shot shell wrapper,
 * so instead we call via reflection. If a future Shizuku release ever removes the method,
 * [isReady] flips to false and the ViewModel routes users to the "Root required" dialog.
 */
object ShizukuShellExecutor {

    private val newProcessMethod: java.lang.reflect.Method? = try {
        Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        ).apply { isAccessible = true }
    } catch (t: Throwable) {
        Timber.w(t, "Shizuku.newProcess not reachable — Shizuku shell path disabled")
        null
    }

    fun isReady(): Boolean {
        if (newProcessMethod == null) return false
        return try {
            Shizuku.pingBinder() &&
                !Shizuku.isPreV11() &&
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
        } catch (t: Throwable) {
            Timber.w(t, "Shizuku readiness check failed")
            false
        }
    }

    /**
     * Shells out via Shizuku's remote process. Package name is regex-validated to block
     * shell injection — in practice the caller feeds a PackageManager-sourced string that
     * can't contain metacharacters, but we verify anyway.
     */
    suspend fun uninstallSystemApp(
        packageName: String,
        method: SystemAppMethod,
    ): Result<String> = withContext(Dispatchers.IO) {
        require(packageName.matches(Regex("^[A-Za-z0-9._]+$"))) {
            "Refusing to shell out with suspicious package name: $packageName"
        }
        val reflectedMethod = newProcessMethod
            ?: return@withContext Result.failure(
                IllegalStateException("Shizuku.newProcess unavailable on this Shizuku build"),
            )
        val cmd = when (method) {
            SystemAppMethod.UninstallForUser0 -> "pm uninstall --user 0 $packageName"
            SystemAppMethod.Disable -> "pm disable-user --user 0 $packageName"
        }
        runCatching {
            val process = reflectedMethod.invoke(
                null,
                arrayOf("sh", "-c", cmd),
                null,
                null,
            ) as Process
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            // Same token strategy as the Root path — `pm` on some ROMs returns 0 for soft
            // failures like `Failure [NOT_INSTALLED_FOR_USER]`, so we verify both.
            val successToken = when (method) {
                SystemAppMethod.UninstallForUser0 -> "Success"
                SystemAppMethod.Disable -> "new state: disabled"
            }
            if (exitCode != 0 || !stdout.contains(successToken, ignoreCase = true)) {
                throw RuntimeException(
                    "shizuku pm failed (exit=$exitCode): ${stdout.ifBlank { stderr }.ifBlank { "no output" }}",
                )
            }
            stdout
        }
    }

    /**
     * Generic-shell helpers for the Manage tab's per-app actions. Force-stop/enable/disable
     * don't fit `SystemAppMethod`'s narrow uninstall-vs-disable contract, but the shell-out
     * mechanics (reflection, package validation, exit-code+token verification) are the same.
     */

    suspend fun forceStop(packageName: String): Result<String> =
        runShell(packageName, "am force-stop $packageName", successToken = null)

    suspend fun setEnabled(packageName: String, enabled: Boolean): Result<String> {
        val cmd = if (enabled) "pm enable $packageName"
            else "pm disable-user --user 0 $packageName"
        val token = if (enabled) "new state: enabled" else "new state: disabled"
        return runShell(packageName, cmd, successToken = token)
    }

    /**
     * Single-shot shell. [successToken] gates "did the command actually do the thing" beyond
     * the exit code — `pm` is notorious for printing soft failures ("Failure [...]") with
     * exit 0. Pass `null` for commands like `am force-stop` that have no output on success.
     */
    private suspend fun runShell(
        packageName: String,
        cmd: String,
        successToken: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        require(packageName.matches(Regex("^[A-Za-z0-9._]+$"))) {
            "Refusing to shell out with suspicious package name: $packageName"
        }
        val reflected = newProcessMethod
            ?: return@withContext Result.failure(
                IllegalStateException("Shizuku.newProcess unavailable on this Shizuku build"),
            )
        runCatching {
            val process = reflected.invoke(
                null,
                arrayOf("sh", "-c", cmd),
                null,
                null,
            ) as Process
            val stdout = process.inputStream.bufferedReader().use { it.readText() }
            val stderr = process.errorStream.bufferedReader().use { it.readText() }
            val exitCode = process.waitFor()
            val tokenOk = successToken == null || stdout.contains(successToken, ignoreCase = true)
            if (exitCode != 0 || !tokenOk) {
                throw RuntimeException(
                    "shizuku shell failed (exit=$exitCode): " +
                        stdout.ifBlank { stderr }.ifBlank { "no output" },
                )
            }
            stdout
        }
    }
}
