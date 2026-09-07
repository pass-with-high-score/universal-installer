package app.pwhs.tv.presentation.updates

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import app.pwhs.updater.data.repo.AppUpdateRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import org.koin.java.KoinJavaComponent

object TvUpdatesProvider {
    val isAvailable: Boolean = true

    fun getUpdateCountFlow(): Flow<Int> {
        return runCatching {
            val repo = KoinJavaComponent.get<AppUpdateRepository>(AppUpdateRepository::class.java)
            repo.getUpdateCount()
        }.getOrElse { flowOf(0) }
    }

    @Composable
    fun UpdatesScreen(modifier: Modifier = Modifier) {
        TvUpdatesScreen(modifier = modifier)
    }
}
