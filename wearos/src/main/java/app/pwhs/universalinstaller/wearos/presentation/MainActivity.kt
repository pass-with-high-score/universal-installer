package app.pwhs.universalinstaller.wearos.presentation

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import app.pwhs.universalinstaller.wearos.presentation.detail.ApkDetailScreen
import app.pwhs.universalinstaller.wearos.presentation.home.HomeScreen
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingApkId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingApkId.value = intent?.getStringExtra(EXTRA_APK_ID)
        setContent {
            UniversalInstallerTheme {
                val apkId by pendingApkId.collectAsState()
                NotificationPermissionRequest()
                WearNavGraph(apkId) { pendingApkId.value = null }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        pendingApkId.value = intent.getStringExtra(EXTRA_APK_ID)
    }

    companion object {
        const val EXTRA_APK_ID = "apk_id"
    }
}

private object Routes {
    const val HOME = "home"
    const val DETAIL = "detail/{apkId}"
    fun detail(apkId: String) = "detail/$apkId"
}

@Composable
fun WearNavGraph(
    deepLinkApkId: String? = null,
    onDeepLinkConsumed: () -> Unit = {},
) {
    val navController = rememberSwipeDismissableNavController()

    LaunchedEffect(deepLinkApkId) {
        val id = deepLinkApkId ?: return@LaunchedEffect
        navController.navigate(Routes.detail(id))
        onDeepLinkConsumed()
    }

    SwipeDismissableNavHost(
        navController = navController,
        startDestination = Routes.HOME,
    ) {
        composable(Routes.HOME) {
            HomeScreen(
                onApkClick = { apkId -> navController.navigate(Routes.detail(apkId)) },
            )
        }
        composable(Routes.DETAIL) { backStackEntry ->
            val apkId = backStackEntry.arguments?.getString("apkId") ?: return@composable
            ApkDetailScreen(
                apkId = apkId,
                onInstallSuccess = { navController.popBackStack() },
                onDelete = { navController.popBackStack() },
            )
        }
    }
}
