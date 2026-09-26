package app.pwhs.universalinstaller.util

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Process
import androidx.core.net.toUri
import timber.log.Timber
import java.lang.reflect.InvocationTargetException

/**
 * Toggles the system's preferred-activity registration so our `DialogUninstallActivity` becomes
 * the default handler for `android.intent.action.UNINSTALL_PACKAGE` and `android.intent.action.DELETE`.
 */
object DefaultUninstallerLogic {

    private const val SCHEME = "package"

    @Suppress("DEPRECATION")
    private val ACTIONS = arrayOf(Intent.ACTION_UNINSTALL_PACKAGE, Intent.ACTION_DELETE)

    fun setDefaultUninstaller(
        iPackageManager: IPackageManager,
        component: ComponentName,
        lock: Boolean,
        hasSystemLevelPermission: Boolean,
    ) {
        try {
            applyPreference(iPackageManager, component, lock, hasSystemLevelPermission)
        } catch (e: InvocationTargetException) {
            throw e.cause ?: e
        }
    }

    private fun applyPreference(
        iPackageManager: IPackageManager,
        component: ComponentName,
        lock: Boolean,
        hasSystemLevelPermission: Boolean,
    ) {
        val userId = Process.myUid() / 100000

        android.util.Log.i(
            "DefaultUninstallerLogic",
            "setDefaultUninstaller: component=${component.flattenToShortString()} lock=$lock userId=$userId systemLevel=$hasSystemLevelPermission",
        )
        Timber.d(
            "setDefaultUninstaller: component=%s lock=%b userId=%d systemLevel=%b",
            component.flattenToShortString(), lock, userId, hasSystemLevelPermission,
        )

        val systemPackages = listOf(
            "com.google.android.packageinstaller",
            "com.android.packageinstaller",
            "com.samsung.android.packageinstaller",
        )

        clearPackagePreferredActivities(iPackageManager, component.packageName, userId, hasSystemLevelPermission)

        if (!lock) {
            if (hasSystemLevelPermission) {
                for (pkg in systemPackages) {
                    runCatching {
                        setComponentEnabled(iPackageManager, ComponentName(pkg, "com.android.packageinstaller.UninstallerActivity"), true, userId)
                    }
                }
            }
            return
        }

        for (action in ACTIONS) {
            val probeIntent = Intent(action).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                data = "package:com.example.placeholder".toUri()
            }

            val competitors = queryIntentActivities(iPackageManager, probeIntent, userId)
            android.util.Log.i("DefaultUninstallerLogic", "action=$action competitors count=${competitors.size}")
            if (competitors.isEmpty()) {
                Timber.w("queryIntentActivities returned nothing for $action with package scheme")
                continue
            }
            val names = mutableListOf<ComponentName>()
            for (info in competitors) {
                val pkg = info.activityInfo.packageName
                val cls = info.activityInfo.name
                if (pkg != component.packageName && pkg != "android") {
                    runCatching {
                        clearPackagePreferredActivities(iPackageManager, pkg, userId, hasSystemLevelPermission)
                    }.onFailure { Timber.w(it, "Could not clear competing preference for %s", pkg) }
                    if (hasSystemLevelPermission && pkg.contains("packageinstaller")) {
                        runCatching {
                            android.util.Log.i("DefaultUninstallerLogic", "disabling system uninstaller $pkg/$cls")
                            setComponentEnabled(iPackageManager, ComponentName(pkg, cls), false, userId)
                        }.onFailure {
                            android.util.Log.w("DefaultUninstallerLogic", "failed to disable $pkg/$cls", it)
                            Timber.w(it, "Could not disable system uninstaller component %s/%s", pkg, cls)
                        }
                    }
                }
                names.add(ComponentName(pkg, cls))
            }

            val filter = IntentFilter().apply {
                addAction(action)
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataScheme(SCHEME)
            }
            val calculatedMatch = filter.match(
                action,
                null,
                SCHEME,
                "package:com.example.placeholder".toUri(),
                setOf(Intent.CATEGORY_DEFAULT),
                "DefaultUninstallerLogic",
            )
            val match = if (calculatedMatch > 0) {
                calculatedMatch
            } else {
                IntentFilter.MATCH_CATEGORY_SCHEME or IntentFilter.MATCH_ADJUSTMENT_MASK
            }

            android.util.Log.i("DefaultUninstallerLogic", "addPreferredActivity match=$match names=${names.size}")
            addPreferredActivity(iPackageManager, filter, match, names.toTypedArray(), component, userId)

            if (hasSystemLevelPermission) {
                runCatching {
                    android.util.Log.i("DefaultUninstallerLogic", "addPersistentPreferredActivity for uninstaller")
                    addPersistentPreferredActivity(iPackageManager, filter, component, userId)
                }.onFailure {
                    android.util.Log.w("DefaultUninstallerLogic", "addPersistentPreferredActivity failed", it)
                    Timber.w(it, "addPersistentPreferredActivity failed for uninstaller")
                }
            }
        }
    }

    private fun clearPackagePreferredActivities(
        iPackageManager: IPackageManager,
        packageName: String,
        userId: Int,
        hasSystemLevelPermission: Boolean,
    ) {
        val m = iPackageManager.javaClass.getMethod(
            "clearPackagePreferredActivities", String::class.java,
        )
        m.invoke(iPackageManager, packageName)

        if (hasSystemLevelPermission) {
            runCatching {
                val mPersistent = iPackageManager.javaClass.getMethod(
                    "clearPackagePersistentPreferredActivities",
                    String::class.java, Int::class.javaPrimitiveType,
                )
                mPersistent.invoke(iPackageManager, packageName, userId)
            }.onFailure { Timber.w(it, "clearPackagePersistentPreferredActivities($packageName) failed") }
        }
    }

    private fun queryIntentActivities(
        iPackageManager: IPackageManager,
        intent: Intent,
        userId: Int,
    ): List<ResolveInfo> {
        val flagsLong = PackageManager.MATCH_DEFAULT_ONLY.toLong()
        val flagsInt = PackageManager.MATCH_DEFAULT_ONLY
        val result = runCatching {
            val m = iPackageManager.javaClass.getMethod(
                "queryIntentActivities",
                Intent::class.java, String::class.java,
                Long::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )
            m.invoke(iPackageManager, intent, null, flagsLong, userId)
        }.recoverCatching {
            val m = iPackageManager.javaClass.getMethod(
                "queryIntentActivities",
                Intent::class.java, String::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )
            m.invoke(iPackageManager, intent, null, flagsInt, userId)
        }.getOrThrow()

        return extractResolveInfoList(result)
    }

    @Suppress("UNCHECKED_CAST")
    private fun extractResolveInfoList(result: Any?): List<ResolveInfo> {
        if (result == null) return emptyList()
        if (result is List<*>) return result as List<ResolveInfo>
        val getList = result.javaClass.getMethod("getList")
        return getList.invoke(result) as List<ResolveInfo>
    }

    private fun addPreferredActivity(
        iPackageManager: IPackageManager,
        filter: IntentFilter,
        match: Int,
        names: Array<ComponentName>,
        component: ComponentName,
        userId: Int,
    ) {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            val m = iPackageManager.javaClass.getMethod(
                "addPreferredActivity",
                IntentFilter::class.java, Int::class.javaPrimitiveType,
                Array<ComponentName>::class.java, ComponentName::class.java,
                Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType,
            )
            m.invoke(iPackageManager, filter, match, names, component, userId, true)
        } else {
            val m = iPackageManager.javaClass.getMethod(
                "addPreferredActivity",
                IntentFilter::class.java, Int::class.javaPrimitiveType,
                Array<ComponentName>::class.java, ComponentName::class.java,
                Int::class.javaPrimitiveType,
            )
            m.invoke(iPackageManager, filter, match, names, component, userId)
        }
    }

    private fun addPersistentPreferredActivity(
        iPackageManager: IPackageManager,
        filter: IntentFilter,
        activity: ComponentName,
        userId: Int,
    ) {
        val m = iPackageManager.javaClass.getMethod(
            "addPersistentPreferredActivity",
            IntentFilter::class.java, ComponentName::class.java,
            Int::class.javaPrimitiveType,
        )
        m.invoke(iPackageManager, filter, activity, userId)
    }

    private fun setComponentEnabled(
        iPackageManager: IPackageManager,
        component: ComponentName,
        enabled: Boolean,
        userId: Int,
    ) {
        val newState = if (enabled) {
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT
        } else {
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        val m = iPackageManager.javaClass.getMethod(
            "setComponentEnabledSetting",
            ComponentName::class.java,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        )
        m.invoke(iPackageManager, component, newState, PackageManager.DONT_KILL_APP, userId)
    }
}
