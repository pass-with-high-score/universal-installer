package app.pwhs.universalinstaller.presentation.setting

import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey

object PreferencesKeys {
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
    val THEME_PRESET = stringPreferencesKey("theme_preset")
    val USE_SHIZUKU = booleanPreferencesKey("use_shizuku")
    val USE_ROOT = booleanPreferencesKey("use_root")
    val INSTALL_USER_ID = intPreferencesKey("install_user_id")
    val VIRUSTOTAL_API_KEY = stringPreferencesKey("virustotal_api_key")
    val STRICT_VIRUSTOTAL_CHECK = booleanPreferencesKey("strict_virustotal_check")
    val SECURITY_LEVEL = stringPreferencesKey("security_level")
    val DELETE_APK_AFTER_INSTALL = booleanPreferencesKey("delete_apk_after_install")

    /** Open the app automatically after a successful install (with a 3-second cancellable countdown). */
    val AUTO_OPEN_AFTER_INSTALL = booleanPreferencesKey("auto_open_after_install")

    /**
     * How a package opened by another app asks for confirmation — dialog over the caller, or a
     * notification. Values are [app.pwhs.universalinstaller.domain.model.ExternalOpenMode.value];
     * missing / unknown reads back as Dialog, which is the behaviour that predates the setting.
     */
    val EXTERNAL_OPEN_MODE = stringPreferencesKey("external_open_mode")

    /**
     * Dialog or bottom sheet for the install UI. Values are
     * [app.pwhs.universalinstaller.domain.model.InstallUiStyle.value]; missing reads back as
     * Dialog, the style that predates the setting.
     */
    val INSTALL_UI_STYLE = stringPreferencesKey("install_ui_style")

    /**
     * Per-package installer-source overrides. Stored as one entry per line
     * (`pkg=installer`) so we don't pull in a JSON dependency. See
     * [app.pwhs.universalinstaller.presentation.install.dialog.InstallerOverrides]
     * for the parser. Empty / missing → no override; the global Shizuku/Root
     * installer pref is used.
     */
    val INSTALLER_OVERRIDES = stringPreferencesKey("installer_overrides")

    // Shizuku install options
    val SHIZUKU_BYPASS_LOW_TARGET_SDK = booleanPreferencesKey("shizuku_bypass_low_target_sdk")
    val SHIZUKU_ALLOW_TEST = booleanPreferencesKey("shizuku_allow_test")
    val SHIZUKU_REPLACE_EXISTING = booleanPreferencesKey("shizuku_replace_existing")
    val SHIZUKU_REQUEST_DOWNGRADE = booleanPreferencesKey("shizuku_request_downgrade")
    val USE_DHIZUKU = booleanPreferencesKey("use_dhizuku")
    val DHIZUKU_REQUEST_DOWNGRADE = booleanPreferencesKey("dhizuku_request_downgrade")
    val USE_CUSTOM_AUTHORIZER = booleanPreferencesKey("use_custom_authorizer")
    val USE_MICROG = booleanPreferencesKey("use_microg")
    val CUSTOM_AUTHORIZER_COMMAND = stringPreferencesKey("custom_authorizer_command")
    const val DEFAULT_CUSTOM_AUTHORIZER_COMMAND = "su -c {command}"
    val SHIZUKU_GRANT_ALL_PERMISSIONS = booleanPreferencesKey("shizuku_grant_all_permissions")
    val SHIZUKU_ALL_USERS = booleanPreferencesKey("shizuku_all_users")
    val SHIZUKU_SET_INSTALL_SOURCE = booleanPreferencesKey("shizuku_set_install_source")
    val SHIZUKU_INSTALLER_PACKAGE_NAME = stringPreferencesKey("shizuku_installer_package_name")

    val SHIZUKU_ALLOW_RESTRICTED_PERMISSIONS = booleanPreferencesKey("shizuku_allow_restricted_permissions")
    val SHIZUKU_DONT_KILL_APP = booleanPreferencesKey("shizuku_dont_kill_app")
    val SHIZUKU_DISABLE_VERIFICATION = booleanPreferencesKey("shizuku_disable_verification")
    val SHIZUKU_ENABLE_ROLLBACK = booleanPreferencesKey("shizuku_enable_rollback")
    val SHIZUKU_REQUEST_UPDATE_OWNERSHIP = booleanPreferencesKey("shizuku_request_update_ownership")

    // Shizuku uninstall options (pm uninstall -k / --user all)
    val SHIZUKU_UNINSTALL_KEEP_DATA = booleanPreferencesKey("shizuku_uninstall_keep_data")
    val SHIZUKU_UNINSTALL_ALL_USERS = booleanPreferencesKey("shizuku_uninstall_all_users")

    // Post-install optimization (cmd package compile -m speed)
    val DEX2OAT_OPTIMIZATION = booleanPreferencesKey("dex2oat_optimization_after_install")

