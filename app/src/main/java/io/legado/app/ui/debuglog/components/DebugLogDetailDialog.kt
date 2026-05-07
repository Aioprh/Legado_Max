package io.legado.app.ui.debuglog.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.model.debug.DebugEvent
import io.legado.app.model.debug.DebugLevel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 调试日志详情对话框
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugLogDetailDialog(
    log: DebugEvent,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .heightIn(max = 600.dp)
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // 标题栏
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = when (log.level) {
                            DebugLevel.ERROR -> Icons.Default.Error
                            DebugLevel.WARN -> Icons.Default.Warning
                            else -> Icons.Default.Info
                        },
                        tint = when (log.level) {
                            DebugLevel.ERROR -> MaterialTheme.colorScheme.error
                            DebugLevel.WARN -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        },
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "日志详情",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                TextButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "关闭")
                }
            }

            Spacer(Modifier.height(16.dp))

            // 基本信息
            DetailSection(title = "基本信息") {
                DetailRow("时间", formatFullTime(log.time), copyable = false)
                DetailRow("级别", log.level.displayName, copyable = false)
                DetailRow("分类", log.category.displayName, copyable = false)

                if (!log.traceId.isNullOrBlank()) {
                    DetailRow("Trace ID", log.traceId!!, copyable = true)
                }
            }

            Spacer(Modifier.height(12.dp))

            // 网络请求信息
            if (log.category == io.legado.app.model.debug.DebugCategory.NETWORK && log.url != null) {
                DetailSection(title = "请求信息") {
                    DetailRow("URL", log.url!!, copyable = true)
                    DetailRow("方法", log.method ?: "-", copyable = false)
                    DetailRow("状态码", log.statusCode?.toString() ?: "-", copyable = false)
                    DetailRow("耗时", "${log.duration ?: 0}ms", copyable = false)
                }

                if (log.sourceName != null || log.sourceUrl != null) {
                    Spacer(Modifier.height(12.dp))
                    DetailSection(title = "来源信息") {
                        log.sourceName?.let { DetailRow("书源名", it, copyable = false) }
                        log.sourceUrl?.let { DetailRow("书源URL", it, copyable = true) }
                    }
                }
            }

            // 异常堆栈
            if (log.throwable != null) {
                Spacer(Modifier.height(12.dp))
                DetailSection(title = "异常信息") {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                    ) {
                        Text(
                            text = log.throwable!!.stackTraceToString(),
                            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                }
            }

            // 详细消息
            if (!log.detail.isNullOrBlank()) {
                Spacer(Modifier.height(12.dp))
                DetailSection(title = "详细内容") {
                    Text(
                        text = log.detail!!,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 底部操作按钮
            Spacer(Modifier.height(20.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                OutlinedButton(onClick = onDismiss) {
                    Text("关闭")
                }

                Spacer(Modifier.width(8.dp))

                Button(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("复制全部")
                }
            }
        }
    }
}

/**
 * 详情分组标题 + 内容容器
 */
@Composable
private fun DetailSection(
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

/**
 * 键值对行
 */
@Composable
private fun DetailRow(
    label: String,
    value: String,
    copyable: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(80.dp)
        )

        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 3
        )
    }
}

/**
 * 格式化完整时间戳
 */
private fun formatFullTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}
