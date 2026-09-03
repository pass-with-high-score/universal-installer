package app.pwhs.universalinstaller.wearos.data

import app.pwhs.core.domain.PackageMetadata
import java.io.File

fun PackageMetadata.toWearApkInfo(
    file: File,
    declaresWatchFeature: Boolean,
    installedVersionCode: Long?,
): WearApkInfo = WearApkInfo(
    id = file.name,
    fileName = file.name.substringAfter('_', file.name),
    appName = appName,
    packageName = packageName,
    versionName = versionName,
    versionCode = versionCode,
    minSdk = minSdk,
    isBundle = isBundle,
    sizeBytes = file.length(),
    cachedFilePath = file.absolutePath,
    declaresWatchFeature = declaresWatchFeature,
    installedVersionCode = installedVersionCode,
    receivedAt = file.lastModified(),
)
