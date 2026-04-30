package app.pwhs.universalinstaller.presentation.install.controller

import android.app.Application
import android.content.pm.PackageManager
import androidx.datastore.preferences.core.edit
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.dataStore
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import rikka.shizuku.Shizuku
import timber.log.Timber

/**
 * One-shot probe at process start: if the user has USE_ROOT or USE_SHIZUKU on but the
 * underlying backend isn't actually usable (root revoked, Shizuku app stopped or
 * permission revoked), flip the pref OFF so the rest of the app — InstallerModeBadge,
 * settings UI, install routing — falls back to the standard system installer instead of
 * surfacing low-level binder/shell errors.
 *
 * The per-install fallback in [InstallViewModel.activeController] still handles the
 * race where the backend dies between this probe and a later install; this just keeps
 * the persisted prefs honest across cold starts.
 */
object BackendSelfHeal {

    @Volatile
    private var done: Boolean = false

    suspend fun runOnce(application: Application, factory: InstallerBackendFactory) {
        if (done) return
        done = true

        val prefs = try {
            application.dataStore.data.first()
        } catch (t: Throwable) {
            Timber.w(t, "BackendSelfHeal: failed to read prefs")
            return
        }

        val useRoot = prefs[PreferencesKeys.USE_ROOT] ?: false
        if (useRoot) {
            val state = try {
                factory.probeRootState()
            } catch (t: Throwable) {
                Timber.w(t, "BackendSelfHeal: root probe threw")
                RootState.UNAVAILABLE
            }
            if (state != RootState.READY) {
                Timber.i("BackendSelfHeal: USE_ROOT on but root probe=$state — disabling pref")
                runCatching {
                    application.dataStore.edit { it[PreferencesKeys.USE_ROOT] = false }
                }
            }
        }

        val useShizuku = prefs[PreferencesKeys.USE_SHIZUKU] ?: false
        if (useShizuku && !awaitShizukuReady()) {
            Timber.i("BackendSelfHeal: USE_SHIZUKU on but Shizuku not ready — disabling pref")
            runCatching {
                application.dataStore.edit { it[PreferencesKeys.USE_SHIZUKU] = false }
            }
        }
    }

    // Shizuku's binder is bound asynchronously after the app starts, so an immediate
    // pingBinder() right at process launch can return false even when Shizuku is healthy.
    // Poll briefly (~2s) to give the binder a chance to land before we conclude it's gone.
    private suspend fun awaitShizukuReady(timeoutMs: Long = 2000L, stepMs: Long = 100L): Boolean {
        var elapsed = 0L
        while (elapsed <= timeoutMs) {
            if (probeShizukuReady()) return true
            delay(stepMs)
            elapsed += stepMs
        }
        return false
    }

    private fun probeShizukuReady(): Boolean = try {
        Shizuku.pingBinder() &&
            !Shizuku.isPreV11() &&
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        Timber.w(t, "BackendSelfHeal: Shizuku probe threw")
        false
    }
}
