package app.pwhs.universalinstaller.presentation.install.controller

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import app.pwhs.universalinstaller.util.HiddenApiHacks
import kotlinx.coroutines.CompletableDeferred
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
        onProgress: (Float) -> Unit = {}
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val targetedInstaller = HiddenApiHacks.createPackageInstallerForUser(context, userId)
                ?: throw RuntimeException("Failed to get targeted PackageInstaller")

            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            
            val sessionId = targetedInstaller.createSession(params)
            val action = "app.pwhs.universalinstaller.INSTALL_STATUS_$sessionId"
            val resultDeferred = CompletableDeferred<Result<Unit>>()
            
            val receiver = object : BroadcastReceiver() {
                override fun onReceive(ctx: Context, intent: Intent) {
                    val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                    if (status == PackageInstaller.STATUS_SUCCESS) {
                        resultDeferred.complete(Result.success(Unit))
                    } else {
                        val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
                        resultDeferred.complete(Result.failure(RuntimeException(message ?: "Installation failed (status $status)")))
                    }
                    ctx.unregisterReceiver(this)
                }
            }
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.registerReceiver(receiver, IntentFilter(action), Context.RECEIVER_NOT_EXPORTED)
            } else {
                context.registerReceiver(receiver, IntentFilter(action))
            }

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
                    
                    val intent = Intent(action)
                    intent.setPackage(context.packageName)
                    val pendingIntent = android.app.PendingIntent.getBroadcast(
                        context, sessionId, intent,
                        android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                    )
                    session.commit(pendingIntent.intentSender)
                }
                resultDeferred.await().getOrThrow()
            } catch (e: Exception) {
                try {
                    context.unregisterReceiver(receiver)
                } catch (_: Exception) {}
                throw e
            }
        }
    }
}
