package app.pwhs.universalinstaller.presentation.install.util

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import androidx.core.graphics.createBitmap
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import app.pwhs.core.data.local.dataStore
import app.pwhs.core.util.RootShell
import app.pwhs.universalinstaller.domain.manager.ProfileManager
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.InstallerProfile
import app.pwhs.universalinstaller.presentation.install.controller.BaseInstallController
import app.pwhs.universalinstaller.presentation.install.controller.DefaultInstallController
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.ManualInstallController
import app.pwhs.universalinstaller.presentation.install.controller.RootState
import app.pwhs.universalinstaller.presentation.install.controller.ShizukuInstallController
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.presentation.setting.security.util.SystemInstallerManager
import app.pwhs.universalinstaller.util.CustomShellExecutor
import app.pwhs.universalinstaller.util.DhizukuCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import ru.solrudev.ackpine.session.Session
import ru.solrudev.ackpine.session.await
import ru.solrudev.ackpine.session.parameters.Confirmation
import ru.solrudev.ackpine.shizuku.shizuku
import ru.solrudev.ackpine.uninstaller.PackageUninstaller
import ru.solrudev.ackpine.uninstaller.createSession
import timber.log.Timber
import java.io.File

object InstallSessionManager {

    suspend fun activeController(
        context: Context,
        profileId: String?,
        defaultController: DefaultInstallController,
        shizukuController: ShizukuInstallController,
        rootController: BaseInstallController?,
        dhizukuController: BaseInstallController?,
        customController: BaseInstallController? = null,
        microGController: BaseInstallController? = null,
        backendFactory: InstallerBackendFactory,
    ): BaseInstallController {
        val prefs = try { context.dataStore.data.first() } catch (_: Exception) { null }
        val profiles = ProfileManager.parseProfiles(prefs?.get(PreferencesKeys.INSTALLER_PROFILES))
        val profile = profiles.find { it.id == profileId }

        val preferredBackend = profile?.preferredBackend
        if (preferredBackend != null) {
            when (preferredBackend) {
                "Custom" -> customController?.let { return it }
                "MicroG", "microG" -> microGController?.let {
                    if (app.pwhs.universalinstaller.util.MicroGCompat.isAvailable(context)) return it
                }
                "Root" -> if (rootController != null) {
                    val state = backendFactory.probeRootState()
                    val finalState = if (state == RootState.READY) state
                    else if (state == RootState.UNKNOWN || state == RootState.DENIED) backendFactory.requestRoot()
                    else state
                    if (finalState == RootState.READY) return rootController
                }
                "Shizuku" -> if (isShizukuReadyForInstall()) return shizukuController
                "Dhizuku" -> dhizukuController?.let {
                    if (DhizukuCompat.isReady(context)) return it
                }
                "Shizuku + Dhizuku" -> {
                    if (isShizukuReadyForInstall()) return shizukuController
                    dhizukuController?.let {
                        if (DhizukuCompat.isReady(context)) return it
                    }
                }
                "Default" -> return defaultController
            }
        }

        val priorityList = app.pwhs.universalinstaller.domain.model.InstallBackend.parsePriorityList(
            prefs?.get(PreferencesKeys.INSTALL_BACKEND_PRIORITY)
        )
        val useMicroG = prefs?.get(PreferencesKeys.USE_MICROG) ?: false
        val useCustomAuthorizer = prefs?.get(PreferencesKeys.USE_CUSTOM_AUTHORIZER) ?: false
        val useRoot = prefs?.get(PreferencesKeys.USE_ROOT) ?: false
        val spoofRoot = prefs?.get(PreferencesKeys.ROOT_SET_INSTALL_SOURCE) ?: false
        val useShizuku = prefs?.get(PreferencesKeys.USE_SHIZUKU) ?: false
        val spoofShizuku = prefs?.get(PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE) ?: false
        val useDhizuku = prefs?.get(PreferencesKeys.USE_DHIZUKU) ?: false

        for (backend in priorityList) {
            when (backend) {
                app.pwhs.universalinstaller.domain.model.InstallBackend.SHIZUKU -> {
                    if ((useShizuku || spoofShizuku) && isShizukuReadyForInstall()) {
                        return shizukuController
                    }
                }
                app.pwhs.universalinstaller.domain.model.InstallBackend.DHIZUKU -> {
                    if (useDhizuku && dhizukuController != null && DhizukuCompat.isReady(context)) {
                        return dhizukuController
                    }
                }
                app.pwhs.universalinstaller.domain.model.InstallBackend.ROOT -> {
                    if ((useRoot || spoofRoot) && rootController != null) {
                        val state = backendFactory.probeRootState()
                        val finalState = if (state == RootState.READY) state
                        else if (state == RootState.UNKNOWN || state == RootState.DENIED) backendFactory.requestRoot()
                        else state
                        if (finalState == RootState.READY) return rootController
                    }
                }
                app.pwhs.universalinstaller.domain.model.InstallBackend.CUSTOM -> {
                    if (useCustomAuthorizer && customController != null) {
                        return customController
                    }
                }
                app.pwhs.universalinstaller.domain.model.InstallBackend.MICROG -> {
                    if (useMicroG && microGController != null && app.pwhs.universalinstaller.util.MicroGCompat.isAvailable(context)) {
                        return microGController
                    }
                }
                app.pwhs.universalinstaller.domain.model.InstallBackend.DEFAULT -> {
                    if (!SystemInstallerManager.isSystemPackageInstallerDisabled(context)) {
                        return defaultController
                    }
                }
            }
        }

        // Fallback when system package installer is frozen:
        // DefaultInstallController relies on the system package installer UI to show the
        // confirmation dialog. If it is disabled, DefaultInstallController will fail or hang.
        // Therefore, if any elevated backend (Shizuku / Root / Dhizuku) is ready, auto-promote to it.
        if (SystemInstallerManager.isSystemPackageInstallerDisabled(context)) {
            if (isShizukuReadyForInstall()) {
                Timber.i("System package installer is frozen: auto-promoting to Shizuku")
                return shizukuController
            }
            if (rootController != null) {
                val state = backendFactory.probeRootState()
                if (state == RootState.READY) {
                    Timber.i("System package installer is frozen: auto-promoting to Root")
                    return rootController
                }
            }
            if (dhizukuController != null && DhizukuCompat.isReady(context)) {
                Timber.i("System package installer is frozen: auto-promoting to Dhizuku")
                return dhizukuController
            }
            Timber.w("System package installer is frozen and no elevated backend is available!")
        }

        return defaultController
    }

