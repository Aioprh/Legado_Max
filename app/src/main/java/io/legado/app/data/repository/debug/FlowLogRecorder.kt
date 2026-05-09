package io.legado.app.data.repository.debug

import io.legado.app.data.entities.BaseSource
import io.legado.app.model.debug.FlowLogItem
import io.legado.app.model.debug.FlowStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object FlowLogRecorder {

    private const val MAX_LOG_COUNT = 3000

    private val _logs = MutableStateFlow<List<FlowLogItem>>(emptyList())
    val logs: StateFlow<List<FlowLogItem>> = _logs.asStateFlow()

    private val requestSessions = ConcurrentHashMap<String, String>()
    private val operationMap = ConcurrentHashMap<String, String>()

    /**
     * 设置当前书源的操作类型（搜索/详情/目录/正文）
     */
    fun setOperation(sourceUrl: String, operation: String) {
        operationMap[sourceUrl] = operation
    }

    private fun getOperation(sourceUrl: String?): String? {
        return sourceUrl?.let { operationMap[it] }
    }

    fun startSession(sourceUrl: String, sourceName: String? = null): String {
        val requestId = UUID.randomUUID().toString()
        requestSessions[sourceUrl] = requestId
        return requestId
    }

    fun getOrCreateRequestId(sourceUrl: String): String {
        return requestSessions.getOrPut(sourceUrl) {
            UUID.randomUUID().toString()
        }
    }

    fun endSession(sourceUrl: String) {
        requestSessions.remove(sourceUrl)
        operationMap.remove(sourceUrl)
    }

    fun logNetwork(
        source: BaseSource?,
        message: String,
        url: String? = null,
        method: String? = null,
        statusCode: Int? = null,
        duration: Long? = null,
        detail: String? = null,
        error: Throwable? = null
    ) {
        val sourceUrl = source?.getKey()
        log(
            sourceUrl = sourceUrl,
            sourceName = source?.getTag(),
            stage = FlowStage.NETWORK,
            operation = getOperation(sourceUrl),
            message = message,
            detail = detail,
            url = url,
            method = method,
            statusCode = statusCode,
            duration = duration,
            error = error
        )
    }

    fun logParse(
        source: BaseSource?,
        message: String,
        rule: String? = null,
        result: String? = null,
        duration: Long? = null,
        detail: String? = null,
        error: Throwable? = null
    ) {
        val sourceUrl = source?.getKey()
        log(
            sourceUrl = sourceUrl,
            sourceName = source?.getTag(),
            stage = FlowStage.PARSE,
            operation = getOperation(sourceUrl),
            message = message,
            detail = detail,
            rule = rule,
            result = result,
            duration = duration,
            error = error
        )
    }

    fun logExtract(
        source: BaseSource?,
        message: String,
        rule: String? = null,
        result: String? = null,
        duration: Long? = null,
        detail: String? = null,
        error: Throwable? = null
    ) {
        val sourceUrl = source?.getKey()
        log(
            sourceUrl = sourceUrl,
            sourceName = source?.getTag(),
            stage = FlowStage.EXTRACT,
            operation = getOperation(sourceUrl),
            message = message,
            detail = detail,
            rule = rule,
            result = result,
            duration = duration,
            error = error
        )
    }

    fun logReplace(
        source: BaseSource?,
        message: String,
        rule: String? = null,
        result: String? = null,
        duration: Long? = null,
        detail: String? = null,
        error: Throwable? = null
    ) {
        val sourceUrl = source?.getKey()
        log(
            sourceUrl = sourceUrl,
            sourceName = source?.getTag(),
            stage = FlowStage.REPLACE,
            operation = getOperation(sourceUrl),
            message = message,
            detail = detail,
            rule = rule,
            result = result,
            duration = duration,
            error = error
        )
    }

    fun log(
        sourceUrl: String?,
        sourceName: String? = null,
        stage: FlowStage,
        operation: String? = null,
        message: String,
        detail: String? = null,
        duration: Long? = null,
        url: String? = null,
        method: String? = null,
        statusCode: Int? = null,
        rule: String? = null,
        result: String? = null,
        error: Throwable? = null
    ) {
        GlobalScope.launch(Dispatchers.IO) {
            val requestId = sourceUrl?.let { getOrCreateRequestId(it) }
                ?: UUID.randomUUID().toString()

            val item = FlowLogItem(
                requestId = requestId,
                sourceUrl = sourceUrl,
                sourceName = sourceName,
                stage = stage,
                operation = operation,
                message = message,
                detail = detail,
                duration = duration,
                url = url,
                method = method,
                statusCode = statusCode,
                rule = rule,
                result = result,
                error = error
            )

            addLog(item)
        }
    }

    @Synchronized
    private fun addLog(item: FlowLogItem) {
        val currentLogs = _logs.value.toMutableList()
        currentLogs.add(0, item)

        if (currentLogs.size > MAX_LOG_COUNT) {
            val removedCount = currentLogs.size - MAX_LOG_COUNT
            repeat(removedCount) {
                currentLogs.removeAt(currentLogs.size - 1)
            }
        }

        _logs.value = currentLogs
    }

    fun clear() {
        _logs.value = emptyList()
        requestSessions.clear()
        operationMap.clear()
    }

    fun groupByRequestId(logs: List<FlowLogItem>): Map<String, List<FlowLogItem>> {
        return logs.groupBy { it.requestId }
    }

    fun filterByStage(logs: List<FlowLogItem>, stage: FlowStage?): List<FlowLogItem> {
        return if (stage == null) logs else logs.filter { it.stage == stage }
    }

    fun filterBySource(logs: List<FlowLogItem>, sourceUrl: String?): List<FlowLogItem> {
        return if (sourceUrl == null) logs else logs.filter { it.sourceUrl == sourceUrl }
    }

    fun filterByOperation(logs: List<FlowLogItem>, operation: String?): List<FlowLogItem> {
        return if (operation == null) logs else logs.filter { it.operation == operation }
    }
}