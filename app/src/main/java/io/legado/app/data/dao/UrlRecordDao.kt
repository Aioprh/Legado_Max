package io.legado.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import io.legado.app.data.entities.UrlRecord

/**
 * URL访问记录数据访问对象
 * 
 * 提供对 url_records 表的增删改查操作。
 * 所有查询按时间戳降序排列，最新的记录在前。
 */
@Dao
interface UrlRecordDao {

    /**
     * 获取所有URL记录
     * @return 按时间戳降序排列的所有记录
     */
    @Query("SELECT * FROM url_records ORDER BY timestamp DESC")
    fun getAll(): List<UrlRecord>

    /**
     * 分页获取URL记录
     * @param limit 每页数量
     * @param offset 偏移量
     * @return 指定页的记录列表
     */
    @Query("SELECT * FROM url_records ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    fun getAll(limit: Int, offset: Int): List<UrlRecord>

    /**
     * 按域名筛选记录
     * @param domain 要筛选的域名
     * @return 该域名下的所有记录
     */
    @Query("SELECT * FROM url_records WHERE domain = :domain ORDER BY timestamp DESC")
    fun getByDomain(domain: String): List<UrlRecord>

    /**
     * 按来源名称筛选记录
     * @param sourceName 来源名称（书源名/RSS源名）
     * @return 该来源的所有记录
     */
    @Query("SELECT * FROM url_records WHERE sourceName = :sourceName ORDER BY timestamp DESC")
    fun getBySourceName(sourceName: String): List<UrlRecord>

    /**
     * 搜索URL记录
     * @param keyword 搜索关键词，匹配URL、域名或来源名称
     * @return 匹配的记录列表
     */
    @Query("SELECT * FROM url_records WHERE url LIKE :keyword OR domain LIKE :keyword OR sourceName LIKE :keyword ORDER BY timestamp DESC")
    fun search(keyword: String): List<UrlRecord>

    /**
     * 获取所有不重复的域名列表
     * @return 按字母排序的域名列表
     */
    @Query("SELECT DISTINCT domain FROM url_records ORDER BY domain")
    fun getAllDomains(): List<String>

    /**
     * 获取所有不重复的来源名称列表
     * @return 按字母排序的来源名称列表
     */
    @Query("SELECT DISTINCT sourceName FROM url_records WHERE sourceName IS NOT NULL ORDER BY sourceName")
    fun getAllSourceNames(): List<String>

    /**
     * 插入URL记录
     * @param records 要插入的记录
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    fun insert(vararg records: UrlRecord)

    /**
     * 删除指定ID的记录
     * @param id 要删除的记录ID
     */
    @Query("DELETE FROM url_records WHERE id = :id")
    fun delete(id: Long)

    /**
     * 删除所有记录
     */
    @Query("DELETE FROM url_records")
    fun deleteAll()

    /**
     * 删除指定时间之前的旧记录
     * @param timestamp 时间戳阈值，删除早于此时间的记录
     */
    @Query("DELETE FROM url_records WHERE timestamp < :timestamp")
    fun deleteOldRecords(timestamp: Long)

    /**
     * 获取记录总数
     * @return 数据库中的记录总数
     */
    @Query("SELECT COUNT(*) FROM url_records")
    fun getCount(): Int

    /**
     * 获取指定时间之后的记录数
     * @param timestamp 时间戳阈值
     * @return 该时间之后的记录数
     */
    @Query("SELECT COUNT(*) FROM url_records WHERE timestamp > :timestamp")
    fun getCountSince(timestamp: Long): Int

}
