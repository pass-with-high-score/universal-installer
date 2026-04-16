package app.pwhs.universalinstaller.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [InstallHistoryEntity::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun installHistoryDao(): InstallHistoryDao
}
