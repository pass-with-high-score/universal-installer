package app.pwhs.universalinstaller.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface InstallHistoryDao {
    @Insert
    suspend fun insert(entry: InstallHistoryEntity)

    @Query("SELECT * FROM install_history ORDER BY installedAt DESC")
    fun getAll(): Flow<List<InstallHistoryEntity>>

    @Query("DELETE FROM install_history")
    suspend fun clearAll()

    @Query("DELETE FROM install_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
