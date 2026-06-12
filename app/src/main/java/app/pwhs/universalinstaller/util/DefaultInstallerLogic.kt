package app.pwhs.universalinstaller.util

import android.content.ComponentName
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.IPackageManager
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.os.Build
import android.os.Process
import androidx.core.net.toUri
import timber.log.Timber

/**
 * Toggles the system's preferred-activity registration so our `DialogInstallActivity` becomes
 * the default handler for `application/vnd.android.package-archive`. Ported from
 * InstallerX-Revived's DefaultPrivilegedService.
 *
 * The hidden `IPackageManager` methods we need (addPreferredActivity, clearPackagePreferredActivities,
 * etc.) aren't exposed by the rikka-stub compile-only artifact, so we reach them reflectively.
 * The methods themselves exist at runtime on the binder proxy that `IPackageManager.Stub.asInterface`
 * returns; reflection just bypasses the stub's missing signatures.
 *
 * Caller must supply an [IPackageManager] obtained either via Shizuku binder wrapping (shell
 * UID 2000) or from inside a libsu RootService (UID 0). [hasSystemLevelPermission] controls
 * whether we also call the persistent variants — those require UID 0 and throw on shell.
 */
object DefaultInstallerLogic {

    private const val MIME = "application/vnd.android.package-archive"

    @Suppress("DEPRECATION")
    private val ACTIONS = arrayOf(Intent.ACTION_VIEW, Intent.ACTION_INSTALL_PACKAGE)

    fun setDefaultInstaller(
        iPackageManager: IPackageManager,
        component: ComponentName,
        lock: Boolean,
        hasSystemLevelPermission: Boolean,
    ) {
        val userId = Process.myUid() / 100000

        Timber.d(
            "setDefaultInstaller: component=%s lock=%b userId=%d systemLevel=%b",
            component.flattenToShortString(), lock, userId, hasSystemLevelPermission,
        )

        clearPackagePreferredActivities(iPackageManager, component.packageName, userId, hasSystemLevelPermission)

        if (!lock) return

        for (action in ACTIONS) {
            val probeIntent = Intent(action).apply {
                addCategory(Intent.CATEGORY_DEFAULT)
                setDataAndType("content://storage/emulated/0/test.apk".toUri(), MIME)
            }

            val competitors = queryIntentActivities(iPackageManager, probeIntent, userId)
            val names = mutableListOf<ComponentName>()
            for (info in competitors) {
                val pkg = info.activityInfo.packageName
                val cls = info.activityInfo.name
                if (pkg != component.packageName && pkg != "android") {
                    clearPackagePreferredActivities(iPackageManager, pkg, userId, hasSystemLevelPermission)
                }
                names.add(ComponentName(pkg, cls))
            }

            val filter = IntentFilter().apply {
                addAction(action)
                addCategory(Intent.CATEGORY_DEFAULT)
                addDataType(MIME)
            }
            val match = IntentFilter.MATCH_CATEGORY_TYPE or IntentFilter.MATCH_ADJUSTMENT_MASK

            addPreferredActivity(iPackageManager, filter, match, names.toTypedArray(), component, userId)

            if (hasSystemLevelPermission) {
                addPersistentPreferredActivity(iPackageManager, filter, component, userId)
            }
        }
    }

    private fun clearPackagePreferredActivities(
        iPackageManager: IPackageManager,
        packageName: String,
        userId: Int,
        hasSystemLevelPermission: Boolean,
    ) {
        runCatching {
            val m = iPackageManager.javaClass.getMethod(
                "clearPackagePreferredActivities", String::class.java,
            )
            m.invoke(iPackageManager, packageName)
        }.onFailure { Timber.w(it, "clearPackagePreferredActivities($packageName) failed") }

        if (hasSystemLevelPermission) {
            runCatching {
                val m = iPackageManager.javaClass.getMethod(
                    "clearPackagePersistentPreferredActivities",
                    String::class.java, Int::class.javaPrimitiveType,
                )
                m.invoke(iPackageManager, packageName, userId)
            }.onFailure { Timber.w(it, "clearPackagePersistentPreferredActivities($packageName) failed") }
        }
    }

    private fun queryIntentActivities(
        iPackageManager: IPackageManager,
        intent: Intent,
        userId: Int,
    ): List<ResolveInfo> {
        // Signature picks between long (T+) and int (<T) for the flags arg. Try both.
        val flagsLong = PackageManager.MATCH_DEFAULT_ONLY.toLong()
        val flagsInt = PackageManager.MATCH_DEFAULT_ONLY
        val result = runCatching {
            val m = iPackageManager.javaClass.getMethod(
                "queryIntentActivities",
                Intent::class.java, String::class.java,
                Long::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )
            m.invoke(iPackageManager, intent, MIME, flagsLong, userId)
        }.recoverCatching {
            val m = iPackageManager.javaClass.getMethod(
                "queryIntentActivities",
                Intent::class.java, String::class.java,
                Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
            )
            m.invoke(iPackageManager, intent, MIME, flagsInt, userId)
        }.getOrNull() ?: return emptyList()

        // Result is ParceledListSlice<ResolveInfo>. Reflect getList() to keep us off the
        // hidden-API surface — the class is hidden but the method is present at runtime.
        return runCatching {
            @Suppress("UNCHECKED_CAST")
            result.javaClass.getMethod("getList").invoke(result) as List<ResolveInfo>
        }.getOrElse {
            Timber.w(it, "ParceledListSlice.getList() failed")
            emptyList()
        }
    }

    private fun addPreferredActivity(
        iPackageManager: IPackageManager,
        filter: IntentFilter,
        match: Int,
        names: Array<ComponentName>,
        component: ComponentName,
        userId: Int,
    ) {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
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
        }.onFailure { Timber.w(it, "addPreferredActivity failed") }
    }

    private fun addPersistentPreferredActivity(
        iPackageManager: IPackageManager,
        filter: IntentFilter,
        component: ComponentName,
        userId: Int,
    ) {
        runCatching {
            val m = iPackageManager.javaClass.getMethod(
                "addPersistentPreferredActivity",
                IntentFilter::class.java, ComponentName::class.java, Int::class.javaPrimitiveType,
            )
            m.invoke(iPackageManager, filter, component, userId)
        }.onFailure { Timber.w(it, "addPersistentPreferredActivity failed") }
    }
}
