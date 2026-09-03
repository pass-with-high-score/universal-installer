package app.pwhs.universalinstaller.wearos.presentation

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
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
import app.pwhs.universalinstaller.wearos.presentation.about.AboutScreen
import app.pwhs.universalinstaller.wearos.presentation.manage.ManageScreen
import app.pwhs.universalinstaller.wearos.presentation.more.MoreScreen
import app.pwhs.universalinstaller.wearos.data.WearSettings
import app.pwhs.universalinstaller.wearos.presentation.settings.AccentScreen
import app.pwhs.universalinstaller.wearos.presentation.settings.AppLocale
import app.pwhs.universalinstaller.wearos.presentation.settings.LanguageScreen
import app.pwhs.universalinstaller.wearos.presentation.settings.SettingsScreen
import app.pwhs.universalinstaller.wearos.presentation.settings.SettingsViewModel
import app.pwhs.universalinstaller.wearos.presentation.theme.WearAccent
import app.pwhs.universalinstaller.wearos.presentation.theme.UniversalInstallerTheme
import kotlinx.coroutines.flow.MutableStateFlow
import org.koin.android.ext.android.inject
import org.koin.androidx.compose.koinViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val pendingApkId = MutableStateFlow<String?>(null)
    private val settings: WearSettings by inject()

    /** The locale has to be in place before the first resource is resolved, which is here. */
    override fun attachBaseContext(newBase: Context) {
        val locale = AppLocale.toLocale(WearSettings.readLanguageBlocking(newBase))
        if (locale == null) {
            super.attachBaseContext(newBase)
            return
        }
        Locale.setDefault(locale)
        val config = Configuration(newBase.resources.configuration).apply { setLocale(locale) }
        super.attachBaseContext(newBase.createConfigurationContext(config))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingApkId.value = intent?.getStringExtra(EXTRA_APK_ID)
        setContent {
            val accentId by settings.accentId.collectAsState(initial = null)
            UniversalInstallerTheme(accent = WearAccent.fromId(accentId)) {
                val apkId by pendingApkId.collectAsState()
                NotificationPermissionRequest()
                WearNavGraph(apkId, onRecreate = { recreate() }) { pendingApkId.value = null }
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
    const val MORE = "more"
    const val MANAGE = "manage"
    const val SETTINGS = "settings"
    const val ABOUT = "about"
    const val LANGUAGE = "language"
    const val ACCENT = "accent"
    fun detail(apkId: String) = "detail/$apkId"
}

@Composable
fun WearNavGraph(
    deepLinkApkId: String? = null,
    onRecreate: () -> Unit = {},
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
                onMoreClick = { navController.navigate(Routes.MORE) },
            )
        }
        composable(Routes.MORE) {
            MoreScreen(
                onManageClick = { navController.navigate(Routes.MANAGE) },
                onSettingsClick = { navController.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.MANAGE) { ManageScreen() }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onAboutClick = { navController.navigate(Routes.ABOUT) },
                onLanguageClick = { navController.navigate(Routes.LANGUAGE) },
                onAccentClick = { navController.navigate(Routes.ACCENT) },
            )
        }
        composable(Routes.LANGUAGE) {
            val viewModel: SettingsViewModel = koinViewModel()
            val tag by viewModel.languageTag.collectAsState()
            LanguageScreen(
                selectedTag = tag,
                onSelect = { viewModel.setLanguage(it, onRecreate) },
            )
        }
        composable(Routes.ACCENT) {
            val viewModel: SettingsViewModel = koinViewModel()
            val accent by viewModel.accent.collectAsState()
            AccentScreen(selected = accent, onSelect = viewModel::setAccent)
        }
        composable(Routes.ABOUT) { AboutScreen() }
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
