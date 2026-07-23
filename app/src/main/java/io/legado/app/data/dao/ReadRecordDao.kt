package io.legado.app.data.dao

import androidx.room.*
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import kotlinx.coroutines.flow.Flow

/**
 * 阅读记录数据访问接口
 */
@Dao
interface ReadRecordDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(vararg readRecord: ReadRecord)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDetail(detail: ReadRecordDetail)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ReadRecordSession)

    @Update
    suspend fun update(vararg record: ReadRecord)

    @Update
    suspend fun updateSession(session: ReadRecordSession)

    @Delete
    suspend fun delete(vararg record: ReadRecord)

    @Delete
    suspend fun deleteDetail(detail: ReadRecordDetail)

    @Delete
    suspend fun deleteSession(session: ReadRecordSession)

    @Query("DELETE FROM readRecord")
    suspend fun clear()

    @Query("DELETE FROM readRecordDetail")
    suspend fun clearDetails()

    @Query("DELETE FROM readRecordSession")
    suspend fun clearSessions()

    @Query("DELETE FROM readRecord WHERE bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun deleteByNameAndAuthor(bookName: String, bookAuthor: String)

    @Query("SELECT * FROM readRecord WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun getReadRecord(deviceId: String, bookName: String, bookAuthor: String): ReadRecord?

    @Query("SELECT * FROM readRecord WHERE bookName = :bookName")
    suspend fun getReadRecordsByName(bookName: String): List<ReadRecord>

    @Query("SELECT * FROM readRecord WHERE bookName = :bookName AND NOT (deviceId = :excludeDeviceId AND bookAuthor = :excludeAuthor)")
    suspend fun getReadRecordsByNameExcludingTarget(bookName: String, excludeDeviceId: String, excludeAuthor: String): List<ReadRecord>

    @Query("SELECT SUM(readTime) FROM readRecord")
    fun getTotalReadTime(): Flow<Long?>

    /**
     * 计算总阅读时间（复刻 applyDetailReadTimes 逻辑）。
     * 对每本书取 max(readRecord.readTime, 该书所有 detail 的 readTime 之和)，再汇总。
     * 始终基于全量数据，不受搜索过滤影响。
     */
    @Query(
        "SELECT COALESCE(SUM(" +
            "CASE WHEN detail_sums.total_detail_time IS NOT NULL " +
            "AND detail_sums.total_detail_time > readRecord.readTime " +
            "THEN detail_sums.total_detail_time " +
            "ELSE readRecord.readTime END), 0) " +
            "FROM readRecord " +
            "LEFT JOIN (SELECT deviceId, bookName, bookAuthor, SUM(readTime) AS total_detail_time " +
            "FROM readRecordDetail GROUP BY deviceId, bookName, bookAuthor) detail_sums " +
            "ON readRecord.deviceId = detail_sums.deviceId " +
            "AND readRecord.bookName = detail_sums.bookName " +
            "AND readRecord.bookAuthor = detail_sums.bookAuthor"
    )
    fun getCalculatedTotalReadTime(): Flow<Long>

    @Query("SELECT readTime FROM readRecord WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    fun getReadTimeFlow(deviceId: String, bookName: String, bookAuthor: String): Flow<Long?>

    @Query("SELECT readTime FROM readRecord WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun getReadTime(deviceId: String, bookName: String, bookAuthor: String): Long?

    @Query("SELECT * FROM readRecord ORDER BY lastRead DESC")
    fun getAllReadRecordsSortedByLastRead(): Flow<List<ReadRecord>>

    @get:Query("SELECT * FROM readRecord")
    val all: List<ReadRecord>

    @get:Query("SELECT COUNT(*) FROM readRecord")
    val count: Int

    @Query("SELECT * FROM readRecord WHERE bookName LIKE '%' || :query || '%' OR bookAuthor LIKE '%' || :query || '%' ORDER BY lastRead DESC")
    fun searchReadRecordsByLastRead(query: String): Flow<List<ReadRecord>>

    @Query("SELECT * FROM readRecordDetail WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor AND date = :date")
    suspend fun getDetail(deviceId: String, bookName: String, bookAuthor: String, date: String): ReadRecordDetail?

    @Query("SELECT * FROM readRecordDetail WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun getDetailsByBook(deviceId: String, bookName: String, bookAuthor: String): List<ReadRecordDetail>

    @Query("SELECT * FROM readRecordDetail ORDER BY date DESC")
    fun getAllDetails(): Flow<List<ReadRecordDetail>>

    /**
     * 轻量级查询，仅返回详情总数，用作 Flow 触发器。
     * 用于替代直接 getAllDetails() 的 Flow，避免大数据量时 CursorWindow 溢出。
     */
    @Query("SELECT COUNT(*) FROM readRecordDetail")
    fun detailsCountFlow(): Flow<Int>

    /**
     * 分页查询详情记录，每页数据量小，不会超出 CursorWindow 的 2MB 限制。
     */
    @Query("SELECT * FROM readRecordDetail ORDER BY date DESC LIMIT :limit OFFSET :offset")
    suspend fun getDetailsPage(limit: Int, offset: Int): List<ReadRecordDetail>

    @Query("SELECT * FROM readRecordDetail")
    suspend fun getAllDetailsList(): List<ReadRecordDetail>

    @Query("SELECT COUNT(*) FROM readRecordDetail")
    fun getDetailsCount(): Int

    @Query("SELECT * FROM readRecordDetail WHERE bookName LIKE '%' || :query || '%' OR bookAuthor LIKE '%' || :query || '%' ORDER BY date DESC")
    fun searchDetails(query: String): Flow<List<ReadRecordDetail>>

    @Query("DELETE FROM readRecordDetail WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun deleteDetailsByBook(deviceId: String, bookName: String, bookAuthor: String)

    @Query("SELECT * FROM readRecordSession ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<ReadRecordSession>>

    /**
     * 轻量级查询，仅返回会话总数，用作 Flow 触发器。
     * 用于替代直接 getAllSessions() 的 Flow，避免大数据量时 CursorWindow 溢出。
     */
    @Query("SELECT COUNT(*) FROM readRecordSession")
    fun sessionsCountFlow(): Flow<Int>

    /**
     * 分页查询会话记录，每页数据量小，不会超出 CursorWindow 的 2MB 限制。
     */
    @Query("SELECT * FROM readRecordSession ORDER BY startTime DESC LIMIT :limit OFFSET :offset")
    suspend fun getSessionsPage(limit: Int, offset: Int): List<ReadRecordSession>

    @Query("SELECT * FROM readRecordSession")
    suspend fun getAllSessionsList(): List<ReadRecordSession>

    @Query("SELECT COUNT(*) FROM readRecordSession")
    fun getSessionsCount(): Int

    @Query("SELECT * FROM readRecordSession WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor ORDER BY startTime DESC")
    fun getSessionsByBookFlow(deviceId: String, bookName: String, bookAuthor: String): Flow<List<ReadRecordSession>>

    @Query("SELECT * FROM readRecordSession WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun getSessionsByBook(deviceId: String, bookName: String, bookAuthor: String): List<ReadRecordSession>

    @Query("SELECT * FROM readRecordSession WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor AND date(startTime / 1000, 'unixepoch', 'localtime') = :date")
    suspend fun getSessionsByBookAndDate(deviceId: String, bookName: String, bookAuthor: String, date: String): List<ReadRecordSession>

    @Query(
        "SELECT * FROM readRecordSession " +
            "WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor " +
            "AND startTime = :startTime AND endTime = :endTime AND words = :words LIMIT 1"
    )
    suspend fun getSessionExact(
        deviceId: String,
        bookName: String,
        bookAuthor: String,
        startTime: Long,
        endTime: Long,
        words: Long
    ): ReadRecordSession?

    @Query("DELETE FROM readRecordSession WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor")
    suspend fun deleteSessionsByBook(deviceId: String, bookName: String, bookAuthor: String)

    @Query("DELETE FROM readRecordSession WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = :bookAuthor AND date(startTime / 1000, 'unixepoch', 'localtime') = :date")
    suspend fun deleteSessionsByBookAndDate(deviceId: String, bookName: String, bookAuthor: String, date: String)

    @Query("SELECT * FROM readRecord WHERE bookAuthor = ''")
    suspend fun getRecordsWithEmptyAuthor(): List<ReadRecord>

    @Query("DELETE FROM readRecord WHERE trim(bookName) = ''")
    suspend fun deleteRecordsWithBlankBookName()

    @Query("DELETE FROM readRecordDetail WHERE trim(bookName) = ''")
    suspend fun deleteDetailsWithBlankBookName()

    @Query("DELETE FROM readRecordSession WHERE trim(bookName) = ''")
    suspend fun deleteSessionsWithBlankBookName()

    @Query("UPDATE readRecord SET bookAuthor = :author WHERE deviceId = :deviceId AND bookName = :bookName AND bookAuthor = ''")
    suspend fun updateAuthorByBookName(deviceId: String, bookName: String, author: String)

    @Query("SELECT bookName, bookAuthor, SUM(readTime) as totalReadTime FROM readRecordDetail GROUP BY bookName, bookAuthor")
    suspend fun getBookReadTimes(): List<BookReadTime>

    @Delete
    suspend fun deleteReadRecord(record: ReadRecord)
}

data class BookReadTime(
    val bookName: String,
    val bookAuthor: String,
    val totalReadTime: Long
)
