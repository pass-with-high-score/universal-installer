package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.net.Uri
import app.pwhs.core.security.GhostMapPregate
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.InstallerProfile
import app.pwhs.universalinstaller.presentation.install.ObbEntry
import app.pwhs.universalinstaller.presentation.install.ObbExtractor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.splits.SplitPackage

data class ParseResult(
    val info: ApkInfo,
    val obbEntries: List<ObbEntry>,
    val splitUris: List<Uri>?,
    val matchingProfileId: String?,
)

object InstallParseCoordinator {

    suspend fun parseApkInfo(
        context: Context,
        uri: Uri,
        splitPackage: SplitPackage.Provider,
        fileName: String,
        isAndroidAuto: Boolean?,
        blacklist: Set<String>,
        currentProfiles: List<InstallerProfile>,
        appProfileMapping: Map<String, String>,
    ): ParseResult = withContext(Dispatchers.IO) {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        if (ext == "apk") {
            val pregateCheck = GhostMapPregate.validateUri(context, uri)
            if (pregateCheck is GhostMapPregate.ValidationResult.Invalid) {
                throw GhostMapPregate.GhostMapSecurityException(pregateCheck.reason, pregateCheck.entryName)
            }
        }

        var splitUris: List<Uri>? = null
        val info = InstallApkSplitsHelper.extractApkInfoAndCacheUris(
            context = context,
            originalUri = uri,
            splitPackage = splitPackage,
            fileName = fileName,
            isBlocked = false,
        ) { splitUris = it }

        val obbEntries = if (ext in setOf("apks", "xapk", "apkm", "zip")) ObbExtractor.scan(context, uri) else emptyList()
        val installed = InstallSessionManager.lookupInstalledVersion(context, info.packageName)
        val installedPermissions = if (installed != null) {
            InstallSessionManager.lookupInstalledPermissions(context, info.packageName)
        } else {
            emptyList()
        }
        val aaSupported = isAndroidAuto ?: info.isAndroidAutoSupported

        val fullInfo = info.copy(
            obbFileNames = obbEntries.map { it.fileName },
            obbTotalBytes = obbEntries.sumOf { it.sizeBytes.coerceAtLeast(0L) },
            installedVersionName = installed?.first,
            installedVersionCode = installed?.second,
            installedPermissions = installedPermissions,
            isBlocked = info.packageName in blacklist,
            isAndroidAutoSupported = aaSupported,
        )

        val matchingProfileId = appProfileMapping[info.packageName]?.let { profileId ->
            if (currentProfiles.any { it.id == profileId }) profileId else null
        }

        ParseResult(
            info = fullInfo,
            obbEntries = obbEntries,
            splitUris = splitUris,
            matchingProfileId = matchingProfileId,
        )
    }
}
