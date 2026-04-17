package app.pwhs.universalinstaller.presentation.install.controller

import android.content.Context
import android.net.Uri
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.createSession
import ru.solrudev.ackpine.session.ProgressSession
import ru.solrudev.ackpine.session.parameters.Confirmation

class DefaultInstallController(
    context: Context,
    packageInstaller: PackageInstaller,
    sessionDataRepository: SessionDataRepository,
    historyDao: InstallHistoryDao,
) : BaseInstallController(context, packageInstaller, sessionDataRepository, historyDao) {

    override suspend fun createSession(
        uris: List<Uri>,
        name: String,
    ): ProgressSession<InstallFailure> {
        return packageInstaller.createSession(uris) {
            this.name = name
            confirmation = Confirmation.IMMEDIATE
        }
    }
}
