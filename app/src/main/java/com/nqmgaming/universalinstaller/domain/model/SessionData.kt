package com.nqmgaming.universalinstaller.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class SessionData(
    val id: UUID,
    val name: String,
    val error: String? = null,
    val isCancellable: Boolean = true
): Parcelable