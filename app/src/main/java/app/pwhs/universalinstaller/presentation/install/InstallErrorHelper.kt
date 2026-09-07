package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.os.Build
import app.pwhs.core.util.DeviceCompat
import app.pwhs.universalinstaller.R
import ru.solrudev.ackpine.installer.InstallFailure

object InstallErrorHelper {

    private val REQUIRED_SDK_REGEX = Regex(
        """(?:newer sdk version|sdk version)\s*#?(\d+)""",
        RegexOption.IGNORE_CASE
    )

    data class ErrorInfo(
        val title: String,
        val guidance: String,
    )

    /**
     * Appends the MIUI/HyperOS workaround to [info] when we're on Xiaomi hardware and the failure
     * is one this ROM family is known to cause (issue #104).
     *
     * Kept separate from [getErrorInfo] so it can be applied to the live error only — install
     * history stores the plain diagnosis, not a paragraph of advice that would be replayed on
     * every history card forever.
     */
    fun withDeviceHint(context: Context, info: ErrorInfo, failure: InstallFailure): ErrorInfo {
        if (!DeviceCompat.isXiaomi || !isMiuiSuspect(failure)) return info
        return info.copy(
            guidance = "${info.guidance}\n\n${context.getString(R.string.install_error_miui_hint)}",
        )
    }

    /**
     * `Conflict` covers several unrelated `INSTALL_FAILED_*` codes — a version downgrade, a
     * signature mismatch and a plain duplicate all land here. The old single message named the
     * "Replace existing" toggle for all of them, which sent users who hit a downgrade or a
     * signature mismatch to a switch that was already on and couldn't have helped.
     *
     * ackpine passes the platform's status message through verbatim, so the code is in there.
     */
    private fun conflictGuidance(message: String?): Int {
        val raw = message.orEmpty().uppercase()
        return when {
            "VERSION_DOWNGRADE" in raw -> R.string.install_error_conflict_downgrade_guidance
            // The platform reports a signature mismatch as UPDATE_INCOMPATIBLE, and older/OEM
            // builds sometimes spell it out in prose instead.
            "UPDATE_INCOMPATIBLE" in raw || "SIGNATURES DO NOT MATCH" in raw ->
                R.string.install_error_conflict_signature_guidance
            else -> R.string.install_error_conflict_guidance
        }
    }

    fun sdkToAndroidVersion(sdk: Int): String = when (sdk) {
        21 -> "5.0"
        22 -> "5.1"
        23 -> "6.0"
        24 -> "7.0"
        25 -> "7.1"
        26 -> "8.0"
        27 -> "8.1"
        28 -> "9"
        29 -> "10"
        30 -> "11"
        31 -> "12"
        32 -> "12L"
        33 -> "13"
        34 -> "14"
        35 -> "15"
        36 -> "16"
        37 -> "17"
        else -> sdk.toString()
    }

    private fun incompatibleErrorInfo(context: Context, message: String?): ErrorInfo {
        val raw = message.orEmpty().uppercase()
        return when {
            "OLDER_SDK" in raw || "NEWER SDK" in raw -> {
                val match = REQUIRED_SDK_REGEX.find(message.orEmpty())
                val reqSdk = match?.groupValues?.getOrNull(1)?.toIntOrNull()
                val guidance = if (reqSdk != null) {
                    val reqVer = sdkToAndroidVersion(reqSdk)
                    val currentSdk = Build.VERSION.SDK_INT
                    val currentVer = Build.VERSION.RELEASE.takeIf { !it.isNullOrBlank() }
                        ?: sdkToAndroidVersion(currentSdk)
                    context.getString(
                        R.string.install_error_incompatible_sdk_detailed_guidance,
                        reqVer,
                        reqSdk,
                        currentVer,
                        currentSdk
                    )
                } else {
                    context.getString(R.string.install_error_incompatible_sdk_guidance)
                }
                ErrorInfo(
                    title = context.getString(R.string.install_error_incompatible_sdk_title),
                    guidance = guidance,
                )
            }
            "CPU_ABI" in raw || "NO_MATCHING_ABIS" in raw || "NATIVE_LIBRARIES" in raw -> ErrorInfo(
                title = context.getString(R.string.install_error_incompatible_abi_title),
                guidance = context.getString(R.string.install_error_incompatible_abi_guidance),
            )
            "MISSING_FEATURE" in raw || "FEATURE" in raw -> ErrorInfo(
                title = context.getString(R.string.install_error_incompatible_feature_title),
                guidance = context.getString(R.string.install_error_incompatible_feature_guidance),
            )
            else -> ErrorInfo(
                title = context.getString(R.string.install_error_incompatible_title),
                guidance = context.getString(R.string.install_error_incompatible_guidance),
            )
        }
    }

