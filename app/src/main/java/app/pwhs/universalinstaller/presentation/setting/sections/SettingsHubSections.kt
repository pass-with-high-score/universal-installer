package app.pwhs.universalinstaller.presentation.setting.sections

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.HelpOutline
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.FileUpload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SettingsApplications
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.ViewCarousel
import androidx.compose.material.icons.rounded.Wallpaper
import androidx.compose.material.icons.rounded.WifiTethering
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.SettingUiState
import app.pwhs.universalinstaller.presentation.setting.about.AboutActivity
import app.pwhs.universalinstaller.presentation.setting.components.SettingsCardGroup
import app.pwhs.universalinstaller.presentation.setting.components.SettingsNavRow
import app.pwhs.universalinstaller.presentation.setting.components.SettingsSwitchRow
import app.pwhs.universalinstaller.presentation.setting.components.matchesQuery
import app.pwhs.universalinstaller.presentation.setting.diagnostics.DiagnosticsActivity
import app.pwhs.universalinstaller.presentation.setting.help.HelpActivity
import app.pwhs.universalinstaller.presentation.setting.installer.InstallerSettingsActivity
import app.pwhs.universalinstaller.presentation.setting.installui.InstallUiActivity
import app.pwhs.universalinstaller.presentation.setting.security.SecurityActivity
import app.pwhs.universalinstaller.presentation.setting.theme.ThemeActivity
import app.pwhs.universalinstaller.presentation.setting.virustotal.VirusTotalSettingActivity
import app.pwhs.universalinstaller.presentation.sync.SyncActivity
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.util.AndroidAutoCompat

