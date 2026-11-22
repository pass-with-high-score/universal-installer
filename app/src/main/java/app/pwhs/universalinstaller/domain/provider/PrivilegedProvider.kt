package app.pwhs.universalinstaller.domain.provider

import android.content.Context
import android.content.pm.PackageManager
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.util.ShizukuServices
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rikka.shizuku.Shizuku
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.resume

enum class PrivilegedExecutor { Root, Shizuku }

interface PrivilegedProvider {
    suspend fun resolveExecutor(requestIfNeeded: Boolean = true): PrivilegedExecutor?
    suspend fun isPrivilegedAvailable(): Boolean
    fun isShizukuReady(): Boolean
}

class PrivilegedProviderImpl(
    private val context: Context,
    private val backendFactory: InstallerBackendFactory
) : PrivilegedProvider {

    override suspend fun resolveExecutor(requestIfNeeded: Boolean): PrivilegedExecutor? {
        val prefs = context.dataStore.data.first()
        val useRoot = prefs[PreferencesKeys.USE_ROOT] ?: false
        val useShizuku = prefs[PreferencesKeys.USE_SHIZUKU] ?: false

        if (useRoot) {
            resolveRoot(requestIfNeeded)?.let { return it }
        }

        if (useShizuku) {
            resolveShizuku(requestIfNeeded)?.let { return it }
        }

        // If user hasn't explicitly toggled Root/Shizuku in Settings yet, still request/use an
        // available privileged backend on demand when they tap a privileged action.
        if (requestIfNeeded) {
            if (!useShizuku) {
                resolveShizuku(requestIfNeeded = true)?.let { return it }
            }
            if (!useRoot && backendFactory.rootSupportCompiledIn) {
                val state = backendFactory.probeRootState()
                if (state == RootState.READY) return PrivilegedExecutor.Root
                if (state == RootState.UNKNOWN && backendFactory.requestRoot() == RootState.READY) {
                    return PrivilegedExecutor.Root
                }
            }
        }

        return null
    }

    private suspend fun resolveRoot(requestIfNeeded: Boolean): PrivilegedExecutor? {
        val state = backendFactory.probeRootState()
        if (state == RootState.READY) return PrivilegedExecutor.Root
        if (requestIfNeeded && (state == RootState.UNKNOWN || state == RootState.DENIED)) {
            if (backendFactory.requestRoot() == RootState.READY) {
                return PrivilegedExecutor.Root
            }
        }
        return null
    }

    private suspend fun resolveShizuku(requestIfNeeded: Boolean): PrivilegedExecutor? {
        if (isShizukuReady()) return PrivilegedExecutor.Shizuku
        if (!requestIfNeeded) return null
        val alive = runCatching { Shizuku.pingBinder() && !Shizuku.isPreV11() }.getOrDefault(false)
        if (!alive) return null
        return if (requestShizukuPermissionAndAwait()) PrivilegedExecutor.Shizuku else null
    }

    private suspend fun requestShizukuPermissionAndAwait(): Boolean {
        if (ShizukuServices.hasShizukuPermission(context)) return true
        val reqCode = shizukuReqCodeSeq.incrementAndGet()
        return withTimeoutOrNull(SHIZUKU_PERMISSION_TIMEOUT_MS) {
            suspendCancellableCoroutine { cont ->
                val listener = object : Shizuku.OnRequestPermissionResultListener {
                    override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
                        if (requestCode != reqCode) return
                        Shizuku.removeRequestPermissionResultListener(this)
                        if (cont.isActive) {
                            cont.resume(grantResult == PackageManager.PERMISSION_GRANTED)
                        }
                    }
                }
                Shizuku.addRequestPermissionResultListener(listener)
                cont.invokeOnCancellation { Shizuku.removeRequestPermissionResultListener(listener) }
                try {
                    Shizuku.requestPermission(reqCode)
                } catch (t: Throwable) {
                    Timber.w(t, "Shizuku.requestPermission failed in PrivilegedProvider")
                    Shizuku.removeRequestPermissionResultListener(listener)
                    if (cont.isActive) cont.resume(false)
                }
            }
        } ?: false
    }

    private companion object {
        private const val SHIZUKU_PERMISSION_TIMEOUT_MS = 30_000L
        private val shizukuReqCodeSeq = AtomicInteger(9200)
    }

    override suspend fun isPrivilegedAvailable(): Boolean {
        val prefs = context.dataStore.data.first()
        val useRoot = prefs[PreferencesKeys.USE_ROOT] ?: false
        val useShizuku = prefs[PreferencesKeys.USE_SHIZUKU] ?: false

        if (useRoot && backendFactory.rootSupportCompiledIn) {
            val state = backendFactory.probeRootState()
            if (state == RootState.READY || state == RootState.UNKNOWN) {
                return true
            }
        }

        if (useShizuku && isShizukuReady()) {
            return true
        }

        return false
    }

    override fun isShizukuReady(): Boolean = try {
        Shizuku.pingBinder() &&
            !Shizuku.isPreV11() &&
            ShizukuServices.hasShizukuPermission(context)
    } catch (e: Exception) {
        Timber.w(e, "Shizuku readiness probe failed")
        false
    }
}
