package io.legado.app.ui.book.readRecord

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.legado.app.constant.AppConst
import io.legado.app.constant.PreferKey
import io.legado.app.data.appDb
import io.legado.app.data.dao.DailyReadStat
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.BookRepository
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefInt
import io.legado.app.utils.putPrefInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import splitties.init.appCtx

private typealias RecordIdentity = Triple<String, String, String>

data class ReadRecordUiState(
    val isLoading: Boolean = true,
    val totalReadTime: Long = 0,
    val todayReadTime: Long = 0,
    val todayBookCount: Int = 0,
    val groupedRecords: Map<String, List<ReadRecordDetail>> = emptyMap(),
    val timelineRecords: Map<String, List<ReadRecordSession>> = emptyMap(),
    val latestRecords: List<ReadRecord> = emptyList(),
    val readTimeRecords: List<ReadRecord> = emptyList(),
    val selectedDate: LocalDate? = null,
    val searchKey: String? = null,
    val dailyReadCounts: Map<LocalDate, Int> = emptyMap(),
    val dailyReadTimes: Map<LocalDate, Long> = emptyMap(),
    val isSelectionMode: Boolean = false,
    val selectedRecords: Set<RecordIdentity> = emptySet()
)

enum class DisplayMode {
    AGGREGATE,
    TIMELINE,
    LATEST,
    READ_TIME
}

/**
 * 按显示模式加载的额外数据。
 * - AGGREGATE 模式加载 details
 * - TIMELINE 模式加载 sessions
 * - LATEST / READ_TIME 模式不需要额外数据（使用 recordsFlow）
 */
private data class ModeData(
    val details: List<ReadRecordDetail>? = null,
    val sessions: List<ReadRecordSession>? = null
)

/** 轻量级统计数据（SQL 聚合，始终加载）。 */
private data class StatsData(
    val totalReadTime: Long,
    val dailyStats: List<DailyReadStat>,
    val todayReadTime: Long,
    val todayBookCount: Int
)

/** 搜索 + 日期筛选状态。 */
private data class FilterState(
    val query: String,
    val dateStr: String?
)

@OptIn(ExperimentalCoroutinesApi::class)
class ReadRecordViewModel : ViewModel() {

    private val repository = ReadRecordRepository(appDb.readRecordDao)
    private val bookRepository = BookRepository()

