package app.pwhs.core.telemetry

/**
 * Standard Firebase Analytics events and parameter names for Universal Installer.
 *
 * All names follow Firebase conventions: <= 40 chars, snake_case, max 25 params per event.
 * Privacy rule: Never record PII or specific application identities (no app names, package names, or URLs).
 */
object TelemetryEvents {

    // ── Giai đoạn 1: Onboarding & Cấp Quyền (Permission Funnel) ───────────
    const val EVENT_ONBOARDING_START = "onboarding_start"
    const val EVENT_ONBOARDING_PAGE_VIEW = "onboarding_page_view"
    const val EVENT_ONBOARDING_SKIPPED = "onboarding_skipped"
    const val EVENT_PERMISSION_REQUESTED = "permission_requested"
    const val EVENT_PERMISSION_RESULT = "permission_result"
    const val EVENT_ONBOARDING_COMPLETE = "onboarding_complete"

    const val PARAM_PAGE_INDEX = "page_index"
    const val PARAM_PERMISSION_NAME = "permission_name"
    const val PARAM_GRANTED = "granted"
    const val PARAM_PERMISSION_TYPE = "permission_type"
    const val PARAM_STATUS = "status"
    const val PARAM_STEP_COUNT = "step_count"
    const val PARAM_DURATION_SEC = "duration_sec"

    const val PERM_STORAGE = "storage"
    const val PERM_ALL_FILES = "all_files"
    const val PERM_MANAGE_EXTERNAL_STORAGE = "manage_external_storage"
    const val PERM_INSTALL_PACKAGES = "install_packages"
    const val PERM_INSTALL_UNKNOWN_APPS = "install_unknown_apps"
    const val PERM_NOTIFICATIONS = "notifications"

    const val STATUS_GRANTED = "granted"
    const val STATUS_DENIED = "denied"
    const val STATUS_PERMANENTLY_DENIED = "permanently_denied"

    const val SOURCE_ONBOARDING = "onboarding"
    const val SOURCE_FIRST_INSTALL = "first_install_attempt"
    const val SOURCE_SETTINGS = "settings"

    // ── Giai đoạn 2: Phễu Cài đặt Cốt lõi (Core Installation Funnel) ──────
    const val EVENT_FILE_PICKED = "file_picked"
    const val EVENT_PACKAGE_PARSE_RESULT = "package_parse_result"
    const val EVENT_INSTALL_STARTED = "install_started"
    const val EVENT_INSTALL_RESULT = "install_result"

    const val PARAM_FILE_TYPE = "file_type"
    const val PARAM_FILE_COUNT = "file_count"
    const val PARAM_SOURCE = "source"
    const val PARAM_HAS_OBB = "has_obb"
    const val PARAM_TARGET_SDK = "target_sdk"
    const val PARAM_INSTALL_MODE = "install_mode"
    const val PARAM_IS_SPLIT = "is_split"
    const val PARAM_FILE_SIZE_MB = "file_size_mb"
    const val PARAM_ERROR_CODE = "error_code"
    const val PARAM_ERROR_TYPE = "error_type"
    const val PARAM_ERROR_REASON = "error_reason"
    const val PARAM_DURATION_MS = "duration_ms"
    const val PARAM_SCREEN_NAME = "screen_name"
    const val PARAM_SCREEN_CLASS = "screen_class"

    const val SOURCE_IN_APP_BROWSER = "in_app_browser"
    const val SOURCE_SYSTEM_FILE_PICKER = "system_file_picker"
    const val SOURCE_EXTERNAL_INTENT = "external_intent"
    const val SOURCE_LOCAL_DOWNLOADS = "local_downloads"
    const val SOURCE_TV_RECEIVED = "tv_received"

    const val PARSE_SUCCESS = "success"
    const val PARSE_CORRUPTED = "corrupted_file"
    const val PARSE_UNSUPPORTED = "unsupported_format"

    const val MODE_SESSION_INSTALLER = "session_installer"
    const val MODE_SHIZUKU = "shizuku"
    const val MODE_ROOT = "root"
    const val MODE_DHIZUKU = "dhizuku"
    const val MODE_SYSTEM = "system"

    const val RESULT_SUCCESS = "success"
    const val RESULT_FAILURE = "failed"
    const val RESULT_CANCELLED = "cancelled_by_user"

    // ── Giai đoạn 3: Tính năng Bổ trợ & Quản lý (Utilities & Management) ──
    const val EVENT_APP_MANAGEMENT_ACTION = "app_management_action"
    const val EVENT_CACHE_CLEAN_STARTED = "cache_clean_started"
    const val EVENT_DEFAULT_INSTALLER_ACTION = "default_installer_action"
    const val EVENT_PRIVILEGED_SERVICE_STATUS_CHANGED = "privileged_service_status_changed"
    /** @deprecated Kept as an alias for compatibility with older callers. */
    const val EVENT_SHIZUKU_STATUS_CHANGED = EVENT_PRIVILEGED_SERVICE_STATUS_CHANGED

