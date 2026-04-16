package app.pwhs.universalinstaller.domain.model

data class InstalledApp(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val isSystemApp: Boolean,
)
