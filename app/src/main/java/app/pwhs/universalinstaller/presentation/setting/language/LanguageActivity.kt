package app.pwhs.universalinstaller.presentation.setting.language

import android.os.Bundle
import app.pwhs.universalinstaller.base.BaseActivity

class LanguageActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            LanguageScreen()
        }
    }
}
