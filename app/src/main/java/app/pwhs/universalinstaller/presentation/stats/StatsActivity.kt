package app.pwhs.universalinstaller.presentation.stats

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

class StatsActivity : BaseActivity() {

    private val viewModel: StatsViewModel by viewModel()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val openMilestones = intent?.getBooleanExtra(EXTRA_OPEN_MILESTONES, false) == true
        setContentWithTheme {
            Scaffold(
                bottomBar = { BottomBar(BottomBarItem.Stats) },
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = innerPadding.calculateBottomPadding()),
                ) {
                    StatsScreen(
                        viewModel = viewModel,
                        initialTab = if (openMilestones) 1 else 0,
                    )
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN_MILESTONES = "extra_open_milestones"
    }
}
