package app.pwhs.core.install

/** Archive extensions that hold a split package rather than a single APK. */
val BUNDLE_EXTS = setOf("apks", "xapk", "apkm", "apk+", "zip")

fun String.isBundleFileName(): Boolean =
    substringAfterLast('.', "").lowercase() in BUNDLE_EXTS
