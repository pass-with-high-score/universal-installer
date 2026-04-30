package app.pwhs.universalinstaller

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import app.pwhs.universalinstaller.di.appModule
import app.pwhs.universalinstaller.di.flavorModule
import app.pwhs.universalinstaller.presentation.install.controller.BackendSelfHeal
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.util.ApkFileIconFetcher
import app.pwhs.universalinstaller.util.AppIconFetcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.GlobalContext.startKoin
import timber.log.Timber

class App : Application(), SingletonImageLoader.Factory {

    private val backendFactory: InstallerBackendFactory by inject()

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }
        startKoin{
            androidLogger()
            androidContext(this@App)
            modules(appModule, flavorModule)
        }
        // Self-heal stale install-method prefs (Root revoked, Shizuku not running). Runs
        // once per process on a background dispatcher; never blocks app start.
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            BackendSelfHeal.runOnce(this@App, backendFactory)
        }
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