package io.legado.app.ui.download.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.service.DownloadStatus
import io.legado.app.service.DownloadTask
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.utils.ConvertUtils

/**
 * 下载任务卡片
 * 显示单个下载任务的信息和操作按钮
 */
@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit,
    onOpenFileClick: () -> Unit = {},
    onOpenFolderClick: () -> Unit = {},
    onCopyPathClick: () -> Unit = {}
) {
    val containerColor = pageCardContainerColor()
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            // 无障碍：合并卡片内文本节点（accessibility.md §15.2）
            .semantics(mergeDescendants = true) {}
            .then(
                if (task.status == DownloadStatus.SUCCESSFUL) {
                    Modifier.clickable { showMenu = true }
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 状态图标
                StatusIcon(task.status, modifier = Modifier.size(24.dp))

                Spacer(modifier = Modifier.width(12.dp))

                // 文件名和状态信息
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = task.fileName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        // 状态文本
                        Text(
                            text = getStatusText(task.status),
                            style = MaterialTheme.typography.bodySmall,
                            color = getStatusColor(task.status)
                        )
                        // 下载中显示进度百分比
                        if (task.status == DownloadStatus.RUNNING) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${task.progress}%",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                        // 显示文件总大小
                        if (task.totalSize > 0) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = ConvertUtils.formatFileSize(task.totalSize.toLong()),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // 操作按钮
                when (task.status) {
                    DownloadStatus.RUNNING, DownloadStatus.PENDING -> {
                        // 取消按钮
                        IconButton(onClick = onCancelClick) {
                            Icon(
                                Icons.Default.Pause,
                                contentDescription = stringResource(R.string.cancel),
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        // 继续按钮
                        IconButton(onClick = onRetryClick) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = stringResource(R.string.download_resume),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    DownloadStatus.FAILED -> {
                        // 重试按钮
                        IconButton(onClick = onRetryClick) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = stringResource(R.string.retry),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    DownloadStatus.SUCCESSFUL -> {
                        // 删除按钮移至 PopupMenu
                    }
                }
            }

            // 下载中或等待中显示进度条
            if (task.status == DownloadStatus.RUNNING || task.status == DownloadStatus.PENDING) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { task.progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            // 下载中显示速度 + 已下载/总大小
            if (task.status == DownloadStatus.RUNNING && task.downloadedSize > 0 && task.totalSize > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "${ConvertUtils.formatFileSize(task.downloadedSize.toLong())} / ${ConvertUtils.formatFileSize(task.totalSize.toLong())}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (task.speed > 0) {
                        Text(
                            text = "${formatSpeed(task.speed)}/s",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // 来源信息
            if (task.sourceUrl.isNotEmpty() || task.downloadUrl.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                if (task.sourceUrl.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.download_source_label, task.sourceUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (task.downloadUrl.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.download_link_label, task.downloadUrl),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // 操作菜单（仅已完成状态）
            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.download_open_file)) },
                    onClick = { showMenu = false; onOpenFileClick() },
                    leadingIcon = { Icon(Icons.Default.OpenInNew, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.download_open_folder)) },
                    onClick = { showMenu = false; onOpenFolderClick() },
                    leadingIcon = { Icon(Icons.Default.Folder, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.download_copy_path)) },
                    onClick = { showMenu = false; onCopyPathClick() },
                    leadingIcon = { Icon(Icons.Default.ContentCopy, contentDescription = null) }
                )
                HorizontalDivider()
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) },
                    onClick = { showMenu = false; onCancelClick() },
                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) }
                )
            }
        }
    }
}

/**
 * 状态图标
 * 根据下载状态显示不同图标和颜色
 */
@Composable
fun StatusIcon(status: DownloadStatus, modifier: Modifier = Modifier) {
    val (icon, color) = when (status) {
        DownloadStatus.RUNNING -> Icons.Default.Refresh to MaterialTheme.colorScheme.primary
        DownloadStatus.PENDING -> Icons.Default.Schedule to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.PAUSED -> Icons.Default.Pause to MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.SUCCESSFUL -> Icons.Default.CheckCircle to MaterialTheme.colorScheme.tertiary
        DownloadStatus.FAILED -> Icons.Default.Error to MaterialTheme.colorScheme.error
    }

    Icon(
        imageVector = icon,
        contentDescription = null,
        modifier = modifier,
        tint = color
    )
}

/**
 * 获取状态文本
 */
@Composable
fun getStatusText(status: DownloadStatus): String {
    return when (status) {
        DownloadStatus.RUNNING -> stringResource(R.string.download_status_running)
        DownloadStatus.PENDING -> stringResource(R.string.download_status_pending)
        DownloadStatus.PAUSED -> stringResource(R.string.download_status_paused)
        DownloadStatus.SUCCESSFUL -> stringResource(R.string.download_status_completed)
        DownloadStatus.FAILED -> stringResource(R.string.download_status_failed)
    }
}

/**
 * 获取状态颜色
 */
@Composable
fun getStatusColor(status: DownloadStatus): Color {
    return when (status) {
        DownloadStatus.RUNNING -> MaterialTheme.colorScheme.primary
        DownloadStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.PAUSED -> MaterialTheme.colorScheme.onSurfaceVariant
        DownloadStatus.SUCCESSFUL -> MaterialTheme.colorScheme.tertiary
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    }
}

/**
 * 格式化下载速度
 */
private fun formatSpeed(bytesPerSec: Long): String {
    return when {
        bytesPerSec >= 1_048_576 -> String.format("%.1f MB", bytesPerSec / 1_048_576.0)
        bytesPerSec >= 1024 -> String.format("%.1f KB", bytesPerSec / 1024.0)
        else -> "$bytesPerSec B"
    }
}
