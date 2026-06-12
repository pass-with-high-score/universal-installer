package app.pwhs.universalinstaller.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [InstallHistoryEntity::class, UninstallLogEntity::class, DownloadHistoryEntity::class],
    version = 3,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun installHistoryDao(): InstallHistoryDao
    abstract fun uninstallLogDao(): UninstallLogDao
    abstract fun downloadHistoryDao(): DownloadHistoryDao

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

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `download_history` (
                        `id` INTEGER NOT NULL PRIMARY KEY AUTOINCREMENT,
                        `url` TEXT NOT NULL,
                        `fileName` TEXT NOT NULL,
                        `filePath` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `downloadedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }
    }
}
