package app.pwhs.tv.baselineprofile

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.Until
import org.junit.Rule
import org.junit.Test

/**
 * Records the classes and methods used during TV startup and tab navigation, so ART can compile
 * them ahead of time instead of interpreting them while the user is pressing the D-pad.
 *
 * Every smooth TV app examined ships one of these — Netflix and Downloader both carry
 * `assets/dexopt/baseline.prof`. This module produces ours.
 *
 * The journey below is deliberately the *navigation* path rather than a deep feature tour: the
 * reported jank is tab switching and list focus, and a profile only helps the code it covers.
 */
class BaselineProfileGenerator {

    @get:Rule
    val rule = BaselineProfileRule()

    @Test
    fun startupAndNavigate() = rule.collect(packageName = PACKAGE) {
        pressHome()
        startActivityAndWait()

        // Splash resolves onboarding state before the shell appears; without this the profile
        // captures the splash and little else.
        device.waitForIdle()

        repeat(2) {
            // The rail is the thing being switched between, and each destination composes its
            // own screen on first focus. Two passes so the second visit records the
            // already-composed path as well as the cold one.
            navigateTabs()
        }
    }

    private fun androidx.benchmark.macro.MacrobenchmarkScope.navigateTabs() {
        listOf("Manage", "Settings", "Receive").forEach { label ->
            val tab = device.wait(Until.findObject(By.textContains(label)), TIMEOUT_MS)
            if (tab != null) {
                tab.click()
                device.waitForIdle()
                // Scroll whatever list the tab shows — list item composition and focus handling
                // are exactly the paths that jank.
                device.findObject(By.scrollable(true))?.let { list ->
                    runCatching { list.scroll(Direction.DOWN, 1f) }
                    runCatching { list.scroll(Direction.UP, 1f) }
                }
                device.waitForIdle()
            }
        }
    }

    private companion object {
        // The applicationId, not the namespace: tv/build.gradle.kts sets
        // applicationId is shared with :app for the Play listing; the namespace is app.pwhs.tv.
        const val PACKAGE = "app.pwhs.universalinstaller"
        const val TIMEOUT_MS = 5_000L
    }
}
