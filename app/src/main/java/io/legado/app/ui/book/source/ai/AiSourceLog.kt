package io.legado.app.ui.book.source.ai

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * AI 生成书源功能的运行日志缓冲（单例、内存态）。
 *
 * 记录「抓取 HTML / AI 生成 / 规则校验 / 自动修复」等操作的步骤与错误，
 * 供日志页 [AiSourceLogActivity] 展示与复制。应用进程重启后清空。
 */
object AiSourceLog {

    private val logs = ArrayDeque<String>()

    private val timeFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    /** 追加一条日志并附带当前时间戳，最多保留 500 条，超出丢弃最早的一条 */
    @Synchronized
    fun log(level: String, tag: String, message: String) {
        val time = timeFormat.format(Date())
        logInternal("[$time][$level][$tag] $message")
    }

    /** 无级别/标签的原始日志（如 AI 返回内容） */
    @Synchronized
    fun raw(message: String) {
        val time = timeFormat.format(Date())
        logInternal("[$time] $message")
    }

    private fun logInternal(line: String) {
        logs.addLast(line)
        while (logs.size > 500) logs.removeFirst()
    }

    /** 当前所有日志，按时间从早到晚 */
    @Synchronized
    fun dump(): String = logs.joinToString("\n")

    /** 清空日志 */
    @Synchronized
    fun clear() {
        logs.clear()
    }
}