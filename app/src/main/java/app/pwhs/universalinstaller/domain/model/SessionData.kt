package app.pwhs.universalinstaller.domain.model

import android.net.Uri
import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import kotlinx.parcelize.RawValue
import ru.solrudev.ackpine.resources.ResolvableString
import java.util.UUID

@Parcelize
data class SessionData(
    val id: UUID,
    val name: String,
    val appName: String = "",
    val packageName: String = "",
    val versionName: String = "",
    val oldVersionName: String? = null,
    val iconPath: String? = null,
    val error: ResolvableString = ResolvableString.empty(),
    val isCancellable: Boolean = true,
    val uris: List<@RawValue Uri> = emptyList(),
    val originalUri: @RawValue Uri? = null,
    val deleteAfterInstall: Boolean = false,
    val allowDowngrade: Boolean = false,
    val targetUserId: Int? = null,
    val abiOverride: String? = null,
    val installerMode: String? = null,
    val operationType: String? = null,
    val fileSizeBytes: Long = 0,
    val filePath: String? = null,
): Parcelable
