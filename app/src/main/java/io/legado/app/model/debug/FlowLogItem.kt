package io.legado.app.model.debug

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * 流程日志项
 *
 * 记录源规则执行的每个步骤，包括：
 * - 基础信息：时间、阶段、操作类型
 * - 网络信息：URL、方法、状态码
 * - 规则信息：规则内容、执行结果
 * - 嵌套信息：规则执行路径树
 * - JS信息：JS执行环境状态
 */
@Immutable
data class FlowLogItem(
    val id: String = UUID.randomUUID().toString(),
    val requestId: String,
    val sourceUrl: String?,
    val sourceName: String?,
    val stage: FlowStage,
    val operation: String?,
    val message: String,
    val detail: String? = null,
    val startTime: Long = System.currentTimeMillis(),
    val duration: Long? = null,
    val url: String? = null,
    val method: String? = null,
    val statusCode: Int? = null,
    val rule: String? = null,
    val result: String? = null,
    val originalValue: String? = null,
    val error: Throwable? = null,
    val executionTree: RuleExecutionTree? = null,
    val jsExecution: JsExecutionRecord? = null,
    val ruleType: RuleType? = null,
    val matchCount: Int? = null,
    val inputPreview: String? = null,
    val outputPreview: String? = null,
    val variableOperations: List<VariableOperation> = emptyList()
) {
    /**
     * 格式化显示时间
     */
    fun formatTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss.SSS", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(startTime))
    }
    
    /**
     * 格式化显示耗时
     */
    fun formatDuration(): String? {
        return duration?.let {
            when {
                it < 1000 -> "${it}ms"
                it < 60000 -> "${it / 1000.0}s"
                else -> "${it / 60000}m ${it % 60000 / 1000}s"
            }
        }
    }

    fun hasExecutionTree(): Boolean = executionTree != null

    fun hasJsExecution(): Boolean = jsExecution != null

    fun hasVariableOperations(): Boolean = variableOperations.isNotEmpty()

    fun getVariableSummary(): String {
        if (variableOperations.isEmpty()) return ""
        val reads = variableOperations.count { it.operationType == VariableOperationType.READ }
        val writes = variableOperations.count { it.operationType == VariableOperationType.WRITE }
        val deletes = variableOperations.count { it.operationType == VariableOperationType.DELETE }
        val parts = mutableListOf<String>()
        if (reads > 0) parts.add("读${reads}")
        if (writes > 0) parts.add("写${writes}")
        if (deletes > 0) parts.add("删${deletes}")
        return parts.joinToString(" ")
    }

    fun getSummaryText(): String {
        val parts = mutableListOf<String>()
        ruleType?.let { parts.add("${it.icon}${it.displayName}") }
        matchCount?.let { parts.add("匹配${it}个") }
        duration?.let { parts.add(formatDuration() ?: "") }
        return parts.filter { it.isNotBlank() }.joinToString(" | ")
    }
}

/**
 * 流程日志分组
 *
 * 按请求ID分组的流程日志
 */
@Immutable
data class FlowLogGroup(
    val requestId: String,
    val sourceUrl: String?,
    val sourceName: String?,
    val operation: String?,
    val startTime: Long,
    val items: List<FlowLogItem>,
    val totalDuration: Long = items.lastOrNull()?.let { end ->
        end.startTime + (end.duration ?: 0) - items.firstOrNull()!!.startTime
    } ?: 0,
    val isSuccess: Boolean = items.none { it.error != null }
) {
    /**
     * 格式化显示时间
     */
    fun formatTime(): String {
        val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return sdf.format(java.util.Date(startTime))
    }
    
    /**
     * 格式化显示总耗时
     */
    fun formatTotalDuration(): String {
        return when {
            totalDuration < 1000 -> "${totalDuration}ms"
            totalDuration < 60000 -> "${totalDuration / 1000.0}s"
            else -> "${totalDuration / 60000}m ${totalDuration % 60000 / 1000}s"
        }
    }

    fun getRuleExecutionTrees(): List<RuleExecutionTree> {
        return items.mapNotNull { it.executionTree }
    }

    fun getJsExecutions(): List<JsExecutionRecord> {
        return items.mapNotNull { it.jsExecution }
    }
}
