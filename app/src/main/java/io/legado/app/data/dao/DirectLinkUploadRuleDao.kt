package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.DirectLinkUploadRule
import kotlinx.coroutines.flow.Flow

@Dao
interface DirectLinkUploadRuleDao {

    @Query("SELECT * FROM direct_link_upload_rules ORDER BY isDefault DESC, sortOrder ASC, createTime DESC")
    fun flowAll(): Flow<List<DirectLinkUploadRule>>

    @Query("SELECT * FROM direct_link_upload_rules WHERE id = :id")
    fun flowById(id: Long): Flow<DirectLinkUploadRule?>

    @Query("SELECT * FROM direct_link_upload_rules WHERE isDefault = 1 LIMIT 1")
    fun getDefault(): DirectLinkUploadRule?

    @Query("SELECT * FROM direct_link_upload_rules ORDER BY isDefault DESC, sortOrder ASC, createTime DESC")
    suspend fun getAll(): List<DirectLinkUploadRule>

    @Query("SELECT * FROM direct_link_upload_rules WHERE id = :id")
    suspend fun getById(id: Long): DirectLinkUploadRule?

    @Query("SELECT COUNT(*) FROM direct_link_upload_rules")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg rules: DirectLinkUploadRule)

    @Update
    suspend fun update(rule: DirectLinkUploadRule)

    @Delete
    suspend fun delete(rule: DirectLinkUploadRule)

    @Query("DELETE FROM direct_link_upload_rules WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM direct_link_upload_rules")
    suspend fun deleteAll()

    @Query("UPDATE direct_link_upload_rules SET uploadCount = uploadCount + 1, lastUsedTime = :time WHERE id = :id")
    suspend fun incrementUploadCount(id: Long, time: Long = System.currentTimeMillis())

    @Query("UPDATE direct_link_upload_rules SET isDefault = 0")
    suspend fun clearDefault()

    @Query("UPDATE direct_link_upload_rules SET isDefault = 1 WHERE id = :ruleId")
    suspend fun setDefaultById(ruleId: Long)

    @Transaction
    suspend fun setDefault(ruleId: Long) {
        clearDefault()
        setDefaultById(ruleId)
    }

    @Query("UPDATE direct_link_upload_rules SET sortOrder = :order WHERE id = :id")
    suspend fun updateSortOrder(id: Long, order: Int)
}
