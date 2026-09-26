package app.pwhs.universalinstaller.domain.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AdminPanelSettings
import androidx.compose.material.icons.rounded.Android
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Terminal
import androidx.compose.ui.graphics.vector.ImageVector
import app.pwhs.universalinstaller.R

enum class InstallBackend(
    val id: String,
    val titleRes: Int,
    val descriptionRes: Int,
    val iconRes: Int? = null,
) {
    SHIZUKU("shizuku", R.string.setting_install_mode_shizuku, R.string.installer_engine_shizuku_desc, R.drawable.ic_shizuku_logo),
    DHIZUKU("dhizuku", R.string.setting_install_mode_dhizuku, R.string.installer_engine_dhizuku_desc, R.drawable.ic_dhizuku_logo),
    ROOT("root", R.string.setting_install_mode_root, R.string.installer_engine_root_desc),
    CUSTOM("custom", R.string.setting_install_mode_custom, R.string.setting_install_mode_custom_sub),
    MICROG("microg", R.string.installer_mode_microg, R.string.installer_mode_microg_desc, R.drawable.ic_microg_logo),
    DEFAULT("default", R.string.setting_install_mode_default, R.string.setting_install_mode_default_sub);

    val icon: ImageVector
        get() = when (this) {
            SHIZUKU -> Icons.Rounded.Key
            DHIZUKU -> Icons.Rounded.AdminPanelSettings
            ROOT -> Icons.Rounded.Shield
            CUSTOM -> Icons.Rounded.Terminal
            MICROG -> Icons.Rounded.CloudDownload
            DEFAULT -> Icons.Rounded.Android
        }

    companion object {
        val DEFAULT_ORDER = listOf(SHIZUKU, DHIZUKU, ROOT, CUSTOM, MICROG, DEFAULT)

        fun fromId(id: String): InstallBackend? = entries.firstOrNull { it.id.equals(id, ignoreCase = true) }

        fun parsePriorityList(serialized: String?): List<InstallBackend> {
            if (serialized.isNullOrBlank()) return DEFAULT_ORDER
            val parsed = serialized.split(",")
                .mapNotNull { fromId(it.trim()) }
                .distinct()
            val missing = DEFAULT_ORDER.filter { it !in parsed }
            return parsed + missing
        }

        fun serializePriorityList(list: List<InstallBackend>): String {
            return list.joinToString(",") { it.id }
        }
    }
}

data class BackendPriorityItem(
    val backend: InstallBackend,
    val enabled: Boolean,
    val isReady: Boolean,
    val isSupported: Boolean = true,
    val statusText: String = "",
    val canToggle: Boolean = true,
)

@androidx.compose.runtime.Composable
fun BackendIcon(
    backend: InstallBackend,
    modifier: androidx.compose.ui.Modifier = androidx.compose.ui.Modifier,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    if (backend.iconRes != null) {
        androidx.compose.foundation.Image(
            painter = androidx.compose.ui.res.painterResource(backend.iconRes),
            contentDescription = null,
            modifier = modifier,
        )
    } else {
        androidx.compose.material3.Icon(
            imageVector = backend.icon,
            contentDescription = null,
            tint = tint ?: androidx.compose.material3.LocalContentColor.current,
            modifier = modifier,
        )
    }
}
