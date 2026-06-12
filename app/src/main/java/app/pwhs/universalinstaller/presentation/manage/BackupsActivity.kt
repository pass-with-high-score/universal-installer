package app.pwhs.universalinstaller.presentation.manage

import android.os.Bundle
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import app.pwhs.universalinstaller.base.BaseActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class BackupsActivity : BaseActivity() {

    private val viewModel: BackupsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            BackupsScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                onBack = { finish() },
            )
        }
    }
}
