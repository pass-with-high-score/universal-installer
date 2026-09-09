package app.pwhs.universalinstaller.presentation.install.util

import android.app.Application
import android.net.Uri
import android.widget.Toast
import app.pwhs.core.data.local.dataStore
import app.pwhs.universalinstaller.R
import app.pwhs.universalinstaller.domain.manager.ProfileManager
import app.pwhs.universalinstaller.domain.model.ApkInfo
import app.pwhs.universalinstaller.domain.model.SessionData
import app.pwhs.universalinstaller.presentation.install.AttachedObb
import app.pwhs.universalinstaller.presentation.install.BatchApkEntry
import app.pwhs.universalinstaller.presentation.install.DialogTarget
import app.pwhs.universalinstaller.presentation.install.ObbEntry
import app.pwhs.universalinstaller.presentation.install.controller.BaseInstallController
import app.pwhs.universalinstaller.presentation.install.controller.InstallerBackendFactory
import app.pwhs.universalinstaller.presentation.install.controller.ManualInstallController
import app.pwhs.universalinstaller.presentation.install.controller.ShizukuShellExecutor
import app.pwhs.universalinstaller.presentation.install.dialog.isDowngrade
import app.pwhs.universalinstaller.presentation.setting.PreferencesKeys
import app.pwhs.universalinstaller.telemetry.Telemetry
import app.pwhs.universalinstaller.telemetry.TelemetryEvents
import app.pwhs.universalinstaller.util.extension.getDisplayName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.UUID

object InstallExecutionCoordinator {

