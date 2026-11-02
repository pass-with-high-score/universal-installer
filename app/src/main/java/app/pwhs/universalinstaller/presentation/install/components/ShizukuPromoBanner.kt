package app.pwhs.universalinstaller.presentation.install.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.ShizukuStarterHelper
import app.pwhs.core.util.openUriSafely
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.InstallMode
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.presentation.setting.security.util.SystemInstallerManager
import app.pwhs.universalinstaller.util.MicroGCompat
import app.pwhs.universalinstaller.util.ShizukuServices
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
private const val SHIZUKU_WEBSITE_URL = "https://shizuku.rikka.app"

@Composable
fun ShizukuPromoBanner(
    modifier: Modifier = Modifier,
    settingViewModel: SettingViewModel = koinViewModel(),
    onDismissed: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingState by settingViewModel.uiState.collectAsState()
    val useDhizuku by settingViewModel.useDhizuku.collectAsState()
    val dhizukuState by settingViewModel.dhizukuState.collectAsState()

    var isSystemInstallerFrozen by remember {
        mutableStateOf(SystemInstallerManager.isSystemPackageInstallerDisabled(context))
    }
    var porterOnly by remember { mutableStateOf(ShizukuServices.isPorterOnly(context)) }
    val porterServing by ShizukuServices.porterServing.collectAsState(initial = ShizukuServices.isPorterServing())

    LifecycleResumeEffect(Unit) {
        isSystemInstallerFrozen = SystemInstallerManager.isSystemPackageInstallerDisabled(context)
        porterOnly = ShizukuServices.isPorterOnly(context)
        settingViewModel.updateShizukuState()
        onPauseOrDispose {}
    }

    val isDismissed by remember(context) {
        context.dataStore.data.map {
            it[PreferencesKeys.DISMISS_SHIZUKU_PROMO_BANNER] ?: false
        }
    }.collectAsState(initial = true)

    val microGAvailable = remember(context) { MicroGCompat.isAvailable(context) }

    val configuredMode = remember(
        settingState.useShizuku,
        settingState.useRoot,
        useDhizuku,
        settingState.useCustomAuthorizer,
        settingState.useMicroG,
    ) {
        InstallMode.from(
            useShizuku = settingState.useShizuku,
            useRoot = settingState.useRoot,
            useDhizuku = useDhizuku,
            useCustomAuthorizer = settingState.useCustomAuthorizer,
            useMicroG = settingState.useMicroG,
        )
    }

    val effectiveMode = remember(
        configuredMode,
        settingState.shizukuState,
        settingState.rootState,
        dhizukuState,
        microGAvailable,
        isSystemInstallerFrozen,
    ) {
        InstallMode.resolveEffective(
            configuredMode = configuredMode,
            shizukuState = settingState.shizukuState,
            rootState = settingState.rootState,
            dhizukuState = dhizukuState,
            isMicroGAvailable = microGAvailable,
            isSystemInstallerFrozen = isSystemInstallerFrozen,
        )
    }

    val shizukuState = settingState.shizukuState

    val shouldShow = !isDismissed &&
        effectiveMode == InstallMode.DEFAULT &&
        shizukuState != ShizukuState.READY &&
        shizukuState != ShizukuState.UNSUPPORTED

    AnimatedVisibility(
        visible = shouldShow,
        enter = fadeIn() + slideInVertically { it },
        exit = fadeOut() + shrinkVertically() + slideOutVertically { it },
        modifier = modifier,
    ) {
        val onDismiss: () -> Unit = {
            scope.launch {
                context.dataStore.edit { prefs ->
                    prefs[PreferencesKeys.DISMISS_SHIZUKU_PROMO_BANNER] = true
                }
            }
        }

        val initialPage = remember {
            if (shizukuState == ShizukuState.NO_PERMISSION) 1 else 0
        }
        val pagerState = rememberPagerState(
            initialPage = initialPage,
            pageCount = { 2 },
        )

        LaunchedEffect(shizukuState) {
            if (shizukuState == ShizukuState.NO_PERMISSION && pagerState.currentPage == 0) {
                pagerState.animateScrollToPage(1)
            }
        }

        val step1ActionClick: () -> Unit = {
            when (shizukuState) {
                ShizukuState.NO_PERMISSION -> {
                    scope.launch { pagerState.animateScrollToPage(1) }
                }
                ShizukuState.NOT_RUNNING -> {
                    val launchIntent = if (porterOnly) {
                        context.packageManager.getLaunchIntentForPackage(ShizukuServices.PORTER_PACKAGE)
                    } else {
                        ShizukuStarterHelper.getManagerLaunchIntent(context)
                    }
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(launchIntent) }
                            .onFailure {
                                Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        context.openUriSafely(SHIZUKU_WEBSITE_URL)
                    }
                }
                else -> {
                    context.openUriSafely(SHIZUKU_WEBSITE_URL)
                }
            }
        }

        val step2ActionClick: () -> Unit = {
            settingViewModel.setInstallMode(InstallMode.SHIZUKU)
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_shizuku_logo),
                        contentDescription = stringResource(R.string.installer_mode_shizuku),
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp)),
                    )

                    Spacer(Modifier.width(8.dp))

                    Text(
                        text = if (pagerState.currentPage == 0) {
                            stringResource(R.string.shizuku_promo_step_1_title)
                        } else {
                            stringResource(R.string.shizuku_promo_step_2_title)
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    Spacer(Modifier.width(6.dp))

                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(6.dp),
                    ) {
                        Text(
                            text = "${pagerState.currentPage + 1}/2",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        )
                    }

                    Spacer(Modifier.width(4.dp))

                    IconButton(
                        onClick = {
                            onDismiss()
                            onDismissed?.invoke()
                        },
                        modifier = Modifier.size(24.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = stringResource(R.string.shizuku_promo_dismiss),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }

                Spacer(Modifier.height(6.dp))

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.fillMaxWidth(),
                ) { page ->
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = if (page == 0) {
                                if (shizukuState == ShizukuState.NO_PERMISSION) {
                                    stringResource(R.string.shizuku_promo_step_1_ready_desc)
                                } else if (shizukuState == ShizukuState.NOT_RUNNING) {
                                    stringResource(if (porterOnly) R.string.porter_promo_desc_not_running else R.string.shizuku_promo_desc_not_running)
                                } else {
                                    stringResource(R.string.shizuku_promo_desc_not_installed)
                                }
                            } else {
                                stringResource(if (porterOnly || porterServing) R.string.porter_promo_desc_no_perm else R.string.shizuku_promo_desc_no_perm)
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            minLines = 2,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )

                        Spacer(Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                repeat(2) { index ->
                                    val isSelected = pagerState.currentPage == index
                                    Box(
                                        modifier = Modifier
                                            .height(6.dp)
                                            .width(if (isSelected) 16.dp else 6.dp)
                                            .clip(RoundedCornerShape(3.dp))
                                            .background(
                                                if (isSelected) MaterialTheme.colorScheme.primary
                                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                            )
                                            .clickable {
                                                scope.launch { pagerState.animateScrollToPage(index) }
                                            }
                                    )
                                }
                            }

                            FilledTonalButton(
                                onClick = if (page == 0) step1ActionClick else step2ActionClick,
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp),
                            ) {
                                Text(
                                    text = if (page == 0) {
                                        if (shizukuState == ShizukuState.NO_PERMISSION) {
                                            stringResource(R.string.shizuku_promo_next_step)
                                        } else if (shizukuState == ShizukuState.NOT_RUNNING) {
                                            stringResource(if (porterOnly) R.string.porter_promo_action_open else R.string.shizuku_promo_action_open)
                                        } else {
                                            stringResource(R.string.shizuku_promo_action_learn_more)
                                        }
                                    } else {
                                        stringResource(R.string.shizuku_promo_action_connect)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
