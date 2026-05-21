package io.legado.app.data.repository.debug

import io.legado.app.model.debug.RssExecutionRecord
import io.legado.app.model.debug.RssExecutionStatus
import io.legado.app.model.debug.RssExecutionStep
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import java.util.ArrayDeque

/**
 * 订阅源执行记录器
 *
 * 记录订阅源执行过程中每一步的状态（成功/失败/跳过），
 * 用于在调试日志面板的"执行情况"区域展示。
 *
 * 按 sourceUrl 分组存储，支持同时调试多个订阅源。
 */
object RssExecutionRecorder {

    private const val MAX_RECORDS = 500

    private val records = ArrayDeque<RssExecutionRecord>()

    private val _recordsFlow = MutableSharedFlow<List<RssExecutionRecord>>(
        replay = 1,
        extraBufferCapacity = 16
    )
    val recordsFlow: SharedFlow<List<RssExecutionRecord>> = _recordsFlow.asSharedFlow()

    val isEnabled: Boolean get() = io.legado.app.help.config.AppConfig.debugLogFloatingBall

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
            record(RssExecutionRecord(step, RssExecutionStatus.EMPTY_SKIP))
        } else {
            record(RssExecutionRecord(step, RssExecutionStatus.SUCCESS, detail = value.take(100)))
        }
    }

    /**
     * 记录布尔值配置检查
     */
    fun check(step: RssExecutionStep, value: Boolean) {
        record(RssExecutionRecord(
            step, RssExecutionStatus.SUCCESS,
            detail = value.toString()
        ))
    }

    /**
     * 记录执行步骤：成功
     */
    fun success(step: RssExecutionStep, detail: String? = null, duration: Long? = null) {
        record(RssExecutionRecord(step, RssExecutionStatus.SUCCESS, detail, duration = duration))
    }

    /**
     * 记录执行步骤：失败
     */
    fun failed(step: RssExecutionStep, error: String, duration: Long? = null) {
        record(RssExecutionRecord(step, RssExecutionStatus.FAILED, error = error, duration = duration))
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

    private fun emitUpdate() {
        try {
            _recordsFlow.tryEmit(getCurrentRecords())
        } catch (e: Exception) {
            io.legado.app.model.Debug.log("RssExecutionRecorder", "emitUpdate失败: ${e.message}")
        }
    }
}
