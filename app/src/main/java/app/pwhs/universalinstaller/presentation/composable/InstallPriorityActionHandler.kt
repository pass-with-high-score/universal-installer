package app.pwhs.universalinstaller.presentation.composable

import app.pwhs.universalinstaller.util.ShizukuServices
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import app.pwhs.core.util.PermissionSettingsHelper
import app.pwhs.core.util.openUriSafely
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.model.InstallBackend
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.setting.InstallMode
import app.pwhs.universalinstaller.presentation.setting.SettingUiState
import app.pwhs.universalinstaller.presentation.setting.SettingViewModel
import app.pwhs.universalinstaller.presentation.setting.ShizukuState
import app.pwhs.universalinstaller.util.DhizukuState

internal fun handleBackendSelection(
    backend: InstallBackend,
    context: Context,
    settingViewModel: SettingViewModel,
    settingState: SettingUiState,
    dhizukuState: DhizukuState,
    microGAvailable: Boolean,
    isSystemInstallerFrozen: Boolean,
    canInstallPackages: Boolean,
) {
    when (backend) {
        InstallBackend.DEFAULT -> {
            if (isSystemInstallerFrozen) {
                Toast.makeText(
                    context,
                    context.getString(R.string.setting_system_installer_frozen_cannot_select),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            if (!canInstallPackages) {
                Toast.makeText(
                    context,
                    context.getString(R.string.permission_install_prompt_required),
                    Toast.LENGTH_LONG
                ).show()
                PermissionSettingsHelper.openInstallPermission(context)
            }
            settingViewModel.promoteBackendToTop(InstallBackend.DEFAULT)
            settingViewModel.setInstallMode(InstallMode.DEFAULT)
        }
        InstallBackend.SHIZUKU -> {
            when (settingState.shizukuState) {
                ShizukuState.READY -> {
                    settingViewModel.promoteBackendToTop(InstallBackend.SHIZUKU)
                    settingViewModel.setBackendEnabled(InstallBackend.SHIZUKU, true)
                    settingViewModel.setInstallMode(InstallMode.SHIZUKU)
                }
                ShizukuState.NO_PERMISSION -> {
                    settingViewModel.requestShizukuPermission()
                }
                ShizukuState.NOT_RUNNING -> {
                    Toast.makeText(
                        context,
                        context.getString(
                            if (ShizukuServices.isPorterOnly(context)) R.string.setting_porter_start_service_hint
                            else R.string.setting_shizuku_start_service_hint
                        ),
                        Toast.LENGTH_SHORT
                    ).show()
                    settingViewModel.startShizukuService()
                }
                ShizukuState.NOT_INSTALLED -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.setting_shizuku_install_hint),
                        Toast.LENGTH_SHORT
                    ).show()
                    context.openUriSafely("https://play.google.com/store/apps/details?id=moe.shizuku.privileged.api")
                }
                ShizukuState.UNSUPPORTED -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.setting_shizuku_unsupported),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        InstallBackend.DHIZUKU -> {
            when (dhizukuState) {
                DhizukuState.READY -> {
                    settingViewModel.promoteBackendToTop(InstallBackend.DHIZUKU)
                    settingViewModel.setBackendEnabled(InstallBackend.DHIZUKU, true)
                    settingViewModel.setInstallMode(InstallMode.DHIZUKU)
                }
                DhizukuState.NOT_AUTHORIZED -> {
                    settingViewModel.setBackendEnabled(InstallBackend.DHIZUKU, true)
                }
                DhizukuState.PROFILE_OWNER_UNSUPPORTED -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.setting_dhizuku_profile_owner_unsupported),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                DhizukuState.NOT_RUNNING -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.setting_dhizuku_not_running),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                DhizukuState.NOT_INSTALLED -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.setting_dhizuku_not_installed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
                DhizukuState.UNSUPPORTED -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.setting_dhizuku_unsupported),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
        InstallBackend.ROOT -> {
            if (!settingState.rootSupported) {
                Toast.makeText(
                    context,
                    context.getString(R.string.installer_engine_root_unsupported),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            settingViewModel.promoteBackendToTop(InstallBackend.ROOT)
            settingViewModel.setBackendEnabled(InstallBackend.ROOT, true)
            settingViewModel.setInstallMode(InstallMode.ROOT)
        }
        InstallBackend.CUSTOM -> {
            settingViewModel.promoteBackendToTop(InstallBackend.CUSTOM)
            settingViewModel.setBackendEnabled(InstallBackend.CUSTOM, true)
            settingViewModel.setInstallMode(InstallMode.CUSTOM)
        }
        InstallBackend.MICROG -> {
            if (!microGAvailable) {
                Toast.makeText(
                    context,
                    context.getString(R.string.microg_not_installed),
                    Toast.LENGTH_SHORT
                ).show()
                return
            }
            settingViewModel.promoteBackendToTop(InstallBackend.MICROG)
            settingViewModel.setBackendEnabled(InstallBackend.MICROG, true)
            settingViewModel.setInstallMode(InstallMode.MICROG)
        }
    }
}