    suspend fun executeSingleInstall(
        application: Application,
        scope: CoroutineScope,
        appScope: CoroutineScope,
        trackDialogTarget: Boolean,
        keepApk: Boolean = false,
        apkInfo: ApkInfo?,
        fileName: String,
        originalUri: Uri?,
        uris: List<Uri>,
        obbEntries: List<ObbEntry>,
        attachedObbs: List<AttachedObb>,
        currentProfileId: String?,
        rootController: BaseInstallController?,
        backendFactory: InstallerBackendFactory,
        manualController: ManualInstallController,
        resolveActiveController: suspend (String?) -> BaseInstallController,
        onDialogTargetCreated: (DialogTarget) -> Unit,
        onCopyObbs: suspend (Uri?, List<ObbEntry>, List<AttachedObb>, String, String) -> Unit,
    ) {
        val prefs = try { application.dataStore.data.first() } catch (_: Exception) { null }
        val profiles = ProfileManager.parseProfiles(prefs?.get(PreferencesKeys.INSTALLER_PROFILES))
        val profile = profiles.find { it.id == currentProfileId }
        val iconPath = InstallSessionManager.cacheIcon(application, apkInfo)
        val deleteAfterInstall = InstallSessionManager.readDeleteApkPref(application) && !keepApk
        val controller = resolveActiveController(currentProfileId)
        val backendName = profile?.preferredBackend ?: when (controller) {
            rootController -> "Root"
            is app.pwhs.universalinstaller.presentation.install.controller.ShizukuInstallController -> "Shizuku"
            is app.pwhs.universalinstaller.presentation.install.controller.DhizukuInstallController -> "Dhizuku"
            is app.pwhs.universalinstaller.presentation.install.controller.ManualInstallController -> "Manual"
            else -> "Default"
        }
        val opType = when {
            apkInfo?.installedVersionCode == null || apkInfo.installedVersionCode == 0L -> "INSTALL"
            apkInfo.versionCode < (apkInfo.installedVersionCode ?: 0L) -> "DOWNGRADE"
            else -> "UPDATE"
        }
        val sessionData = SessionData(
            id = UUID.randomUUID(),
            name = fileName,
            appName = apkInfo?.appName ?: "",
            packageName = apkInfo?.packageName ?: "",
            versionName = apkInfo?.versionName ?: "",
            oldVersionName = apkInfo?.installedVersionName,
            iconPath = iconPath,
            uris = uris,
            originalUri = originalUri,
            deleteAfterInstall = deleteAfterInstall,
            allowDowngrade = apkInfo?.let { it.installedVersionCode != null && it.versionCode < (it.installedVersionCode ?: 0L) } ?: false,
            targetUserId = profile?.targetUserId ?: prefs?.get(PreferencesKeys.INSTALL_USER_ID),
            installerMode = backendName,
            operationType = opType,
            fileSizeBytes = apkInfo?.fileSizeBytes ?: 0L,
            filePath = originalUri?.path,
        )
        val hasZipObbs = obbEntries.isNotEmpty() && originalUri != null
        val hasAttachedObbs = attachedObbs.isNotEmpty()
        val dex2oatEnabled = prefs?.get(PreferencesKeys.DEX2OAT_OPTIMIZATION) ?: false
        val pkg = apkInfo?.packageName
        val appName = apkInfo?.appName?.ifBlank { pkg.orEmpty() } ?: pkg.orEmpty()
        val isPrivilegedBackend = backendName == "Shizuku" || backendName == "Root"

        val onSuccess: (suspend () -> Unit)? = if (apkInfo != null && (hasZipObbs || hasAttachedObbs || (dex2oatEnabled && isPrivilegedBackend))) {
            val callback: suspend () -> Unit = {
                if (hasZipObbs || hasAttachedObbs) {
                    onCopyObbs(originalUri, obbEntries, attachedObbs, pkg.orEmpty(), appName)
                }
                if (dex2oatEnabled && isPrivilegedBackend && !pkg.isNullOrBlank()) {
                    triggerDex2oatOptimization(application, pkg, appName, backendName, backendFactory, appScope)
                }
            }
            callback
        } else {
            null
        }

        val pkgForTarget = apkInfo?.packageName.orEmpty()
        val nameForTarget = apkInfo?.appName.orEmpty().ifBlank { fileName }
        val targetedUserId = profile?.targetUserId ?: prefs?.get(PreferencesKeys.INSTALL_USER_ID)
        if (targetedUserId != null) {
            val targetedBackend = InstallSessionManager.resolveTargetedBackend(profile?.preferredBackend, rootController, backendFactory)
            if (targetedBackend == null) {
                android.widget.Toast.makeText(application, application.getString(R.string.install_targeted_no_backend), android.widget.Toast.LENGTH_LONG).show()
                return
            }
            manualController.installTargeted(
                uris = uris,
                sessionData = sessionData,
                userId = targetedUserId,
                backend = targetedBackend,
                scope = if (trackDialogTarget) appScope else scope,
                originalUri = originalUri,
                deleteAfterInstall = deleteAfterInstall,
                onSessionCreated = if (trackDialogTarget) {
                    { realId ->
                        onDialogTargetCreated(
                            DialogTarget(
                                sessionId = realId,
                                packageName = pkgForTarget,
                                appName = nameForTarget,
                                iconPath = iconPath,
                                apkUri = originalUri,
                                deleteAfterInstall = deleteAfterInstall,
                            )
                        )
                    }
                } else null,
            )
        } else {
            InstallSessionManager.writeProfileFlags(application, profile)
            controller.install(
                uris = uris,
                sessionData = sessionData,
                scope = if (trackDialogTarget) appScope else scope,
                context = application,
                originalUri = originalUri,
                deleteAfterInstall = deleteAfterInstall,
                allowDowngrade = apkInfo?.let { isDowngrade(it) } ?: false,
                onSuccess = onSuccess,
                onSessionCreated = if (trackDialogTarget) {
                    { realId ->
                        onDialogTargetCreated(
                            DialogTarget(
                                sessionId = realId,
                                packageName = pkgForTarget,
                                appName = nameForTarget,
                                iconPath = iconPath,
                                apkUri = originalUri,
                                deleteAfterInstall = deleteAfterInstall,
                            )
                        )
                    }
                } else null,
            )
        }
    }

