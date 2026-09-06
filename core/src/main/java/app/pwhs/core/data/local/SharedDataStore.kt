package app.pwhs.core.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore

val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

object SharedPrefsKeys {
    val ONBOARDING_COMPLETED = booleanPreferencesKey("onboarding_completed")

    /** TV: when true (default) and root is available, install silently via the root shell. */
    val ROOT_SILENT_INSTALL = booleanPreferencesKey("tv_root_silent_install")
    val DELETE_APK_AFTER_INSTALL = booleanPreferencesKey("delete_apk_after_install")
    val TV_SHIZUKU_REPLACE = booleanPreferencesKey("tv_shizuku_replace")
    val TV_ROOT_REPLACE = booleanPreferencesKey("tv_root_replace")
    val TV_SHIZUKU_DOWNGRADE = booleanPreferencesKey("tv_shizuku_downgrade")
    val TV_ROOT_DOWNGRADE = booleanPreferencesKey("tv_root_downgrade")
    val TV_SHIZUKU_GRANT = booleanPreferencesKey("tv_shizuku_grant")
    val TV_ROOT_GRANT = booleanPreferencesKey("tv_root_grant")
    val TV_SHIZUKU_TEST = booleanPreferencesKey("tv_shizuku_test")
    val TV_ROOT_TEST = booleanPreferencesKey("tv_root_test")
    val TV_SHIZUKU_ALL_USERS = booleanPreferencesKey("tv_shizuku_all_users")
    val TV_ROOT_ALL_USERS = booleanPreferencesKey("tv_root_all_users")

    /**
     * "Normal" or "Strict". Declared here because onboarding lives in :core but the setting is
     * read by the phone app's install flow; both sides must agree on the key name.
     */
    val SECURITY_LEVEL = stringPreferencesKey("security_level")

    /** Legacy companion to [SECURITY_LEVEL], kept in step so older read sites still agree. */
    val STRICT_VIRUSTOTAL_CHECK = booleanPreferencesKey("strict_virustotal_check")

    /**
     * The VirusTotal API key. Onboarding writes it and the phone app's Settings screen reads and
     * writes the same preference, so the key name must match `SettingViewModel.PreferencesKeys`.
     */
    val VIRUSTOTAL_API_KEY = stringPreferencesKey("virustotal_api_key")

    /**
     * Whether the Play build may report anonymous install statistics and crashes. Absent means
     * on — the onboarding page presents it opted in, and the open-source build ignores the key
     * entirely because it has nothing to report with.
     *
     * Lives here, like [SECURITY_LEVEL], because onboarding is in :core while the reporting it
     * governs is in :app; both sides have to agree on the key name.
     */
    val ANALYTICS_ENABLED = booleanPreferencesKey("analytics_enabled")

    /** Optional GitHub Personal Access Token to avoid rate limiting (60 req/hr -> 5000 req/hr). */
    val GITHUB_PAT_TOKEN = stringPreferencesKey("github_pat_token")

    /** Optional GitLab Personal Access Token. */
    val GITLAB_PAT_TOKEN = stringPreferencesKey("gitlab_pat_token")

    /** Optional Codeberg / Gitea Access Token. */
    val CODEBERG_PAT_TOKEN = stringPreferencesKey("codeberg_pat_token")

    /** Auto-update check interval in hours (6, 12, 24, or 0 for disabled). Default: 12 */
    val UPDATE_CHECK_INTERVAL_HOURS = stringPreferencesKey("update_check_interval_hours")

    /** Only check for updates when connected to Wi-Fi. Default: false */
    val UPDATE_CHECK_WIFI_ONLY = booleanPreferencesKey("update_check_wifi_only")

    /** Theme configuration keys */
    val THEME_MODE = stringPreferencesKey("theme_mode")
    val THEME_PRESET = stringPreferencesKey("theme_preset")
    val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
    val AMOLED_MODE = booleanPreferencesKey("amoled_mode")
}
