package app.pwhs.universalinstaller.presentation.install

import android.content.Context
import android.os.Process
import android.os.UserHandle
import android.os.UserManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

data class DeviceUserProfile(
    val id: Int,
    val displayName: String,
    val isOwner: Boolean,
    val isWorkProfile: Boolean,
)

@Composable
fun rememberDeviceUserProfiles(): List<DeviceUserProfile> {
    val context = LocalContext.current
    return remember(context) { loadDeviceUserProfiles(context) }
}

fun loadDeviceUserProfiles(context: Context): List<DeviceUserProfile> {
    val um = context.getSystemService(Context.USER_SERVICE) as? UserManager ?: return emptyList()
    val ownHandle = Process.myUserHandle()
    val ownId = getUserId(ownHandle)
    val handles: List<UserHandle> = runCatching { um.userProfiles }.getOrElse { listOf(ownHandle) }
    return handles.map { handle ->
        val id = getUserId(handle)
        val isOwner = id == ownId
        DeviceUserProfile(
            id = id,
            displayName = if (isOwner) "Owner" else "Work profile ($id)",
            isOwner = isOwner,
            isWorkProfile = !isOwner,
        )
    }
}

private fun getUserId(handle: UserHandle): Int {
    return try {
        val method = UserHandle::class.java.getDeclaredMethod("getIdentifier")
        method.invoke(handle) as Int
    } catch (e: Exception) {
        // Fallback: UserHandle.toString() is usually "UserHandle{ID}"
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
