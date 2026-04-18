package app.pwhs.universalinstaller.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_history")
data class DownloadHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val fileName: String,
    val filePath: String,
    val sizeBytes: Long,
    val downloadedAt: Long = System.currentTimeMillis(),
)
