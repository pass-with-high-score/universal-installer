package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.installer.PackageInstaller
import timber.log.Timber

class FullInstallerBackendFactory : InstallerBackendFactory {

    override val rootSupportCompiledIn: Boolean = true

    /**
     * Non-blocking probe — never triggers a SuperUser prompt. Maps libsu's nullable
     * "grant" result to our finer-grained enum. Returns UNKNOWN when nothing has asked
     * for a shell yet so the UI can show "Tap to check" instead of a misleading error.
     */
    override suspend fun probeRootState(): RootState = withContext(Dispatchers.IO) {
        try {
            when (Shell.isAppGrantedRoot()) {
                null -> RootState.UNKNOWN
                true -> RootState.READY
                false -> RootState.DENIED
            }
        } catch (t: Throwable) {
            Timber.w(t, "probeRootState failed")
            RootState.UNKNOWN
        }
    }

    /**
     * Blocking — may surface the Magisk/KernelSU prompt. Caller must confine this to a
     * coroutine on IO; never run from the main thread or the UI will jank while the
     * manager animates its bottom sheet.
     */
    override suspend fun requestRoot(): RootState = withContext(Dispatchers.IO) {
        try {
            val shell = Shell.getShell()
            if (shell.isRoot) RootState.READY else RootState.NOT_ROOTED
        } catch (t: Throwable) {
            Timber.w(t, "Shell.getShell() failed — likely no root manager / su binary")
            RootState.NOT_ROOTED
        }
    }

    /**
     * Called when the user taps "Retry" after being DENIED. Closing the cached shell
     * forces the next getShell() to re-prompt the manager, which is how users who just
     * granted access in Magisk recover without restarting the app.
     */
    override suspend fun resetCachedShell() {
        withContext(Dispatchers.IO) {
            try {
                Shell.getCachedShell()?.close()
            } catch (t: Throwable) {
                Timber.w(t, "Failed to close cached shell")
            }
        }
    }

    override fun createRootController(
        application: Application,
        packageInstaller: PackageInstaller,
        sessionDataRepository: SessionDataRepository,
        historyDao: InstallHistoryDao,
    ): BaseInstallController = RootInstallController(
        application, packageInstaller, sessionDataRepository, historyDao,
    )

    /**
     * Shells out to `pm` directly. The quoting is trivial because package names match
     * `[A-Za-z0-9._]+` — no shell metacharacters can appear. We still verify before
     * passing to make refactors obvious if that assumption changes.
     */
    override suspend fun uninstallSystemAppViaRoot(
        packageName: String,
        method: SystemAppMethod,
    ): Result<String> = withContext(Dispatchers.IO) {
        require(packageName.matches(Regex("^[A-Za-z0-9._]+$"))) {
            "Refusing to shell out with suspicious package name: $packageName"
        }
        val cmd = when (method) {
            SystemAppMethod.UninstallForUser0 -> "pm uninstall --user 0 $packageName"
            SystemAppMethod.Disable -> "pm disable-user --user 0 $packageName"
        }
        runCatching {
            val result = Shell.cmd(cmd).exec()
            val stdout = result.out.joinToString("\n")
            val stderr = result.err.joinToString("\n")
            // `pm` returns non-zero on real failures AND prints a distinctive success string
            // per subcommand. We verify both because some ROMs return 0 for soft failures
            // like `Failure [NOT_INSTALLED_FOR_USER]`. The string token differs per command:
            //   `pm uninstall --user 0` → "Success"
            //   `pm disable-user --user 0` → "new state: disabled-user"
            val successToken = when (method) {
                SystemAppMethod.UninstallForUser0 -> "Success"
                SystemAppMethod.Disable -> "new state: disabled"
            }
            if (!result.isSuccess || !stdout.contains(successToken, ignoreCase = true)) {
                throw RuntimeException(
                    "pm command failed: ${stdout.ifBlank { stderr }.ifBlank { "no output" }}",
                )
            }
            stdout
        }
    }
}
