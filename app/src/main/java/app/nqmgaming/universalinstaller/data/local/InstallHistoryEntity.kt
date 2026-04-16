package app.nqmgaming.universalinstaller.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "install_history")
data class InstallHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val appName: String,
    val packageName: String,
    val fileName: String,
    val versionName: String = "",
    val fileSizeBytes: Long = 0,
    val iconPath: String? = null,
    val success: Boolean,
    val errorMessage: String? = null,
    val installedAt: Long = System.currentTimeMillis(),
)
