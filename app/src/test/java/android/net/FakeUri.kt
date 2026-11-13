package android.net

import android.os.Parcel

class FakeUri(private val uriString: String = "content://app.pwhs/test.apk") : Uri() {
    override fun isHierarchical(): Boolean = false
    override fun isRelative(): Boolean = false
    override fun getScheme(): String = "content"
    override fun getSchemeSpecificPart(): String = uriString.removePrefix("content:")
    override fun getEncodedSchemeSpecificPart(): String = getSchemeSpecificPart()
    override fun getAuthority(): String = "app.pwhs"
    override fun getEncodedAuthority(): String = "app.pwhs"
    override fun getPath(): String = "/test.apk"
    override fun getEncodedPath(): String = "/test.apk"
    override fun getQuery(): String? = null
    override fun getEncodedQuery(): String? = null
    override fun getFragment(): String? = null
    override fun getEncodedFragment(): String? = null
    override fun getPathSegments(): List<String> = listOf("test.apk")
    override fun getLastPathSegment(): String = "test.apk"
    override fun getUserInfo(): String? = null
    override fun getEncodedUserInfo(): String? = null
    override fun getHost(): String? = "app.pwhs"
    override fun getPort(): Int = -1
    override fun buildUpon(): Builder = throw UnsupportedOperationException()
    override fun toString(): String = uriString
    override fun compareTo(other: Uri?): Int = uriString.compareTo(other?.toString().orEmpty())
    override fun describeContents(): Int = 0
    override fun writeToParcel(dest: Parcel, flags: Int) {}
}

fun createTestUri(uriString: String = "content://app.pwhs/test.apk"): Uri = FakeUri(uriString)
