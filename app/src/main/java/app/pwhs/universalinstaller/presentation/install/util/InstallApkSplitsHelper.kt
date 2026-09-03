package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import app.pwhs.core.util.WatchAppCheck
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SplitEntry
import app.pwhs.universalinstaller.domain.model.SplitType
import app.pwhs.universalinstaller.presentation.install.SingletonApkSequence
import app.pwhs.universalinstaller.util.SignatureCheck
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.splits.Apk
import ru.solrudev.ackpine.splits.ApkSplits.validate
import ru.solrudev.ackpine.splits.CloseableSequence
import ru.solrudev.ackpine.splits.SplitPackage
import ru.solrudev.ackpine.splits.SplitPackage.Companion.toSplitPackage
import ru.solrudev.ackpine.splits.ZippedApkSplits
import ru.solrudev.ackpine.splits.get
import timber.log.Timber
import java.io.File
import java.util.Locale
import java.util.zip.ZipFile

object InstallApkSplitsHelper {

    private val ABI_TOKENS = setOf(
        "armeabi_v7a", "arm64_v8a", "x86_64", "armeabi", "x86", "mips64", "mips",
    )
    private val DPI_TOKENS = setOf(
        "ldpi", "mdpi", "tvdpi", "hdpi", "xhdpi", "xxhdpi", "xxxhdpi",
    )

    fun buildSplitProvider(
        context: Context,
        uri: Uri,
        extension: String,
    ): SplitPackage.Provider {
        return when {
            extension == "apk" ||
                    context.contentResolver.getType(uri)?.lowercase() ==
                    "application/vnd.android.package-archive" ->
                SingletonApkSequence(uri, context).toSplitPackage()
            extension in listOf("apks", "xapk", "apkm", "zip") ->
                ZippedApkSplits.getApksForUri(uri, context)
                    .validate()
                    .toSplitPackage()
                    .filterCompatible(context)
            else -> SingletonApkSequence(uri, context).toSplitPackage()
        }
    }

    suspend fun extractApkInfoAndCacheUris(
        context: Context,
        originalUri: Uri,
        splitPackage: SplitPackage.Provider,
        fileName: String,
        isBlocked: Boolean,
        onPendingUrisResolved: (List<Uri>) -> Unit,
    ): ApkInfo {
        val pm = context.packageManager
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val fileFormat = when (extension) {
            "apk" -> "APK"
            "apks" -> "APKS (Split Bundle)"
            "xapk" -> "XAPK (Split Bundle)"
            "apkm" -> "APKM (Split Bundle)"
            else -> extension.uppercase()
        }

        val fileSize = try {
            context.contentResolver.query(originalUri, arrayOf(OpenableColumns.SIZE), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (idx >= 0) cursor.getLong(idx) else 0L
                    } else 0L
                } ?: 0L
        } catch (_: Exception) {
            0L
        }

        var ackpinePackageName = ""
        var ackpineVersionName = ""
        var ackpineVersionCode = 0L
        var ackpineSize = 0L
        var splitCount = 0
        var baseApkUri: Uri? = null
        val supportedAbis = mutableListOf<String>()
        val splitEntries = mutableListOf<SplitEntry>()

