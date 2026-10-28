package app.pwhs.core.util

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import app.pwhs.core.R

object PermissionSettingsHelper {

    /**
     * Builds an ordered list of fallback Intents to reach the "Install unknown apps" permission.
     * 1. ACTION_MANAGE_UNKNOWN_APP_SOURCES with package: URI (Direct toggle)
     * 2. ACTION_MANAGE_UNKNOWN_APP_SOURCES without URI (App list on Android Go / custom ROMs)
     * 3. ACTION_APPLICATION_DETAILS_SETTINGS (App info screen - present on all Android builds)
     * 4. ACTION_SECURITY_SETTINGS (Security / privacy settings)
     * 5. ACTION_SETTINGS (Main system settings)
     */
    fun getInstallPermissionIntents(context: Context): List<Intent> {
        val packageUri = Uri.parse("package:${context.packageName}")
        return listOfNotNull(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, packageUri)
            } else null,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            } else null,
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, packageUri),
            Intent(Settings.ACTION_SECURITY_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )
    }

    /**
     * Attempts to open the install permission settings using a 5-tier fallback cascade.
     * If all intents fail, a user-friendly guidance toast is displayed.
     */
    fun openInstallPermission(context: Context, showToastOnFailure: Boolean = true): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false

        val intents = getInstallPermissionIntents(context)
        for (intent in intents) {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)

            if (launched) return true
        }

        if (showToastOnFailure) {
            runCatching {
                Toast.makeText(
                    context,
                    context.getString(R.string.error_cannot_open_settings),
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        return false
    }

    /**
     * Opens developer options with graceful fallbacks and guidance for MIUI/HyperOS.
     */
    fun openDeveloperOptions(context: Context): Boolean {
        val intents = listOf(
            Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            Intent(Settings.ACTION_DEVICE_INFO_SETTINGS),
            Intent(Settings.ACTION_SETTINGS),
        )

        for (intent in intents) {
            if (context !is Activity) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val launched = runCatching {
                context.startActivity(intent)
                true
            }.getOrDefault(false)

            if (launched) {
                if (intent.action != Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.onboarding_xiaomi_dev_options_hint),
                        Toast.LENGTH_LONG,
                    ).show()
                }
                return true
            }
        }

        runCatching {
            Toast.makeText(
                context,
                context.getString(R.string.onboarding_xiaomi_dev_options_hint),
                Toast.LENGTH_LONG,
            ).show()
        }
        return false
    }
}
