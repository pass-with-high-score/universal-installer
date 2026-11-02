package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.content.pm.PackageManager
import android.content.pm.UserInfo
import android.os.Build
import android.os.IUserManager
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.topjohnwu.superuser.Shell
import app.pwhs.universalinstaller.util.ShizukuServices
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.lsposed.hiddenapibypass.HiddenApiBypass
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

data class DeviceUserProfile(
    val id: Int,
    val displayName: String,
    val isOwner: Boolean,
    val isWorkProfile: Boolean,
    val isCurrent: Boolean = false,
    val isSecureFolder: Boolean = false,
)

fun isKnoxOrSecureFolder(userId: Int): Boolean {
    if (userId <= 0) return false
    return try {
        val clazz = Class.forName("com.samsung.android.knox.SemPersonaManager")
        val isSecure = clazz.getMethod("isSecureFolderId", Int::class.javaPrimitiveType).invoke(null, userId) as? Boolean ?: false
        val isKnox = clazz.getMethod("isKnoxId", Int::class.javaPrimitiveType).invoke(null, userId) as? Boolean ?: false
        isSecure || isKnox
    } catch (_: Throwable) {
        userId in 150..160
    }
}

@Composable
fun rememberDeviceUserProfiles(): List<DeviceUserProfile> {
    val context = LocalContext.current
    val ownId = remember { getUserId(Process.myUserHandle()) }
    var profiles by remember(context) {
        mutableStateOf(getUsersFromUserManager(context, ownId))
    }
    LaunchedEffect(context) {
        withContext(Dispatchers.IO) {
            val enriched = getUsersFromShizuku(ownId, context) ?: getUsersFromRoot(ownId)
            if (!enriched.isNullOrEmpty()) {
                withContext(Dispatchers.Main) {
                    profiles = enriched
                }
            }
        }
    }
    return profiles
}

fun loadDeviceUserProfiles(context: Context): List<DeviceUserProfile> {
    val ownId = getUserId(Process.myUserHandle())
    val fromShizuku = getUsersFromShizuku(ownId, context)
    if (!fromShizuku.isNullOrEmpty()) return fromShizuku

    val fromRoot = getUsersFromRoot(ownId)
    if (!fromRoot.isNullOrEmpty()) return fromRoot

    return getUsersFromUserManager(context, ownId)
}

private fun getUsersFromShizuku(ownId: Int, context: Context? = null): List<DeviceUserProfile>? {
    return runCatching {
        if (!ShizukuServices.hasShizukuPermission(context)) {
            return null
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions(
                "Landroid/os/IUserManager",
                "Landroid/content/pm/UserInfo",
                "Landroid/os/UserManager",
            )
        }
        val binder = SystemServiceHelper.getSystemService("user") ?: return null
        val ium = IUserManager.Stub.asInterface(ShizukuBinderWrapper(binder)) ?: return null
        val users = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                ium.getUsers(false, false, false)
            } catch (e: Throwable) {
                ium.getUsers(false)
            }
        } else {
            ium.getUsers(false)
        }
        users?.map { info ->
            val isWorkProfile = (info.flags and UserInfo.FLAG_MANAGED_PROFILE) != 0 ||
                runCatching { info.isManagedProfile }.getOrDefault(false)
            val isOwner = info.id == 0 || runCatching { info.isPrimary }.getOrDefault(false)
            val isSecureFolder = isKnoxOrSecureFolder(info.id) || (info.name?.contains("Secure Folder", ignoreCase = true) == true)
            DeviceUserProfile(
                id = info.id,
                displayName = info.name?.takeIf { it.isNotBlank() } ?: (if (isOwner) "Owner" else "User ${info.id}"),
                isOwner = isOwner,
                isWorkProfile = isWorkProfile,
                isCurrent = info.id == ownId,
                isSecureFolder = isSecureFolder,
            )
        }
    }.getOrNull()
}

private fun getUsersFromRoot(ownId: Int): List<DeviceUserProfile>? {
    return runCatching {
        val shell = Shell.getCachedShell()
        if (shell == null || !shell.isRoot) return null
        val out = mutableListOf<String>()
        val res = shell.newJob().add("pm list users").to(out).exec()
        if (!res.isSuccess || out.isEmpty()) return null
        val regex = Regex("""UserInfo\{(\d+):([^:]+):([0-9a-fA-F]+)\}""")
        val list = out.mapNotNull { line ->
            regex.find(line)?.let { match ->
                val id = match.groupValues[1].toIntOrNull() ?: return@let null
                val name = match.groupValues[2]
                val flags = match.groupValues[3].toIntOrNull(16) ?: 0
                val isWorkProfile = (flags and 0x00000020) != 0
                val isOwner = id == 0 || (flags and 0x00000001) != 0
                val isSecureFolder = isKnoxOrSecureFolder(id) || name.contains("Secure Folder", ignoreCase = true)
                DeviceUserProfile(
                    id = id,
                    displayName = name.takeIf { it.isNotBlank() } ?: (if (isOwner) "Owner" else "User $id"),
                    isOwner = isOwner,
                    isWorkProfile = isWorkProfile,
                    isCurrent = id == ownId,
                    isSecureFolder = isSecureFolder,
                )
            }
        }
        list.takeIf { it.isNotEmpty() }
    }.getOrNull()
}

private fun getUsersFromUserManager(context: Context, ownId: Int): List<DeviceUserProfile> {
    val um = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return emptyList()
    val ownHandle = Process.myUserHandle()
    val ownName = runCatching { um.userName }.getOrNull()?.takeIf { it.isNotBlank() }
    val handles: List<UserHandle> = runCatching { um.userProfiles }.getOrElse { listOf(ownHandle) }
    return handles.map { handle ->
        val id = getUserId(handle)
        val isOwner = id == ownId
        val isCurrent = id == ownId
        val isSecureFolder = isKnoxOrSecureFolder(id)
        val name = if (isCurrent) {
            ownName ?: "Owner"
        } else if (isSecureFolder) {
            "Secure Folder"
        } else {
            "Work profile ($id)"
        }
        DeviceUserProfile(
            id = id,
            displayName = name,
            isOwner = isOwner,
            isWorkProfile = !isOwner,
            isCurrent = isCurrent,
            isSecureFolder = isSecureFolder,
        )
    }
}

private fun getUserId(handle: UserHandle): Int {
    return try {
        val method = UserHandle::class.java.getDeclaredMethod("getIdentifier")
        method.invoke(handle) as Int
    } catch (e: Exception) {
        val str = handle.toString()
        val start = str.indexOf('{')
        val end = str.indexOf('}')
        if (start != -1 && end > start) {
            str.substring(start + 1, end).toIntOrNull() ?: 0
        } else {
            0
        }
    }
}
