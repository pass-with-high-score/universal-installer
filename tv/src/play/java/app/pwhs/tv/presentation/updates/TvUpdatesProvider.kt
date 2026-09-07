package app.pwhs.tv.presentation.updates

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

object TvUpdatesProvider {
    val isAvailable: Boolean = false

    fun getUpdateCountFlow(): Flow<Int> = flowOf(0)

    @Composable
    fun UpdatesScreen(modifier: Modifier = Modifier) {
        // No-op for play flavor
    }
}