    // Root (libsu) install options — full flavor only, but the keys live here so common
    // code can read them unconditionally. On the store flavor these stay at their defaults.
    val ROOT_BYPASS_LOW_TARGET_SDK = booleanPreferencesKey("root_bypass_low_target_sdk")
    val ROOT_ALLOW_TEST = booleanPreferencesKey("root_allow_test")
    val ROOT_REPLACE_EXISTING = booleanPreferencesKey("root_replace_existing")
    val ROOT_REQUEST_DOWNGRADE = booleanPreferencesKey("root_request_downgrade")
    val ROOT_GRANT_ALL_PERMISSIONS = booleanPreferencesKey("root_grant_all_permissions")
    val ROOT_ALL_USERS = booleanPreferencesKey("root_all_users")
    val ROOT_SET_INSTALL_SOURCE = booleanPreferencesKey("root_set_install_source")
    val ROOT_INSTALLER_PACKAGE_NAME = stringPreferencesKey("root_installer_package_name")
    val ROOT_ALLOW_RESTRICTED_PERMISSIONS = booleanPreferencesKey("root_allow_restricted_permissions")
    val ROOT_DONT_KILL_APP = booleanPreferencesKey("root_dont_kill_app")
    val ROOT_DISABLE_VERIFICATION = booleanPreferencesKey("root_disable_verification")
    val ROOT_ENABLE_ROLLBACK = booleanPreferencesKey("root_enable_rollback")
    val ROOT_REQUEST_UPDATE_OWNERSHIP = booleanPreferencesKey("root_request_update_ownership")

    // Sync options
    val SYNC_REQUIRE_PIN = booleanPreferencesKey("sync_require_pin")
    val SYNC_PIN_CODE = stringPreferencesKey("sync_pin_code")
    val SYNC_SERVER_PORT = stringPreferencesKey("sync_server_port")

    // Built-in PIN lock (Parental Control / App Security)
    val PIN_LOCK_ENABLED = booleanPreferencesKey("pin_lock_enabled")
    val PIN_LOCK_HASH = stringPreferencesKey("pin_lock_hash")
    val PIN_LOCK_SALT = stringPreferencesKey("pin_lock_salt")
    val PIN_LOCK_INSTALL = booleanPreferencesKey("pin_lock_install")
    val PIN_LOCK_UNINSTALL = booleanPreferencesKey("pin_lock_uninstall")
    val PIN_LOCK_APP_OPEN = booleanPreferencesKey("pin_lock_app_open")
    val PIN_LOCK_PROTECT_SETTINGS = booleanPreferencesKey("pin_lock_protect_settings")
    val PIN_LOCK_DISABLE_SYSTEM_INSTALLER = booleanPreferencesKey("pin_lock_disable_system_installer")

    // Biometric gate — independent toggles so users can guard install but not uninstall (or
    // vice versa) without one switch implying the other.
    val BIOMETRIC_LOCK_INSTALL = booleanPreferencesKey("biometric_lock_install")
    val BIOMETRIC_LOCK_UNINSTALL = booleanPreferencesKey("biometric_lock_uninstall")

    /**
     * Automatically start the installation when an APK is opened from an external intent
     * (e.g. from a file manager or Obtainium) without showing the confirmation dialog.
     */
    val AUTO_CONFIRM_EXTERNAL_INSTALL = booleanPreferencesKey("auto_confirm_external_install")

    /**
     * Automatically approve installation requests from selected applications without prompting.
     */
    val AUTO_APPROVE_CALLER_APPS = booleanPreferencesKey("auto_approve_caller_apps")

    /**
     * Set of package names permitted to trigger auto-approved installs.
     */
    val AUTO_APPROVE_PACKAGES = stringSetPreferencesKey("auto_approve_packages")

    /** Whether to show the "Download" tab in the source picker on the main screen. */
    val SHOW_DOWNLOAD_TAB = booleanPreferencesKey("show_download_tab")

    /**
     * When true (default), external VIEW/SEND intents land in DialogInstallActivity instead
     * of the full InstallActivity — i.e. opening an APK from a file manager pops up a focused
     * dialog over the calling app rather than launching our full UI. Off → fall back to the
     * historical InstallActivity flow (full screen with bottom bar).
     */
    val DIALOG_INSTALL_MODE = booleanPreferencesKey("dialog_install_mode")

    // Manage screen filter-sheet state — persisted so the user's sort/group/filter survives
    // process death. Enums stored by `name` so renaming a constant breaks loudly rather
    // than silently mapping to ordinal 0.
    val MANAGE_SORT_BY = stringPreferencesKey("manage_sort_by")
    val MANAGE_SORT_DIRECTION = stringPreferencesKey("manage_sort_direction")
    val MANAGE_GROUP_BY = stringPreferencesKey("manage_group_by")
    val MANAGE_APP_FILTER = stringSetPreferencesKey("manage_app_filter")

    // APK Extractor options
    val APK_EXTRACTOR_OUTPUT_PATH = stringPreferencesKey("apk_extractor_output_path")
    val APK_EXTRACTOR_FILENAME_TEMPLATE = stringPreferencesKey("apk_extractor_filename_template")
    /** Output container for apps that have split APKs: "apks" (default) or "xapk". */
    val APK_EXTRACTOR_SPLIT_FORMAT = stringPreferencesKey("apk_extractor_split_format")

    // Installer Profiles
    val INSTALLER_PROFILES = stringPreferencesKey("installer_profiles")
    val APP_PROFILE_MAPPING = stringPreferencesKey("app_profile_mapping")
}
