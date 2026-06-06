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
import ru.solrudev.ackpine.session.ProgressSession
import ru.solrudev.ackpine.session.parameters.Confirmation
import ru.solrudev.ackpine.shizuku.shizuku

class ShizukuInstallController(
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
            shizuku {
                bypassLowTargetSdkBlock = prefs[PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK] ?: false
                allowTest = prefs[PreferencesKeys.SHIZUKU_ALLOW_TEST] ?: false
                // Default ON to match the UI (Settings/dialog show this as `?: true`). ackpine only
                // ORs INSTALL_REPLACE_EXISTING into the session when this is true; with it false a
                // privileged upgrade of an existing package fails with INSTALL_FAILED_ALREADY_EXISTS
                // (the "package conflict" error). An explicit user choice is still honored — `?:`
                // only fills the never-set case.
                replaceExisting = prefs[PreferencesKeys.SHIZUKU_REPLACE_EXISTING] ?: true
                requestDowngrade = prefs[PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE] ?: false
                grantAllRequestedPermissions = prefs[PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS] ?: false
                allUsers = prefs[PreferencesKeys.SHIZUKU_ALL_USERS] ?: false
                // Spoof installer package (shows in PackageManager.getInstallerPackageName).
                // Applied only when user opted in; otherwise ackpine defaults to this app.
                // Under-the-hood API requires Android 9+ — silently ignored on older devices.
                if (prefs[PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE] == true) {
                    val overrides = prefs[PreferencesKeys.INSTALLER_OVERRIDES]
                    val override = if (packageName.isNotBlank()) {
                        InstallerOverrides.get(overrides, packageName)
                    } else null
                    
                    installerPackageName = override
                        ?: prefs[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME]
                        ?.trim()
                        ?.ifBlank { DEFAULT_INSTALLER_PACKAGE_NAME }
                        ?: DEFAULT_INSTALLER_PACKAGE_NAME
                }
            }
        }
    }
}
