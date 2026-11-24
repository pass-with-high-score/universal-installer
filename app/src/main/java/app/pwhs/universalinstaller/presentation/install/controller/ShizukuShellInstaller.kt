package app.pwhs.universalinstaller.presentation.install.controller

import android.content.Context
import android.net.Uri
import android.os.Build
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.install.dialog.InstallerOverrides
import app.pwhs.universalinstaller.presentation.install.util.AbiOverrideUtil
import app.pwhs.universalinstaller.presentation.setting.DEFAULT_INSTALLER_PACKAGE_NAME
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.io.File
import java.util.UUID

/**
 * Installs APKs via Shizuku's remote shell (`pm install-create` → `pm install-write` via stdin →
 * `pm install-commit`). Mirrors `adb install --abi <abi>` under shell UID 2000.
 *
 * Used when options not exposed by Ackpine's Shizuku plugin (such as `--abi`) are requested.
 * Streaming over stdin avoids file-permission issues since UID 2000 cannot read app-private cache.
 */
object ShizukuShellInstaller {

    private val SESSION_ID_REGEX = Regex("""session\s*\[(\d+)]""", RegexOption.IGNORE_CASE)

    private val newProcessMethod: java.lang.reflect.Method? by lazy {
        try {
            Shizuku::class.java.getDeclaredMethod(
                "newProcess",
                Array<String>::class.java,
                Array<String>::class.java,
                String::class.java,
            ).apply { isAccessible = true }
        } catch (t: Throwable) {
            Timber.w(t, "Shizuku.newProcess unavailable for ShizukuShellInstaller")
            null
        }
    }

