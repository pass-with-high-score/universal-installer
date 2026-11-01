package app.pwhs.universalinstaller.telemetry

import app.pwhs.core.telemetry.TelemetryEvents as CoreEvents

object TelemetryEvents {
    const val INSTALL_STARTED = CoreEvents.INSTALL_STARTED
    const val INSTALL_RESULT = CoreEvents.INSTALL_RESULT
    const val PARAM_METHOD = CoreEvents.PARAM_METHOD
    const val PARAM_RESULT = CoreEvents.PARAM_RESULT
    const val PARAM_FAILURE = CoreEvents.PARAM_FAILURE
    const val PARAM_APK_COUNT = CoreEvents.PARAM_APK_COUNT

    const val RESULT_SUCCESS = CoreEvents.RESULT_SUCCESS
    const val RESULT_FAILURE = CoreEvents.RESULT_FAILURE
    const val RESULT_CANCELLED = CoreEvents.RESULT_CANCELLED

    const val BACKEND_HEALTH = CoreEvents.BACKEND_HEALTH
    const val PARAM_HEALTHY = CoreEvents.PARAM_HEALTHY

    const val DEFAULT_INSTALLER_SET = CoreEvents.DEFAULT_INSTALLER_SET
    const val PARAM_ENABLED = CoreEvents.PARAM_ENABLED
    const val RESULT_BLOCKED = CoreEvents.RESULT_BLOCKED

    const val FEATURE_USED = CoreEvents.FEATURE_USED
    const val PARAM_FEATURE = CoreEvents.PARAM_FEATURE

    const val FEATURE_LAN_SHARE = CoreEvents.FEATURE_LAN_SHARE
    const val FEATURE_VIRUSTOTAL = CoreEvents.FEATURE_VIRUSTOTAL
    const val FEATURE_APK_BACKUP = CoreEvents.FEATURE_APK_BACKUP
    const val FEATURE_INSTALLER_PROFILE = CoreEvents.FEATURE_INSTALLER_PROFILE
    const val FEATURE_BATCH_INSTALL = CoreEvents.FEATURE_BATCH_INSTALL
    const val FEATURE_OBB_COPY = CoreEvents.FEATURE_OBB_COPY
    const val FEATURE_URL_DOWNLOAD = CoreEvents.FEATURE_URL_DOWNLOAD
    const val FEATURE_UNINSTALL = CoreEvents.FEATURE_UNINSTALL
    const val FEATURE_REVIEW_PROMPT = CoreEvents.FEATURE_REVIEW_PROMPT
    const val FEATURE_USER_STATS = CoreEvents.FEATURE_USER_STATS

    const val EVENT_WATCH_SEND_STARTED = CoreEvents.EVENT_WATCH_SEND_STARTED
    const val EVENT_WATCH_SEND_RESULT = CoreEvents.EVENT_WATCH_SEND_RESULT
    const val PARAM_IS_WATCH_APP = CoreEvents.PARAM_IS_WATCH_APP
    const val PARAM_DEVICE_NAME = CoreEvents.PARAM_DEVICE_NAME

    const val PROPERTY_INSTALL_METHOD = CoreEvents.PROPERTY_INSTALL_METHOD
}
