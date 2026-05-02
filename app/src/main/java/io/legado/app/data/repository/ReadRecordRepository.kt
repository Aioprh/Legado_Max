package io.legado.app.data.repository

import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.entities.readRecord.ReadRecordTimelineDay
import io.legado.app.constant.AppConst
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

class ReadRecordRepository(
    private val dao: ReadRecordDao
) {
    private fun getCurrentDeviceId(): String = AppConst.androidId

    fun getTotalReadTime(): Flow<Long> {
        return dao.getTotalReadTime().map { it ?: 0L }
    }

    fun getLatestReadRecords(query: String = ""): Flow<List<ReadRecord>> {
        return if (query.isBlank()) {
            dao.getAllReadRecordsSortedByLastRead()
        } else {
            dao.searchReadRecordsByLastRead(query)
        }
    }

    fun getAllRecordDetails(query: String = ""): Flow<List<ReadRecordDetail>> {
        return if (query.isBlank()) {
            dao.getAllDetails()
        } else {
            dao.searchDetails(query)
        }
    }

    fun getAllSessions(): Flow<List<ReadRecordSession>> {
        return dao.getAllSessions()
    }

    fun getBookSessions(bookName: String, bookAuthor: String): Flow<List<ReadRecordSession>> {
        return dao.getSessionsByBookFlow(getCurrentDeviceId(), bookName, bookAuthor)
    }

    fun getBookTimelineDays(bookName: String, bookAuthor: String): Flow<List<ReadRecordTimelineDay>> {
        return getBookSessions(bookName, bookAuthor).map { sessions ->
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            sessions.groupBy { dateFormat.format(Date(it.startTime)) }
                .toSortedMap(compareByDescending { it })
                .map { (date, daySessions) ->
                    ReadRecordTimelineDay(
                        date = date,
                        sessions = daySessions.sortedByDescending { it.startTime }
                    )
                }
        }
    }

    fun getBookReadTime(bookName: String, bookAuthor: String): Flow<Long> {
        return dao.getReadTimeFlow(getCurrentDeviceId(), bookName, bookAuthor).map { it ?: 0L }
    }

    suspend fun getMergeCandidates(targetRecord: ReadRecord): List<ReadRecord> {
        return dao.getReadRecordsByNameExcludingTarget(
            targetRecord.bookName,
            targetRecord.deviceId,
            targetRecord.bookAuthor
        )
    }

    suspend fun saveReadSession(newSession: ReadRecordSession) {
        val segmentDuration = newSession.endTime - newSession.startTime
        dao.insertSession(newSession)
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = dateFormat.format(Date(newSession.startTime))
        updateReadRecordDetail(newSession, segmentDuration, newSession.words, dateString)
        updateReadRecord(newSession, segmentDuration)
    }

    private suspend fun updateReadRecord(session: ReadRecordSession, durationDelta: Long) {
        if (durationDelta <= 0) return
        val existingRecord = dao.getReadRecord(session.deviceId, session.bookName, session.bookAuthor)
        if (existingRecord != null) {
            dao.update(
                existingRecord.copy(
                    readTime = existingRecord.readTime + durationDelta,
                    lastRead = session.endTime
                )
            )
        } else {
            dao.insert(
                ReadRecord(
                    deviceId = session.deviceId,
                    bookName = session.bookName,
                    bookAuthor = session.bookAuthor,
                    readTime = durationDelta,
                    lastRead = session.endTime
                )
            )
        }
    }

    private suspend fun updateReadRecordDetail(
        session: ReadRecordSession,
        durationDelta: Long,
        wordsDelta: Long,
        dateString: String
    ) {
        if (durationDelta <= 0 && wordsDelta <= 0) return
        val existingDetail = dao.getDetail(
            session.deviceId,
            session.bookName,
            session.bookAuthor,
            dateString
        )
        if (existingDetail != null) {
            existingDetail.readTime += durationDelta
            existingDetail.readWords += wordsDelta
            existingDetail.firstReadTime = min(existingDetail.firstReadTime, session.startTime)
            existingDetail.lastReadTime = max(existingDetail.lastReadTime, session.endTime)
            dao.insertDetail(existingDetail)
        } else {
            dao.insertDetail(
                ReadRecordDetail(
                    deviceId = session.deviceId,
                    bookName = session.bookName,
                    bookAuthor = session.bookAuthor,
                    date = dateString,
                    readTime = durationDelta,
                    readWords = wordsDelta,
                    firstReadTime = session.startTime,
                    lastReadTime = session.endTime
                )
            )
        }
    }

    suspend fun deleteDetail(detail: ReadRecordDetail) {
        dao.deleteDetail(detail)
        dao.deleteSessionsByBookAndDate(
            detail.deviceId,
            detail.bookName,
            detail.bookAuthor,
            detail.date
        )
        updateReadRecordTotal(detail.deviceId, detail.bookName, detail.bookAuthor)
    }

    suspend fun deleteSession(session: ReadRecordSession) {
        dao.deleteSession(session)

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateString = dateFormat.format(Date(session.startTime))
        val remainingSessions =
            dao.getSessionsByBookAndDate(
                session.deviceId,
                session.bookName,
                session.bookAuthor,
                dateString
            )

        if (remainingSessions.isEmpty()) {
            val detail = dao.getDetail(
                session.deviceId,
                session.bookName,
                session.bookAuthor,
                dateString
            )
            detail?.let { dao.deleteDetail(it) }
        } else {
            val totalTime = remainingSessions.sumOf { it.endTime - it.startTime }
            val totalWords = remainingSessions.sumOf { it.words }
            val firstRead = remainingSessions.minOf { it.startTime }
            val lastRead = remainingSessions.maxOf { it.endTime }

            val existingDetail = dao.getDetail(
                session.deviceId,
                session.bookName,
                session.bookAuthor,
                dateString
            )
            existingDetail?.copy(
                readTime = totalTime,
                readWords = totalWords,
                firstReadTime = firstRead,
                lastReadTime = lastRead
            )?.let { dao.insertDetail(it) }
        }

        updateReadRecordTotal(session.deviceId, session.bookName, session.bookAuthor)
    }

    private suspend fun updateReadRecordTotal(
        deviceId: String,
        bookName: String,
        bookAuthor: String,
        minimumReadTime: Long = 0L,
        minimumLastRead: Long = 0L
    ) {
        val allRemainingSessions = dao.getSessionsByBook(deviceId, bookName, bookAuthor)
        val allRemainingDetails = dao.getDetailsByBook(deviceId, bookName, bookAuthor)

        if (allRemainingSessions.isEmpty() && allRemainingDetails.isEmpty()) {
            dao.getReadRecord(deviceId, bookName, bookAuthor)?.let { existing ->
                if (existing.readTime <= minimumReadTime && existing.lastRead <= minimumLastRead) {
                    dao.deleteReadRecord(existing)
                } else {
                    dao.update(
                        existing.copy(
                            readTime = max(existing.readTime, minimumReadTime),
                            lastRead = max(existing.lastRead, minimumLastRead)
                        )
                    )
                }
            }
        } else {
            val sessionTotalTime = allRemainingSessions.sumOf { it.endTime - it.startTime }
            val detailTotalTime = allRemainingDetails.sumOf { it.readTime }
            val totalTime = max(max(sessionTotalTime, detailTotalTime), minimumReadTime)
            val sessionLastRead = allRemainingSessions.maxOfOrNull { it.endTime } ?: 0L
            val detailLastRead = allRemainingDetails.maxOfOrNull { it.lastReadTime } ?: 0L
            val lastRead = max(max(sessionLastRead, detailLastRead), minimumLastRead)

            dao.getReadRecord(deviceId, bookName, bookAuthor)?.copy(
                readTime = totalTime,
                lastRead = lastRead
            )?.let { dao.update(it) }
        }
    }

    suspend fun deleteReadRecord(record: ReadRecord) {
        dao.deleteReadRecord(record)
        dao.deleteDetailsByBook(record.deviceId, record.bookName, record.bookAuthor)
        dao.deleteSessionsByBook(record.deviceId, record.bookName, record.bookAuthor)
    }

    suspend fun mergeReadRecordInto(targetRecord: ReadRecord, sourceRecords: List<ReadRecord>) {
        sourceRecords.forEach { sourceRecord ->
            mergeSingleReadRecordInto(targetRecord, sourceRecord)
        }
    }

    private suspend fun mergeSingleReadRecordInto(targetRecord: ReadRecord, sourceRecord: ReadRecord) {
        if (targetRecord == sourceRecord) return
        if (targetRecord.bookName != sourceRecord.bookName) return

        val source = dao.getReadRecord(
            sourceRecord.deviceId,
            sourceRecord.bookName,
            sourceRecord.bookAuthor
        ) ?: return

        val target = dao.getReadRecord(
            targetRecord.deviceId,
            targetRecord.bookName,
            targetRecord.bookAuthor
        ) ?: targetRecord.copy(readTime = 0L, lastRead = 0L)

        val useSourceProgress = source.lastRead >= target.lastRead

        val mergedReadTime = target.readTime + source.readTime
        val mergedLastRead = max(target.lastRead, source.lastRead)

        dao.insert(
            target.copy(
                readTime = mergedReadTime,
                lastRead = mergedLastRead,
                durChapterTitle = if (useSourceProgress) source.durChapterTitle else target.durChapterTitle,
                durChapterIndex = if (useSourceProgress) source.durChapterIndex else target.durChapterIndex
            )
        )

        val sourceDetails = dao.getDetailsByBook(
            sourceRecord.deviceId,
            sourceRecord.bookName,
            sourceRecord.bookAuthor
        )
        sourceDetails.forEach { detail ->
            val existingTargetDetail = dao.getDetail(
                targetRecord.deviceId,
                targetRecord.bookName,
                targetRecord.bookAuthor,
                detail.date
            )
            if (existingTargetDetail == null) {
                dao.insertDetail(
                    detail.copy(
                        deviceId = targetRecord.deviceId,
                        bookAuthor = targetRecord.bookAuthor
                    )
                )
            } else {
                dao.insertDetail(
                    existingTargetDetail.copy(
                        readTime = existingTargetDetail.readTime + detail.readTime,
                        readWords = existingTargetDetail.readWords + detail.readWords,
                        firstReadTime = min(existingTargetDetail.firstReadTime, detail.firstReadTime),
                        lastReadTime = max(existingTargetDetail.lastReadTime, detail.lastReadTime)
                    )
                )
            }
        }
        dao.deleteDetailsByBook(sourceRecord.deviceId, sourceRecord.bookName, sourceRecord.bookAuthor)

        val sourceSessions = dao.getSessionsByBook(
            sourceRecord.deviceId,
            sourceRecord.bookName,
            sourceRecord.bookAuthor
        )
        sourceSessions.forEach { session ->
            dao.updateSession(
                session.copy(
                    deviceId = targetRecord.deviceId,
                    bookAuthor = targetRecord.bookAuthor
                )
            )
        }

        dao.deleteReadRecord(source)
        updateReadRecordTotal(
            targetRecord.deviceId,
            targetRecord.bookName,
            targetRecord.bookAuthor,
            minimumReadTime = mergedReadTime,
            minimumLastRead = mergedLastRead
        )
    }

    suspend fun fixEmptyAuthors(getAuthorByBookName: suspend (String) -> String?) {
        val recordsWithEmptyAuthor = dao.getRecordsWithEmptyAuthor()
        recordsWithEmptyAuthor.forEach { record ->
            val author = getAuthorByBookName(record.bookName)
            if (!author.isNullOrBlank()) {
                val existingRecord = dao.getReadRecord(record.deviceId, record.bookName, author)
                if (existingRecord != null) {
                    mergeSingleReadRecordInto(existingRecord, record)
                } else {
                    migrateRecordAuthor(record, author)
                }
            }
        }
    }

    suspend fun normalizeDuplicateDeviceRecords() {
        val currentDeviceId = getCurrentDeviceId()
        val groupedRecords = dao.all.groupBy { it.bookName to it.bookAuthor }
        groupedRecords.values.forEach { records ->
            if (records.size <= 1) return@forEach
            val targetRecord = records.firstOrNull { it.deviceId == currentDeviceId }
                ?: records.maxByOrNull { it.lastRead }
                ?: return@forEach

            if (targetRecord.deviceId != currentDeviceId) {
                val migratedTarget = targetRecord.copy(deviceId = currentDeviceId)
                importSingleRecord(migratedTarget)
                importSingleDetailRecords(
                    dao.getDetailsByBook(
                        targetRecord.deviceId,
                        targetRecord.bookName,
                        targetRecord.bookAuthor
                    ).map { it.copy(deviceId = currentDeviceId) }
                )
                importSingleSessionRecords(
                    dao.getSessionsByBook(
                        targetRecord.deviceId,
                        targetRecord.bookName,
                        targetRecord.bookAuthor
                    ).map { it.copy(id = 0, deviceId = currentDeviceId) }
                )
                dao.deleteDetailsByBook(targetRecord.deviceId, targetRecord.bookName, targetRecord.bookAuthor)
                dao.deleteSessionsByBook(targetRecord.deviceId, targetRecord.bookName, targetRecord.bookAuthor)
                dao.deleteReadRecord(targetRecord)
            }

            val normalizedTarget = dao.getReadRecord(
                currentDeviceId,
                targetRecord.bookName,
                targetRecord.bookAuthor
            ) ?: return@forEach

            records.filter { it.deviceId != currentDeviceId }.forEach { sourceRecord ->
                mergeSingleReadRecordInto(normalizedTarget, sourceRecord)
            }
        }
    }

    private suspend fun migrateRecordAuthor(record: ReadRecord, author: String) {
        val source = dao.getReadRecord(record.deviceId, record.bookName, record.bookAuthor) ?: return

        dao.insert(
            source.copy(
                bookAuthor = author
            )
        )
        dao.deleteReadRecord(source)

        val sourceDetails = dao.getDetailsByBook(record.deviceId, record.bookName, record.bookAuthor)
        sourceDetails.forEach { detail ->
            val existingTargetDetail = dao.getDetail(
                record.deviceId,
                record.bookName,
                author,
                detail.date
            )
            if (existingTargetDetail == null) {
                dao.insertDetail(detail.copy(bookAuthor = author))
            } else {
                dao.insertDetail(
                    existingTargetDetail.copy(
                        readTime = existingTargetDetail.readTime + detail.readTime,
                        readWords = existingTargetDetail.readWords + detail.readWords,
                        firstReadTime = min(existingTargetDetail.firstReadTime, detail.firstReadTime),
                        lastReadTime = max(existingTargetDetail.lastReadTime, detail.lastReadTime)
                    )
                )
            }
        }
        dao.deleteDetailsByBook(record.deviceId, record.bookName, record.bookAuthor)

        val sourceSessions = dao.getSessionsByBook(record.deviceId, record.bookName, record.bookAuthor)
        sourceSessions.forEach { session ->
            dao.updateSession(session.copy(bookAuthor = author))
        }
    }

    suspend fun importRecords(
        records: List<ReadRecord>,
        details: List<ReadRecordDetail> = emptyList(),
        sessions: List<ReadRecordSession> = emptyList()
    ) {
        records.forEach { record ->
            importSingleRecord(record)
        }
        importSingleDetailRecords(details)
        importSingleSessionRecords(sessions)
        rebuildImportedBookTotals(records, details, sessions)
    }

    private suspend fun importSingleDetailRecords(details: List<ReadRecordDetail>) {
        details.forEach { detail ->
            importSingleDetail(detail)
        }
    }

    private suspend fun importSingleSessionRecords(sessions: List<ReadRecordSession>) {
        sessions.forEach { session ->
            importSingleSession(session)
        }
    }

    private suspend fun importSingleRecord(record: ReadRecord) {
        val normalized = record.copy(deviceId = getCurrentDeviceId())
        val existing = dao.getReadRecord(
            normalized.deviceId,
            normalized.bookName,
            normalized.bookAuthor
        )
        if (record.deviceId == getCurrentDeviceId()) {
            if (existing == null || existing.readTime < normalized.readTime) {
                dao.insert(normalized)
            }
        } else {
            if (existing == null) {
                dao.insert(normalized)
            } else {
                dao.insert(
                    existing.copy(
                        readTime = existing.readTime + normalized.readTime,
                        lastRead = max(existing.lastRead, normalized.lastRead),
                        durChapterTitle = if (normalized.lastRead >= existing.lastRead) {
                            normalized.durChapterTitle
                        } else {
                            existing.durChapterTitle
                        },
                        durChapterIndex = if (normalized.lastRead >= existing.lastRead) {
                            normalized.durChapterIndex
                        } else {
                            existing.durChapterIndex
                        }
                    )
                )
            }
        }
    }

    private suspend fun importSingleDetail(detail: ReadRecordDetail) {
        val normalized = detail.copy(deviceId = getCurrentDeviceId())
        val existing = dao.getDetail(
            normalized.deviceId,
            normalized.bookName,
            normalized.bookAuthor,
            normalized.date
        )
        if (detail.deviceId == getCurrentDeviceId()) {
            if (existing == null || existing.readTime < normalized.readTime) {
                dao.insertDetail(normalized)
            }
        } else {
            if (existing == null) {
                dao.insertDetail(normalized)
            } else {
                dao.insertDetail(
                    existing.copy(
                        readTime = existing.readTime + normalized.readTime,
                        readWords = existing.readWords + normalized.readWords,
                        firstReadTime = min(existing.firstReadTime, normalized.firstReadTime),
                        lastReadTime = max(existing.lastReadTime, normalized.lastReadTime)
                    )
                )
            }
        }
    }

    private suspend fun importSingleSession(session: ReadRecordSession) {
        val normalized = session.copy(id = 0, deviceId = getCurrentDeviceId())
        val existing = dao.getSessionExact(
            normalized.deviceId,
            normalized.bookName,
            normalized.bookAuthor,
            normalized.startTime,
            normalized.endTime,
            normalized.words
        )
        if (existing == null) {
            dao.insertSession(normalized)
        }
    }

    private suspend fun rebuildImportedBookTotals(
        records: List<ReadRecord>,
        details: List<ReadRecordDetail>,
        sessions: List<ReadRecordSession>
    ) {
        val importedBooks = linkedSetOf<Pair<String, String>>()
        records.forEach { importedBooks.add(it.bookName to it.bookAuthor) }
        details.forEach { importedBooks.add(it.bookName to it.bookAuthor) }
        sessions.forEach { importedBooks.add(it.bookName to it.bookAuthor) }
        importedBooks.forEach { (bookName, bookAuthor) ->
            val current = dao.getReadRecord(getCurrentDeviceId(), bookName, bookAuthor)
            updateReadRecordTotal(
                getCurrentDeviceId(),
                bookName,
                bookAuthor,
                minimumReadTime = current?.readTime ?: 0L,
                minimumLastRead = current?.lastRead ?: 0L
            )
        }
    }
}
