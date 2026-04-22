package app.pwhs.universalinstaller.presentation.download

import android.os.Bundle
import app.pwhs.universalinstaller.base.BaseActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class DownloadHistoryActivity : BaseActivity() {

    private val viewModel: DownloadHistoryViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            DownloadHistoryScreen(viewModel = viewModel)
        }
    }
}
