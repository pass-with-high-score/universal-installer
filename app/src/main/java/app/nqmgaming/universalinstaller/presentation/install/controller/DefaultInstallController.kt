package app.nqmgaming.universalinstaller.presentation.install.controller

import android.net.Uri
import com.pwhs.universalinstaller.data.local.InstallHistoryDao
import com.pwhs.universalinstaller.domain.repository.SessionDataRepository
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.createSession
import ru.solrudev.ackpine.session.ProgressSession
import ru.solrudev.ackpine.session.parameters.Confirmation

class DefaultInstallController(
    packageInstaller: PackageInstaller,
    sessionDataRepository: SessionDataRepository,
    historyDao: InstallHistoryDao,
) : BaseInstallController(packageInstaller, sessionDataRepository, historyDao) {

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
