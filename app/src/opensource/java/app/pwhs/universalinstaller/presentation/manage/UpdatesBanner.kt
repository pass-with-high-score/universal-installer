package app.pwhs.universalinstaller.presentation.manage

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import app.pwhs.updater.data.repo.AppUpdateRepository
import app.pwhs.updater.presentation.UpdatesActivity
import app.pwhs.updater.presentation.component.UpdatesBannerCard
import app.pwhs.universalinstaller.util.extension.disableSceneTransition
import org.koin.compose.koinInject

@Composable
fun UpdatesBanner(
    modifier: Modifier = Modifier,
) {
    val repository: AppUpdateRepository = koinInject()
    val updateCount by repository.getUpdateCount().collectAsState(initial = 0)
    val context = LocalContext.current

    UpdatesBannerCard(
        updateCount = updateCount,
        onClick = {
            val intent = Intent(context, UpdatesActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_REORDER_TO_FRONT or Intent.FLAG_ACTIVITY_NO_ANIMATION
            }
            context.startActivity(intent)
            (context as? android.app.Activity)?.disableSceneTransition()
        },
        modifier = modifier,
    )
}