    suspend fun executeBatchInstall(
        application: Application,
        scope: CoroutineScope,
        picked: List<BatchApkEntry>,
        currentProfileId: String?,
        backendFactory: InstallerBackendFactory? = null,
        resolveActiveController: suspend (String?) -> BaseInstallController,
    ) {
        if (picked.isEmpty()) return
        Telemetry.feature(TelemetryEvents.FEATURE_BATCH_INSTALL)
        val deleteAfterInstall = InstallSessionManager.readDeleteApkPref(application)
        val prefs = try { application.dataStore.data.first() } catch (_: Exception) { null }
        val profile = ProfileManager.parseProfiles(prefs?.get(PreferencesKeys.INSTALLER_PROFILES)).find { it.id == currentProfileId }
        InstallSessionManager.writeProfileFlags(application, profile)
        val controller = resolveActiveController(currentProfileId)
        val backendName = profile?.preferredBackend ?: when (controller) {
            is app.pwhs.universalinstaller.presentation.install.controller.ShizukuInstallController -> "Shizuku"
            is app.pwhs.universalinstaller.presentation.install.controller.RootInstallController -> "Root"
            else -> "Default"
        }
        val dex2oatEnabled = prefs?.get(PreferencesKeys.DEX2OAT_OPTIMIZATION) ?: false
        val isPrivilegedBackend = backendName == "Shizuku" || backendName == "Root"

        for (entry in picked) {
            val iconPath = InstallSessionManager.cacheIcon(application, entry.apkInfo)
            val opType = when {
                entry.apkInfo.installedVersionCode == null || entry.apkInfo.installedVersionCode == 0L -> "INSTALL"
                entry.apkInfo.versionCode < (entry.apkInfo.installedVersionCode ?: 0L) -> "DOWNGRADE"
                else -> "UPDATE"
            }
            val sessionData = SessionData(
                id = UUID.randomUUID(),
                name = entry.fileName,
                appName = entry.apkInfo.appName,
                packageName = entry.apkInfo.packageName,
                versionName = entry.apkInfo.versionName,
                oldVersionName = entry.apkInfo.installedVersionName,
                iconPath = iconPath,
                uris = entry.splitUris,
                originalUri = entry.uri,
                deleteAfterInstall = deleteAfterInstall,
                allowDowngrade = isDowngrade(entry.apkInfo),
                installerMode = backendName,
                operationType = opType,
                fileSizeBytes = entry.apkInfo.fileSizeBytes,
                filePath = entry.uri.path,
            )
            val batchOnSuccess: (suspend () -> Unit)? = if (dex2oatEnabled && isPrivilegedBackend && backendFactory != null) {
                {
                    triggerDex2oatOptimization(
                        application = application,
                        packageName = entry.apkInfo.packageName,
                        appName = entry.apkInfo.appName,
                        backendName = backendName,
                        backendFactory = backendFactory,
                        scope = scope,
                    )
                }
            } else null

            controller.install(
                uris = entry.splitUris,
                sessionData = sessionData,
                scope = scope,
                context = application,
                originalUri = entry.uri,
                deleteAfterInstall = deleteAfterInstall,
                allowDowngrade = isDowngrade(entry.apkInfo),
                onSuccess = batchOnSuccess,
            )
        }
    }

    suspend fun executeSkipSingle(
        application: Application,
        scope: CoroutineScope,
        uri: Uri,
        fileName: String,
        sessionId: UUID,
        resolveActiveController: suspend () -> BaseInstallController,
        onSuccess: () -> Unit,
    ) {
        val deleteAfterInstall = InstallSessionManager.readDeleteApkPref(application)
        val controller = resolveActiveController()
        val sessionData = SessionData(
            id = sessionId,
            name = fileName,
            appName = fileName,
        )
        controller.install(
            uris = listOf(uri),
            sessionData = sessionData,
            scope = scope,
            context = application,
            originalUri = uri,
            deleteAfterInstall = deleteAfterInstall,
            onSuccess = onSuccess,
        )
    }

    suspend fun executeSkipBatch(
        application: Application,
        scope: CoroutineScope,
        uris: List<Uri>,
        resolveActiveController: suspend () -> BaseInstallController,
    ) {
        val deleteAfterInstall = InstallSessionManager.readDeleteApkPref(application)
        val controller = resolveActiveController()
        for (uri in uris) {
            val fileName = application.contentResolver.getDisplayName(uri)
            val sessionData = SessionData(
                id = UUID.randomUUID(),
                name = fileName,
                appName = fileName,
            )
            controller.install(
                uris = listOf(uri),
                sessionData = sessionData,
                scope = scope,
                context = application,
                originalUri = uri,
                deleteAfterInstall = deleteAfterInstall,
                onSuccess = null,
            )
        }
    }

    private fun triggerDex2oatOptimization(
        application: Application,
        packageName: String,
        appName: String,
        backendName: String,
        backendFactory: InstallerBackendFactory,
        scope: CoroutineScope,
    ) {
        scope.launch(Dispatchers.IO) {
            Timber.i("Triggering dex2oat optimization for $packageName ($backendName)")
            withContext(Dispatchers.Main) {
                Toast.makeText(
                    application,
                    application.getString(R.string.install_optimizing_dex2oat, appName),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            val result = if (backendName == "Shizuku") {
                ShizukuShellExecutor.compilePackage(packageName)
            } else {
                backendFactory.compilePackageViaRoot(packageName)
            }
            result.onSuccess {
                Timber.i("Dex2oat optimization completed for $packageName: $it")
                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        application,
                        application.getString(R.string.install_optimized_dex2oat, appName),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }.onFailure { e ->
                Timber.w(e, "Dex2oat optimization failed for $packageName")
            }
        }
    }
}
