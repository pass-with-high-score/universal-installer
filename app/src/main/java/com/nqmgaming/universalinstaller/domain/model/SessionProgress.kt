package com.nqmgaming.universalinstaller.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import java.util.UUID

@Parcelize
data class SessionProgress(
    val id: UUID,
    val currentProgress: Int,
    val progressMax: Int = 100
) : Parcelable