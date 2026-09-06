package app.pwhs.universalinstaller.presentation.setting.security.util

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Manages runtime lock/unlock state for the app lock feature.
 * Automatically tracks activity lifecycle to re-lock when the app is placed in background.
 */
object AppLockManager : Application.ActivityLifecycleCallbacks {

    private val _isAppUnlocked = MutableStateFlow(false)
    val isAppUnlocked = _isAppUnlocked.asStateFlow()

    private var activeActivities = 0

    fun unlock() {
        _isAppUnlocked.value = true
    }

    fun lock() {
        _isAppUnlocked.value = false
    }

    override fun onActivityStarted(activity: Activity) {
        activeActivities++
    }

    override fun onActivityStopped(activity: Activity) {
        activeActivities--
        if (activity.isChangingConfigurations) {
            return
        }
        if (activeActivities <= 0) {
            activeActivities = 0
            // Re-lock when all activities stop (app moved to background)
            lock()
        }
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
