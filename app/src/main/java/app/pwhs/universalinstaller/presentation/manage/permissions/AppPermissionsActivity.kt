package app.pwhs.universalinstaller.presentation.manage.permissions

import android.os.Bundle
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import app.pwhs.universalinstaller.base.BaseActivity
import org.koin.androidx.viewmodel.ext.android.viewModel

class AppPermissionsActivity : BaseActivity() {

    private val viewModel: AppPermissionsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        if (pkg.isNullOrBlank()) {
            finish()
            return
        }
        setContentWithTheme {
            LaunchedEffect(pkg) { viewModel.load(pkg) }
            // Drain back-press through the Activity directly so the BaseActivity exit
            // animation runs the same as every other sub-screen in the app.
            val activity = LocalActivity.current
            AppPermissionsScreen(
                viewModel = viewModel,
                modifier = Modifier.fillMaxSize(),
                onBack = { activity?.finish() },
            )
        }
    }

    companion object {
        const val EXTRA_PACKAGE_NAME = "package_name"
    }
}