    suspend fun install(
        context: Context,
        uris: List<Uri>,
        userId: Int,
        packageName: String = "",
        allowDowngrade: Boolean = false,
        abiOverride: String? = null,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            require(uris.isNotEmpty()) { "No APK URIs provided" }
            val method = newProcessMethod
                ?: throw IllegalStateException("Shizuku shell is unavailable on this Shizuku version")

            val createArgs = buildCreateArgs(
                context = context,
                userId = userId,
                packageName = packageName,
                allowDowngrade = allowDowngrade,
                abiOverride = abiOverride,
            )

            val sessionId = createSession(method, createArgs)
            onProgress(0.1f)

            var committed = false
            try {
                uris.forEachIndexed { idx, uri ->
                    writeSplitFromUri(context, method, sessionId, idx, uri)
                    onProgress(0.1f + 0.8f * ((idx + 1).toFloat() / uris.size))
                }
                commitSession(method, sessionId)
                committed = true
                onProgress(1f)
            } finally {
                if (!committed) {
                    runCatching { execSimple(method, arrayOf("pm", "install-abandon", sessionId)) }
                }
            }
        }
    }

    private suspend fun buildCreateArgs(
        context: Context,
        userId: Int,
        packageName: String,
        allowDowngrade: Boolean,
        abiOverride: String?,
    ): List<String> {
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull()
        val args = mutableListOf("pm", "install-create")
        if (userId >= 0) {
            args += "--user"
            args += userId.toString()
        }
        if (prefs?.get(PreferencesKeys.SHIZUKU_REPLACE_EXISTING) != false) args += "-r"
        if (allowDowngrade || prefs?.get(PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE) == true) args += "-d"
        if (prefs?.get(PreferencesKeys.SHIZUKU_ALLOW_TEST) == true) args += "-t"
        if (prefs?.get(PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS) == true) args += "-g"
        if (prefs?.get(PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK) == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) args += "--bypass-low-target-sdk-block"
        if (prefs?.get(PreferencesKeys.SHIZUKU_DONT_KILL_APP) == true) args += "--dont-kill"
        if (prefs?.get(PreferencesKeys.SHIZUKU_DISABLE_VERIFICATION) == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) args += "--skip-verification"
        if (prefs?.get(PreferencesKeys.SHIZUKU_ENABLE_ROLLBACK) == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) args += "--enable-rollback"
        if (prefs?.get(PreferencesKeys.SHIZUKU_REQUEST_UPDATE_OWNERSHIP) == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) args += "--update-ownership"
        val sanitizedAbi = AbiOverrideUtil.sanitize(abiOverride)
        if (sanitizedAbi != null) {
            args += "--abi"
            args += sanitizedAbi
        }
        if (prefs?.get(PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE) == true) {
            val override = if (packageName.isNotBlank()) {
                InstallerOverrides.get(prefs[PreferencesKeys.INSTALLER_OVERRIDES], packageName)
            } else null
            val installer = override
                ?: prefs[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME]?.trim()?.ifBlank { DEFAULT_INSTALLER_PACKAGE_NAME }
                ?: DEFAULT_INSTALLER_PACKAGE_NAME
            if (installer.isNotBlank()) {
                args += "-i"
                args += installer
            }
        }
        return args
    }

    private fun createSession(method: java.lang.reflect.Method, args: List<String>): String {
        val (exitCode, stdout, stderr) = execSimple(method, args.toTypedArray())
        val combined = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n").trim()
        if (exitCode != 0) {
            throw RuntimeException("pm install-create failed: ${combined.ifBlank { "exit=$exitCode" }}")
        }
        return SESSION_ID_REGEX.find(stdout)?.groupValues?.get(1)
            ?: throw RuntimeException("Could not parse session id from: ${combined.ifBlank { "empty output" }}")
    }

    private fun writeSplitFromUri(
        context: Context,
        method: java.lang.reflect.Method,
        sessionId: String,
        index: Int,
        uri: Uri,
    ) {
        val splitName = "split_$index.apk"
        val knownSize = runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { it.length } ?: -1L
        }.getOrDefault(-1L)

        if (knownSize > 0L) {
            context.contentResolver.openInputStream(uri).use { input ->
                requireNotNull(input) { "Failed to open URI $uri" }
                streamToInstallWrite(method, sessionId, splitName, knownSize, input)
            }
        } else {
            // ContentProvider did not report size — buffer locally so we can pass exact -S <size>.
            val tempFile = File(context.cacheDir, "shizuku_abi_${UUID.randomUUID()}.apk")
            try {
                context.contentResolver.openInputStream(uri).use { input ->
                    requireNotNull(input) { "Failed to open URI $uri" }
                    tempFile.outputStream().use { input.copyTo(it) }
                }
                tempFile.inputStream().use { input ->
                    streamToInstallWrite(method, sessionId, splitName, tempFile.length(), input)
                }
            } finally {
                tempFile.delete()
            }
        }
    }

    private fun streamToInstallWrite(
        method: java.lang.reflect.Method,
        sessionId: String,
        splitName: String,
        size: Long,
        input: java.io.InputStream,
    ) {
        val cmd = arrayOf("pm", "install-write", "-S", size.toString(), sessionId, splitName, "-")
        val process = method.invoke(null, cmd, null, null) as Process
        try {
            process.outputStream.use { out ->
                input.copyTo(out)
                out.flush()
            }
        } catch (e: Exception) {
            val stderr = runCatching { process.errorStream.bufferedReader().use { it.readText() } }.getOrDefault("")
            process.destroy()
            throw RuntimeException("pm install-write failed for $splitName: ${stderr.ifBlank { e.message ?: "stream error" }}", e)
        }
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            val detail = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n").trim()
            throw RuntimeException("pm install-write failed for $splitName: ${detail.ifBlank { "exit=$exitCode" }}")
        }
    }

    private fun commitSession(method: java.lang.reflect.Method, sessionId: String) {
        val (exitCode, stdout, stderr) = execSimple(method, arrayOf("pm", "install-commit", sessionId))
        val detail = listOf(stdout, stderr).filter { it.isNotBlank() }.joinToString("\n").trim()
        if (exitCode != 0 || !stdout.contains("Success", ignoreCase = true)) {
            throw RuntimeException(detail.ifBlank { "pm install-commit failed (exit=$exitCode)" })
        }
    }

    private fun execSimple(
        method: java.lang.reflect.Method,
        cmd: Array<String>,
    ): Triple<Int, String, String> {
        val process = method.invoke(null, cmd, null, null) as Process
        val stdout = process.inputStream.bufferedReader().use { it.readText() }
        val stderr = process.errorStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        return Triple(exitCode, stdout, stderr)
    }
}
