package io.legado.app.data.repository.debug

import io.legado.app.data.entities.BaseSource
import io.legado.app.help.config.AppConfig
import io.legado.app.model.debug.FlowLogItem
import io.legado.app.model.debug.FlowStage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import java.util.ArrayDeque
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
/**
 * 流程日志记录器
 * 
 * 负责记录源规则执行的完整流程，包括：
 * - 网络请求阶段
 * - 规则解析阶段
 * - 字段提取阶段
 * - 数据替换阶段
 * 
 * 特性：
 * - 异步记录日志，不阻塞主流程
 * - 最多保留3000条日志，超过自动删除最旧的
 * - 按书源URL分组管理请求ID
 * - 支持按阶段、书源、操作类型过滤
 */
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

object FlowLogRecorder {

     // 最大日志数量限制
    private const val MAX_LOG_COUNT = 500
    private const val UPDATE_DEBOUNCE_MS = 500L
    // 日志列表，使用MutableSharedFlow实现响应式更新，设置缓冲区避免丢失更新
    private val _logs = MutableSharedFlow<List<FlowLogItem>>(
        replay = 1, // 保留最新的1个值，新订阅者可以立即收到
        extraBufferCapacity = 64// 缓冲区大小，减少丢失更新的风险
    )
    val logs: SharedFlow<List<FlowLogItem>> = _logs.asSharedFlow()
    
     // 请求会话映射：书源URL -> 请求ID
    private val logDeque = ArrayDeque<FlowLogItem>()
    private val logSize = AtomicInteger(0)
    private val pendingUpdate = AtomicBoolean(false)

    private val requestSessions = ConcurrentHashMap<String, String>()
    
    // 操作类型映射：书源URL -> 操作类型（搜索/详情/目录/正文）
    private val operationMap = ConcurrentHashMap<String, String>()

    val isEnabled: Boolean get() = AppConfig.debugLogFloatingBall

    /**
     * 设置当前书源的操作类型
     * 
     * @param sourceUrl 书源URL
     * @param operation 操作类型（搜索/发现/详情/目录/正文）
     */
    fun setOperation(sourceUrl: String, operation: String) {
        operationMap[sourceUrl] = operation
    }

    /**
     * 获取当前书源的操作类型
     */
    private fun getOperation(sourceUrl: String?): String? {
        return sourceUrl?.let { operationMap[it] }
    }

    /**
     * 开始新的调试会话
     * 
     * @param sourceUrl 书源URL
     * @param sourceName 书源名称
     * @return 请求ID
     */
    fun startSession(sourceUrl: String, sourceName: String? = null): String {
        val requestId = UUID.randomUUID().toString()
        requestSessions[sourceUrl] = requestId
        return requestId
    }

    /**
     * 获取或创建请求ID
     * 
     * @param sourceUrl 书源URL
     * @return 请求ID，如果没有则创建新的
     */
    fun getOrCreateRequestId(sourceUrl: String): String {
        return requestSessions.getOrPut(sourceUrl) {
            UUID.randomUUID().toString()
        }
    }

    /**
     * 结束调试会话
     * 
     * @param sourceUrl 书源URL
     */
    fun endSession(sourceUrl: String) {
        requestSessions.remove(sourceUrl)
        operationMap.remove(sourceUrl)
    }

    /**
     * 记录网络请求日志
     * 
     * @param source 书源对象
     * @param message 日志消息
     * @param url 请求URL
     * @param method 请求方法（GET/POST等）
     * @param statusCode 响应状态码
     * @param duration 请求耗时（毫秒）
     * @param detail 详细信息（如响应体）
     * @param error 错误信息
     */
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

    /**
     * 记录规则解析日志
     * 
     * @param source 书源对象
     * @param message 日志消息
     * @param rule 规则内容
     * @param result 解析结果
     * @param duration 解析耗时（毫秒）
     * @param detail 详细信息
     * @param error 错误信息
     */
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

