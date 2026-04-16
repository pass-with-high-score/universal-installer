package app.pwhs.universalinstaller.presentation.install

import ru.solrudev.ackpine.installer.InstallFailure

object InstallErrorHelper {

    data class ErrorInfo(
        val title: String,
        val guidance: String,
    )

    fun getErrorInfo(failure: InstallFailure): ErrorInfo = when (failure) {
        is InstallFailure.Aborted -> ErrorInfo(
            title = "Installation cancelled",
            guidance = "The installation was cancelled by the user or system. Tap Retry to try again.",
        )
        is InstallFailure.Blocked -> ErrorInfo(
            title = "Installation blocked",
            guidance = "Your device policy or security settings blocked this install. Check if unknown sources are allowed in Settings > Apps > Special access.",
        )
        is InstallFailure.Conflict -> ErrorInfo(
            title = "Package conflict",
            guidance = "A conflicting version is already installed. Uninstall the existing app first, or enable \"Replace existing\" in Shizuku options.",
        )
        is InstallFailure.Incompatible -> ErrorInfo(
            title = "Incompatible package",
            guidance = "This APK is not compatible with your device. It may require a different CPU architecture, Android version, or missing hardware feature.",
        )
        is InstallFailure.Invalid -> ErrorInfo(
            title = "Invalid package",
            guidance = "The APK file is corrupted or not a valid Android package. Try downloading it again from a trusted source.",
        )
        is InstallFailure.Storage -> ErrorInfo(
            title = "Not enough storage",
            guidance = "Your device doesn't have enough free space. Free up storage in Settings > Storage and try again.",
        )
        is InstallFailure.Timeout -> ErrorInfo(
            title = "Installation timed out",
            guidance = "The installation took too long and was cancelled. Tap Retry to try again.",
        )
        is InstallFailure.Exceptional -> ErrorInfo(
            title = "Unexpected error",
            guidance = "An unexpected error occurred: ${failure.message}. Try restarting the app or your device.",
        )
        is InstallFailure.Generic -> ErrorInfo(
            title = "Installation failed",
            guidance = failure.message ?: "An unknown error occurred. Tap Retry to try again.",
        )
        else -> ErrorInfo(
            title = "Installation failed",
            guidance = failure.message ?: "An unknown error occurred.",
        )
    }

    fun getUserFriendlyMessage(failure: InstallFailure): String {
        val info = getErrorInfo(failure)
        return "${info.title}: ${info.guidance}"
    }
}
