package app.pwhs.updater.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [TrackedAppEntity::class],
    version = 6,
    exportSchema = false,
)
abstract class UpdaterDatabase : RoomDatabase() {
    abstract fun trackedAppDao(): TrackedAppDao

    companion object {
        private const val DATABASE_NAME = "universal_updater.db"

        private val MIGRATION_3_4 = object : androidx.room.migration.Migration(3, 4) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracked_apps ADD COLUMN versionRegex TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_4_5 = object : androidx.room.migration.Migration(4, 5) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracked_apps ADD COLUMN matchGroup TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tracked_apps ADD COLUMN useReleaseTitleAsVersion INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE tracked_apps ADD COLUMN availableAssetsJson TEXT DEFAULT NULL")
            }
        }

        private val MIGRATION_5_6 = object : androidx.room.migration.Migration(5, 6) {
            override fun migrate(db: androidx.sqlite.db.SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE tracked_apps ADD COLUMN installedVersionRegex TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE tracked_apps ADD COLUMN installedVersionMatchGroup TEXT DEFAULT NULL")
            }
        }

        @Volatile
        private var INSTANCE: UpdaterDatabase? = null

        fun getInstance(context: Context): UpdaterDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    UpdaterDatabase::class.java,
                    DATABASE_NAME,
                )
                    .addMigrations(MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                    .fallbackToDestructiveMigration(dropAllTables = true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
