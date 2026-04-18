package app.pwhs.universalinstaller.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadHistoryDao {
    @Insert
    suspend fun insert(entry: DownloadHistoryEntity): Long

    @Query("SELECT * FROM download_history ORDER BY downloadedAt DESC")
    fun getAll(): Flow<List<DownloadHistoryEntity>>

    @Query("DELETE FROM download_history")
    suspend fun clearAll()

    @Query("DELETE FROM download_history WHERE id = :id")
    suspend fun deleteById(id: Long)
}
