package app.pwhs.universalinstaller.presentation.install.controller

import android.content.Context
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import app.pwhs.universalinstaller.util.HiddenApiHacks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * A manual installer that targets a specific user ID using hidden APIs.
 * Used when Ackpine doesn't support the target user.
 */
object ManualTargetedInstaller {

    suspend fun install(
        context: Context,
        uris: List<Uri>,
        userId: Int,
        overrideInstallerPackageName: String? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val targetedInstaller = HiddenApiHacks.createPackageInstallerForUser(context, userId, overrideInstallerPackageName)
                ?: throw RuntimeException("Failed to get targeted PackageInstaller")

            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            
            val sessionId = targetedInstaller.createSession(params)
            try {
                targetedInstaller.openSession(sessionId).use { session ->
                    uris.forEachIndexed { index, uri ->
                        val name = "apk_$index.apk"
                        val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                            ?: throw RuntimeException("Failed to open Uri: $uri")
                        pfd.use {
                            session.openWrite(name, 0, it.statSize).use { out ->
                                java.io.FileInputStream(it.fileDescriptor).use { input ->
                                    input.copyTo(out)
                                }
                            }
                        }
                        onProgress((index + 1).toFloat() / uris.size)
                    }
                }

                // Commit the session using Shizuku shell.
                // This avoids "Session does not belong to uid" on Android 11,
                // which happens when the app (UID 10xxx) tries to supply a PendingIntent
                // to a session owned by shell (UID 2000).
                val newProcessMethod = rikka.shizuku.Shizuku::class.java.getDeclaredMethod(
                    "newProcess",
                    Array<String>::class.java,
                    Array<String>::class.java,
                    String::class.java
                ).apply { isAccessible = true }
                
                val process = newProcessMethod.invoke(
                    null,
                    arrayOf("pm", "install-commit", sessionId.toString()),
                    null,
                    null
                ) as Process
                process.waitFor()
                
                val output = process.inputStream.bufferedReader().readText()
                val error = process.errorStream.bufferedReader().readText()
                
                if (output.contains("Success", ignoreCase = true)) {
                    return@withContext Result.success(Unit)
                } else {
                    val msg = output.ifBlank { error }.trim()
                    return@withContext Result.failure(RuntimeException(msg.ifBlank { "Installation failed" }))
                }
            } catch (e: Exception) {
                throw e
            }
        }
    }
}
