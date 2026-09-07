package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import com.topjohnwu.superuser.Shell
import java.io.File

internal object RootApkScanner {

    fun isRootAvailable(): Boolean {
        return runCatching {
            when (Shell.isAppGrantedRoot()) {
                true -> true
                false -> false
                null -> {
                    if (File("/system/bin/su").exists() || File("/system/xbin/su").exists()) {
                        Shell.getShell().isRoot
                    } else {
                        false
                    }
                }
            }
        }.getOrDefault(false)
    }

    fun scanRootRestrictedDirs(
        context: Context,
        roots: List<File>,
        supportedExtensions: Set<String>,
        out: MutableMap<String, FoundPackageFile>,
    ) {
        if (!isRootAvailable()) return

        val targetPaths = mutableListOf<String>()
        roots.forEach { root ->
            val dataDir = File(root, "Android/data")
            if (dataDir.exists()) targetPaths.add(dataDir.absolutePath)
            val obbDir = File(root, "Android/obb")
            if (obbDir.exists()) targetPaths.add(obbDir.absolutePath)
        }
        val appData = File("/data/data")
        if (appData.exists()) {
            targetPaths.add("/data/data/*/cache")
        }

        if (targetPaths.isEmpty()) return

        val pathsArg = targetPaths.joinToString(" ")
        val exts = supportedExtensions.joinToString(" -o ") { "-name \"*.$it\"" }
        val cmd = "find $pathsArg -maxdepth 5 \\( $exts \\) -exec stat -c \"%n|%s|%Y\" {} + 2>/dev/null"

        val result = runCatching { Shell.cmd(cmd).exec() }.getOrNull() ?: return
        if (!result.isSuccess) return

        val stageDir = File(context.cacheDir, "scanned_root")
        val ourPackage = context.packageName

        for (line in result.out) {
            val parts = line.split('|')
            val path = parts.getOrNull(0)?.trim() ?: continue
            if (path.isBlank() || out.containsKey(path)) continue
            // Skip Google Play Store internal splits and our own cache dir
            if (path.contains("/com.android.vending/") || path.contains("/$ourPackage/")) continue

            val sizeBytes = parts.getOrNull(1)?.toLongOrNull() ?: 0L
            val modifiedSec = parts.getOrNull(2)?.toLongOrNull() ?: 0L
            val modifiedMillis = if (modifiedSec > 0L) modifiedSec * 1000L else System.currentTimeMillis()

            val origFile = File(path)
            val ext = origFile.extension.lowercase()
            if (ext !in supportedExtensions) continue

            // If file is in a root-restricted directory not readable by our app process,
            // stage it into our cache directory via root so we can parse package metadata, load icon, and install it.
            val accessiblePath = if (!origFile.canRead()) {
                val safeParent = origFile.parentFile?.name?.replace(Regex("[^a-zA-Z0-9._-]"), "_") ?: "app"
                val safeName = "${safeParent}_${origFile.name}"
                val staged = File(stageDir, safeName)
                val stageCmd = "MY_UID=\$(stat -c \"%u:%g\" \"${context.applicationInfo.dataDir}\"); mkdir -p \"${stageDir.absolutePath}\"; cp \"$path\" \"${staged.absolutePath}\"; chown -R \"\$MY_UID\" \"${stageDir.absolutePath}\"; chmod 644 \"${staged.absolutePath}\""
                val stageResult = runCatching { Shell.cmd(stageCmd).exec() }.getOrNull()
                if (stageResult?.isSuccess == true && staged.exists() && staged.canRead()) {
                    staged.absolutePath
                } else {
                    path
                }
            } else {
                path
            }

            out[path] = FoundPackageFile(
                path = accessiblePath,
                name = origFile.name,
                sizeBytes = if (sizeBytes > 0L) sizeBytes else origFile.length(),
                modifiedMillis = modifiedMillis,
                extension = ext,
                originalPath = path,
            )
        }
    }
}