    /**
     * Failure kinds MIUI/HyperOS "optimization" is known to produce when it silently vetoes a
     * third-party install. Which one surfaces depends on the ROM version, so we cover the whole
     * ambiguous set rather than guessing; the well-diagnosed failures (storage, invalid APK,
     * ABI mismatch, package conflict) keep their own guidance untouched.
     *
     * [InstallFailure.Aborted] is in the "suspect" set on purpose: ackpine's `await()` resolves an
     * in-app cancel by throwing [kotlinx.coroutines.CancellationException], so an Aborted result
     * here is always a *system* abort — precisely what MIUI optimization produces.
     */
    private fun isMiuiSuspect(failure: InstallFailure): Boolean = when (failure) {
        is InstallFailure.Aborted,
        is InstallFailure.Blocked,
        is InstallFailure.Generic,
        -> true
        is InstallFailure.Conflict,
        is InstallFailure.Incompatible,
        is InstallFailure.Invalid,
        is InstallFailure.Storage,
        is InstallFailure.Timeout,
        is InstallFailure.Exceptional,
        -> false
        else -> true
    }

    fun getErrorInfo(context: Context, failure: InstallFailure): ErrorInfo = when (failure) {
        is InstallFailure.Aborted -> ErrorInfo(
            title = context.getString(R.string.install_error_cancelled_title),
            guidance = context.getString(R.string.install_error_cancelled_guidance),
        )
        is InstallFailure.Blocked -> ErrorInfo(
            title = context.getString(R.string.install_error_blocked_title),
            guidance = context.getString(R.string.install_error_blocked_guidance),
        )
        is InstallFailure.Conflict -> ErrorInfo(
            title = context.getString(R.string.install_error_conflict_title),
            guidance = context.getString(conflictGuidance(failure.message)),
        )
        is InstallFailure.Incompatible -> incompatibleErrorInfo(context, failure.message)
        is InstallFailure.Invalid -> ErrorInfo(
            title = context.getString(R.string.install_error_invalid_title),
            guidance = context.getString(R.string.install_error_invalid_guidance),
        )
        is InstallFailure.Storage -> ErrorInfo(
            title = context.getString(R.string.install_error_storage_title),
            guidance = context.getString(R.string.install_error_storage_guidance),
        )
        is InstallFailure.Timeout -> ErrorInfo(
            title = context.getString(R.string.install_error_timeout_title),
            guidance = context.getString(R.string.install_error_timeout_guidance),
        )
        is InstallFailure.Exceptional -> ErrorInfo(
            title = context.getString(R.string.install_error_unexpected_title),
            guidance = context.getString(R.string.install_error_unexpected_guidance, failure.message ?: ""),
        )
        is InstallFailure.Generic -> ErrorInfo(
            title = context.getString(R.string.install_error_failed_title),
            guidance = failure.message ?: context.getString(R.string.install_error_unknown_guidance),
        )
        else -> ErrorInfo(
            title = context.getString(R.string.install_error_failed_title),
            guidance = failure.message ?: context.getString(R.string.install_error_unknown_guidance_short),
        )
    }

    fun getUserFriendlyMessage(context: Context, failure: InstallFailure): String {
        val info = getErrorInfo(context, failure)
        return "${info.title}: ${info.guidance}"
    }

    /**
     * A stable, non-localised name for a failure kind, for telemetry.
     *
     * Deliberately not `failure::class.simpleName`: R8 renames ackpine's classes, so release
     * builds would report a different — and meaningless — name than debug ones. Never include
     * `failure.message`; it carries package and file names.
     */
    fun failureKey(failure: InstallFailure): String = when (failure) {
        is InstallFailure.Aborted -> "aborted"
        is InstallFailure.Blocked -> "blocked"
        is InstallFailure.Conflict -> "conflict"
        is InstallFailure.Incompatible -> "incompatible"
        is InstallFailure.Invalid -> "invalid"
        is InstallFailure.Storage -> "storage"
        is InstallFailure.Timeout -> "timeout"
        is InstallFailure.Exceptional -> "exceptional"
        is InstallFailure.Generic -> "generic"
        else -> "unknown"
    }
}
