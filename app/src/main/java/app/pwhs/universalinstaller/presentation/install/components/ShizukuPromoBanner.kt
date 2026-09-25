package app.pwhs.universalinstaller.presentation.install.components

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.unit.dp
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.InstallMode
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.presentation.setting.security.util.SystemInstallerManager
import app.pwhs.universalinstaller.util.DhizukuCompat
import app.pwhs.universalinstaller.util.MicroGCompat
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"
private const val SHIZUKU_WEBSITE_URL = "https://shizuku.rikka.app"

@Composable
fun ShizukuPromoBanner(
    modifier: Modifier = Modifier,
    settingViewModel: SettingViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settingState by settingViewModel.uiState.collectAsState()
    val useDhizuku by settingViewModel.useDhizuku.collectAsState()
    val dhizukuState by settingViewModel.dhizukuState.collectAsState()

    var isSystemInstallerFrozen by remember {
        mutableStateOf(SystemInstallerManager.isSystemPackageInstallerDisabled(context))
    }

    LifecycleResumeEffect(Unit) {
        isSystemInstallerFrozen = SystemInstallerManager.isSystemPackageInstallerDisabled(context)
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
        enter = fadeIn(),
        exit = fadeOut() + shrinkVertically(),
    ) {
        val onDismiss: () -> Unit = {
            scope.launch {
                context.dataStore.edit { prefs ->
                    prefs[PreferencesKeys.DISMISS_SHIZUKU_PROMO_BANNER] = true
                }
            }
        }

        val descRes: Int
        val actionTextRes: Int
        val actionClick: () -> Unit
        when (shizukuState) {
            ShizukuState.NO_PERMISSION -> {
                descRes = R.string.shizuku_promo_desc_no_perm
                actionTextRes = R.string.shizuku_promo_action_connect
                actionClick = { settingViewModel.setInstallMode(InstallMode.SHIZUKU) }
            }
            ShizukuState.NOT_RUNNING -> {
                descRes = R.string.shizuku_promo_desc_not_running
                actionTextRes = R.string.shizuku_promo_action_open
                actionClick = {
                    val launchIntent = context.packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
                    if (launchIntent != null) {
                        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        runCatching { context.startActivity(launchIntent) }
                            .onFailure {
                                Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
                            }
                    } else {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_WEBSITE_URL)).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        runCatching { context.startActivity(browserIntent) }
                    }
                }
            }
            else -> {
                descRes = R.string.shizuku_promo_desc_not_installed
                actionTextRes = R.string.shizuku_promo_action_learn_more
                actionClick = {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(SHIZUKU_WEBSITE_URL)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    runCatching { context.startActivity(browserIntent) }
                        .onFailure {
                            Toast.makeText(context, context.getString(R.string.error_cannot_open_app), Toast.LENGTH_SHORT).show()
                        }
                }
            }
        }

        Card(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ),
            border = BorderStroke(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
            ),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Image(
                        painter = painterResource(R.drawable.ic_shizuku_logo),
                        contentDescription = stringResource(R.string.installer_mode_shizuku),
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    )

                    Spacer(Modifier.width(10.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.shizuku_promo_title),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface,
                            )
                            Surface(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(4.dp),
                            ) {
                                Text(
                                    text = stringResource(R.string.shizuku_promo_badge_recommended),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp),
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
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

                Spacer(Modifier.height(8.dp))

                Text(
                    text = stringResource(descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    ) {
                        Text(
                            text = stringResource(R.string.shizuku_promo_dismiss),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }

                    Spacer(Modifier.width(6.dp))

                    FilledTonalButton(
                        onClick = actionClick,
                        shape = RoundedCornerShape(10.dp),
                    ) {
                        Text(
                            text = stringResource(actionTextRes),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}
