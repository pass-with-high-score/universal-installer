package app.pwhs.universalinstaller.util

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Process
import android.os.StatFs
import android.util.Log
import androidx.core.content.FileProvider
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.RootShell
import app.pwhs.universalinstaller.BuildConfig
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object Diagnostics {

    private const val TAG = "Diagnostics"

    fun readSessionLogs(maxLines: Int = 600): String {
        return try {
            val pid = Process.myPid()
            val process = ProcessBuilder(
                "logcat", "-v", "threadtime", "--pid=$pid", "-d", "-t", maxLines.toString()
            )
                .redirectErrorStream(true)
                .start()
            val output = process.inputStream.bufferedReader(Charsets.UTF_8).readText()
            process.waitFor()
            output.ifBlank { "No warnings or errors found in this session." }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read session logcat", e)
            "Unable to read session logs: ${e.message}\n\n" +
                    "This can happen on some heavily customized Android skins."
        }
    }

    fun getCrashLogs(context: Context): String =
        CrashHandler.getCrashLogs(context)

    fun clearCrashLogs(context: Context) =
        CrashHandler.clearCrashLogs(context)

    fun buildDeviceInfo(context: Context): String = buildString {
        appendLine("Manufacturer : ${Build.MANUFACTURER}")
        appendLine("Model        : ${Build.MODEL}")
        appendLine("Android      : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Brand        : ${Build.BRAND}")
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION") pi.versionCode.toString()
            }
            appendLine("App version  : ${pi.versionName} ($code)")
        } catch (_: Exception) {
            appendLine("App version  : unknown")
        }
    }.trimEnd()

    suspend fun buildFullReport(context: Context, sessionLogs: String): String = buildString {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        appendLine("=".repeat(60))
        appendLine("UNIVERSAL INSTALLER DIAGNOSTICS REPORT")
        appendLine("Generated: $ts (${TimeZone.getDefault().id})")
        appendLine("=".repeat(60))
        appendLine()

        appendLine("[DEVICE & HARDWARE]")
        appendLine("Manufacturer       : ${Build.MANUFACTURER}")
        appendLine("Brand              : ${Build.BRAND}")
        appendLine("Model              : ${Build.MODEL}")
        appendLine("Device             : ${Build.DEVICE}")
        appendLine("Product            : ${Build.PRODUCT}")
        appendLine("Hardware           : ${Build.HARDWARE}")
        appendLine("Board              : ${Build.BOARD}")
        appendLine("Fingerprint        : ${Build.FINGERPRINT}")
        appendLine("Supported ABIs     : ${Build.SUPPORTED_ABIS.joinToString(", ")}")
        appendLine()

        appendLine("[OPERATING SYSTEM]")
        appendLine("Android Version    : ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})")
        appendLine("Build ID / Display : ${Build.DISPLAY}")
        appendLine("Build ID           : ${Build.ID}")
        appendLine("Incremental        : ${Build.VERSION.INCREMENTAL}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            appendLine("Security Patch     : ${Build.VERSION.SECURITY_PATCH}")
        }
        appendLine()

        appendLine("[APPLICATION INFO]")
        appendLine("Package Name       : ${context.packageName}")
        try {
            val pi = context.packageManager.getPackageInfo(context.packageName, 0)
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode.toString()
            } else {
                @Suppress("DEPRECATION") pi.versionCode.toString()
            }
            appendLine("App Version        : ${pi.versionName} ($code)")
        } catch (_: Exception) {
            appendLine("App Version        : unknown")
        }
        appendLine("Build Type / Flavor: ${BuildConfig.BUILD_TYPE} (${BuildConfig.FLAVOR})")
        appendLine("Target SDK         : ${context.applicationInfo.targetSdkVersion}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appendLine("Min SDK            : ${context.applicationInfo.minSdkVersion}")
        }
        appendLine("Installer Source   : ${getInstallerSource(context)}")
        appendLine()

        appendLine("[PRIVILEGED ENVIRONMENT]")
        appendLine(buildPrivilegedSummary(context))
        appendLine()

        appendLine("[PREFERENCES & CONFIGURATION]")
        appendLine(buildPreferencesSummary(context))
        appendLine()

        appendLine("[DEFAULT ROLES RESOLUTION]")
        appendLine("Default Installer   : ${resolveDefaultInstaller(context)}")
        appendLine("Default Uninstaller : ${resolveDefaultUninstaller(context)}")
        appendLine()

        appendLine("[MEMORY & STORAGE]")
        appendLine("RAM State          : ${getMemoryInfo(context)}")
        appendLine("Internal Storage   : ${getStorageInfo(context)}")
        appendLine("System Locale      : ${Locale.getDefault().toLanguageTag()}")
        appendLine()

        appendLine("=".repeat(60))
        appendLine("SESSION LOGS (current session)")
        appendLine("=".repeat(60))
        appendLine(sessionLogs)

        val crashes = getCrashLogs(context)
        if (crashes != "No crash logs") {
            appendLine()
            appendLine("=".repeat(60))
            appendLine("CRASH REPORTS (persisted across sessions)")
            appendLine("=".repeat(60))
            appendLine(crashes)
        }
    }

    suspend fun createReportFile(context: Context, sessionLogs: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "diagnostics").apply { mkdirs() }
        // Clean up log files older than 24 hours
        dir.listFiles()?.forEach { file ->
            if (System.currentTimeMillis() - file.lastModified() > 24 * 60 * 60 * 1000) {
                file.delete()
            }
        }
        val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(dir, "universal_installer_diagnostics_$ts.log")
        file.writeText(buildFullReport(context, sessionLogs), Charsets.UTF_8)
        file
    }

    suspend fun shareReportFile(context: Context, sessionLogs: String) = withContext(Dispatchers.IO) {
        try {
            val file = createReportFile(context, sessionLogs)
            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Universal Installer Diagnostics Report")
                putExtra(
                    Intent.EXTRA_TEXT,
                    "Universal Installer Diagnostics Report (${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE})"
                )
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            withContext(Dispatchers.Main) {
                runCatching {
                    context.startActivity(
                        Intent.createChooser(intent, context.getString(R.string.diagnostics_share))
                    )
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to share diagnostics file, falling back to text", e)
            val report = buildFullReport(context, sessionLogs)
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, "Universal Installer Diagnostics Report")
                putExtra(Intent.EXTRA_TEXT, report)
            }
            withContext(Dispatchers.Main) {
                runCatching {
                    context.startActivity(
                        Intent.createChooser(intent, context.getString(R.string.diagnostics_share))
                    )
                }
            }
        }
    }

    private suspend fun buildPrivilegedSummary(context: Context): String = buildString {
        val rootAvailable = runCatching { RootShell.isAvailable() }.getOrDefault(false)
        appendLine("Root Available     : $rootAvailable")

        val shizukuInstalled = ShizukuServices.isShizukuInstalled(context)
        val porterInstalled = ShizukuServices.isPorterInstalled(context)
        appendLine("Shizuku Installed  : $shizukuInstalled (Porter: $porterInstalled)")

        val shizukuPing = runCatching { Shizuku.pingBinder() }.getOrDefault(false)
        appendLine("Shizuku Binder Ping: $shizukuPing")
        if (shizukuPing) {
            val version = runCatching { Shizuku.getVersion() }.getOrNull()
            val perm = runCatching {
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
            appendLine("Shizuku Version    : $version")
            appendLine("Shizuku Permission : ${if (perm) "GRANTED" else "NOT_GRANTED"}")
        }

        val dhizukuSupported = DhizukuCompat.isSupported
        val dhizukuInstalled = runCatching {
            context.packageManager.getPackageInfo(DhizukuCompat.PACKAGE_NAME, 0)
            true
        }.getOrDefault(false)
        appendLine("Dhizuku Supported  : $dhizukuSupported (Installed: $dhizukuInstalled)")

        appendLine("Process UID / PID  : ${Process.myUid()} / ${Process.myPid()}")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            appendLine("User Handle Hash   : ${Process.myUserHandle().hashCode()}")
        }
    }.trimEnd()

    private suspend fun buildPreferencesSummary(context: Context): String = try {
        val prefs = context.dataStore.data.firstOrNull() ?: return "Preferences unavailable"
        buildString {
            appendLine("Install Backend Priority : ${prefs[PreferencesKeys.INSTALL_BACKEND_PRIORITY] ?: "Default"}")
            appendLine("Use Root                 : ${prefs[PreferencesKeys.USE_ROOT] ?: false}")
            appendLine("Use Shizuku              : ${prefs[PreferencesKeys.USE_SHIZUKU] ?: false}")
            appendLine("Use Dhizuku              : ${prefs[PreferencesKeys.USE_DHIZUKU] ?: false}")
            appendLine("Use Custom Authorizer    : ${prefs[PreferencesKeys.USE_CUSTOM_AUTHORIZER] ?: false}")
            appendLine("Install UI Style         : ${prefs[PreferencesKeys.INSTALL_UI_STYLE] ?: "Default"}")
            appendLine("External Open Mode       : ${prefs[PreferencesKeys.EXTERNAL_OPEN_MODE] ?: "Default"}")
            appendLine("Target User ID           : ${prefs[PreferencesKeys.INSTALL_USER_ID] ?: 0}")
            appendLine("Delete APK After Install : ${prefs[PreferencesKeys.DELETE_APK_AFTER_INSTALL] ?: false}")
            appendLine("Strict VirusTotal Check  : ${prefs[PreferencesKeys.STRICT_VIRUSTOTAL_CHECK] ?: false}")
            appendLine("Has VirusTotal Key       : ${!prefs[PreferencesKeys.VIRUSTOTAL_API_KEY].isNullOrBlank()}")
            appendLine("Shizuku Bypass Low SDK   : ${prefs[PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK] ?: false}")
        }.trimEnd()
    } catch (e: Exception) {
        "Error reading preferences: ${e.message}"
    }

    private fun resolveDefaultInstaller(context: Context): String = try {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(Uri.parse("file:///dummy.apk"), "application/vnd.android.package-archive")
        }
        val resolve = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)
        resolve?.activityInfo?.packageName ?: "None"
    } catch (e: Exception) {
        "Unknown (${e.message})"
    }

    @Suppress("DEPRECATION")
    private fun resolveDefaultUninstaller(context: Context): String = try {
        val pkg = context.packageName
        val rDelete = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val rUninstall = context.packageManager.resolveActivity(
            Intent(Intent.ACTION_UNINSTALL_PACKAGE, Uri.parse("package:$pkg")),
            PackageManager.MATCH_DEFAULT_ONLY
        )
        val delPkg = rDelete?.activityInfo?.packageName ?: "None"
        val unPkg = rUninstall?.activityInfo?.packageName ?: "None"
        if (delPkg == unPkg) delPkg else "Delete: $delPkg, Uninstall: $unPkg"
    } catch (e: Exception) {
        "Unknown (${e.message})"
    }

    private fun getMemoryInfo(context: Context): String = try {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am?.getMemoryInfo(mem)
        if (am != null) {
            val total = formatBytes(mem.totalMem)
            val avail = formatBytes(mem.availMem)
            "$avail free of $total (low memory: ${mem.lowMemory})"
        } else "Unavailable"
    } catch (_: Exception) {
        "Unavailable"
    }

    private fun getStorageInfo(context: Context): String = try {
        val path = context.filesDir
        val stat = StatFs(path.path)
        val avail = formatBytes(stat.availableBytes)
        val total = formatBytes(stat.totalBytes)
        "$avail free of $total"
    } catch (_: Exception) {
        "Unavailable"
    }

    private fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    private fun getInstallerSource(context: Context): String = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val info = context.packageManager.getInstallSourceInfo(context.packageName)
            val initiating = info.initiatingPackageName
            val installing = info.installingPackageName
            if (initiating != null && initiating != installing) "$installing (init: $initiating)" else installing ?: "sideload"
        } else {
            @Suppress("DEPRECATION")
            context.packageManager.getInstallerPackageName(context.packageName) ?: "sideload"
        }
    } catch (_: Exception) {
        "unknown"
    }
}
