package app.pwhs.universalinstaller.testutil

import android.content.Context
import android.content.ContextWrapper
import java.io.File

class TestContext(
    private val tempDir: File = File(System.getProperty("java.io.tmpdir") ?: "/tmp", "test_app_${System.currentTimeMillis()}"),
) : ContextWrapper(null) {

    init {
        tempDir.mkdirs()
    }

    override fun getApplicationContext(): Context = this
    override fun getPackageName(): String = "app.pwhs.universalinstaller"
    override fun getCacheDir(): File = tempDir
    override fun getFilesDir(): File = tempDir
}
