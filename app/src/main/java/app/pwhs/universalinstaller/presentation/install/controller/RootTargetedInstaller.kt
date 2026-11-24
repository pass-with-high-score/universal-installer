package app.pwhs.universalinstaller.presentation.install.controller

import android.content.Context
import android.net.Uri
import android.os.Build
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.install.isKnoxOrSecureFolder
import app.pwhs.universalinstaller.presentation.install.dialog.InstallerOverrides
import app.pwhs.universalinstaller.presentation.setting.DEFAULT_INSTALLER_PACKAGE_NAME
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
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
 *
 * For Samsung Knox / Secure Folder profiles, direct session streaming is blocked
 * with INSTALL_FAILED_USER_RESTRICTED (PersonaServiceHelper blocks INSTALL_FROM_ADB).
 * We handle this by staging to user 0 and cloning via `pm install-existing --user <id>`.
 */
object RootTargetedInstaller {

    /**
     * @param userId target user id, or a negative value to omit `--user` (install for the
     *   default/all-users scope — used for the normal current-user install).
     * @param packageName the app being installed, for per-app installer-source overrides.
     */
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
            val isKnox = isKnoxOrSecureFolder(userId)
            val createArgs = buildCreateArgs(context, packageName, allowDowngrade, abiOverride)
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
                val targetPkg = packageName.ifBlank {
                    stagedFiles.firstNotNullOfOrNull { file ->
                        extractPackageName(context, file)
                    }.orEmpty()
                }

