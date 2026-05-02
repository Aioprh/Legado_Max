package io.legado.app.data.repository

import io.legado.app.data.dao.BookReadTime
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ReadRecordRepositoryTest {

    @Test
    fun importAggregateOnlyBackupKeepsReadTime() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }

        repository.importRecords(
            records = listOf(
                ReadRecord(
                    deviceId = "remote",
                    bookName = "Test Book",
                    bookAuthor = "Author",
                    readTime = 3_600_000L,
                    lastRead = 200L
                )
            )
        )

        val record = dao.getReadRecord(CURRENT_DEVICE_ID, "Test Book", "Author")
        assertNotNull(record)
        assertEquals(3_600_000L, record?.readTime)
        assertEquals(200L, record?.lastRead)
    }

    @Test
    fun importDetailAndSessionRebuildsAggregateWhenMissing() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }

        repository.importRecords(
            records = emptyList(),
            details = listOf(
                ReadRecordDetail(
                    deviceId = "remote",
                    bookName = "Detail Book",
                    bookAuthor = "Author",
                    date = "2026-05-02",
                    readTime = 90_000L,
                    lastReadTime = 150L
                )
            ),
            sessions = listOf(
                ReadRecordSession(
                    deviceId = "remote",
                    bookName = "Detail Book",
                    bookAuthor = "Author",
                    startTime = 100L,
                    endTime = 150L,
                    words = 0L
                )
            )
        )

        val record = dao.getReadRecord(CURRENT_DEVICE_ID, "Detail Book", "Author")
        assertNotNull(record)
        assertEquals(90_000L, record?.readTime)
        assertEquals(150L, record?.lastRead)
    }

    @Test
    fun mergeReadRecordIntoAccumulatesReadTime() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }
        val target = ReadRecord(CURRENT_DEVICE_ID, "Merge Book", "Author", 360_000_000L, 1_000L)
        val source = ReadRecord("remote", "Merge Book", "Author", 60_000L, 1_100L)
        dao.insert(target)
        dao.insert(source)

        repository.mergeReadRecordInto(target, listOf(source))

        val record = dao.getReadRecord(CURRENT_DEVICE_ID, "Merge Book", "Author")
        assertNotNull(record)
        assertEquals(360_060_000L, record?.readTime)
        assertEquals(1_100L, record?.lastRead)
    }

    @Test
    fun repairRecordsMergesEmptyAuthorAndDuplicateDeviceRecords() = runBlocking {
        val dao = FakeReadRecordDao()
        val repository = ReadRecordRepository(dao) { CURRENT_DEVICE_ID }
        dao.insert(
            ReadRecord(deviceId = "remote", bookName = "Repair Book", bookAuthor = "", readTime = 60_000L, lastRead = 100L),
            ReadRecord(deviceId = CURRENT_DEVICE_ID, bookName = "Repair Book", bookAuthor = "Author", readTime = 120_000L, lastRead = 200L)
        )

        repository.repairRecords { "Author" }

        val record = dao.getReadRecord(CURRENT_DEVICE_ID, "Repair Book", "Author")
        assertNotNull(record)
        assertEquals(180_000L, record?.readTime)
        assertEquals(200L, record?.lastRead)
        assertEquals(1, dao.all.size)
    }

    private class FakeReadRecordDao : ReadRecordDao {
        private val records = mutableListOf<ReadRecord>()
        private val details = mutableListOf<ReadRecordDetail>()
        private val sessions = mutableListOf<ReadRecordSession>()
        private var nextSessionId = 1L

        override suspend fun insert(vararg readRecord: ReadRecord) {
            readRecord.forEach { record ->
                records.removeAll {
                    it.deviceId == record.deviceId &&
                        it.bookName == record.bookName &&
                        it.bookAuthor == record.bookAuthor
                }
                records.add(record.copy())
            }
        }

        override suspend fun insertDetail(detail: ReadRecordDetail) {
            details.removeAll {
                it.deviceId == detail.deviceId &&
                    it.bookName == detail.bookName &&
                    it.bookAuthor == detail.bookAuthor &&
                    it.date == detail.date
            }
            details.add(detail.copy())
        }

        override suspend fun insertSession(session: ReadRecordSession) {
            val stored = if (session.id == 0L) {
                session.copy(id = nextSessionId++)
            } else {
                session
            }
            sessions.removeAll { it.id == stored.id }
            sessions.add(stored)
        }

        override suspend fun update(vararg record: ReadRecord) {
            insert(*record)
        }

        override suspend fun updateSession(session: ReadRecordSession) {
            sessions.removeAll { it.id == session.id }
            sessions.add(session.copy())
        }

        override suspend fun delete(vararg record: ReadRecord) {
            record.forEach { target ->
                records.removeAll {
                    it.deviceId == target.deviceId &&
                        it.bookName == target.bookName &&
                        it.bookAuthor == target.bookAuthor
                }
            }
        }

        override suspend fun deleteDetail(detail: ReadRecordDetail) {
            details.removeAll {
                it.deviceId == detail.deviceId &&
                    it.bookName == detail.bookName &&
                    it.bookAuthor == detail.bookAuthor &&
                    it.date == detail.date
            }
        }

        override suspend fun deleteSession(session: ReadRecordSession) {
            sessions.removeAll { it.id == session.id }
        }

        override suspend fun clear() {
            records.clear()
        }

        override suspend fun deleteByNameAndAuthor(bookName: String, bookAuthor: String) {
            records.removeAll { it.bookName == bookName && it.bookAuthor == bookAuthor }
        }

        override suspend fun getReadRecord(deviceId: String, bookName: String, bookAuthor: String): ReadRecord? {
            return records.firstOrNull {
                it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
            }?.copy()
        }

        override suspend fun getReadRecordsByName(bookName: String): List<ReadRecord> {
            return records.filter { it.bookName == bookName }.map { it.copy() }
        }

        override suspend fun getReadRecordsByNameExcludingTarget(
            bookName: String,
            excludeDeviceId: String,
            excludeAuthor: String
        ): List<ReadRecord> {
            return records.filter {
                it.bookName == bookName && !(it.deviceId == excludeDeviceId && it.bookAuthor == excludeAuthor)
            }.map { it.copy() }
        }

        override fun getTotalReadTime(): Flow<Long?> {
            return flowOf(records.sumOf { it.readTime })
        }

        override fun getReadTimeFlow(deviceId: String, bookName: String, bookAuthor: String): Flow<Long?> {
            return flowOf(
                records.firstOrNull {
                    it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
                }?.readTime
            )
        }

        override suspend fun getReadTime(deviceId: String, bookName: String, bookAuthor: String): Long? {
            return getReadRecord(deviceId, bookName, bookAuthor)?.readTime
        }

        override fun getAllReadRecordsSortedByLastRead(): Flow<List<ReadRecord>> {
            return flowOf(records.sortedByDescending { it.lastRead }.map { it.copy() })
        }

        override val all: List<ReadRecord>
            get() = records.map { it.copy() }

        override fun searchReadRecordsByLastRead(query: String): Flow<List<ReadRecord>> {
            return flowOf(
                records.filter { it.bookName.contains(query) || it.bookAuthor.contains(query) }
                    .sortedByDescending { it.lastRead }
                    .map { it.copy() }
            )
        }

        override suspend fun getDetail(
            deviceId: String,
            bookName: String,
            bookAuthor: String,
            date: String
        ): ReadRecordDetail? {
            return details.firstOrNull {
                it.deviceId == deviceId &&
                    it.bookName == bookName &&
                    it.bookAuthor == bookAuthor &&
                    it.date == date
            }?.copy()
        }

        override suspend fun getDetailsByBook(deviceId: String, bookName: String, bookAuthor: String): List<ReadRecordDetail> {
            return details.filter {
                it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
            }.map { it.copy() }
        }

        override fun getAllDetails(): Flow<List<ReadRecordDetail>> {
            return flowOf(details.sortedByDescending { it.date }.map { it.copy() })
        }

        override suspend fun getAllDetailsList(): List<ReadRecordDetail> {
            return details.map { it.copy() }
        }

        override fun getDetailsCount(): Int {
            return details.size
        }

        override fun searchDetails(query: String): Flow<List<ReadRecordDetail>> {
            return flowOf(
                details.filter { it.bookName.contains(query) || it.bookAuthor.contains(query) }
                    .sortedByDescending { it.date }
                    .map { it.copy() }
            )
        }

        override suspend fun deleteDetailsByBook(deviceId: String, bookName: String, bookAuthor: String) {
            details.removeAll {
                it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
            }
        }

        override fun getAllSessions(): Flow<List<ReadRecordSession>> {
            return flowOf(sessions.sortedByDescending { it.startTime }.map { it.copy() })
        }

        override suspend fun getAllSessionsList(): List<ReadRecordSession> {
            return sessions.map { it.copy() }
        }

        override fun getSessionsCount(): Int {
            return sessions.size
        }

        override fun getSessionsByBookFlow(deviceId: String, bookName: String, bookAuthor: String): Flow<List<ReadRecordSession>> {
            return flowOf(
                sessions.filter {
                    it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
                }.map { it.copy() }
            )
        }

        override suspend fun getSessionsByBook(deviceId: String, bookName: String, bookAuthor: String): List<ReadRecordSession> {
            return sessions.filter {
                it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
            }.map { it.copy() }
        }

        override suspend fun getSessionsByBookAndDate(
            deviceId: String,
            bookName: String,
            bookAuthor: String,
            date: String
        ): List<ReadRecordSession> {
            return getSessionsByBook(deviceId, bookName, bookAuthor).filter {
                java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it.startTime)) == date
            }
        }

        override suspend fun getSessionExact(
            deviceId: String,
            bookName: String,
            bookAuthor: String,
            startTime: Long,
            endTime: Long,
            words: Long
        ): ReadRecordSession? {
            return sessions.firstOrNull {
                it.deviceId == deviceId &&
                    it.bookName == bookName &&
                    it.bookAuthor == bookAuthor &&
                    it.startTime == startTime &&
                    it.endTime == endTime &&
                    it.words == words
            }?.copy()
        }

        override suspend fun deleteSessionsByBook(deviceId: String, bookName: String, bookAuthor: String) {
            sessions.removeAll {
                it.deviceId == deviceId && it.bookName == bookName && it.bookAuthor == bookAuthor
            }
        }

        override suspend fun deleteSessionsByBookAndDate(
            deviceId: String,
            bookName: String,
            bookAuthor: String,
            date: String
        ) {
            sessions.removeAll {
                it.deviceId == deviceId &&
                    it.bookName == bookName &&
                    it.bookAuthor == bookAuthor &&
                    java.text.SimpleDateFormat("yyyy-MM-dd").format(java.util.Date(it.startTime)) == date
            }
        }

        override suspend fun getRecordsWithEmptyAuthor(): List<ReadRecord> {
            return records.filter { it.bookAuthor.isEmpty() }.map { it.copy() }
        }

        override suspend fun updateAuthorByBookName(deviceId: String, bookName: String, author: String) {
            records.indices.forEach { index ->
                val record = records[index]
                if (record.deviceId == deviceId && record.bookName == bookName && record.bookAuthor.isEmpty()) {
                    records[index] = record.copy(bookAuthor = author)
                }
            }
        }

        override suspend fun getBookReadTimes(): List<BookReadTime> {
            return details.groupBy { it.bookName to it.bookAuthor }
                .map { (identity, grouped) ->
                    BookReadTime(identity.first, identity.second, grouped.sumOf { it.readTime })
                }
        }

        override suspend fun deleteReadRecord(record: ReadRecord) {
            delete(record)
        }
    }

    private companion object {
        const val CURRENT_DEVICE_ID = "current-device"
    }
}
