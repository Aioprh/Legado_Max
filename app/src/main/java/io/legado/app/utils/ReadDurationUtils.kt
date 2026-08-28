package io.legado.app.utils

import android.content.Context
import io.legado.app.R

/**
 * 格式化阅读时长为可读字符串。
 * 格式：x小时x分钟 / x分钟x秒 / x秒
 *
 * @param mss 毫秒
 * @param context 用于获取字符串资源
 */
fun formatReadDuration(context: Context, mss: Long): String {
    val totalSeconds = mss / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60

    return when {
        hours > 0 -> {
            val hourStr = context.getString(if (hours == 1L) R.string.rr_hour else R.string.rr_hours)
            val minStr = context.getString(if (minutes == 1L) R.string.rr_minute else R.string.rr_minutes)
            "$hours$hourStr$minutes$minStr"
        }
        minutes > 0 -> {
            val minStr = context.getString(if (minutes == 1L) R.string.rr_minute else R.string.rr_minutes)
            val secStr = context.getString(if (seconds == 1L) R.string.rr_second else R.string.rr_seconds)
            "$minutes$minStr$seconds$secStr"
        }
        else -> {
            val secStr = context.getString(if (seconds == 1L) R.string.rr_second else R.string.rr_seconds)
            "$seconds$secStr"
        }
    }
}
