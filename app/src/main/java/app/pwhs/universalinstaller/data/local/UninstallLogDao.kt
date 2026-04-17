package app.pwhs.universalinstaller.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface UninstallLogDao {
    @Insert
    suspend fun insert(entry: UninstallLogEntity)

    @Query("SELECT * FROM uninstall_logs ORDER BY uninstalledAt DESC")
    fun getAll(): Flow<List<UninstallLogEntity>>

    @Query("DELETE FROM uninstall_logs")
    suspend fun clearAll()

    @Query("DELETE FROM uninstall_logs WHERE id = :id")
    suspend fun deleteById(id: Long)
}
