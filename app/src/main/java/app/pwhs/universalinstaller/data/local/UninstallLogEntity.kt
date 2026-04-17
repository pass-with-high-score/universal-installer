package app.pwhs.universalinstaller.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "uninstall_logs")
data class UninstallLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val appName: String,
    val success: Boolean,
    val errorMessage: String? = null,
    val uninstalledAt: Long = System.currentTimeMillis(),
)
