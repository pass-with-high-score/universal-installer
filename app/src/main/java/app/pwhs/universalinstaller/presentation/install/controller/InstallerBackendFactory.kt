package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import ru.solrudev.ackpine.installer.PackageInstaller

/**
 * Flavor-bound entry point for Root-backed installs. The `store` flavor returns a no-op
 * implementation so neither the libsu classes nor its native `.so` end up in the APK that
 * ships to Google Play. The `full` flavor returns the real RootInstallController.
 *
 * We keep every touchpoint that mentions the Root controller behind this interface so no
 * `main` code needs conditional `BuildConfig.FLAVOR` checks.
 */
interface InstallerBackendFactory {

    /** Whether this build was compiled with libsu — drives UI gating. */
    val rootSupportCompiledIn: Boolean

    /** Cheap, non-blocking probe. Returns [RootState.UNAVAILABLE] on `store`. */
    suspend fun probeRootState(): RootState

    /**
     * Blocking call that may surface a superuser prompt on `full`. Never called from the
     * main thread; on `store` returns [RootState.UNAVAILABLE] immediately.
     */
    suspend fun requestRoot(): RootState

    /**
     * Tear down the cached shell so the next [requestRoot] reopens it. Used when the user
     * retries after a DENIED state (they just granted access in Magisk/KernelSU Manager).
     */
    suspend fun resetCachedShell()

    /**
     * Returns a controller that drives installs via libsu, or `null` on `store`.
     */
    fun createRootController(
        application: Application,
        packageInstaller: PackageInstaller,
        sessionDataRepository: SessionDataRepository,
        historyDao: InstallHistoryDao,
    ): BaseInstallController?

    /**
     * Shell-out wrapper for system-app removal. Ackpine's libsu plugin routes through
     * `IPackageInstaller.uninstall` which rejects system apps with DELETE_FAILED_INTERNAL_ERROR,
     * so for `/system` packages we bypass ackpine and call `pm` directly via the root shell.
     *
     * Safe to call only when [probeRootState] returned [RootState.READY]; otherwise this
     * returns `Result.failure`. Store flavor always returns failure.
     */
    suspend fun uninstallSystemAppViaRoot(
        packageName: String,
        method: SystemAppMethod,
    ): Result<String>
}

enum class SystemAppMethod {
    /** `pm uninstall --user 0 <pkg>` — hides package for user 0; reversible via `cmd package install-existing`. */
    UninstallForUser0,

    /** `pm disable-user --user 0 <pkg>` — freezes package; reversible via `pm enable`. */
    Disable,
}
