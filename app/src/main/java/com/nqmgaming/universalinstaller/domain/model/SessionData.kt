package com.nqmgaming.universalinstaller.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import ru.solrudev.ackpine.resources.ResolvableString
import java.util.UUID

@Parcelize
data class SessionData(
    val id: UUID,
    val name: String,
    val appName: String = "",
    val iconPath: String? = null,
    val error: ResolvableString = ResolvableString.empty(),
    val isCancellable: Boolean = true
): Parcelable