                if (isKnox && userId > 0 && targetPkg.isNotBlank()) {
                    installForKnoxOrRestrictedUser(userId, targetPkg, createArgs, stagedFiles, onProgress)
                } else {
                    try {
                        val sessionId = createSession(userId, createArgs)
                        onProgress(0.1f)

                        stagedFiles.forEachIndexed { idx, file ->
                            writeSplit(sessionId, idx, file)
                            onProgress(0.1f + 0.8f * ((idx + 1).toFloat() / stagedFiles.size))
                        }

                        commitSession(sessionId)
                        onProgress(1f)
                    } catch (e: Exception) {
                        if (userId > 0 && targetPkg.isNotBlank() &&
                            e.message?.contains("INSTALL_FAILED_USER_RESTRICTED", ignoreCase = true) == true
                        ) {
                            Timber.w("Direct session install into user $userId failed with user restriction; falling back to Knox / clone install flow")
                            installForKnoxOrRestrictedUser(userId, targetPkg, createArgs, stagedFiles, onProgress)
                        } else {
                            throw e
                        }
                    }
                }
            } finally {
                stagingDir.deleteRecursively()
            }
        }
    }

    private fun extractPackageName(context: Context, file: File): String? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageArchiveInfo(
                    file.absolutePath,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )?.packageName
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageArchiveInfo(file.absolutePath, 0)?.packageName
            }
        } catch (_: Throwable) {
            null
        }
    }

    private fun isPackageInstalledForUser(userId: Int, packageName: String): Boolean {
        val res = Shell.cmd("pm list packages --user $userId $packageName").exec()
        return res.isSuccess && res.out.any { it.trim() == "package:$packageName" }
    }

    private fun installForKnoxOrRestrictedUser(
        targetUserId: Int,
        packageName: String,
        createArgs: List<String>,
        stagedFiles: List<File>,
        onProgress: (Float) -> Unit,
    ) {
        Timber.i("RootTargetedInstaller: Installing $packageName for restricted/Knox user $targetUserId via user 0 staging")
        val isAlreadyOnUser0 = isPackageInstalledForUser(0, packageName)

        val session0 = createSession(userId = 0, createArgs)
        onProgress(0.1f)
        stagedFiles.forEachIndexed { idx, file ->
            writeSplit(session0, idx, file)
            onProgress(0.1f + 0.7f * ((idx + 1).toFloat() / stagedFiles.size))
        }
        commitSession(session0)
        onProgress(0.85f)

        val installExistingResult = Shell.cmd("pm install-existing --user $targetUserId $packageName").exec()
        val outText = installExistingResult.out.joinToString("\n")
        if (!installExistingResult.isSuccess || !outText.contains("installed for user", ignoreCase = true)) {
            if (!isAlreadyOnUser0) {
                Shell.cmd("pm uninstall --user 0 $packageName").exec()
            }
            throw RuntimeException(
                "pm install-existing --user $targetUserId failed: ${joinErr(installExistingResult)}"
            )
        }
        onProgress(0.95f)

        if (!isAlreadyOnUser0) {
            Timber.d("RootTargetedInstaller: Package $packageName was not on user 0 before; removing from user 0")
            Shell.cmd("pm uninstall --user 0 $packageName").exec()
        }
        onProgress(1f)
    }

    /**
     * Build the `pm install-create` flags from the Root install prefs so the shell path
     * honours the same options as the UI/profiles (replace, downgrade, test, grant-all,
     * bypass-low-target-sdk, installer source) instead of only `-r`.
     */
    private suspend fun buildCreateArgs(
        context: Context,
        packageName: String,
        allowDowngrade: Boolean,
        abiOverride: String?,
    ): List<String> {
        val prefs = runCatching { context.dataStore.data.first() }.getOrNull()
        val args = mutableListOf<String>()
        if (prefs?.get(PreferencesKeys.ROOT_REPLACE_EXISTING) != false) args += "-r" // default ON
        // `allowDowngrade` is this install's consent from the risk dialog; the pref is the
        // standing setting. Either one is enough.
        if (allowDowngrade || prefs?.get(PreferencesKeys.ROOT_REQUEST_DOWNGRADE) == true) args += "-d"
        if (prefs?.get(PreferencesKeys.ROOT_ALLOW_TEST) == true) args += "-t"
        if (prefs?.get(PreferencesKeys.ROOT_GRANT_ALL_PERMISSIONS) == true) args += "-g"
        // --bypass-low-target-sdk-block only exists on Android 14+; older pm rejects it.
        if (prefs?.get(PreferencesKeys.ROOT_BYPASS_LOW_TARGET_SDK) == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) args += "--bypass-low-target-sdk-block"
        if (prefs?.get(PreferencesKeys.ROOT_DONT_KILL_APP) == true) args += "--dont-kill"
        if (prefs?.get(PreferencesKeys.ROOT_DISABLE_VERIFICATION) == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) args += "--skip-verification"
        if (prefs?.get(PreferencesKeys.ROOT_ENABLE_ROLLBACK) == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
        ) args += "--enable-rollback"
        if (prefs?.get(PreferencesKeys.ROOT_REQUEST_UPDATE_OWNERSHIP) == true &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE
        ) args += "--update-ownership"
        val sanitizedAbi = app.pwhs.universalinstaller.presentation.install.util.AbiOverrideUtil.sanitize(abiOverride)
        if (sanitizedAbi != null) args += "--abi $sanitizedAbi"
        if (prefs?.get(PreferencesKeys.ROOT_SET_INSTALL_SOURCE) == true) {
            val override = if (packageName.isNotBlank()) {
                InstallerOverrides.get(prefs[PreferencesKeys.INSTALLER_OVERRIDES], packageName)
            } else null
            val installer = override
                ?: prefs[PreferencesKeys.ROOT_INSTALLER_PACKAGE_NAME]?.trim()?.ifBlank { DEFAULT_INSTALLER_PACKAGE_NAME }
                ?: DEFAULT_INSTALLER_PACKAGE_NAME
            if (installer.isNotBlank()) args += "-i $installer"
        }
        return args
    }

    private fun createSession(userId: Int, createArgs: List<String>): String {
        // `pm install-create [--user <id>] <flags>` → "Success: created install session [<id>]"
        val userArg = if (userId >= 0) "--user $userId " else ""
        val flags = createArgs.joinToString(" ")
        val result = Shell.cmd("pm install-create $userArg$flags".trim()).exec()
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
