package app.pwhs.core.domain.scanner

import android.content.Context
import android.content.ContextWrapper
import android.net.FakeUri
import android.net.Uri
import java.io.File

open class FakeContext : ContextWrapper(null) {
    override fun getApplicationContext(): Context = this
    override fun getPackageName(): String = "app.pwhs.test"
    override fun getCacheDir(): File = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
    override fun getFilesDir(): File = File(System.getProperty("java.io.tmpdir") ?: "/tmp")
}

fun DummyUri(uriString: String = "content://dummy/app.apk"): Uri = FakeUri(uriString)

