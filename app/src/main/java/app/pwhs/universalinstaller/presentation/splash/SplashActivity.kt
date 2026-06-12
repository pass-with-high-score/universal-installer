package app.pwhs.universalinstaller.presentation.splash

import android.content.Intent
import android.os.Bundle
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import app.pwhs.universalinstaller.MainActivity
import app.pwhs.universalinstaller.base.BaseActivity
import app.pwhs.universalinstaller.util.extension.disableSceneTransition

/**
 * Launcher entry point. Owns the system splash (via core-splashscreen) plus the in-app
 * Compose splash, reads onboarding state, then hands off to MainActivity for the actual
 * routing decision (install / sync / manage).
 *
 * Shortcuts (universalinstaller://sync, ://uninstall) target MainActivity directly via
 * explicit ComponentName, so they bypass this Activity entirely.
 */
class SplashActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        // Must run before super.onCreate so the system splash window is in place
        // before the Activity attaches its content — otherwise we'd flash the Material
        // default grey before the first Compose frame on slower devices.
        installSplashScreen()
        super.onCreate(savedInstanceState)

        setContentWithTheme {
            SplashScreen(
                onNavigateToOnboarding = { navigate(showOnboarding = true) },
                onNavigateToMain = { navigate(showOnboarding = false) },
            )
        }
    }

    private fun navigate(showOnboarding: Boolean) {
        startActivity(
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NO_ANIMATION
                putExtra(EXTRA_SHOW_ONBOARDING, showOnboarding)
            },
        )
        disableSceneTransition()
        finish()
    }

    companion object {
        const val EXTRA_SHOW_ONBOARDING = "show_onboarding"
    }
}