    /**
     * 记录字段提取日志
     * 
     * @param source 书源对象
     * @param message 日志消息
     * @param rule 提取规则
     * @param result 提取结果
     * @param originalValue 原始数据（提取前的数据）
     * @param duration 提取耗时（毫秒）
     * @param detail 详细信息
     * @param error 错误信息
     */
    fun logExtract(
        source: BaseSource?,
        message: String,
        rule: String? = null,
        result: String? = null,
        originalValue: String? = null,
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
            originalValue = originalValue,
            duration = duration,
            error = error
        )
    }

    /**
     * 记录数据替换日志
     * 
     * @param source 书源对象
     * @param message 日志消息
     * @param rule 替换规则
     * @param result 替换结果
     * @param originalValue 原始数据（替换前的数据）
     * @param duration 替换耗时（毫秒）
     * @param detail 详细信息
     * @param error 错误信息
     */
    fun logReplace(
        source: BaseSource?,
        message: String,
        rule: String? = null,
        result: String? = null,
        originalValue: String? = null,
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
            originalValue = originalValue,
            duration = duration,
            error = error
        )
    }

    /**
     * 记录流程日志（异步）
     * 
     * @param sourceUrl 书源URL
     * @param sourceName 书源名称
     * @param stage 流程阶段
     * @param operation 操作类型
     * @param message 日志消息
     * @param detail 详细信息
     * @param duration 耗时（毫秒）
     * @param url 请求URL
     * @param method 请求方法
     * @param statusCode 状态码
     * @param rule 规则内容
     * @param result 执行结果
     * @param originalValue 原始数据（替换前的数据）
     * @param error 错误信息
     */
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
        originalValue: String? = null,
        error: Throwable? = null
    ) {
        if (!isEnabled) return
        
        GlobalScope.launch(Dispatchers.IO) {
            // 获取或创建请求ID，用于分组
            val requestId = sourceUrl?.let { getOrCreateRequestId(it) }
                ?: UUID.randomUUID().toString()

            // 创建日志项
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
                originalValue = originalValue,
                error = error
            )

            addLog(item)
        }
    }

    /**
     * 添加日志项（内部方法）
     * 
     * @param item 日志项
     */
    @Synchronized
    private fun addLog(item: FlowLogItem) {
        logDeque.addFirst(item)
        logSize.incrementAndGet()
        
        while (logSize.get() > MAX_LOG_COUNT) {
            logDeque.removeLast()
            logSize.decrementAndGet()
        }
        
        scheduleUpdate()
    }
    
    private fun scheduleUpdate() {
        if (pendingUpdate.compareAndSet(false, true)) {
            GlobalScope.launch(Dispatchers.IO) {
                delay(UPDATE_DEBOUNCE_MS)
                pendingUpdate.set(false)
                _logs.emit(getCurrentLogs())
            }
        }
    }
    
    /**
     * 获取当前日志列表快照（同步方法）
     * 
     * @return 当前日志列表
     */
    fun getCurrentLogs(): List<FlowLogItem> {
        return synchronized(logDeque) {
            logDeque.toList()
        }
    }

    /**
     * 清空所有日志
     */
    fun clear() {
        synchronized(logDeque) {
            logDeque.clear()
            logSize.set(0)
        }
        requestSessions.clear()
        operationMap.clear()
        
        // 发送空列表到Flow
        GlobalScope.launch(Dispatchers.IO) {
            _logs.emit(emptyList())
        }
    }

    /**
     * 按请求ID分组
     * 
     * @param logs 日志列表
     * @return 按请求ID分组的日志Map
     */
    fun groupByRequestId(logs: List<FlowLogItem>): Map<String, List<FlowLogItem>> {
        return logs.groupBy { it.requestId }
    }

    /**
     * 按阶段过滤
     * 
     * @param logs 日志列表
     * @param stage 流程阶段（null表示不过滤）
     * @return 过滤后的日志列表
     */
    fun filterByStage(logs: List<FlowLogItem>, stage: FlowStage?): List<FlowLogItem> {
        return if (stage == null) logs else logs.filter { it.stage == stage }
    }

    /**
     * 按书源过滤
     * 
     * @param logs 日志列表
     * @param sourceUrl 书源URL（null表示不过滤）
     * @return 过滤后的日志列表
     */
    fun filterBySource(logs: List<FlowLogItem>, sourceUrl: String?): List<FlowLogItem> {
        return if (sourceUrl == null) logs else logs.filter { it.sourceUrl == sourceUrl }
    }

    /**
     * 按操作类型过滤
     * 
     * @param logs 日志列表
     * @param operation 操作类型（null表示不过滤）
     * @return 过滤后的日志列表
     */
    fun filterByOperation(logs: List<FlowLogItem>, operation: String?): List<FlowLogItem> {
        return if (operation == null) logs else logs.filter { it.operation == operation }
    }
}
