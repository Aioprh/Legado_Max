package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.UploadHistory
import kotlinx.coroutines.flow.Flow

@Dao
interface UploadHistoryDao {

    @Query("SELECT * FROM upload_histories ORDER BY uploadTime DESC")
    fun flowAll(): Flow<List<UploadHistory>>

    @Query("SELECT * FROM upload_histories WHERE ruleId = :ruleId ORDER BY uploadTime DESC")
    fun flowByRuleId(ruleId: Long): Flow<List<UploadHistory>>

    @Query("""
        SELECT * FROM upload_histories 
        WHERE fileName LIKE '%' || :keyword || '%' 
        OR downloadUrl LIKE '%' || :keyword || '%'
        OR ruleSummary LIKE '%' || :keyword || '%'
        ORDER BY uploadTime DESC
    """)
    fun flowSearch(keyword: String): Flow<List<UploadHistory>>

    @Query("SELECT * FROM upload_histories WHERE success = :success ORDER BY uploadTime DESC")
    fun flowBySuccess(success: Boolean): Flow<List<UploadHistory>>

    @Query("SELECT * FROM upload_histories WHERE uploadTime >= :startTime AND uploadTime <= :endTime ORDER BY uploadTime DESC")
    fun flowByTimeRange(startTime: Long, endTime: Long): Flow<List<UploadHistory>>

    @Query("SELECT * FROM upload_histories ORDER BY uploadTime DESC")
    suspend fun getAll(): List<UploadHistory>

    @Query("SELECT * FROM upload_histories WHERE id = :id")
    suspend fun getById(id: Long): UploadHistory?

    @Query("SELECT COUNT(*) FROM upload_histories")
    suspend fun getCount(): Int

    @Query("SELECT COUNT(*) FROM upload_histories WHERE success = 1")
    suspend fun getSuccessCount(): Int

    @Query("SELECT SUM(fileSize) FROM upload_histories WHERE success = 1")
    suspend fun getTotalUploadSize(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(history: UploadHistory)

    @Delete
    suspend fun delete(history: UploadHistory)

    @Query("DELETE FROM upload_histories WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM upload_histories")
    suspend fun deleteAll(): Int

    @Query("DELETE FROM upload_histories WHERE uploadTime < :timestamp")
    suspend fun deleteOldRecords(timestamp: Long): Int

    @Query("DELETE FROM upload_histories WHERE ruleId = :ruleId")
    suspend fun deleteByRuleId(ruleId: Long): Int
}
