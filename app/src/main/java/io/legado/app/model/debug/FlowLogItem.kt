package io.legado.app.model.debug

import androidx.compose.runtime.Immutable
import java.util.UUID

/**
 * 流程日志项
 *
 * 记录源规则执行的每个步骤
 */
@Immutable
data class FlowLogItem(
    val id: String = UUID.randomUUID().toString(),
    val requestId: String,           // 请求ID，用于分组
    val sourceUrl: String?,          // 书源URL
    val sourceName: String?,         // 书源名称
    val stage: FlowStage,            // 流程阶段
    val operation: String?,          // 操作类型（搜索、详情、目录、正文）
    val message: String,             // 日志消息
    val detail: String? = null,      // 详细信息
    val startTime: Long = System.currentTimeMillis(),  // 开始时间
    val duration: Long? = null,      // 耗时（毫秒）
    val url: String? = null,         // 请求URL
    val method: String? = null,      // 请求方法
    val statusCode: Int? = null,     // 状态码
    val rule: String? = null,        // 规则内容
    val result: String? = null,      // 执行结果
    val originalValue: String? = null, // 原始数据（替换前的数据）
    val error: Throwable? = null     // 错误信息
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
}
