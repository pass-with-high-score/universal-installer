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
    fun withDeviceHint(
        context: Context,
        info: ErrorInfo,
        failure: InstallFailure,
        isShizuku: Boolean = false,
    ): ErrorInfo {
        if (!DeviceCompat.isXiaomi || !isMiuiSuspect(failure)) return info
        val hintRes = if (isShizuku) {
            R.string.install_error_miui_shizuku_hint
        } else {
            R.string.install_error_miui_hint
        }
        return info.copy(
            guidance = "${info.guidance}\n\n${context.getString(hintRes)}",
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
            "CPU_ABI" in raw || "NO_MATCHING_ABIS" in raw || "NATIVE_LIBRARIES" in raw || "RES=-113" in raw -> {
                val deviceAbis = Build.SUPPORTED_ABIS.joinToString(", ")
                val recommended = if (app.pwhs.universalinstaller.presentation.install.util.AbiCompatibilityHelper.isDevice32BitOnly()) {
                    "32-bit (armeabi-v7a)"
                } else if (app.pwhs.universalinstaller.presentation.install.util.AbiCompatibilityHelper.isDevice64BitOnly()) {
                    "64-bit (arm64-v8a)"
                } else {
                    "Universal"
                }
                val apkAbi = when {
                    "ARM64" in raw || "AARCH64" in raw -> "arm64-v8a (64-bit)"
                    "ARMEABI" in raw || "ARMV7" in raw -> "armeabi-v7a (32-bit)"
                    "X86_64" in raw -> "x86_64"
                    "X86" in raw -> "x86"
                    else -> "a different"
                }
                ErrorInfo(
                    title = context.getString(R.string.install_error_incompatible_abi_title),
                    guidance = context.getString(
                        R.string.install_error_incompatible_abi_detailed_guidance,
                        apkAbi,
                        deviceAbis,
                        recommended,
                    ),
                )
            }
            "UPDATE_INCOMPATIBLE" in raw || "SIGNATURES DO NOT MATCH" in raw -> ErrorInfo(
                title = context.getString(R.string.install_error_conflict_title),
                guidance = context.getString(R.string.install_error_conflict_signature_guidance),
            )
            "MISSING_FEATURE" in raw || "FEATURE" in raw -> ErrorInfo(
                title = context.getString(R.string.install_error_incompatible_feature_title),
                guidance = context.getString(R.string.install_error_incompatible_feature_guidance),
            )
            else -> {
                val baseGuidance = context.getString(R.string.install_error_incompatible_guidance)
                val detail = message?.takeIf { it.isNotBlank() }
                ErrorInfo(
                    title = context.getString(R.string.install_error_incompatible_title),
                    guidance = if (detail != null) "$baseGuidance\n($detail)" else baseGuidance,
                )
            }
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
    fun isFrpMessage(message: String): Boolean {
        val upper = message.uppercase()
        return "SECURE FRP" in upper || ("FRP" in upper && ("INSTALL" in upper || "SECURITY" in upper || "DEVICE" in upper || "CAN'T INSTALL" in upper))
    }

    fun isFrpException(throwable: Throwable?): Boolean {
        var current: Throwable? = throwable
        while (current != null) {
            if (current is SecurityException && isFrpMessage(current.message.orEmpty())) {
                return true
            }
            if (isFrpMessage(current.message.orEmpty())) {
                return true
            }
            current = current.cause
        }
        return false
    }

    fun isFrpFailure(failure: InstallFailure): Boolean {
        if (failure is InstallFailure.Exceptional) {
            if (isFrpException(failure.exception)) return true
        }
        return isFrpMessage(failure.message.orEmpty())
    }

    fun isIsolationViolationMessage(message: String?): Boolean {
        val upper = message.orEmpty().uppercase()
        return "ISOLATION_VIOLATION" in upper ||
                "HYPEROS" in upper ||
                "COLOROS" in upper
    }

    fun isMiuiSuspect(failure: InstallFailure): Boolean {
        if (isFrpFailure(failure)) return false
        val msg = failure.message.orEmpty()
        if (isIsolationViolationMessage(msg)) return true
        if (failure is InstallFailure.Exceptional && isIsolationViolationMessage(failure.exception.message)) {
            return true
        }
        return when (failure) {
            is InstallFailure.Aborted,
            is InstallFailure.Blocked,
            is InstallFailure.Generic,
            -> true
            is InstallFailure.Incompatible -> {
                val raw = msg.uppercase()
                !app.pwhs.universalinstaller.presentation.install.util.AbiCompatibilityHelper.isAbiErrorMessage(raw) &&
                    "OLDER_SDK" !in raw && "NEWER SDK" !in raw && "MISSING_FEATURE" !in raw
            }
            is InstallFailure.Conflict,
            is InstallFailure.Invalid,
            is InstallFailure.Storage,
            is InstallFailure.Timeout,
            is InstallFailure.Exceptional,
            -> false
            else -> true
        }
    }

    fun isNoCertificatesMessage(message: String?): Boolean {
        val upper = message.orEmpty().uppercase()
        return "NO_CERTIFICATE" in upper || "NO_CERTIFICATES" in upper || "NO CERTIFICATE" in upper || "NO CERTIFICATES" in upper
    }

    fun isMissingSplitMessage(message: String?): Boolean {
        val upper = message.orEmpty().uppercase()
        return "MISSING_SPLIT" in upper
    }

    fun isSecurityMessage(message: String?): Boolean {
        val upper = message.orEmpty().uppercase()
        return "SECURITYEXCEPTION" in upper ||
                "SECURITY_EXCEPTION" in upper ||
                "STATUS_FAILURE_SECURITY" in upper ||
                "SESSION DOES NOT BELONG" in upper ||
                ("PERMISSION DENIED" in upper && "INSTALL" in upper)
    }

    fun isInternalErrorMessage(message: String?): Boolean {
        val upper = message.orEmpty().uppercase()
        return "INTERNAL_ERROR" in upper || "FAILED TO COMMIT" in upper
    }

    fun shouldSuggestShizuku(message: String?): Boolean {
        val upper = message.orEmpty().uppercase()
        return isSecurityMessage(upper) ||
                isInternalErrorMessage(upper) ||
                isIsolationViolationMessage(upper) ||
                "STATUS_FAILURE" in upper ||
                "INSTALL_FAILED_INTERNAL_ERROR" in upper ||
                "USER_RESTRICTED" in upper ||
                "SECURITYEXCEPTION" in upper
    }

    fun getErrorInfo(context: Context, failure: InstallFailure): ErrorInfo {
        if (isFrpFailure(failure)) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_security_frp_title),
                guidance = context.getString(R.string.install_error_security_frp_guidance),
            )
        }
        val msg = failure.message.orEmpty()
        if (app.pwhs.universalinstaller.presentation.install.util.AbiCompatibilityHelper.isAbiErrorMessage(msg)) {
            return incompatibleErrorInfo(context, msg)
        }
        if (isNoCertificatesMessage(msg)) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_no_certificates_title),
                guidance = context.getString(R.string.install_error_no_certificates_guidance),
            )
        }
        if (isMissingSplitMessage(msg)) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_missing_split_title),
                guidance = context.getString(R.string.install_error_missing_split_guidance),
            )
        }
        if (isSecurityMessage(msg) || (failure is InstallFailure.Exceptional && failure.exception is SecurityException)) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_security_title),
                guidance = context.getString(R.string.install_error_security_guidance),
            )
        }
        if (isIsolationViolationMessage(msg) || (failure is InstallFailure.Exceptional && isIsolationViolationMessage(failure.exception.message))) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_blocked_title),
                guidance = msg.ifEmpty { failure.message.orEmpty() },
            )
        }
        if (isInternalErrorMessage(msg)) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_internal_title),
                guidance = context.getString(R.string.install_error_internal_guidance),
            )
        }
        return when (failure) {
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
    }

    fun getUserFriendlyMessage(context: Context, failure: InstallFailure): String {
        val info = getErrorInfo(context, failure)
        return "${info.title}: ${info.guidance}"
    }

    /**
     * Resolves exceptions originating from shell commands (libsu root, custom authorizer) into
     * user-friendly structured [ErrorInfo] instead of surfacing raw terminal dumps.
     */
    fun resolveException(context: Context, message: String?): ErrorInfo {
        if (isFrpMessage(message.orEmpty())) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_security_frp_title),
                guidance = context.getString(R.string.install_error_security_frp_guidance),
            )
        }
        if (app.pwhs.universalinstaller.presentation.install.util.AbiCompatibilityHelper.isAbiErrorMessage(message)) {
            return incompatibleErrorInfo(context, message)
        }
        if (isNoCertificatesMessage(message)) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_no_certificates_title),
                guidance = context.getString(R.string.install_error_no_certificates_guidance),
            )
        }
        if (isMissingSplitMessage(message)) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_missing_split_title),
                guidance = context.getString(R.string.install_error_missing_split_guidance),
            )
        }
        if (isSecurityMessage(message)) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_security_title),
                guidance = context.getString(R.string.install_error_security_guidance),
            )
        }
        if (isIsolationViolationMessage(message)) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_blocked_title),
                guidance = message.orEmpty(),
            )
        }
        if (isInternalErrorMessage(message)) {
            return ErrorInfo(
                title = context.getString(R.string.install_error_internal_title),
                guidance = context.getString(R.string.install_error_internal_guidance),
            )
        }
        val raw = message.orEmpty().uppercase()
        return when {
            "VERSION_DOWNGRADE" in raw -> ErrorInfo(
                title = context.getString(R.string.install_error_conflict_title),
                guidance = context.getString(R.string.install_error_conflict_downgrade_guidance),
            )
            "UPDATE_INCOMPATIBLE" in raw || "SIGNATURES DO NOT MATCH" in raw -> ErrorInfo(
                title = context.getString(R.string.install_error_conflict_title),
                guidance = context.getString(R.string.install_error_conflict_signature_guidance),
            )
            "INSUFFICIENT_STORAGE" in raw -> ErrorInfo(
                title = context.getString(R.string.install_error_storage_title),
                guidance = context.getString(R.string.install_error_storage_guidance),
            )
            else -> ErrorInfo(
                title = context.getString(R.string.install_error_failed_title),
                guidance = message ?: context.getString(R.string.install_error_unknown_guidance),
            )
        }
    }

    /**
     * A stable, non-localised name for a failure kind, for telemetry.
     */
    fun failureKey(failure: InstallFailure): String = InstallErrorClassifier.failureKey(failure)

    /**
     * Extracts a standard Android PackageManager error code.
     */
    fun extractErrorCode(failure: InstallFailure): String = InstallErrorClassifier.extractErrorCode(failure)

    /**
     * Classifies a failure into high-level technical categories for GA4 dimension reporting.
     */
    fun classifyErrorType(failure: InstallFailure, errorCode: String = extractErrorCode(failure)): String =
        InstallErrorClassifier.classifyErrorType(failure, errorCode)
}
