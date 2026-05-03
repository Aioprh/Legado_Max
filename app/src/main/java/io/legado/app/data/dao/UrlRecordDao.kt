package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.UrlRecord
import kotlinx.coroutines.flow.Flow

/**
 * URL记录数据访问对象
 * 提供URL记录的增删改查操作
 */
@Dao
interface UrlRecordDao {

    /**
     * 获取所有URL记录的Flow
     * @return 按时间戳降序排列的URL记录列表Flow
     */
    @Query("SELECT * FROM url_records ORDER BY timestamp DESC")
    fun flowAll(): Flow<List<UrlRecord>>

    /**
     * 根据关键词搜索URL记录的Flow
     * 支持在url、domain、sourceName字段中进行模糊匹配
     * @param keyword 搜索关键词
     * @return 匹配的URL记录列表Flow，按时间戳降序排列
     */
    @Query("SELECT * FROM url_records WHERE url LIKE '%' || :keyword || '%' OR domain LIKE '%' || :keyword || '%' OR sourceName LIKE '%' || :keyword || '%' ORDER BY timestamp DESC")
    fun flowSearch(keyword: String): Flow<List<UrlRecord>>

    /**
     * 获取所有URL记录
     * @return 按时间戳降序排列的URL记录列表
     */
    @Query("SELECT * FROM url_records ORDER BY timestamp DESC")
    fun getAll(): List<UrlRecord>

    /**
     * 分页获取URL记录
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 按时间戳降序排列的URL记录列表
     */
    @Query("SELECT * FROM url_records ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getAll(limit: Int, offset: Int): List<UrlRecord>

    /**
     * 根据域名获取URL记录
     * @param domain 域名
     * @return 该域名下的URL记录列表，按时间戳降序排列
     */
    @Query("SELECT * FROM url_records WHERE domain = :domain ORDER BY timestamp DESC")
    fun getByDomain(domain: String): List<UrlRecord>

    /**
     * 根据书源名称获取URL记录
     * @param sourceName 书源名称
     * @return 该书源的URL记录列表，按时间戳降序排列
     */
    @Query("SELECT * FROM url_records WHERE sourceName = :sourceName ORDER BY timestamp DESC")
    fun getBySourceName(sourceName: String): List<UrlRecord>

    /**
     * 根据关键词搜索URL记录
     * 支持在url、domain、sourceName字段中进行模糊匹配
     * @param keyword 搜索关键词（需包含通配符）
     * @return 匹配的URL记录列表，按时间戳降序排列
     */
    @Query("SELECT * FROM url_records WHERE url LIKE :keyword OR domain LIKE :keyword OR sourceName LIKE :keyword ORDER BY timestamp DESC")
    fun search(keyword: String): List<UrlRecord>

    /**
     * 获取所有不重复的域名列表
     * @return 按域名排序的不重复域名列表
     */
    @Query("SELECT DISTINCT domain FROM url_records ORDER BY domain")
    fun getAllDomains(): List<String>

    /**
     * 获取所有不重复的书源名称列表
     * @return 按名称排序的不重复书源名称列表
     */
    @Query("SELECT DISTINCT sourceName FROM url_records WHERE sourceName IS NOT NULL ORDER BY sourceName")
    fun getAllSourceNames(): List<String>

    /**
     * 插入URL记录
     * 如果记录已存在则替换
     * @param records 要插入的URL记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg records: UrlRecord)

    /**
     * 根据ID删除URL记录
     * @param id 要删除的记录ID
     */
    @Query("DELETE FROM url_records WHERE id = :id")
    fun delete(id: Long)

    /**
     * 删除所有URL记录
     * @return 被删除的记录数量
     */
    @Query("DELETE FROM url_records")
    fun deleteAll(): Int

    /**
     * 删除指定时间之前的旧记录
     * @param timestamp 时间戳阈值
     * @return 被删除的记录数量
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
     * 获取指定时间之前的旧记录数量
     * @param timestamp 时间戳阈值
     * @return 旧记录数量
     */
    @Query("SELECT COUNT(*) FROM url_records WHERE timestamp < :timestamp")
    fun getOldRecordsCount(timestamp: Long): Int

    /**
     * 获取指定时间之后的新记录数量
     * @param timestamp 时间戳阈值
     * @return 新记录数量
     */
    @Query("SELECT COUNT(*) FROM url_records WHERE timestamp > :timestamp")
    fun getCountSince(timestamp: Long): Int

}
