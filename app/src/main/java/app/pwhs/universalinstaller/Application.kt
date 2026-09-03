package app.pwhs.universalinstaller

import android.app.Application
import android.os.Build
import coil3.ImageLoader
import coil3.SingletonImageLoader
import org.lsposed.hiddenapibypass.HiddenApiBypass
import app.pwhs.universalinstaller.di.appModule
import app.pwhs.universalinstaller.di.flavorModule
import app.pwhs.universalinstaller.presentation.install.controller.BackendSelfHeal
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.core.data.local.SharedPrefsKeys
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.review.AppReview
import app.pwhs.universalinstaller.review.ReviewGate
import app.pwhs.universalinstaller.review.createReviewPrompter
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.createTelemetrySink
import app.pwhs.core.ui.ApkFileIconFetcher
import app.pwhs.universalinstaller.util.AppIconFetcher
import app.pwhs.universalinstaller.util.CrashHandler
import com.topjohnwu.superuser.Shell
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import android.util.Log
import timber.log.Timber

/**
 * Forwards WARN and above to logcat in release builds.
 *
 * Deliberately not everything: `Timber.d` carries URIs and file names that are fine on a
 * developer's machine but end up in a diagnostics report the user may paste into a public issue.
 * Warnings and errors are what makes a report actionable, and they are written to be safe to
 * share.
 */
private class ReleaseTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        val resolvedTag = tag ?: "UniversalInstaller"
        if (t != null) Log.println(priority, resolvedTag, "$message\n${Log.getStackTraceString(t)}")
        else Log.println(priority, resolvedTag, message)
    }
}

/**
 * Feeds the same warnings and errors [ReleaseTree] writes to logcat into the crash reporter,
 * so a report arrives with the run-up to the failure attached rather than a bare stack trace.
 *
 * A no-op on `opensource`, where [Telemetry] has no sink. The WARN floor is the same one
 * [ReleaseTree] uses and for the same reason: debug-level lines carry URIs and file names.
 */
private class TelemetryTree : Timber.Tree() {
    override fun isLoggable(tag: String?, priority: Int): Boolean = priority >= Log.WARN

    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        Telemetry.breadcrumb("${tag ?: "UniversalInstaller"}: $message")
        // Timber.e(throwable) is how this codebase reports a failure it recovered from. Those
        // are exactly the non-fatals worth seeing; warnings stay breadcrumbs so a device that
        // warns in a loop can't drown out the crash reports.
        if (priority >= Log.ERROR && t != null) Telemetry.recordException(t)
    }
}

class App : Application(), SingletonImageLoader.Factory {

    init {
        // Exempt hidden API restrictions for the entire process so reflection and hidden AIDL stubs
        // (like IPackageInstaller$Stub, IPackageManager$Stub used by Shizuku and Ackpine) work across all Android versions.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            try {
                HiddenApiBypass.addHiddenApiExemptions("")
            } catch (t: Throwable) {
                // Ignore if platform denies exemption
            }
        }

        // libsu setup MUST run before the first Shell.getShell() (ackpine's libsu plugin and
        // our RootServices both rely on it). A companion init runs at class-load, before
        // onCreate and any shell use. MOUNT_MASTER so install/uninstall changes apply in the
        // global mount namespace; without an explicit builder libsu's first shell could be
        // created non-root and cached, making root installs silently fall back to the system
        // PackageInstaller (the "shows a confirm dialog like PackageInstaller" bug). #82
        Shell.enableVerboseLogging = BuildConfig.DEBUG
        Shell.setDefaultBuilder(
            Shell.Builder.create()
                .setFlags(Shell.FLAG_MOUNT_MASTER)
                .setTimeout(10),
        )
    }

    private val backendFactory: InstallerBackendFactory by inject()

    override fun onCreate() {
        super.onCreate()
        // Before CrashHandler.install: that one chains to whatever handler is already default,
        // which on `play` is Crashlytics'. Binding the sink first also means a crash during the
        // rest of onCreate is still reported.
        Telemetry.install(createTelemetrySink(this))
        AppReview.install(createReviewPrompter(this))
        CrashHandler.install(this)
        // Release builds used to plant nothing, so Settings -> Diagnostics collected a logcat
        // dump containing not one line from this app. Issues #92 and #100 both arrived with a
        // full report attached and no clue in it. Release now keeps warnings and errors — the
        // lines that explain a failure — while debug keeps everything.
        Timber.plant(if (BuildConfig.DEBUG) Timber.DebugTree() else ReleaseTree())
        if (Telemetry.isCollecting) Timber.plant(TelemetryTree())
        startKoin{
            androidLogger()
            androidContext(this@App)
            modules(appModule, flavorModule)
        }
        // Self-heal stale install-method prefs (Root revoked, Shizuku not running). Runs
        // once per process on a background dispatcher; never blocks app start.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            applyTelemetryPreference()
            // Stamps the install date on first run — the review gate refuses to ask
            // anyone who has had the app for less than a few days.
            ReviewGate.rememberFirstLaunch(this@App)
            BackendSelfHeal.runOnce(this@App, backendFactory)
        }
    }

    /**
     * Hands the user's choice to the reporting sink at every start.
     *
     * Firebase remembers the flag on its own, so this is not what makes the setting stick — it
     * is what makes the *preference* authoritative, including after a restore onto a new device
     * where the preference travelled but Firebase's own state did not. Absent means on.
     *
     * Runs before [BackendSelfHeal] in the same coroutine deliberately: self-heal is the first
     * thing that reports, and it must not report on a build the user opted out of.
     */
    private suspend fun applyTelemetryPreference() {
        if (!Telemetry.isCollecting) return
        val enabled = runCatching { dataStore.data.first()[SharedPrefsKeys.ANALYTICS_ENABLED] }
            .onFailure { Timber.w(it, "Could not read the analytics preference; leaving it on") }
            .getOrNull() ?: true
        Telemetry.setCollectionEnabled(enabled)
    }

    override fun newImageLoader(context: android.content.Context): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(AppIconFetcher.Factory(context))
                add(ApkFileIconFetcher.Factory(context))
            }
            .build()
    }
}