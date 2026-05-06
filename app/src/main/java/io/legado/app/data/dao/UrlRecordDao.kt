package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.UrlRecord
import kotlinx.coroutines.flow.Flow

/**
 * URL访问记录数据访问对象
 * 提供URL记录的增删改查操作
 */
@Dao
interface UrlRecordDao {

    /**
     * 获取所有URL记录（Flow方式，支持实时观察）
     * @return 按时间倒序排列的URL记录列表
     */
    @Query("SELECT * FROM url_records ORDER BY timestamp DESC")
    fun flowAll(): Flow<List<UrlRecord>>

    /**
     * 搜索URL记录（Flow方式，支持实时观察）
     * @param keyword 搜索关键词，匹配URL、域名或来源名称
     * @return 匹配的URL记录列表
     */
    @Query("SELECT * FROM url_records WHERE url LIKE '%' || :keyword || '%' OR domain LIKE '%' || :keyword || '%' OR sourceName LIKE '%' || :keyword || '%' ORDER BY timestamp DESC")
    fun flowSearch(keyword: String): Flow<List<UrlRecord>>

    /**
     * 按域名查询URL记录（Flow方式，支持实时观察）
     * @param domain 域名
     * @return 该域名下的所有URL记录
     */
    @Query("SELECT * FROM url_records WHERE domain = :domain ORDER BY timestamp DESC")
    fun flowByDomain(domain: String): Flow<List<UrlRecord>>

    /**
     * 获取所有URL记录（一次性查询）
     * @return 按时间倒序排列的URL记录列表
     */
    @Query("SELECT * FROM url_records ORDER BY timestamp DESC")
    fun getAll(): List<UrlRecord>

    /**
     * 分页获取URL记录
     * @param limit 每页数量
     * @param offset 偏移量
     * @return URL记录列表
     */
    @Query("SELECT * FROM url_records ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getAll(limit: Int, offset: Int): List<UrlRecord>

    /**
     * 按域名查询URL记录（一次性查询）
     * @param domain 域名
     * @return 该域名下的所有URL记录
     */
    @Query("SELECT * FROM url_records WHERE domain = :domain ORDER BY timestamp DESC")
    fun getByDomain(domain: String): List<UrlRecord>

    /**
     * 按来源名称查询URL记录
     * @param sourceName 来源名称（书源名/RSS源名）
     * @return 该来源的所有URL记录
     */
    @Query("SELECT * FROM url_records WHERE sourceName = :sourceName ORDER BY timestamp DESC")
    fun getBySourceName(sourceName: String): List<UrlRecord>

    /**
     * 搜索URL记录（一次性查询）
     * @param keyword 搜索关键词
     * @return 匹配的URL记录列表
     */
    @Query("SELECT * FROM url_records WHERE url LIKE :keyword OR domain LIKE :keyword OR sourceName LIKE :keyword ORDER BY timestamp DESC")
    fun search(keyword: String): List<UrlRecord>

    /**
     * 获取所有不同的域名（一次性查询）
     * @return 域名列表，按字母排序
     */
    @Query("SELECT DISTINCT domain FROM url_records ORDER BY domain")
    fun getAllDomains(): List<String>

    /**
     * 获取所有不同的域名（Flow方式，支持实时观察）
     * @return 域名列表，按字母排序
     */
    @Query("SELECT DISTINCT domain FROM url_records ORDER BY domain")
    fun flowAllDomains(): Flow<List<String>>

    /**
     * 获取所有不同的来源名称
     * @return 来源名称列表，按字母排序
     */
    @Query("SELECT DISTINCT sourceName FROM url_records WHERE sourceName IS NOT NULL ORDER BY sourceName")
    fun getAllSourceNames(): List<String>

    /**
     * 插入URL记录
     * @param records URL记录数组
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg records: UrlRecord)

    /**
     * 删除指定ID的URL记录
     * @param id 记录ID
     */
    @Query("DELETE FROM url_records WHERE id = :id")
    fun delete(id: Long)

    /**
     * 删除所有URL记录
     * @return 删除的记录数
     */
    @Query("DELETE FROM url_records")
    fun deleteAll(): Int

    /**
     * 删除指定时间之前的URL记录
     * @param timestamp 时间戳（毫秒）
     * @return 删除的记录数
     */
    @Query("DELETE FROM url_records WHERE timestamp < :timestamp")
    fun deleteOldRecords(timestamp: Long): Int

    /**
     * 获取URL记录总数
     * @return 记录总数
     */
    @Query("SELECT COUNT(*) FROM url_records")
    fun getCount(): Int

    /**
     * 获取指定时间之前的记录数
     * @param timestamp 时间戳（毫秒）
     * @return 符合条件的记录数
     */
    @Query("SELECT COUNT(*) FROM url_records WHERE timestamp < :timestamp")
    fun getOldRecordsCount(timestamp: Long): Int

    /**
     * 获取指定时间之后的记录数
     * @param timestamp 时间戳（毫秒）
     * @return 符合条件的记录数
     */
    @Query("SELECT COUNT(*) FROM url_records WHERE timestamp > :timestamp")
    fun getCountSince(timestamp: Long): Int

}
