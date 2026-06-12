package app.pwhs.universalinstaller.presentation.setting.diagnostics

import android.os.Bundle
import app.pwhs.universalinstaller.base.BaseActivity

class DiagnosticsActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            DiagnosticsScreen()
        }
    }
}