        try {
            val sequence = splitPackage.get()
            var entries = try {
                sequence.toList()
            } finally {
                (sequence as? CloseableSequence<*>)?.close()
            }

            if (entries.isEmpty() && extension in setOf("apks", "xapk", "apkm", "zip")) {
                Timber.w("SplitPackage enumerated 0 entries for $originalUri (fileName=$fileName ext=$extension) — retrying as single APK")
                val fallbackSequence = SingletonApkSequence(originalUri, context).toSplitPackage().get()
                entries = try {
                    fallbackSequence.toList()
                } finally {
                    (fallbackSequence as? CloseableSequence<*>)?.close()
                }
            }
            splitCount = entries.size
            if (entries.isEmpty()) {
                Timber.e("SplitPackage enumerated 0 entries for $originalUri (fileName=$fileName ext=$extension)")
                if (extension == "apk" || extension.isBlank()) {
                    splitEntries.add(
                        SplitEntry(
                            name = "Base APK (Unparsed)",
                            type = SplitType.Base,
                            uri = originalUri,
                            sizeBytes = fileSize.coerceAtLeast(0L),
                        )
                    )
                    baseApkUri = originalUri
                }
            }

            for (entry in entries) {
                val apk = entry.apk
                if (ackpinePackageName.isEmpty()) {
                    ackpinePackageName = when (apk) {
                        is Apk.Base -> apk.packageName
                        is Apk.Libs -> apk.packageName
                        is Apk.Localization -> apk.packageName
                        is Apk.ScreenDensity -> apk.packageName
                        is Apk.Feature -> apk.packageName
                        is Apk.Other -> apk.packageName
                    }
                }
                if (ackpineVersionName.isEmpty()) {
                    ackpineVersionName = when (apk) {
                        is Apk.Base -> apk.versionName
                        else -> ""
                    }
                }
                if (ackpineVersionCode == 0L) {
                    ackpineVersionCode = when (apk) {
                        is Apk.Base -> apk.versionCode
                        is Apk.Libs -> apk.versionCode
                        is Apk.Localization -> apk.versionCode
                        is Apk.ScreenDensity -> apk.versionCode
                        is Apk.Feature -> apk.versionCode
                        is Apk.Other -> apk.versionCode
                    }
                }
                if (ackpineSize == 0L) {
                    ackpineSize = when (apk) {
                        is Apk.Base -> apk.size
                        is Apk.Libs -> apk.size
                        is Apk.Localization -> apk.size
                        is Apk.ScreenDensity -> apk.size
                        is Apk.Feature -> apk.size
                        is Apk.Other -> apk.size
                    }
                }

                when (apk) {
                    is Apk.Base -> {
                        baseApkUri = apk.uri
                        splitEntries.add(SplitEntry("Base APK", SplitType.Base, apk.uri, apk.size))
                    }
                    is Apk.Libs -> {
                        supportedAbis.add(apk.abi.name)
                        splitEntries.add(SplitEntry(apk.abi.name, SplitType.Libs, apk.uri, apk.size))
                    }
                    is Apk.Localization -> {
                        splitEntries.add(SplitEntry(apk.locale.toLanguageTag(), SplitType.Locale, apk.uri, apk.size))
                    }
                    is Apk.ScreenDensity -> {
                        splitEntries.add(SplitEntry("${apk.dpi}dpi", SplitType.ScreenDensity, apk.uri, apk.size))
                    }
                    is Apk.Feature -> {
                        splitEntries.add(SplitEntry(apk.name, SplitType.Feature, apk.uri, apk.size))
                    }
                    else -> {
                        val reclassified = reclassifyOtherSplit(apk.name)
                        if (reclassified != null) {
                            if (reclassified.first == SplitType.Libs) {
                                supportedAbis.add(reclassified.second)
                            }
                            splitEntries.add(SplitEntry(reclassified.second, reclassified.first, apk.uri, apk.size))
                        } else {
                            splitEntries.add(
                                SplitEntry(
                                    name = apk.name.ifBlank { apk.uri.lastPathSegment ?: "unknown" },
                                    type = SplitType.Other,
                                    uri = apk.uri,
                                    sizeBytes = apk.size,
                                )
                            )
                        }
                    }
                }
            }

            applySmartPick(splitEntries, context)
            onPendingUrisResolved(splitEntries.filter { it.selected }.map { it.uri })
        } catch (e: Exception) {
            Timber.e(e, "Error reading SplitPackage entries")
        }

        val uriForParsing = baseApkUri ?: originalUri
        var appName = fileName.substringBeforeLast('.')
        var icon: android.graphics.drawable.Drawable? = null
        var permissions = emptyList<String>()
        var minSdk = 0
        var targetSdk = 0
        var signatureMismatch: Boolean? = null
        var isAndroidAutoSupported = false
        var isWearOsSupported = false

