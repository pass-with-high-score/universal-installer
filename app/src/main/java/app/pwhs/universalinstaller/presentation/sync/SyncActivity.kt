package app.pwhs.universalinstaller.presentation.sync

import android.os.Bundle
import androidx.activity.compose.setContent
import app.pwhs.universalinstaller.base.BaseActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class SyncActivity : BaseActivity() {

    private val viewModel: SyncViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            SyncScreen(viewModel = viewModel)
        }
    }
}
