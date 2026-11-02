package app.pwhs.core.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShizukuStarterHelperTest {

    @Test
    fun buildStarterScript_containsKnownPackages() {
        val script = ShizukuStarterHelper.buildStarterScript()
        assertTrue(script.contains("moe.shizuku.privileged.api"))
        assertTrue(script.contains("com.hamondev.shevery"))
        assertTrue(script.contains("eu.darken.porter"))
        assertTrue(script.contains("/data/local/tmp/shizuku_starter"))
    }

    @Test
    fun buildStarterScript_withCustomPackage_prependsCustomPackage() {
        val customPkg = "com.custom.stealthshizuku"
        val script = ShizukuStarterHelper.buildStarterScript(installedPackage = customPkg)
        assertTrue(script.contains(customPkg))
        assertTrue(script.indexOf(customPkg) < script.indexOf("moe.shizuku.privileged.api"))
    }

    @Test
    fun constants_sheveryPermission() {
        assertEquals(
            "com.hamondev.shevery.permission.PRIVILEGED_API",
            ShizukuStarterHelper.SHEVERY_PERMISSION
        )
    }
}
