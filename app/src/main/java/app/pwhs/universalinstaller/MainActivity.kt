package app.pwhs.universalinstaller

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pwhs.universalinstaller.app.UniversalInstallerApp
import app.pwhs.universalinstaller.presentation.onboarding.OnboardingScreen
import app.pwhs.universalinstaller.presentation.setting.ThemeMode
import app.pwhs.universalinstaller.presentation.setting.dataStore
import app.pwhs.universalinstaller.presentation.splash.SplashScreen
import app.pwhs.universalinstaller.ui.theme.UniversalInstallerTheme
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.map

private enum class AppRoute { Splash, Onboarding, Main }

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val themeModeFlow = dataStore.data.map { prefs ->
                val name = prefs[stringPreferencesKey("theme_mode")] ?: ThemeMode.System.name
                ThemeMode.entries.find { it.name == name } ?: ThemeMode.System
            }
            val themeMode by themeModeFlow.collectAsState(initial = ThemeMode.System)

            val darkTheme = when (themeMode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }

            var currentRoute by remember { mutableStateOf(AppRoute.Splash) }

            UniversalInstallerTheme(darkTheme = darkTheme) {
                when (currentRoute) {
                    AppRoute.Splash -> SplashScreen(
                        onNavigateToOnboarding = { currentRoute = AppRoute.Onboarding },
                        onNavigateToMain = { currentRoute = AppRoute.Main },
                    )
                    AppRoute.Onboarding -> OnboardingScreen(
                        onFinish = { currentRoute = AppRoute.Main },
                    )
                    AppRoute.Main -> UniversalInstallerApp()
                }
            }
        }
    }
}