        try {
            val tempFile = File(context.cacheDir, "temp_parse_${System.currentTimeMillis()}.apk")
            context.contentResolver.openInputStream(uriForParsing)?.use { input ->
                tempFile.outputStream().use { output -> input.copyTo(output) }
            }

            val parseFlags = (PackageManager.GET_PERMISSIONS or
                    PackageManager.GET_SERVICES or
                    PackageManager.GET_META_DATA or
                    PackageManager.GET_CONFIGURATIONS or
                    SignatureCheck.archiveFlag).toLong()

            val packageInfo = try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    pm.getPackageArchiveInfo(
                        tempFile.absolutePath,
                        PackageManager.PackageInfoFlags.of(parseFlags)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    pm.getPackageArchiveInfo(
                        tempFile.absolutePath,
                        parseFlags.toInt(),
                    )
                }
            } catch (t: Throwable) {
                Timber.w(t, "PackageManager failed to parse $fileName — falling back to metadata")
                null
            }

            if (packageInfo != null) {
                packageInfo.applicationInfo?.sourceDir = tempFile.absolutePath
                packageInfo.applicationInfo?.publicSourceDir = tempFile.absolutePath

                appName = packageInfo.applicationInfo?.loadLabel(pm)?.toString() ?: appName
                icon = try { packageInfo.applicationInfo?.loadIcon(pm) } catch (_: Exception) { null }
                permissions = packageInfo.requestedPermissions?.toList() ?: emptyList()
                minSdk = packageInfo.applicationInfo?.minSdkVersion ?: 0
                targetSdk = packageInfo.applicationInfo?.targetSdkVersion ?: 0
                signatureMismatch = SignatureCheck.isMismatch(context, packageInfo.packageName, packageInfo)

                val metaData = packageInfo.applicationInfo?.metaData
                val hasCarMeta = metaData?.containsKey("com.google.android.gms.car.application") == true ||
                        metaData?.containsKey("androidx.car.app.minCarApiLevel") == true ||
                        metaData?.containsKey("com.google.android.gms.car.notification.SmallIcon") == true
                val hasCarService = packageInfo.services?.any { service ->
                    service.name.contains("MediaBrowserService", ignoreCase = true) ||
                            service.name.contains("CarAppService", ignoreCase = true) ||
                            service.name.contains("CarService", ignoreCase = true)
                } ?: false
                isAndroidAutoSupported = hasCarMeta || hasCarService
                isWearOsSupported = packageInfo.reqFeatures?.any { it.name == WatchAppCheck.WATCH_FEATURE } == true

                if (ackpinePackageName.isEmpty()) ackpinePackageName = packageInfo.packageName
                if (ackpineVersionName.isEmpty()) ackpineVersionName = packageInfo.versionName ?: ""
                if (ackpineVersionCode == 0L) {
                    ackpineVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        packageInfo.longVersionCode
                    } else {
                        @Suppress("DEPRECATION") packageInfo.versionCode.toLong()
                    }
                }
            }

            if (tempFile.exists()) {
                try {
                    withContext(Dispatchers.IO) {
                        ZipFile(tempFile)
                    }.use { zip ->
                        if (supportedAbis.isEmpty()) {
                            val abiRegex = Regex("^lib/([^/]+)/")
                            val foundAbis = mutableSetOf<String>()
                            for (entry in zip.entries()) {
                                abiRegex.find(entry.name)?.groupValues?.get(1)?.let { abi ->
                                    foundAbis.add(abi)
                                }
                            }
                            if (foundAbis.isNotEmpty()) supportedAbis.addAll(foundAbis.sorted())
                        }

                        if (!isAndroidAutoSupported) {
                            val manifestEntry = zip.getEntry("AndroidManifest.xml")
                            if (manifestEntry != null) {
                                val manifestBytes = zip.getInputStream(manifestEntry).use { it.readBytes() }
                                val manifestText = String(manifestBytes, Charsets.ISO_8859_1)
                                if (manifestText.contains("com.google.android.gms.car") ||
                                    manifestText.contains("androidx.car.app") ||
                                    manifestText.contains("MediaBrowserService") ||
                                    manifestText.contains("CarAppService") ||
                                    manifestText.contains("automotive_app_desc")
                                ) {
                                    isAndroidAutoSupported = true
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Timber.d(e, "Error scanning APK zip entries")
                }
            }

            tempFile.delete()
        } catch (e: Exception) {
            Timber.e(e, "Error parsing APK with PackageManager")
        }

        return ApkInfo(
            appName = appName,
            packageName = ackpinePackageName.ifEmpty { "Unknown" },
            versionName = ackpineVersionName,
            versionCode = ackpineVersionCode,
            icon = icon,
            minSdkVersion = minSdk,
            targetSdkVersion = targetSdk,
            fileSizeBytes = if (fileSize > 0) fileSize else ackpineSize,
            permissions = permissions,
            splitCount = splitCount,
            fileFormat = fileFormat,
            supportedAbis = supportedAbis.distinct(),
            splitEntries = splitEntries,
            signatureMismatch = signatureMismatch,
            isBlocked = isBlocked,
            isAndroidAutoSupported = isAndroidAutoSupported,
            isWearOsSupported = isWearOsSupported,
        )
    }

    private fun reclassifyOtherSplit(splitName: String): Pair<SplitType, String>? {
        if (splitName.isBlank()) return null
        for (token in splitName.lowercase().split('.')) {
            if (token in ABI_TOKENS) return SplitType.Libs to token.uppercase()
            if (token in DPI_TOKENS) return SplitType.ScreenDensity to "${token.uppercase()}dpi"

            val langToken = token.replace('-', '_').replace('+', '_').removePrefix("b_")
            val locale = Locale.forLanguageTag(langToken.replace('_', '-'))
            if (locale.language.isNotEmpty() && locale.language.length in 2..3) {
                if (Locale.getISOLanguages().contains(locale.language)) {
                    return SplitType.Locale to locale.toLanguageTag()
                }
            }
        }
        return null
    }

    fun applySmartPick(entries: MutableList<SplitEntry>, context: Context) {
        if (entries.size <= 1) {
            for (i in entries.indices) {
                if (!entries[i].selected) entries[i] = entries[i].copy(selected = true)
            }
            return
        }

        val abiPriority = Build.SUPPORTED_ABIS.orEmpty().mapIndexed { i, abi ->
            abi.replace('-', '_').lowercase() to i
        }.toMap()
        val bestLibsPriority = entries
            .filter { it.type == SplitType.Libs }
            .minOfOrNull { abiPriority[it.name.replace('-', '_').lowercase()] ?: Int.MAX_VALUE }

        val deviceDpi = context.resources.displayMetrics.densityDpi
        val densityBest = entries
            .filter { it.type == SplitType.ScreenDensity }
            .minByOrNull {
                val dpi = it.name.removeSuffix("dpi").toIntOrNull() ?: Int.MAX_VALUE
                kotlin.math.abs(dpi - deviceDpi)
            }
            ?.name

        val userLangs = run {
            val list = androidx.core.os.LocaleListCompat.getDefault()
            (0 until list.size()).flatMap {
                val loc = list[it]
                if (loc != null) listOf(loc.toLanguageTag().lowercase(), loc.language.lowercase()) else emptyList()
            }
        }.toSet() + "en"

        for (i in entries.indices) {
            val e = entries[i]
            val keep = when (e.type) {
                SplitType.Base, SplitType.Feature, SplitType.Other -> true
                SplitType.Libs -> {
                    val normalized = e.name.replace('-', '_').lowercase()
                    val p = abiPriority[normalized] ?: Int.MAX_VALUE
                    val isBest = p == bestLibsPriority
                    val bestAbi = abiPriority.entries.find { it.value == bestLibsPriority }?.key
                    val containsBest = bestAbi != null && normalized.contains(bestAbi)
                    isBest || containsBest
                }
                SplitType.ScreenDensity -> e.name.equals(densityBest, ignoreCase = true)
                SplitType.Locale -> {
                    val splitTag = e.name.lowercase()
                    val splitBase = splitTag.substringBefore('-').substringBefore('_')
                    splitTag in userLangs || splitBase in userLangs
                }
            }
            if (e.selected != keep) entries[i] = e.copy(selected = keep)
        }
    }
}