internal fun LazyListScope.SettingsHubSections(
    q: String,
    context: Context,
    uiState: SettingUiState,
    analyticsEnabled: Boolean,
    onAnalyticsEnabledChanged: (Boolean) -> Unit,
    onLanguageClick: () -> Unit,
    onManageVisibleTabsClick: () -> Unit = {},
    onProfilesClick: () -> Unit,
    onOpenSyncOptions: () -> Unit,
    onExportClick: () -> Unit,
    onRestoreClick: () -> Unit,
    onReplayTutorial: () -> Unit,
) {
    // ── 1. Interface & Personalization ───────────────────────────
    item(key = "hub_interface") {
        val themeTitle = stringResource(R.string.theme_screen_title)
        val themeSub = stringResource(R.string.setting_theme_entry_subtitle)
        val installUiTitle = stringResource(R.string.install_ui_screen_title)
        val installUiSub = stringResource(R.string.install_ui_entry_subtitle)
        val langTitle = stringResource(R.string.setting_language_title)
        val langSub = stringResource(R.string.setting_language_subtitle)
        val tabsTitle = stringResource(R.string.setting_manage_visible_tabs_title)
        val tabsSub = stringResource(R.string.setting_manage_visible_tabs_subtitle)

        val rows = buildList<@Composable () -> Unit> {
            if (matchesQuery(q, listOf(themeTitle, themeSub, "interface theme color dark amoled"))) {
                add {
                    SettingsNavRow(
                        title = themeTitle,
                        subtitle = themeSub,
                        icon = Icons.Rounded.Palette,
                        onClick = { context.startActivity(Intent(context, ThemeActivity::class.java)) },
                    )
                }
            }
            if (matchesQuery(q, listOf(installUiTitle, installUiSub, "dialog notification bottom sheet position card download tab appearance"))) {
                add {
                    SettingsNavRow(
                        title = installUiTitle,
                        subtitle = installUiSub,
                        icon = Icons.Rounded.Wallpaper,
                        onClick = { context.startActivity(Intent(context, InstallUiActivity::class.java)) },
                    )
                }
            }
            if (matchesQuery(q, listOf(langTitle, langSub, "language locale"))) {
                add {
                    SettingsNavRow(
                        title = langTitle,
                        subtitle = langSub,
                        icon = Icons.Rounded.Language,
                        onClick = onLanguageClick,
                    )
                }
            }
            if (matchesQuery(q, listOf(tabsTitle, tabsSub, "tabs bottom bar navigation visible hide"))) {
                add {
                    SettingsNavRow(
                        title = tabsTitle,
                        subtitle = tabsSub,
                        icon = Icons.Rounded.ViewCarousel,
                        onClick = onManageVisibleTabsClick,
                    )
                }
            }
        }
        SettingsCardGroup(
            title = stringResource(R.string.setting_section_interface),
            items = rows,
        )
    }

    // ── 2. Installation & Profiles ───────────────────────────────
    item(key = "hub_installation") {
        val installerTitle = stringResource(R.string.setting_installer_settings_title)
        val installerSub = stringResource(R.string.setting_installer_settings_subtitle)
        val profilesTitle = stringResource(R.string.setting_profiles_title)
        val profilesSub = stringResource(R.string.setting_profiles_subtitle)

        val installerKeywords = listOf(
            installerTitle, installerSub,
            stringResource(R.string.setting_install_priority_title),
            stringResource(R.string.setting_section_install_options),
            stringResource(R.string.setting_delete_apk_title),
            stringResource(R.string.setting_auto_open_title),
            stringResource(R.string.setting_default_installer_title),
            stringResource(R.string.setting_default_uninstaller_title),
            "shizuku dhizuku root default custom microg priority engine mode downgrade replace permission test bypass source rollback uninstaller",
        )

        val rows = buildList<@Composable () -> Unit> {
            if (matchesQuery(q, installerKeywords)) {
                add {
                    SettingsNavRow(
                        title = installerTitle,
                        subtitle = installerSub,
                        icon = Icons.Rounded.SettingsApplications,
                        onClick = { context.startActivity(Intent(context, InstallerSettingsActivity::class.java)) },
                    )
                }
            }
            if (matchesQuery(q, listOf(profilesTitle, profilesSub, "profile"))) {
                add {
                    SettingsNavRow(
                        title = profilesTitle,
                        subtitle = profilesSub,
                        icon = Icons.Rounded.Badge,
                        onClick = onProfilesClick,
                    )
                }
            }
        }
        SettingsCardGroup(
            title = stringResource(R.string.setting_section_installation),
            items = rows,
        )
    }

    // ── 3. Security, Sync & Backup ───────────────────────────────
    item(key = "hub_security_data") {
        val securityTitle = stringResource(R.string.setting_security_screen_title)
        val securitySub = stringResource(R.string.setting_security_entry_subtitle)
        val shieldTitle = stringResource(R.string.setting_advanced_security_title)
        val shieldSub = if (uiState.virusTotalDisabled) {
            stringResource(R.string.setting_advanced_security_desc_disabled)
        } else {
            stringResource(R.string.setting_advanced_security_desc_enabled)
        }
        val syncTitle = stringResource(R.string.setting_sync_control_panel)
        val syncSub = stringResource(R.string.sync_hero_subtitle)
        val syncServerTitle = stringResource(R.string.sync_server_settings)
        val syncServerSub = "${stringResource(R.string.sync_port)}: ${uiState.syncOptions.serverPort} • " +
            if (uiState.syncOptions.requirePin) stringResource(R.string.sync_require_pin)
            else stringResource(R.string.sync_require_pin_off)
        val exportTitle = stringResource(R.string.setting_backup_export_title)
        val exportSub = stringResource(R.string.setting_backup_export_subtitle)
        val restoreTitle = stringResource(R.string.setting_backup_restore_title)
        val restoreSub = stringResource(R.string.setting_backup_restore_subtitle)

        val rows = buildList<@Composable () -> Unit> {
            if (matchesQuery(q, listOf(securityTitle, securitySub, "security lock pin biometric fingerprint blacklist parental"))) {
                add {
                    SettingsNavRow(
                        title = securityTitle,
                        subtitle = securitySub,
                        icon = Icons.Rounded.Security,
                        onClick = { context.startActivity(Intent(context, SecurityActivity::class.java)) },
                    )
                }
            }
            if (matchesQuery(q, listOf(shieldTitle, shieldSub, "appshield virustotal virusdetec scan malware advanced api key"))) {
                add {
                    SettingsNavRow(
                        title = shieldTitle,
                        subtitle = shieldSub,
                        painter = painterResource(R.drawable.ic_appshield),
                        onClick = { context.startActivity(Intent(context, VirusTotalSettingActivity::class.java)) },
                    )
                }
            }
            if (matchesQuery(q, listOf(syncTitle, syncSub, "sync wifi share"))) {
                add {
                    SettingsNavRow(
                        title = syncTitle,
                        subtitle = syncSub,
                        icon = Icons.Rounded.WifiTethering,
                        onClick = { context.startActivity(Intent(context, SyncActivity::class.java)) },
                    )
                }
            }
            if (matchesQuery(q, listOf(syncServerTitle, syncServerSub, "sync port pin server"))) {
                add {
                    SettingsNavRow(
                        title = syncServerTitle,
                        subtitle = syncServerSub,
                        icon = Icons.Rounded.Tune,
                        onClick = onOpenSyncOptions,
                    )
                }
            }
            if (matchesQuery(q, listOf(exportTitle, exportSub, "backup export"))) {
                add {
                    SettingsNavRow(
                        title = exportTitle,
                        subtitle = exportSub,
                        icon = Icons.Rounded.FileUpload,
                        onClick = onExportClick,
                    )
                }
            }
            if (matchesQuery(q, listOf(restoreTitle, restoreSub, "backup restore import"))) {
                add {
                    SettingsNavRow(
                        title = restoreTitle,
                        subtitle = restoreSub,
                        icon = Icons.Rounded.FileDownload,
                        onClick = onRestoreClick,
                    )
                }
            }
        }
        SettingsCardGroup(
            title = stringResource(R.string.setting_section_security),
            items = rows,
        )
    }

    // ── 4. About, Privacy & Help ─────────────────────────────────
    item(key = "hub_about") {
        val analyticsTitle = stringResource(R.string.setting_analytics_title)
        val analyticsSub = stringResource(R.string.setting_analytics_subtitle)
        val helpTitle = stringResource(R.string.help_title)
        val tutorialTitle = stringResource(R.string.help_replay_tutorial)
        val tutorialSub = stringResource(R.string.help_replay_tutorial_sub)
        val diagTitle = stringResource(R.string.setting_diagnostics_title)
        val aboutTitle = stringResource(R.string.setting_section_about)
        val aaTitle = stringResource(R.string.aa_open_settings)
        val aaSub = stringResource(R.string.aa_open_settings_sub)

        val rows = buildList<@Composable () -> Unit> {
            if (Telemetry.isCollecting && matchesQuery(q, listOf(analyticsTitle, analyticsSub, "privacy analytics crash telemetry"))) {
                add {
                    SettingsSwitchRow(
                        title = analyticsTitle,
                        subtitle = analyticsSub,
                        icon = Icons.Rounded.PrivacyTip,
                        checked = analyticsEnabled,
                        onCheckedChange = onAnalyticsEnabledChanged,
                    )
                }
            }
            if (matchesQuery(q, listOf(helpTitle, "help faq"))) {
                add {
                    SettingsNavRow(
                        title = helpTitle,
                        icon = Icons.AutoMirrored.Rounded.HelpOutline,
                        onClick = { context.startActivity(Intent(context, HelpActivity::class.java)) },
                    )
                }
            }
            if (matchesQuery(q, listOf(tutorialTitle, tutorialSub, "tutorial onboarding"))) {
                add {
                    SettingsNavRow(
                        title = tutorialTitle,
                        subtitle = tutorialSub,
                        icon = Icons.Rounded.Replay,
                        onClick = onReplayTutorial,
                    )
                }
            }
            if (matchesQuery(q, listOf(diagTitle, "diagnostics debug logs"))) {
                add {
                    SettingsNavRow(
                        title = diagTitle,
                        icon = Icons.Rounded.BugReport,
                        onClick = { context.startActivity(Intent(context, DiagnosticsActivity::class.java)) },
                    )
                }
            }
            if (matchesQuery(q, listOf(aboutTitle, uiState.appVersion, "about version more apps"))) {
                add {
                    SettingsNavRow(
                        title = aboutTitle,
                        subtitle = "v${uiState.appVersion}",
                        icon = Icons.Rounded.Info,
                        onClick = { context.startActivity(Intent(context, AboutActivity::class.java)) },
                    )
                }
            }
            if (AndroidAutoCompat.isAndroidAutoInstalled(context) && matchesQuery(q, listOf(aaTitle, aaSub, "android auto"))) {
                add {
                    SettingsNavRow(
                        title = aaTitle,
                        subtitle = aaSub,
                        icon = Icons.Rounded.DirectionsCar,
                        onClick = {
                            AndroidAutoCompat.getSettingsIntent(context)?.let { aaIntent ->
                                runCatching { context.startActivity(aaIntent) }
                            }
                        },
                    )
                }
            }
        }
        SettingsCardGroup(
            title = stringResource(R.string.setting_section_about),
            items = rows,
        )
    }
}
