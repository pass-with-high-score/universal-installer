package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import ru.solrudev.ackpine.installer.PackageInstaller

/**
 * Play-store build: libsu is not on the classpath, so every Root entry point degrades
 * to a no-op. The Root settings section is hidden by [rootSupportCompiledIn] `== false`.
 */
class StoreInstallerBackendFactory : InstallerBackendFactory {

    override val rootSupportCompiledIn: Boolean = false

    override suspend fun probeRootState(): RootState = RootState.UNAVAILABLE

    override suspend fun requestRoot(): RootState = RootState.UNAVAILABLE

    override suspend fun resetCachedShell() { /* nothing to reset */ }

    override fun createRootController(
        application: Application,
        packageInstaller: PackageInstaller,
        sessionDataRepository: SessionDataRepository,
        historyDao: InstallHistoryDao,
    ): BaseInstallController? = null

    override suspend fun uninstallSystemAppViaRoot(
        packageName: String,
        method: SystemAppMethod,
    ): Result<String> = Result.failure(
        UnsupportedOperationException("Root not available in this build"),
    )

    override suspend fun forceStopViaRoot(packageName: String): Result<String> =
        Result.failure(UnsupportedOperationException("Root not available in this build"))

    override suspend fun setEnabledViaRoot(
        packageName: String,
        enabled: Boolean,
    ): Result<String> = Result.failure(
        UnsupportedOperationException("Root not available in this build"),
    )
}