    suspend fun resolveTargetedBackend(
        preferred: String?,
        rootController: BaseInstallController?,
        backendFactory: InstallerBackendFactory,
    ): ManualInstallController.TargetedBackend? {
        val shizukuReady = isShizukuReadyForInstall()
        val rootReady = if (rootController != null) {
            val state = backendFactory.probeRootState()
            state == RootState.READY
        } else false

        when (preferred) {
            "Shizuku" -> if (shizukuReady) return ManualInstallController.TargetedBackend.SHIZUKU
            "Root" -> if (rootReady) return ManualInstallController.TargetedBackend.ROOT
        }
        return when {
            shizukuReady -> ManualInstallController.TargetedBackend.SHIZUKU
            rootReady -> ManualInstallController.TargetedBackend.ROOT
            else -> null
        }
    }

    fun isShizukuReadyForInstall(): Boolean = try {
        rikka.shizuku.Shizuku.pingBinder() &&
                !rikka.shizuku.Shizuku.isPreV11() &&
                rikka.shizuku.Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (t: Throwable) {
        Timber.w(t, "Shizuku readiness probe failed")
        false
    }

    /**
     * Determines whether the active install backend requires the `REQUEST_INSTALL_PACKAGES`
     * permission on Android 8.0+. Privileged backends (Shizuku, Root, Dhizuku, Custom, MicroG)
     * do not require this permission.
     */
    fun requiresInstallPermission(
        context: Context,
        prefs: Preferences?,
        profileId: String? = null,
    ): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        if (prefs == null) return !context.packageManager.canRequestPackageInstalls()

        val profiles = ProfileManager.parseProfiles(prefs[PreferencesKeys.INSTALLER_PROFILES])
        val profile = profiles.find { it.id == profileId }

        val preferredBackend = profile?.preferredBackend
        if (preferredBackend != null) {
            when (preferredBackend) {
                "Custom" -> return false
                "MicroG", "microG" -> if (app.pwhs.universalinstaller.util.MicroGCompat.isAvailable(context)) return false
                "Root" -> return false
                "Shizuku" -> if (isShizukuReadyForInstall()) return false
                "Dhizuku" -> if (DhizukuCompat.isReady(context)) return false
                "Default" -> return true
            }
        }

        val priorityList = app.pwhs.universalinstaller.domain.model.InstallBackend.parsePriorityList(
            prefs[PreferencesKeys.INSTALL_BACKEND_PRIORITY]
        )
        val useMicroG = prefs[PreferencesKeys.USE_MICROG] ?: false
        val useCustomAuthorizer = prefs[PreferencesKeys.USE_CUSTOM_AUTHORIZER] ?: false
        val useRoot = prefs[PreferencesKeys.USE_ROOT] ?: false
        val spoofRoot = prefs[PreferencesKeys.ROOT_SET_INSTALL_SOURCE] ?: false
        val useShizuku = prefs[PreferencesKeys.USE_SHIZUKU] ?: false
        val spoofShizuku = prefs[PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE] ?: false
        val useDhizuku = prefs[PreferencesKeys.USE_DHIZUKU] ?: false

        for (backend in priorityList) {
            when (backend) {
                app.pwhs.universalinstaller.domain.model.InstallBackend.SHIZUKU -> {
                    if ((useShizuku || spoofShizuku) && isShizukuReadyForInstall()) return false
                }
                app.pwhs.universalinstaller.domain.model.InstallBackend.DHIZUKU -> {
                    if (useDhizuku && DhizukuCompat.isReady(context)) return false
                }
                app.pwhs.universalinstaller.domain.model.InstallBackend.ROOT -> {
                    if (useRoot || spoofRoot) return false
                }
                app.pwhs.universalinstaller.domain.model.InstallBackend.CUSTOM -> {
                    if (useCustomAuthorizer) return false
                }
                app.pwhs.universalinstaller.domain.model.InstallBackend.MICROG -> {
                    if (useMicroG && app.pwhs.universalinstaller.util.MicroGCompat.isAvailable(context)) return false
                }
                app.pwhs.universalinstaller.domain.model.InstallBackend.DEFAULT -> {
                    if (!SystemInstallerManager.isSystemPackageInstallerDisabled(context)) {
                        return true
                    }
                }
            }
        }

        if (SystemInstallerManager.isSystemPackageInstallerDisabled(context)) {
            if (isShizukuReadyForInstall()) return false
            if (useRoot) return false
            if (DhizukuCompat.isReady(context)) return false
        }

        return true
    }

