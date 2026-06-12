package app.pwhs.universalinstaller.util

import android.content.Context
import android.os.Build
import android.util.Log
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Diagnostics {

    private const val TAG = "Diagnostics"

    fun readSessionLogs(maxLines: Int = 600): String {
        return try {
            val pid = android.os.Process.myPid()
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

    fun buildFullReport(context: Context, sessionLogs: String): String = buildString {
        val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
        appendLine("=".repeat(60))
        appendLine("UNIVERSAL INSTALLER DIAGNOSTICS REPORT")
        appendLine("Generated: $ts")
        appendLine("=".repeat(60))
        appendLine()
        appendLine(buildDeviceInfo(context))
        appendLine()
        appendLine("=".repeat(60))
        appendLine("SESSION LOGS (current session)")
        appendLine("=".repeat(60))
        appendLine(sessionLogs)
        val crashes = getCrashLogs(context)
        if (crashes != "No crash logs") {
            appendLine()
            appendLine("=".repeat(60))
            appendLine("CRASH REPORTS  (persisted across sessions)")
            appendLine("=".repeat(60))
            appendLine(crashes)
        }
    }

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
}
