package app.pwhs.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import app.pwhs.core.data.AppRepository
import app.pwhs.tv.ui.theme.UniversalInstallerTheme
import kotlinx.coroutines.delay

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Start the LAN receiver so a phone can push APKs to this TV.
        ReceiverService.start(applicationContext)
        val repo = AppRepository(applicationContext)
        setContent {
            UniversalInstallerTheme {
                var showSplash by remember { mutableStateOf(true) }
                LaunchedEffect(Unit) {
                    delay(1800)
                    showSplash = false
                }
                TvApp(repo = repo)
                AnimatedVisibility(visible = showSplash, enter = fadeIn(), exit = fadeOut()) {
                    SplashScreen()
                }
            }
        }
    }
}
