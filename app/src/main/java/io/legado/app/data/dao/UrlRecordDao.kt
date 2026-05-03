package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.UrlRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface UrlRecordDao {

    @Query("SELECT * FROM url_records ORDER BY timestamp DESC")
    fun flowAll(): Flow<List<UrlRecord>>

    @Query("SELECT * FROM url_records WHERE url LIKE '%' || :keyword || '%' OR domain LIKE '%' || :keyword || '%' OR sourceName LIKE '%' || :keyword || '%' ORDER BY timestamp DESC")
    fun flowSearch(keyword: String): Flow<List<UrlRecord>>

    @Query("SELECT * FROM url_records ORDER BY timestamp DESC")
    fun getAll(): List<UrlRecord>

    @Query("SELECT * FROM url_records ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getAll(limit: Int, offset: Int): List<UrlRecord>

    @Query("SELECT * FROM url_records WHERE domain = :domain ORDER BY timestamp DESC")
    fun getByDomain(domain: String): List<UrlRecord>

    @Query("SELECT * FROM url_records WHERE sourceName = :sourceName ORDER BY timestamp DESC")
    fun getBySourceName(sourceName: String): List<UrlRecord>

    @Query("SELECT * FROM url_records WHERE url LIKE :keyword OR domain LIKE :keyword OR sourceName LIKE :keyword ORDER BY timestamp DESC")
    fun search(keyword: String): List<UrlRecord>

    @Query("SELECT DISTINCT domain FROM url_records ORDER BY domain")
    fun getAllDomains(): List<String>

    @Query("SELECT DISTINCT sourceName FROM url_records WHERE sourceName IS NOT NULL ORDER BY sourceName")
    fun getAllSourceNames(): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg records: UrlRecord)

    @Query("DELETE FROM url_records WHERE id = :id")
    fun delete(id: Long)

    @Query("DELETE FROM url_records")
    fun deleteAll(): Int

    @Query("DELETE FROM url_records WHERE timestamp < :timestamp")
    fun deleteOldRecords(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM url_records")
    fun getCount(): Int

    @Query("SELECT COUNT(*) FROM url_records WHERE timestamp < :timestamp")
    fun getOldRecordsCount(timestamp: Long): Int

    @Query("SELECT COUNT(*) FROM url_records WHERE timestamp > :timestamp")
    fun getCountSince(timestamp: Long): Int

}
