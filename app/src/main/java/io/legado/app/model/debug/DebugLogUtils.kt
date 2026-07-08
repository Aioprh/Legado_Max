package io.legado.app.model.debug

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 把任意对象转为用于调试日志的简短字符串，避免对 Collection、Map 等容器直接调用 toString()
 * 产生超大字符串导致 OOM。
 */
fun Any?.toDebugString(maxLength: Int = 200): String? {
    if (this == null) return null
    if (this is String) return take(maxLength)
    if (this is CharSequence) return toString().take(maxLength)
    if (this is Collection<*>) {
        val header = "Collection(size=$size)"
        if (isEmpty() || header.length >= maxLength) return header.take(maxLength)
        val remaining = maxLength - header.length - 2
        if (remaining <= 0) return header.take(maxLength)
        val first = firstOrNull()?.toDebugString(remaining.coerceAtMost(120))
        return "$header[$first]"
    }
    if (this is Array<*>) {
        return "Array(size=$size)".take(maxLength)
    }
    if (this is Map<*, *>) {
        return "Map(size=$size)".take(maxLength)
    }
    return toString().take(maxLength)
}

/**
 * 调试日志模块共享工具函数
 */
object DebugLogUtils {

    private val timeFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault())
    }

    private val shortTimeFormatter = ThreadLocal.withInitial {
        SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    }

    /** 格式化时间戳为完整日期时间 */
    fun formatFullTime(timestamp: Long): String {
        return timeFormatter.get()!!.format(Date(timestamp))
    }

    /** 格式化时间戳为短时间（时:分:秒.毫秒） */
    fun formatShortTime(timestamp: Long): String {
        return shortTimeFormatter.get()!!.format(Date(timestamp))
    }

    /** 格式化耗时为人类可读文本（支持 null 输入） */
    fun formatDuration(ms: Long?): String? {
        return ms?.let {
            when {
                it < 1000 -> "${it}ms"
                it < 60000 -> "${it / 1000.0}s"
                else -> "${it / 60000}m ${it % 60000 / 1000}s"
            }
        }
    }
}

/** Compose 版本的文本高亮（需在 Composable 上下文中调用） */
@Composable
fun highlightText(text: String, query: String): AnnotatedString {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        return buildAnnotatedString { append(text) }
    }

    return buildAnnotatedString {
        var startIndex = 0
        var foundIndex = text.indexOf(query, ignoreCase = true)

        while (foundIndex >= 0) {
            append(text.substring(startIndex, foundIndex))
            withStyle(
                SpanStyle(
                    background = Color.Yellow.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(foundIndex, foundIndex + query.length))
            }
            startIndex = foundIndex + query.length
            foundIndex = text.indexOf(query, startIndex, ignoreCase = true)
        }

        if (startIndex < text.length) {
            append(text.substring(startIndex))
        }
    }
}
