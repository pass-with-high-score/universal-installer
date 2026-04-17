package app.pwhs.universalinstaller.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [InstallHistoryEntity::class, UninstallLogEntity::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun installHistoryDao(): InstallHistoryDao
    abstract fun uninstallLogDao(): UninstallLogDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `uninstall_logs` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `packageName` TEXT NOT NULL,
                        `appName` TEXT NOT NULL,
                        `success` INTEGER NOT NULL,
                        `errorMessage` TEXT,
                        `uninstalledAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
