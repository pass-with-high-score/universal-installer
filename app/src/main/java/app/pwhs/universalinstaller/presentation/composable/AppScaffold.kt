package app.pwhs.universalinstaller.presentation.composable

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.ramcosta.composedestinations.generated.NavGraphs
import com.ramcosta.composedestinations.spec.DestinationSpec
import com.ramcosta.composedestinations.utils.currentDestinationAsState
import com.ramcosta.composedestinations.utils.startDestination

@Composable
fun AppScaffold(
    modifier: Modifier = Modifier,
    navController: NavHostController,
    bottomBar: @Composable (DestinationSpec) -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    val destination =
        navController.currentDestinationAsState().value ?: NavGraphs.root.startDestination

    Scaffold(
        modifier = modifier,
        bottomBar = { bottomBar(destination) },
        content = content
    )
}