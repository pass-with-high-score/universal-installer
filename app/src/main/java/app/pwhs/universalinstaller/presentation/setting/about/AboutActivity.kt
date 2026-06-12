package app.pwhs.universalinstaller.presentation.setting.about

import android.os.Bundle
import app.pwhs.universalinstaller.base.BaseActivity

class AboutActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            AboutScreen()
        }
    }
}
