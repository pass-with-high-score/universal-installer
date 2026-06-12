package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import android.net.Uri
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.presentation.install.dialog.InstallerOverrides
import app.pwhs.universalinstaller.presentation.setting.DEFAULT_INSTALLER_PACKAGE_NAME
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.dataStore
import kotlinx.coroutines.flow.first
import ru.solrudev.ackpine.DelicateAckpineApi
import ru.solrudev.ackpine.installer.InstallFailure
import ru.solrudev.ackpine.installer.PackageInstaller
import ru.solrudev.ackpine.installer.createSession
import ru.solrudev.ackpine.libsu.libsu
import ru.solrudev.ackpine.session.ProgressSession
import ru.solrudev.ackpine.session.parameters.Confirmation

/**
 * Root-backed installer built on ackpine's libsu plugin. Only compiled into the `full`
 * flavor — `store` uses the no-op factory instead.
 *
 * Flags mirror [ShizukuInstallController] so users carry the same mental model between
 * the two privileged backends; separate DataStore keys keep the two toggleable independently.
 */
class RootInstallController(
    private val application: Application,
    packageInstaller: PackageInstaller,
    sessionDataRepository: SessionDataRepository,
    historyDao: InstallHistoryDao,
) : BaseInstallController(application, packageInstaller, sessionDataRepository, historyDao) {

    @OptIn(DelicateAckpineApi::class)
    override suspend fun createSession(
        uris: List<Uri>,
        name: String,
        packageName: String,
    ): ProgressSession<InstallFailure> {
        val prefs = application.dataStore.data.first()
        
        return packageInstaller.createSession(uris) {
            this.name = name
            confirmation = Confirmation.IMMEDIATE
            libsu {
                bypassLowTargetSdkBlock = prefs[PreferencesKeys.ROOT_BYPASS_LOW_TARGET_SDK] ?: false
                allowTest = prefs[PreferencesKeys.ROOT_ALLOW_TEST] ?: false
                // Default ON to match the UI (`?: true` in Settings/dialog). ackpine only sets
                // INSTALL_REPLACE_EXISTING when true; false makes an upgrade of an existing package
                // fail with INSTALL_FAILED_ALREADY_EXISTS. Explicit user choice still honored.
                replaceExisting = prefs[PreferencesKeys.ROOT_REPLACE_EXISTING] ?: true
                requestDowngrade = prefs[PreferencesKeys.ROOT_REQUEST_DOWNGRADE] ?: false
                grantAllRequestedPermissions = prefs[PreferencesKeys.ROOT_GRANT_ALL_PERMISSIONS] ?: false
                allUsers = prefs[PreferencesKeys.ROOT_ALL_USERS] ?: false
                if (prefs[PreferencesKeys.ROOT_SET_INSTALL_SOURCE] == true) {
                    val overrides = prefs[PreferencesKeys.INSTALLER_OVERRIDES]
                    val override = if (packageName.isNotBlank()) {
                        InstallerOverrides.get(overrides, packageName)
                    } else null
                    
                    installerPackageName = override
                        ?: prefs[PreferencesKeys.ROOT_INSTALLER_PACKAGE_NAME]
                        ?.trim()
                        ?.ifBlank { DEFAULT_INSTALLER_PACKAGE_NAME }
                        ?: DEFAULT_INSTALLER_PACKAGE_NAME
                }
            }
        }
    }
}
