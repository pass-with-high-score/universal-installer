package app.pwhs.universalinstaller.presentation.setting

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import app.pwhs.universalinstaller.base.BaseActivity
import app.pwhs.universalinstaller.presentation.composable.AdaptiveNavScaffold
import app.pwhs.universalinstaller.presentation.composable.BottomBarItem
import org.koin.androidx.viewmodel.ext.android.viewModel

class SettingActivity : BaseActivity() {

    private val viewModel: SettingViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            AdaptiveNavScaffold(currentTab = BottomBarItem.Settings) {
                SettingScreen(viewModel = viewModel)
            }
        }
    }
}
