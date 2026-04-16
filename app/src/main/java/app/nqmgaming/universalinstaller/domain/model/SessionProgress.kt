package app.nqmgaming.universalinstaller.domain.model

import android.os.Parcelable
import kotlinx.parcelize.Parcelize
import ru.solrudev.ackpine.session.Progress
import java.util.UUID

@Parcelize
data class SessionProgress(
    val id: UUID,
    val currentProgress: Int,
    val progressMax: Int
) : Parcelable {

    constructor(id: UUID, progress: Progress) : this(id, progress.progress, progress.max)

    val progress: Progress
        get() = Progress(currentProgress, progressMax)
}