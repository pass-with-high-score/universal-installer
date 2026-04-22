package app.pwhs.universalinstaller.presentation.uninstall

import android.os.Bundle
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import app.pwhs.universalinstaller.base.BaseActivity
import app.pwhs.universalinstaller.presentation.composable.BottomBar
import app.pwhs.universalinstaller.presentation.composable.BottomBarItem
import org.koin.androidx.viewmodel.ext.android.viewModel

class UninstallActivity : BaseActivity() {

    private val viewModel: UninstallViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            Scaffold(
                bottomBar = { BottomBar(BottomBarItem.Uninstall) }
            ) { innerPadding ->
                Box(modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())) {
                    UninstallScreen(viewModel = viewModel)
                }
            }
        }
    }
}
