package app.pwhs.universalinstaller.presentation.setting.virustotal

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.pwhs.universalinstaller.base.BaseActivity

class VirusTotalSettingActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            Box(modifier = Modifier.fillMaxSize()) {
                VirusTotalSettingScreen(onBack = ::finish)
            }
        }
    }
}