    private val coverPathCache = ConcurrentHashMap<String, String?>()
    private val chapterTitleCache = ConcurrentHashMap<String, String?>()

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).apply {
        timeZone = TimeZone.getDefault()
    }

    private val _displayMode = MutableStateFlow(loadDisplayMode())
    val displayMode = _displayMode.asStateFlow()
    private val _enableReadRecord = MutableStateFlow(AppConfig.enableReadRecord)
    val enableReadRecord = _enableReadRecord.asStateFlow()
    private val _searchKey = MutableStateFlow("")
    private val _selectedDate = MutableStateFlow<LocalDate?>(null)
    private val _isSelectionMode = MutableStateFlow(false)
    private val _selectedRecords = MutableStateFlow<Set<RecordIdentity>>(emptySet())

    init {
        viewModelScope.launch(Dispatchers.IO) {
            if (appCtx.getPrefInt(PreferKey.readRecordRepairVersion) < ReadRecordRepository.CURRENT_REPAIR_VERSION) {
                repository.repairRecords { bookName ->
                    bookRepository.getAuthorByBookName(bookName)
                }
                appCtx.putPrefInt(
                    PreferKey.readRecordRepairVersion,
                    ReadRecordRepository.CURRENT_REPAIR_VERSION
                )
            }
        }
    }

    private fun loadDisplayMode(): DisplayMode {
        val savedOrdinal = appCtx.getPrefInt(PreferKey.readRecordDisplayMode, DisplayMode.AGGREGATE.ordinal)
        return enumValueOf<DisplayMode>(DisplayMode.values().getOrNull(savedOrdinal)?.name ?: DisplayMode.AGGREGATE.name)
    }

    // ==================== 拆分后的 Flow（方案三核心） ====================

    /** 搜索 + 日期筛选状态（派生自 _searchKey 和 _selectedDate） */
    private val filterState = combine(_searchKey, _selectedDate) { query, date ->
        FilterState(query, date?.format(DateTimeFormatter.ISO_LOCAL_DATE))
    }

    /**
     * 轻量级统计数据 Flow —— SQL 聚合，始终加载。
     * 包含：总阅读时间、每日统计（热力图）、今日阅读时间、今日书籍数。
     */
    private val statsFlow = combine(
        repository.getTotalReadTime(),
        repository.getDailyStats(),
        repository.getReadTimeByDate(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE)),
        repository.getBookCountByDate(LocalDate.now().format(DateTimeFormatter.ISO_LOCAL_DATE))
    ) { total, daily, todayTime, todayCount ->
        StatsData(total, daily, todayTime, todayCount)
    }

    /**
     * 阅读记录 Flow（SummaryCard + LATEST / READ_TIME 模式共用）。
     * - 无日期筛选：SQL JOIN 返回 readTime = MAX(record, detail_sum)
     * - 有日期筛选：SQL GROUP BY 返回该日期的每书统计
     */
    private val recordsFlow = filterState.flatMapLatest { filter ->
        if (filter.dateStr == null) {
            repository.getRecordsWithDetailTime(filter.query)
        } else {
            repository.getRecordsByDate(filter.query, filter.dateStr)
        }
    }

    /**
     * 按显示模式加载的额外数据 Flow。
     * flatMapLatest 确保切换模式时自动取消上一个模式的数据加载。
     */
    private val modeDataFlow: StateFlow<ModeData> = _displayMode
        .flatMapLatest { mode ->
            when (mode) {
                DisplayMode.AGGREGATE -> filterState.flatMapLatest { filter ->
                    repository.getFilteredDetails(filter.query, filter.dateStr)
                        .map { ModeData(details = it) }
                }
                DisplayMode.TIMELINE -> filterState.flatMapLatest { filter ->
                    repository.getFilteredSessions(filter.query, filter.dateStr)
                        .map { ModeData(sessions = it) }
                }
                DisplayMode.LATEST, DisplayMode.READ_TIME -> flowOf(ModeData())
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ModeData())

    /** 选择模式状态 */
    private val selectionState = combine(_isSelectionMode, _selectedRecords) { isSel, selRecs ->
        isSel to selRecs
    }

    /**
     * UI 状态 Flow —— 组合统计数据、记录、模式数据和选择状态。
     * 计算量极小：SQL 已完成聚合/过滤/排序，此处仅做轻量组装。
     */
    val uiState: StateFlow<ReadRecordUiState> = combine(
        statsFlow,
        recordsFlow,
        modeDataFlow,
        filterState,
        selectionState
    ) { stats, records, modeData, filter, selection ->
        val (isSelectionMode, selectedRecords) = selection
        val selectedDate = filter.dateStr?.let { LocalDate.parse(it, DateTimeFormatter.ISO_LOCAL_DATE) }

        // AGGREGATE 模式：details 按 date 分组（轻量操作，数据已被 SQL 过滤）
        val groupedRecords = modeData.details
            ?.groupBy { it.date }
            ?: emptyMap()

        // TIMELINE 模式：sessions 按日期分组 + 合并连续会话
        val timelineRecords = modeData.sessions
            ?.let { sessions -> buildTimelineMap(sessions) }
            ?: emptyMap()

        // daily stats 转 Map（SQL 已聚合，仅需转换 key 类型）
        val dailyReadCounts = stats.dailyStats.associate {
            LocalDate.parse(it.date, DateTimeFormatter.ISO_LOCAL_DATE) to it.readCount
        }
        val dailyReadTimes = stats.dailyStats.associate {
            LocalDate.parse(it.date, DateTimeFormatter.ISO_LOCAL_DATE) to it.totalReadTime
        }

        ReadRecordUiState(
            isLoading = false,
            totalReadTime = stats.totalReadTime,
            todayReadTime = stats.todayReadTime,
            todayBookCount = stats.todayBookCount,
            groupedRecords = groupedRecords,
            timelineRecords = timelineRecords,
            latestRecords = records,
            readTimeRecords = records.sortedByDescending { it.readTime },
            selectedDate = selectedDate,
            searchKey = filter.query,
            dailyReadCounts = dailyReadCounts,
            dailyReadTimes = dailyReadTimes,
            isSelectionMode = isSelectionMode,
            selectedRecords = selectedRecords
        )
    }.flowOn(Dispatchers.Default)
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = ReadRecordUiState(isLoading = true)
        )

    /**
     * 构建时间线 Map：按日期分组 + 合并连续会话。
     * 数据已被 SQL 过滤，此处仅做合并和分组。
     */
    private fun buildTimelineMap(sessions: List<ReadRecordSession>): Map<String, List<ReadRecordSession>> {
        return sessions
            .groupBy { dateFormat.format(Date(it.startTime)) }
            .mapValues { (_, daySessions) -> mergeContinuousSessions(daySessions).reversed() }
            .toSortedMap(compareByDescending { it })
    }

    fun setSearchKey(query: String) {
        _searchKey.value = query
    }

    fun setDisplayMode(mode: DisplayMode) {
        _displayMode.value = mode
        appCtx.putPrefInt(PreferKey.readRecordDisplayMode, mode.ordinal)
    }

    fun setEnableReadRecord(enabled: Boolean) {
        AppConfig.enableReadRecord = enabled
        _enableReadRecord.value = enabled
    }

    fun setSelectedDate(date: LocalDate?) {
        _selectedDate.value = date
    }

    fun deleteDetail(detail: ReadRecordDetail) {
        viewModelScope.launch { repository.deleteDetail(detail) }
    }

    fun deleteSession(session: ReadRecordSession) {
        viewModelScope.launch { repository.deleteSession(session) }
    }

    fun deleteReadRecord(record: ReadRecord) {
        viewModelScope.launch {
            val selectedDate = _selectedDate.value?.format(DateTimeFormatter.ISO_LOCAL_DATE)
            if (selectedDate == null) {
                repository.deleteReadRecord(record)
            } else {
                repository.deleteReadRecordByDate(record, selectedDate)
            }
        }
    }

    private fun mergeContinuousSessions(sessions: List<ReadRecordSession>): List<ReadRecordSession> {
        if (sessions.isEmpty()) return emptyList()
        val sortedSessions = sessions.sortedBy { it.startTime }
        val mergedList = mutableListOf<ReadRecordSession>()
        mergedList.add(sortedSessions.first().copy())

        val gapLimit = 20 * 60 * 1000L

        for (i in 1 until sortedSessions.size) {
            val current = sortedSessions[i]
            val last = mergedList.last()
            if (current.bookName == last.bookName &&
                current.bookAuthor == last.bookAuthor &&
                (current.startTime - last.endTime) <= gapLimit
            ) {
                mergedList[mergedList.lastIndex] = last.copy(endTime = maxOf(last.endTime, current.endTime))
            } else {
                mergedList.add(current.copy())
            }
        }
        return mergedList
    }

    suspend fun getChapterTitle(bookName: String, bookAuthor: String, chapterIndexLong: Long): String? {
        return bookRepository.getChapterTitle(bookName, bookAuthor, chapterIndexLong.toInt())
    }

    suspend fun getBookDurChapterTitle(bookName: String, bookAuthor: String): String? {
        val key = cacheKey(bookName, bookAuthor)
        chapterTitleCache[key]?.let { return it }
        val result = bookRepository.getBookDurChapterTitle(bookName, bookAuthor)
        result?.let { chapterTitleCache[key] = it }
        return result
    }

    suspend fun getBookCover(bookName: String, bookAuthor: String): String? {
        val key = cacheKey(bookName, bookAuthor)
        coverPathCache[key]?.let { return it }
        // 查询内部可能触发同步联网搜封面(java.ajax)，必须离开主线程执行
        val result = withContext(Dispatchers.IO) {
            bookRepository.getBookCoverByNameAndAuthor(bookName, bookAuthor)
        }
        result?.let { coverPathCache[key] = it }
        return result
    }

    fun getConfiguredDefaultCover(): String? {
        return bookRepository.getConfiguredDefaultCover()
    }

    suspend fun getMergeCandidates(targetRecord: ReadRecord): List<ReadRecord> {
        return repository.getMergeCandidates(targetRecord)
    }

    fun mergeReadRecords(targetRecord: ReadRecord, sourceRecords: List<ReadRecord>) {
        if (sourceRecords.isEmpty()) return
        viewModelScope.launch {
            repository.mergeReadRecordInto(targetRecord, sourceRecords)
        }
    }

    fun enterSelectionMode(record: ReadRecord) {
        _isSelectionMode.value = true
        _selectedRecords.value = setOf(recordIdentity(record.deviceId, record.bookName, record.bookAuthor))
    }

    fun enterSelectionMode(detail: ReadRecordDetail) {
        _isSelectionMode.value = true
        _selectedRecords.value = setOf(recordIdentity(detail.deviceId, detail.bookName, detail.bookAuthor))
    }

    fun enterSelectionMode(session: ReadRecordSession) {
        _isSelectionMode.value = true
        _selectedRecords.value = setOf(recordIdentity(session.deviceId, session.bookName, session.bookAuthor))
    }

    fun exitSelectionMode() {
        _isSelectionMode.value = false
        _selectedRecords.value = emptySet()
    }

    fun toggleRecordSelection(record: ReadRecord) {
        val identity = recordIdentity(record.deviceId, record.bookName, record.bookAuthor)
        toggleIdentitySelection(identity)
    }

    fun toggleRecordSelection(detail: ReadRecordDetail) {
        val identity = recordIdentity(detail.deviceId, detail.bookName, detail.bookAuthor)
        toggleIdentitySelection(identity)
    }

    fun toggleRecordSelection(session: ReadRecordSession) {
        val identity = recordIdentity(session.deviceId, session.bookName, session.bookAuthor)
        toggleIdentitySelection(identity)
    }

    private fun toggleIdentitySelection(identity: RecordIdentity) {
        val currentSelection = _selectedRecords.value.toMutableSet()
        if (currentSelection.contains(identity)) {
            currentSelection.remove(identity)
            if (currentSelection.isEmpty()) {
                _isSelectionMode.value = false
            }
        } else {
            currentSelection.add(identity)
        }
        _selectedRecords.value = currentSelection
    }

    fun selectAllRecords(displayMode: DisplayMode) {
        val allIdentities = _selectedRecords.value.toMutableSet()
        when (displayMode) {
            DisplayMode.LATEST -> {
                allIdentities.addAll(uiState.value.latestRecords.map { 
                    recordIdentity(it.deviceId, it.bookName, it.bookAuthor) 
                })
            }
            DisplayMode.READ_TIME -> {
                allIdentities.addAll(uiState.value.readTimeRecords.map { 
                    recordIdentity(it.deviceId, it.bookName, it.bookAuthor) 
                })
            }
            DisplayMode.AGGREGATE -> {
                allIdentities.addAll(uiState.value.groupedRecords.values.flatten().map { 
                    recordIdentity(it.deviceId, it.bookName, it.bookAuthor) 
                })
            }
            DisplayMode.TIMELINE -> {
                allIdentities.addAll(uiState.value.timelineRecords.values.flatten().map { 
                    recordIdentity(it.deviceId, it.bookName, it.bookAuthor) 
                })
            }
        }
        _selectedRecords.value = allIdentities
    }

    fun deleteSelectedRecords() {
        viewModelScope.launch {
            val selectedList = _selectedRecords.value.map { identity ->
                ReadRecord(
                    deviceId = identity.first,
                    bookName = identity.second,
                    bookAuthor = identity.third
                )
            }
            selectedList.forEach { record ->
                repository.deleteReadRecord(record)
            }
            exitSelectionMode()
        }
    }

    fun addTestReadRecord(bookName: String, bookAuthor: String): ReadRecord {
        val deviceId = AppConst.androidId
        val now = System.currentTimeMillis()
        val record = ReadRecord(
            deviceId = deviceId,
            bookName = bookName,
            bookAuthor = bookAuthor,
            readTime = 30 * 60 * 1000L,
            lastRead = now
        )
        val session = ReadRecordSession(
            deviceId = deviceId,
            bookName = bookName,
            bookAuthor = bookAuthor,
            startTime = now - 30 * 60 * 1000L,
            endTime = now,
            words = 5000L
        )
        viewModelScope.launch {
            repository.saveReadSession(session)
        }
        return record
    }

    fun isSelected(record: ReadRecord): Boolean {
        return _selectedRecords.value.contains(recordIdentity(record.deviceId, record.bookName, record.bookAuthor))
    }

    fun isSelected(detail: ReadRecordDetail): Boolean {
        return _selectedRecords.value.contains(recordIdentity(detail.deviceId, detail.bookName, detail.bookAuthor))
    }

    fun isSelected(session: ReadRecordSession): Boolean {
        return _selectedRecords.value.contains(recordIdentity(session.deviceId, session.bookName, session.bookAuthor))
    }

    private fun cacheKey(bookName: String, bookAuthor: String) = "$bookName|$bookAuthor"
}

private fun recordIdentity(deviceId: String, bookName: String, bookAuthor: String): RecordIdentity {
    return Triple(deviceId, bookName, bookAuthor)
}
