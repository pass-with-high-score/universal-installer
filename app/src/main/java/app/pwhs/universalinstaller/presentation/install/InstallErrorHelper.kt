package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import app.pwhs.universalinstaller.R
import ru.solrudev.ackpine.installer.InstallFailure

object InstallErrorHelper {

    data class ErrorInfo(
        val title: String,
        val guidance: String,
    )

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
            guidance = context.getString(R.string.install_error_conflict_guidance),
        )
        is InstallFailure.Incompatible -> ErrorInfo(
            title = context.getString(R.string.install_error_incompatible_title),
            guidance = context.getString(R.string.install_error_incompatible_guidance),
        )
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
}
