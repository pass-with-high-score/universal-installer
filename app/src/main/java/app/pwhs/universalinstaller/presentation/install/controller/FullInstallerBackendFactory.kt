package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import app.pwhs.universalinstaller.data.local.InstallHistoryDao
import app.pwhs.universalinstaller.domain.repository.SessionDataRepository
import app.pwhs.universalinstaller.privileged.IPrivilegedService
import app.pwhs.universalinstaller.privileged.PrivilegedRootService
import com.topjohnwu.superuser.Shell
import com.topjohnwu.superuser.ipc.RootService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import ru.solrudev.ackpine.installer.PackageInstaller
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class FullInstallerBackendFactory : InstallerBackendFactory {

    override val rootSupportCompiledIn: Boolean = true

    /**
     * Non-blocking probe — never triggers a SuperUser prompt. First does a cheap
     * filesystem check for an `su` binary on any of the well-known paths; if none
     * exist, we can definitively say NOT_ROOTED without poking libsu (which would
     * otherwise return UNKNOWN forever on non-rooted devices since
     * `Shell.isAppGrantedRoot()` only flips to a real answer after a shell attempt).
     * Then maps libsu's nullable "grant" result to our finer-grained enum:
     *   - null → UNKNOWN (su present but never asked)
     *   - true → READY
     *   - false → DENIED (manager said no)
     */
    override suspend fun probeRootState(): RootState = withContext(Dispatchers.IO) {
        // Don't gate on a hardcoded su-binary path list — modern Magisk/KernelSU don't expose
        // `su` at any of the classic paths, so that heuristic falsely reported NOT_ROOTED on
        // rooted devices and made the whole app skip the Root backend (issue #82). Ask libsu
        // directly: it returns true (granted) / false (denied) / null (not asked yet → UNKNOWN,
        // which the caller resolves with a real requestRoot() shell attempt).
        try {
            when (Shell.isAppGrantedRoot()) {
                true -> RootState.READY
                false -> RootState.DENIED
                null -> RootState.UNKNOWN
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

    override suspend fun forceStopViaRoot(packageName: String): Result<String> =
        runRootShell(packageName, "am force-stop $packageName", successToken = null)

    override suspend fun setEnabledViaRoot(
        packageName: String,
        enabled: Boolean,
    ): Result<String> {
        val cmd = if (enabled) "pm enable $packageName"
            else "pm disable-user --user 0 $packageName"
        val token = if (enabled) "new state: enabled" else "new state: disabled"
        return runRootShell(packageName, cmd, successToken = token)
    }

    override suspend fun clearAppDataViaRoot(packageName: String): Result<String> =
        runRootShell(packageName, "pm clear $packageName", successToken = "Success")

    override suspend fun launchAppViaRoot(packageName: String): Result<String> =
        runRootShell(packageName, "monkey -p $packageName -c android.intent.category.LAUNCHER 1", successToken = null)

    override suspend fun setSystemAppEnabled(packageName: String, enabled: Boolean): Result<String> {
        val cmd = if (enabled) "pm enable $packageName" else "pm disable-user $packageName"
        return runRootShell(packageName, cmd, successToken = null)
    }

    override suspend fun installTargetedViaRoot(
        context: android.content.Context,
        uris: List<android.net.Uri>,
        userId: Int,
        onProgress: (Float) -> Unit,
    ): Result<Unit> = RootTargetedInstaller.install(context, uris, userId, onProgress = onProgress)

    override suspend fun installTargeted(
        context: android.content.Context,
        uris: List<android.net.Uri>,
        userId: Int,
        overrideInstallerPackageName: String?,
        onProgress: (Int) -> Unit
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val targetedInstaller = app.pwhs.universalinstaller.util.HiddenApiHacks.createPackageInstallerForUser(
                context, userId, overrideInstallerPackageName
            ) ?: throw RuntimeException("Failed to get targeted PackageInstaller")

            // Simple session creation and installation for targeted user.
            val params = android.content.pm.PackageInstaller.SessionParams(
                android.content.pm.PackageInstaller.SessionParams.MODE_FULL_INSTALL
            )
            val sessionId = targetedInstaller.createSession(params)
            targetedInstaller.openSession(sessionId).use { session ->
                uris.forEachIndexed { index, uri ->
                    val name = "apk_$index.apk"
                    val pfd = context.contentResolver.openFileDescriptor(uri, "r")
                        ?: throw RuntimeException("Failed to open Uri: $uri")
                    pfd.use { it ->
                        // Use reflection to avoid stub issues
                        val statSize = it.javaClass.getMethod("getStatSize").invoke(it) as Long
                        val fd = it.javaClass.getMethod("getFileDescriptor").invoke(it) as java.io.FileDescriptor
                        
                        session.openWrite(name, 0, statSize).use { out ->
                            java.io.FileInputStream(fd).use { input ->
                                input.copyTo(out)
                            }
                        }
                    }
                    onProgress(((index + 1).toFloat() / uris.size * 100).toInt())
                }
                
                val intent = android.content.Intent("app.pwhs.universalinstaller.INSTALL_STATUS").apply {
                    setPackage(context.packageName)
                }
                val receiver = android.app.PendingIntent.getBroadcast(
                    context, sessionId, intent,
                    android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_MUTABLE
                ) ?: throw RuntimeException("Failed to create PendingIntent")
                
                // Use reflection for getIntentSender to avoid stub issues
                val intentSender = receiver.javaClass.getMethod("getIntentSender").invoke(receiver) as android.content.IntentSender
                session.commit(intentSender)
            }
            "Session $sessionId committed for user $userId"
        }
    }

    // Cached RootService binder so a second toggle doesn't pay the spawn cost again. The
    // mutex guards against two concurrent toggles racing the bind. Cleared lazily on death.
    @Volatile private var privilegedService: IPrivilegedService? = null
    @Volatile private var privilegedConnection: ServiceConnection? = null
    private val bindMutex = Mutex()

    override suspend fun setDefaultInstallerViaRoot(
        context: Context,
        component: ComponentName,
        lock: Boolean,
    ): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val service = obtainPrivilegedService(context.applicationContext as Application)
            service.setDefaultInstaller(component, lock)
        }
    }

    private suspend fun obtainPrivilegedService(application: Application): IPrivilegedService {
        privilegedService?.let { cached ->
            // pingBinder catches the case where the root process was killed since last use.
            if (cached.asBinder().pingBinder()) return cached
            privilegedService = null
            privilegedConnection = null
        }
        return bindMutex.withLock {
            privilegedService?.let { cached ->
                if (cached.asBinder().pingBinder()) return@withLock cached
            }
            withTimeout(15_000) {
                suspendCancellableCoroutine { cont ->
                    val intent = Intent(application, PrivilegedRootService::class.java)
                    val connection = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder) {
                            val svc = IPrivilegedService.Stub.asInterface(binder)
                            privilegedService = svc
                            privilegedConnection = this
                            if (cont.isActive) cont.resume(svc)
                        }
                        override fun onServiceDisconnected(name: ComponentName?) {
                            privilegedService = null
                            privilegedConnection = null
                        }
                    }
                    cont.invokeOnCancellation {
                        runCatching { RootService.unbind(connection) }
                    }
                    android.os.Handler(android.os.Looper.getMainLooper()).post {
                        try {
                            RootService.bind(intent, connection)
                        } catch (t: Throwable) {
                            if (cont.isActive) cont.resumeWithException(t)
                        }
                    }
                }
            }
        }
    }

    private suspend fun runRootShell(
        packageName: String,
        cmd: String,
        successToken: String?,
    ): Result<String> = withContext(Dispatchers.IO) {
        require(packageName.matches(Regex("^[A-Za-z0-9._]+$"))) {
            "Refusing to shell out with suspicious package name: $packageName"
        }
        runCatching {
            val result = Shell.cmd(cmd).exec()
            val stdout = result.out.joinToString("\n")
            val stderr = result.err.joinToString("\n")
            val tokenOk = successToken == null || stdout.contains(successToken, ignoreCase = true)
            if (!result.isSuccess || !tokenOk) {
                throw RuntimeException(
                    "root shell failed: " +
                        stdout.ifBlank { stderr }.ifBlank { "no output" },
                )
            }
            stdout
        }
    }
}
