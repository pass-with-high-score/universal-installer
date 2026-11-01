package app.pwhs.universalinstaller.presentation.install

import app.pwhs.universalinstaller.presentation.install.util.AbiCompatibilityHelper
import ru.solrudev.ackpine.installer.InstallFailure

/**
 * Handles classification of install failure codes and technical error types for telemetry and reporting.
 */
object InstallErrorClassifier {

    private val PM_ERROR_CODE_REGEX = Regex("""\b(INSTALL_(?:PARSE_)?FAILED_[A-Z0-9_]+)\b""")

    /**
     * A stable, non-localised name for a failure kind, for telemetry.
     */
    fun failureKey(failure: InstallFailure): String {
        if (InstallErrorHelper.isFrpFailure(failure)) {
            return "security_frp"
        }
        return when (failure) {
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

    /**
     * Extracts a standard Android PackageManager error code.
     * Never returns file names or package names to preserve privacy.
     */
    fun extractErrorCode(failure: InstallFailure): String {
        if (InstallErrorHelper.isFrpFailure(failure)) {
            return "INSTALL_FAILED_SECURITY_FRP"
        }
        val rawMessage = failure.message.orEmpty()
        if (AbiCompatibilityHelper.isAbiErrorMessage(rawMessage)) {
            return "INSTALL_FAILED_CPU_ABI_INCOMPATIBLE"
        }
        if (InstallErrorHelper.isNoCertificatesMessage(rawMessage)) {
            return "INSTALL_PARSE_FAILED_NO_CERTIFICATES"
        }
        if (InstallErrorHelper.isMissingSplitMessage(rawMessage)) {
            return "INSTALL_FAILED_MISSING_SPLIT"
        }
        if (InstallErrorHelper.isSecurityMessage(rawMessage)) {
            return "INSTALL_FAILED_SECURITY_EXCEPTION"
        }
        if (InstallErrorHelper.isIsolationViolationMessage(rawMessage)) {
            val match = PM_ERROR_CODE_REGEX.find(rawMessage)
            if (match != null) return match.groupValues[1]
            return "INSTALL_FAILED_HYPEROS_ISOLATION_VIOLATION"
        }
        val match = PM_ERROR_CODE_REGEX.find(rawMessage)
        if (match != null) {
            return match.groupValues[1]
        }
        return when (failure) {
            is InstallFailure.Aborted -> "INSTALL_FAILED_ABORTED"
            is InstallFailure.Storage -> "INSTALL_FAILED_INSUFFICIENT_STORAGE"
            is InstallFailure.Conflict -> {
                val upper = rawMessage.uppercase()
                if ("VERSION_DOWNGRADE" in upper) "INSTALL_FAILED_VERSION_DOWNGRADE"
                else "INSTALL_FAILED_UPDATE_INCOMPATIBLE"
            }
            is InstallFailure.Invalid -> "INSTALL_FAILED_INVALID_APK"
            is InstallFailure.Incompatible -> {
                val upper = rawMessage.uppercase()
                when {
                    "CPU_ABI" in upper || "NO_MATCHING_ABIS" in upper -> "INSTALL_FAILED_CPU_ABI_INCOMPATIBLE"
                    "OLDER_SDK" in upper -> "INSTALL_FAILED_OLDER_SDK"
                    "NEWER_SDK" in upper -> "INSTALL_FAILED_NEWER_SDK"
                    "FEATURE" in upper -> "INSTALL_FAILED_MISSING_FEATURE"
                    else -> "INSTALL_FAILED_INCOMPATIBLE"
                }
            }
            is InstallFailure.Blocked -> "INSTALL_FAILED_USER_RESTRICTED"
            is InstallFailure.Timeout -> "INSTALL_FAILED_VERIFICATION_TIMEOUT"
            is InstallFailure.Exceptional, is InstallFailure.Generic -> "INSTALL_FAILED_INTERNAL_ERROR"
            else -> "INSTALL_FAILED_UNKNOWN"
        }
    }

    /**
     * Classifies a failure into high-level technical categories for telemetry reporting.
     */
    fun classifyErrorType(failure: InstallFailure, errorCode: String = extractErrorCode(failure)): String {
        if (InstallErrorHelper.isFrpFailure(failure) || errorCode == "INSTALL_FAILED_SECURITY_FRP" || errorCode.equals("security_frp", ignoreCase = true)) {
            return "security_frp"
        }
        val upperCode = errorCode.uppercase()
        val rawMessage = failure.message.orEmpty().uppercase()
        return when {
            InstallErrorHelper.isIsolationViolationMessage(upperCode) || InstallErrorHelper.isIsolationViolationMessage(rawMessage) ->
                "os_blocked"
            InstallErrorHelper.isMissingSplitMessage(upperCode) || InstallErrorHelper.isMissingSplitMessage(rawMessage) ->
                "missing_split"
            InstallErrorHelper.isSecurityMessage(upperCode) || InstallErrorHelper.isSecurityMessage(rawMessage) ->
                "permission_denied"
            "SIGNATURE" in upperCode || "UPDATE_INCOMPATIBLE" in upperCode || "CERTIFICATE" in upperCode || "SIGNATURES DO NOT MATCH" in rawMessage ->
                "signature_mismatch"
            "DOWNGRADE" in upperCode || "VERSION_DOWNGRADE" in rawMessage ->
                "version_downgrade"
            "STORAGE" in upperCode || failure is InstallFailure.Storage ->
                "insufficient_storage"
            "PARSE" in upperCode || "INVALID_APK" in upperCode || "BAD_MANIFEST" in upperCode || failure is InstallFailure.Invalid ->
                "corrupted_package"
            "ABI" in upperCode || "SDK" in upperCode || "FEATURE" in upperCode || failure is InstallFailure.Incompatible ->
                "incompatible_device"
            "RESTRICTED" in upperCode || "PERMISSION" in upperCode || failure is InstallFailure.Blocked ->
                "permission_denied"
            "ABORTED" in upperCode || failure is InstallFailure.Aborted ->
                "system_aborted"
            "TIMEOUT" in upperCode || failure is InstallFailure.Timeout ->
                "verification_timeout"
            else -> "os_blocked"
        }
    }
}
