package app.pwhs.universalinstaller.presentation.install.controller

enum class RootState {
    /** Flavor does not ship libsu — root will never be available on this build. */
    UNAVAILABLE,

    /** libsu is compiled in but we haven't probed the shell yet. */
    UNKNOWN,

    /** Device reports no root manager / SU binary. */
    NOT_ROOTED,

    /** A root manager is installed but denied this app. */
    DENIED,

    /** Shell is ready — `su` granted. */
    READY,
}