    suspend fun uninstallConflictingApp(
        context: Context,
        packageName: String,
        profileId: String?,
        defaultController: DefaultInstallController,
        shizukuController: ShizukuInstallController,
        rootController: BaseInstallController?,
        dhizukuController: BaseInstallController?,
        customController: BaseInstallController? = null,
        microGController: BaseInstallController? = null,
        backendFactory: InstallerBackendFactory,
        packageUninstaller: PackageUninstaller,
    ): Boolean {
        if (packageName.isBlank()) return false
        val controller = runCatching {
            activeController(
                context = context,
                profileId = profileId,
                defaultController = defaultController,
                shizukuController = shizukuController,
                rootController = rootController,
                dhizukuController = dhizukuController,
                customController = customController,
                microGController = microGController,
                backendFactory = backendFactory,
            )
        }.getOrNull()

        return when {
            customController != null && controller === customController -> uninstallViaCustom(context, packageName)
            controller === shizukuController -> uninstallViaShizuku(packageName, packageUninstaller)
            rootController != null && controller === rootController -> uninstallViaRoot(packageName)
            else -> false
        }
    }

    private suspend fun uninstallViaCustom(
        context: Context,
        packageName: String,
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            CustomShellExecutor.exec(context, "pm uninstall $packageName").isSuccess
        } catch (e: Exception) {
            Timber.e(e, "Custom authorizer uninstall of $packageName failed")
            false
        }
    }

    private suspend fun uninstallViaShizuku(
        packageName: String,
        packageUninstaller: PackageUninstaller,
    ): Boolean = try {
        val session = packageUninstaller.createSession(packageName) {
            confirmation = Confirmation.IMMEDIATE
            shizuku {}
        }
        session.await() == Session.State.Succeeded
    } catch (e: Exception) {
        Timber.e(e, "Shizuku uninstall of $packageName failed")
        false
    }

    private suspend fun uninstallViaRoot(packageName: String): Boolean = try {
        RootShell.exec("pm uninstall $packageName").isSuccess
    } catch (e: Exception) {
        Timber.e(e, "Root uninstall of $packageName failed")
        false
    }

    suspend fun writeProfileFlags(context: Context, profile: InstallerProfile?) {
        profile ?: return
        context.dataStore.edit { p ->
            profile.installerPackageName?.let { pkg ->
                p[PreferencesKeys.SHIZUKU_INSTALLER_PACKAGE_NAME] = pkg
                p[PreferencesKeys.ROOT_INSTALLER_PACKAGE_NAME] = pkg
                p[PreferencesKeys.SHIZUKU_SET_INSTALL_SOURCE] = pkg.isNotBlank()
                p[PreferencesKeys.ROOT_SET_INSTALL_SOURCE] = pkg.isNotBlank()
            }
            profile.replaceExisting?.let {
                p[PreferencesKeys.SHIZUKU_REPLACE_EXISTING] = it
                p[PreferencesKeys.ROOT_REPLACE_EXISTING] = it
            }
            profile.allowTest?.let {
                p[PreferencesKeys.SHIZUKU_ALLOW_TEST] = it
                p[PreferencesKeys.ROOT_ALLOW_TEST] = it
            }
            profile.requestDowngrade?.let {
                p[PreferencesKeys.SHIZUKU_REQUEST_DOWNGRADE] = it
                p[PreferencesKeys.ROOT_REQUEST_DOWNGRADE] = it
            }
            profile.grantAllPermissions?.let {
                p[PreferencesKeys.SHIZUKU_GRANT_ALL_PERMISSIONS] = it
                p[PreferencesKeys.ROOT_GRANT_ALL_PERMISSIONS] = it
            }
            profile.bypassLowTargetSdk?.let {
                p[PreferencesKeys.SHIZUKU_BYPASS_LOW_TARGET_SDK] = it
                p[PreferencesKeys.ROOT_BYPASS_LOW_TARGET_SDK] = it
            }
            profile.allUsers?.let {
                p[PreferencesKeys.SHIZUKU_ALL_USERS] = it
                p[PreferencesKeys.ROOT_ALL_USERS] = it
            }
            profile.allowRestrictedPermissions?.let {
                p[PreferencesKeys.SHIZUKU_ALLOW_RESTRICTED_PERMISSIONS] = it
                p[PreferencesKeys.ROOT_ALLOW_RESTRICTED_PERMISSIONS] = it
            }
            profile.dontKillApp?.let {
                p[PreferencesKeys.SHIZUKU_DONT_KILL_APP] = it
                p[PreferencesKeys.ROOT_DONT_KILL_APP] = it
            }
            profile.disableVerification?.let {
                p[PreferencesKeys.SHIZUKU_DISABLE_VERIFICATION] = it
                p[PreferencesKeys.ROOT_DISABLE_VERIFICATION] = it
            }
            profile.enableRollback?.let {
                p[PreferencesKeys.SHIZUKU_ENABLE_ROLLBACK] = it
                p[PreferencesKeys.ROOT_ENABLE_ROLLBACK] = it
            }
            profile.requestUpdateOwnership?.let {
                p[PreferencesKeys.SHIZUKU_REQUEST_UPDATE_OWNERSHIP] = it
                p[PreferencesKeys.ROOT_REQUEST_UPDATE_OWNERSHIP] = it
            }
        }
    }

    suspend fun readDeleteApkPref(context: Context): Boolean {
        return try {
            val prefs = context.dataStore.data.first()
            prefs[booleanPreferencesKey("delete_apk_after_install")] ?: false
        } catch (_: Exception) {
            false
        }
    }

    fun lookupInstalledVersion(context: Context, packageName: String): Pair<String, Long>? {
        if (packageName.isBlank()) return null
        return try {
            val pi = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0L))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            val name = pi.versionName.orEmpty()
            val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                pi.longVersionCode
            } else {
                @Suppress("DEPRECATION") pi.versionCode.toLong()
            }
            name to code
        } catch (_: PackageManager.NameNotFoundException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    suspend fun cacheIcon(context: Context, apkInfo: ApkInfo?): String? {
        val drawable = apkInfo?.icon ?: return null
        return withContext(Dispatchers.IO) {
            try {
                val bitmap = (drawable as? BitmapDrawable)?.bitmap
                    ?: createBitmap(192, 192).also { bmp ->
                        val canvas = android.graphics.Canvas(bmp)
                        drawable.setBounds(0, 0, 192, 192)
                        drawable.draw(canvas)
                    }
                val file = File(context.cacheDir, "session_icon_${System.currentTimeMillis()}.png")
                file.outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 90, it) }
                file.absolutePath
            } catch (_: Exception) {
                null
            }
        }
    }
}
