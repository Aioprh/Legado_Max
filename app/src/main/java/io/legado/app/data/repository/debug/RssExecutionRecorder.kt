package io.legado.app.data.repository.debug

import io.legado.app.model.debug.RssExecutionRecord
import io.legado.app.model.debug.RssExecutionStatus
import io.legado.app.model.debug.RssExecutionStep
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.ArrayDeque
import java.util.UUID

object RssExecutionRecorder {

    private const val MAX_RECORDS = 500

    private val records = ArrayDeque<RssExecutionRecord>()

    private val _recordsFlow = MutableSharedFlow<List<RssExecutionRecord>>(
        replay = 1,
        extraBufferCapacity = 16
    )
    val recordsFlow: SharedFlow<List<RssExecutionRecord>> = _recordsFlow.asSharedFlow()

    val isEnabled: Boolean get() = io.legado.app.help.config.AppConfig.debugLogFloatingBall

    // 当前执行会话的源信息
    private var currentSourceUrl: String = ""
    private var currentSourceName: String = ""
    private var currentExecutionId: String = ""
    private var currentStartTime: Long = 0L

    /**
     * 开始一个新的执行会话
     */
    fun startSession(sourceUrl: String, sourceName: String) {
        currentSourceUrl = sourceUrl
        currentSourceName = sourceName
        currentExecutionId = UUID.randomUUID().toString().take(8)
        currentStartTime = System.currentTimeMillis()
    }

    /**
     * 记录一个步骤的执行结果
     */
    fun record(record: RssExecutionRecord) {
        if (!isEnabled) return
        synchronized(records) {
            records.addFirst(record)
            while (records.size > MAX_RECORDS) {
                records.removeLast()
            }
            emitUpdate()
        }
    }

    /**
     * 记录配置检查步骤：字段为空则跳过，非空则执行正确
     */
    fun check(step: RssExecutionStep, value: String?) {
        if (value.isNullOrBlank()) {
            record(makeRecord(step, RssExecutionStatus.EMPTY_SKIP))
        } else {
            record(makeRecord(step, RssExecutionStatus.SUCCESS, detail = value.take(100)))
        }
    }

    /**
     * 记录配置检查：带合法性校验
     */
    fun checkWithValidation(step: RssExecutionStep, value: String?, validation: Pair<Boolean, String>) {
        val (isValid, reason) = validation
        if (value.isNullOrBlank()) {
            record(makeRecord(step, RssExecutionStatus.EMPTY_SKIP))
        } else if (!isValid) {
            record(makeRecord(step, RssExecutionStatus.FAILED, error = reason, detail = value.take(100)))
        } else {
            record(makeRecord(step, RssExecutionStatus.SUCCESS, detail = value.take(100)))
        }
    }

    /**
     * 记录布尔值配置检查
     */
    fun check(step: RssExecutionStep, value: Boolean) {
        record(makeRecord(step, RssExecutionStatus.SUCCESS, detail = value.toString()))
    }

    /**
     * 记录执行步骤：成功
     */
    fun success(step: RssExecutionStep, detail: String? = null, duration: Long? = null) {
        record(makeRecord(step, RssExecutionStatus.SUCCESS, detail = detail, duration = duration))
    }

    /**
     * 记录执行步骤：失败
     */
    fun failed(step: RssExecutionStep, error: String, duration: Long? = null) {
        record(makeRecord(step, RssExecutionStatus.FAILED, error = error, duration = duration))
    }

    /**
     * 标记当前会话结束，记录总耗时
     */
    fun endSession() {
        if (currentExecutionId.isEmpty()) return
        val totalDuration = System.currentTimeMillis() - currentStartTime
        record(makeRecord(
            RssExecutionStep.SOURCE_NAME,
            RssExecutionStatus.SUCCESS,
            detail = "本次执行共耗时 ${formatDuration(totalDuration)}",
            duration = totalDuration,
            isSessionEnd = true
        ))
        currentSourceUrl = ""
        currentSourceName = ""
        currentExecutionId = ""
        currentStartTime = 0L
    }

    /**
     * 获取当前所有记录
     */
    fun getCurrentRecords(): List<RssExecutionRecord> {
        synchronized(records) {
            return records.toList()
        }
    }

    /**
     * 清空记录
     */
    fun clear() {
        synchronized(records) {
            records.clear()
        }
        emitUpdate()
    }

    private fun makeRecord(
        step: RssExecutionStep,
        status: RssExecutionStatus,
        detail: String? = null,
        error: String? = null,
        duration: Long? = null,
        isSessionStart: Boolean = false,
        isSessionEnd: Boolean = false
    ): RssExecutionRecord {
        return RssExecutionRecord(
            step = step,
            status = status,
            detail = detail,
            error = error,
            duration = duration,
            sourceUrl = currentSourceUrl,
            sourceName = currentSourceName,
            executionId = currentExecutionId,
            isSessionStart = isSessionStart,
            isSessionEnd = isSessionEnd
        )
    }

    private fun emitUpdate() {
        try {
            _recordsFlow.tryEmit(getCurrentRecords())
        } catch (e: Exception) {
            io.legado.app.model.Debug.log("RssExecutionRecorder", "emitUpdate失败: ${e.message}")
        }
    }

    private fun formatDuration(ms: Long): String = when {
        ms < 1000 -> "${ms}ms"
        ms < 60_000 -> String.format("%.1fs", ms / 1000.0)
        else -> "${ms / 60_000}m${(ms % 60_000) / 1000}s"
    }
}