    const val PARAM_ACTION_TYPE = "action_type"
    const val PARAM_CACHE_SIZE_MB = "cache_size_mb"
    const val PARAM_ACTION = "action"
    const val PARAM_BACKEND = "backend"

    const val ACTION_BACKUP_APK = "backup_apk"
    const val ACTION_EXTRACT_SPLITS = "extract_splits"
    const val ACTION_SHARE_APK = "share_apk"
    const val ACTION_OPEN_APP_INFO = "open_app_info"
    const val ACTION_UNINSTALL_APP = "uninstall_app"

    const val DEFAULT_INSTALLER_PROMPT_SHOWN = "prompt_shown"
    const val DEFAULT_INSTALLER_SET_SUCCESS = "set_success"
    const val DEFAULT_INSTALLER_CANCELLED = "cancelled"

    const val BACKEND_AUTO = "auto"
    const val BACKEND_PORTER = "porter"
    const val BACKEND_SHIZUKU = "shizuku"

    const val SHIZUKU_CONNECTED = "connected"
    const val SHIZUKU_PERMISSION_DENIED = "permission_denied"
    const val SHIZUKU_SERVICE_DEAD = "service_dead"

    // ── Giai đoạn 4: Đánh giá & Giữ chân (Review & Retention) ─────────────
    const val EVENT_REVIEW_PROMPT_TRIGGERED = "review_prompt_triggered"
    const val EVENT_IN_APP_REVIEW_REQUESTED = "in_app_review_requested"
    const val EVENT_IN_APP_REVIEW_DISMISSED = "in_app_review_dismissed"
    const val EVENT_MANUAL_RATE_CLICKED = "manual_rate_clicked"

    const val PARAM_TRIGGER_REASON = "trigger_reason"
    const val PARAM_TOTAL_SUCCESSFUL_INSTALLS = "total_successful_installs"
    const val PARAM_ERROR_MESSAGE = "error_message"

    const val TRIGGER_INSTALL_SUCCESS_MILESTONE = "install_success_milestone"

    // ── Giai đoạn 5: Chuyển đổi & Đơn hàng (Monetization) ──────────────────
    const val EVENT_PAYWALL_VIEWED = "paywall_viewed"
    const val EVENT_IN_APP_PURCHASE = "in_app_purchase"

    const val PARAM_PRODUCT_ID = "product_id"
    const val PARAM_VALUE = "value"
    const val PARAM_CURRENCY = "currency"

    // ── User Properties (User Scopes) ────────────────────────────────────
    const val PROPERTY_INSTALLER_MODE = "installer_mode"
    const val PROPERTY_IS_DEFAULT_INSTALLER = "is_default_installer"
    const val PROPERTY_TOTAL_INSTALLS_TIER = "total_installs_tier"
    const val PROPERTY_PREFERRED_FILE_TYPE = "preferred_file_type"

    const val TIER_0 = "0"
    const val TIER_1_5 = "1_5"
    const val TIER_6_20 = "6_20"
    const val TIER_20_PLUS = "20_plus"

    // ── Legacy Backwards Compatibility ────────────────────────────────────
    const val INSTALL_STARTED = EVENT_INSTALL_STARTED
    const val INSTALL_RESULT = EVENT_INSTALL_RESULT
    const val PARAM_METHOD = "method"
    const val PARAM_RESULT = "result"
    const val PARAM_FAILURE = "failure"
    const val PARAM_APK_COUNT = "apk_count"
    const val BACKEND_HEALTH = "backend_health"
    const val PARAM_HEALTHY = "healthy"
    const val DEFAULT_INSTALLER_SET = "default_installer_set"
    const val PARAM_ENABLED = "enabled"
    const val RESULT_BLOCKED = "blocked"
    const val FEATURE_USED = "feature_used"
    const val PARAM_FEATURE = "feature"

    const val FEATURE_LAN_SHARE = "lan_share"
    const val FEATURE_VIRUSTOTAL = "virustotal_scan"
    const val FEATURE_APK_BACKUP = "apk_backup"
    const val FEATURE_INSTALLER_PROFILE = "installer_profile"
    const val FEATURE_BATCH_INSTALL = "batch_install"
    const val FEATURE_OBB_COPY = "obb_copy"
    const val FEATURE_URL_DOWNLOAD = "url_download"
    const val FEATURE_UNINSTALL = "uninstall"
    const val FEATURE_REVIEW_PROMPT = "review_prompt"

    const val PROPERTY_INSTALL_METHOD = "install_method"
}