package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import ru.solrudev.ackpine.installer.PackageInstaller

/**
 * Entry point for Root-backed installs. Originally there were two implementations behind
 * this interface (a no-op store one and a real full one) so the Play build wouldn't ship
 * libsu. That split was retired — we keep the interface because the seam is convenient
 * for tests and to confine libsu-touching code to one class.
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

    /** `am force-stop <pkg>` via root shell. Store flavor returns failure. */
    suspend fun forceStopViaRoot(packageName: String): Result<String>

    /**
     * `pm enable` or `pm disable-user --user 0 <pkg>` via root shell. Store flavor returns
     * failure.
     */
    suspend fun setEnabledViaRoot(packageName: String, enabled: Boolean): Result<String>

    /** `pm clear <pkg>` via root shell — wipes cache + data + obb. Store flavor → failure. */
    suspend fun clearAppDataViaRoot(packageName: String): Result<String>

    /**
     * Enables or disables a system app via root shell.
     * Store flavor always returns failure.
     */
    suspend fun setSystemAppEnabled(packageName: String, enabled: Boolean): Result<String>

    /**
     * Installs one or more APKs (uris) for a specific user ID.
     * Implementations should use elevated privileges (Root/Shizuku) to bypass
     * PackageInstaller limitations.
     */
    suspend fun installTargeted(
        context: android.content.Context,
        uris: List<android.net.Uri>,
        userId: Int,
        onProgress: (Int) -> Unit = {}
    ): Result<String>

    /**
     * Install [uris] into [userId] via libsu — `pm install-create --user N` →
     * `pm install-write …` → `pm install-commit`. Used when the user picked a
     * specific work profile and Shizuku isn't available.
     * Store flavor returns failure.
     */
    suspend fun installTargetedViaRoot(
        context: android.content.Context,
        uris: List<android.net.Uri>,
        userId: Int,
        onProgress: (Float) -> Unit = {},
    ): Result<Unit>

    /**
     * Toggles preferred-activity registration via libsu RootService so [component] becomes
     * the default APK installer. lock=false clears our own preferred activities, restoring
     * the chooser. Store flavor returns failure — Shizuku path is handled separately by
     * [app.pwhs.universalinstaller.util.ShizukuDefaultInstaller].
     */
    suspend fun setDefaultInstallerViaRoot(
        context: android.content.Context,
        component: android.content.ComponentName,
        lock: Boolean,
    ): Result<Unit>
}

enum class SystemAppMethod {
    /** `pm uninstall --user 0 <pkg>` — hides package for user 0; reversible via `cmd package install-existing`. */
    UninstallForUser0,

    /** `pm disable-user --user 0 <pkg>` — freezes package; reversible via `pm enable`. */
    Disable,
}
