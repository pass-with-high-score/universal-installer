package app.pwhs.universalinstaller.presentation.setting.profile

import android.os.Bundle
import app.pwhs.universalinstaller.base.BaseActivity

class ProfileActivity : BaseActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentWithTheme {
            ProfileScreen()
        }
    }
}
