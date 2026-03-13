package com.nqmgaming.universalinstaller.domain.installer

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.createSession
import ru.solrudev.ackpine.session.Session
import ru.solrudev.ackpine.session.await
import ru.solrudev.ackpine.session.parameters.Confirmation
import ru.solrudev.ackpine.session.progress
import ru.solrudev.ackpine.session.state
import ru.solrudev.ackpine.shizuku.useShizuku
import java.util.UUID

class StandardInstaller(
    private val context: Context,
    private val packageInstaller: PackageInstaller
) : AppInstaller {

    override suspend fun install(uris: List<Uri>, options: InstallOptions): Flow<InstallState> = channelFlow {
        try {
            trySend(InstallState.Processing)

            val sessionName = "InstallSession_${UUID.randomUUID().toString().substring(0, 8)}"

            val session = packageInstaller.createSession(uris) {
                name = sessionName
                confirmation = Confirmation.IMMEDIATE
                if (options.method == InstallMethod.SHIZUKU) {
                    useShizuku()
                }
            }

            session.progress
                .onEach { progress ->
                    val percentage = (progress.progress.toFloat() / progress.max * 100).toInt()
                    trySend(InstallState.Progress(percentage))
                }
                .launchIn(this)

            session.state
                .onEach { state ->
                    when (state) {
                        is Session.State.Succeeded -> {
                            trySend(InstallState.Success)
                            close()
                        }
                        is Session.State.Failed -> {
                            val failureObj = state.failure
                            val message = failureObj.message ?: "Unknown installation failure."
                            trySend(InstallState.Error(message, Exception(message)))
                            close()
                        }
                        is Session.State.Cancelled -> {
                            trySend(InstallState.Error("Installation was cancelled."))
                            close()
                        }
                        else -> {}
                    }
                }
                .launchIn(this)

            session.await()

        } catch (e: Exception) {
            trySend(InstallState.Error(e.message ?: "Exception occurred during installation", e))
            close()
        }
    }
}
