package app.pwhs.universalinstaller.presentation.composable

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.RotateLeft
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstallBackend
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.InstallMode
import app.pwhs.universalinstaller.presentation.setting.SettingActivity
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.presentation.setting.components.CustomAuthorizerCard
import app.pwhs.universalinstaller.presentation.setting.components.isBackendAvailable
import app.pwhs.universalinstaller.presentation.setting.components.resolveStatusText
import app.pwhs.universalinstaller.presentation.setting.security.util.SystemInstallerManager
import app.pwhs.universalinstaller.util.DhizukuCompat
import app.pwhs.universalinstaller.util.DhizukuState
import app.pwhs.universalinstaller.util.MicroGCompat
import kotlinx.coroutines.launch
import org.koin.androidx.compose.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InstallPriorityBottomSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    settingViewModel: SettingViewModel = koinViewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val settingState by settingViewModel.uiState.collectAsState()
    val useDhizuku by settingViewModel.useDhizuku.collectAsState()
    val dhizukuState by settingViewModel.dhizukuState.collectAsState()

    var isSystemInstallerFrozen by remember {
        mutableStateOf(SystemInstallerManager.isSystemPackageInstallerDisabled(context))
    }
    var canInstallPackages by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.packageManager.canRequestPackageInstalls()
            } else true
        )
    }

    LifecycleResumeEffect(Unit) {
        isSystemInstallerFrozen = SystemInstallerManager.isSystemPackageInstallerDisabled(context)
        canInstallPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else true
        settingViewModel.refreshRootState()
        settingViewModel.refreshDhizukuState()
        settingViewModel.updateShizukuState()
        onPauseOrDispose {}
    }

    val microGAvailable = remember(context) { MicroGCompat.isAvailable(context) }

    val activeBackend = remember(
        settingState.backendPriority,
        settingState.useShizuku,
        settingState.useRoot,
        useDhizuku,
        settingState.useCustomAuthorizer,
        settingState.useMicroG,
        settingState.shizukuState,
        settingState.rootState,
        dhizukuState,
        microGAvailable,
        isSystemInstallerFrozen,
    ) {
        var resolved: InstallBackend? = null
        for (backend in settingState.backendPriority) {
            when (backend) {
                InstallBackend.SHIZUKU -> {
                    if (settingState.useShizuku && settingState.shizukuState == ShizukuState.READY) {
                        resolved = backend
                        break
                    }
                }
                InstallBackend.DHIZUKU -> {
                    if (useDhizuku && dhizukuState == DhizukuState.READY) {
                        resolved = backend
                        break
                    }
                }
                InstallBackend.ROOT -> {
                    if (settingState.useRoot && (settingState.rootState == RootState.READY || settingState.rootState == RootState.UNKNOWN)) {
                        resolved = backend
                        break
                    }
                }
                InstallBackend.CUSTOM -> {
                    if (settingState.useCustomAuthorizer) {
                        resolved = backend
                        break
                    }
                }
                InstallBackend.MICROG -> {
                    if (settingState.useMicroG && microGAvailable) {
                        resolved = backend
                        break
                    }
                }
                InstallBackend.DEFAULT -> {
                    if (!isSystemInstallerFrozen) {
                        resolved = backend
                        break
                    }
                }
            }
        }
        resolved ?: if (!isSystemInstallerFrozen) InstallBackend.DEFAULT
        else if (settingState.shizukuState == ShizukuState.READY) InstallBackend.SHIZUKU
        else if (settingState.rootState == RootState.READY || settingState.rootState == RootState.UNKNOWN) InstallBackend.ROOT
        else if (dhizukuState == DhizukuState.READY) InstallBackend.DHIZUKU
        else InstallBackend.DEFAULT
    }

    androidx.compose.runtime.LaunchedEffect(settingState.useRoot, settingState.rootState) {
        if (settingState.useRoot && settingState.rootState == RootState.UNKNOWN) {
            settingViewModel.retryRoot()
        }
    }

    val closeSheet: () -> Unit = {
        scope.launch {
            sheetState.hide()
            onDismissRequest()
        }
    }

    fun selectBackend(backend: InstallBackend) {
        handleBackendSelection(
            backend = backend,
            context = context,
            settingViewModel = settingViewModel,
            settingState = settingState,
            dhizukuState = dhizukuState,
            microGAvailable = microGAvailable,
            isSystemInstallerFrozen = isSystemInstallerFrozen,
            canInstallPackages = canInstallPackages,
        )
    }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
                .padding(bottom = 28.dp),
        ) {
            // Header with Title & Reset
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Tune,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = stringResource(R.string.setting_install_priority_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.setting_install_priority_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                TextButton(
                    onClick = { settingViewModel.resetBackendPriority() },
                    modifier = Modifier.padding(start = 8.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.RotateLeft,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = stringResource(R.string.setting_install_priority_reset),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Backend priority items
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                settingState.backendPriority.forEachIndexed { index, backend ->
                    val isAvailable = isBackendAvailable(
                        backend = backend,
                        uiState = settingState,
                        dhizukuState = dhizukuState,
                        microGAvailable = microGAvailable,
                        isSystemFrozen = isSystemInstallerFrozen,
                    )

                    val isEnabled = when (backend) {
                        InstallBackend.SHIZUKU -> settingState.useShizuku && isAvailable
                        InstallBackend.DHIZUKU -> useDhizuku && isAvailable
                        InstallBackend.ROOT -> settingState.useRoot && isAvailable
                        InstallBackend.CUSTOM -> settingState.useCustomAuthorizer
                        InstallBackend.MICROG -> settingState.useMicroG && isAvailable
                        InstallBackend.DEFAULT -> !isSystemInstallerFrozen
                    }

                    val isActive = backend == activeBackend

                    val statusText = resolveStatusText(
                        backend = backend,
                        isEnabled = isEnabled,
                        uiState = settingState,
                        dhizukuState = dhizukuState,
                        microGAvailable = microGAvailable,
                        isSystemFrozen = isSystemInstallerFrozen,
                    )

                    PriorityItemCard(
                        rank = index + 1,
                        backend = backend,
                        isActive = isActive,
                        isEnabled = isEnabled,
                        isAvailable = isAvailable,
                        statusText = statusText,
                        canMoveUp = index > 0,
                        canMoveDown = index < settingState.backendPriority.size - 1,
                        onMoveUp = { settingViewModel.moveBackendPriority(index, index - 1) },
                        onMoveDown = { settingViewModel.moveBackendPriority(index, index + 1) },
                        onToggle = { checked -> settingViewModel.setBackendEnabled(backend, checked) },
                        onClick = { selectBackend(backend) },
                        isDefaultFallback = backend == InstallBackend.DEFAULT,
                    )
                }
            }

            // Custom Authorizer inline configuration if custom is enabled
            AnimatedVisibility(visible = settingState.useCustomAuthorizer) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    CustomAuthorizerCard(
                        command = settingState.customAuthorizerCommand,
                        onCommandChange = { settingViewModel.setCustomAuthorizerCommand(it) },
                        onTestCommand = { settingViewModel.testCustomAuthorizerCommand(it) },
                        modifier = Modifier.padding(0.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            val isInSetting = context is SettingActivity

            // Footer Actions
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (!isInSetting) {
                    FilledTonalButton(
                        onClick = {
                            closeSheet()
                            context.startActivity(Intent(context, SettingActivity::class.java))
                        },
                        modifier = Modifier.weight(1f),
                        shape = MaterialTheme.shapes.medium,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.setting_title),
                            style = MaterialTheme.typography.labelLarge,
                        )
                    }
                }

                Button(
                    onClick = closeSheet,
                    modifier = if (isInSetting) Modifier.fillMaxWidth() else Modifier.weight(1f),
                    shape = MaterialTheme.shapes.medium,
                ) {
                    Text(
                        text = stringResource(android.R.string.ok),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}
