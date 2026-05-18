package io.legado.app.ui.download

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.service.DownloadStatus
import io.legado.app.service.DownloadTask
import io.legado.app.ui.theme.pageCardContainerColor
import io.legado.app.ui.theme.pageTopBarContainerColor
import io.legado.app.utils.ConvertUtils

/**
 * 下载管理主界面
 * 显示下载任务列表，支持取消、重试、清除等操作
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManageScreen(
    viewModel: DownloadManageViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val tasks by viewModel.tasks.collectAsState()
    val context = LocalContext.current
    
    val containerColor = pageCardContainerColor()
    val topBarColor = pageTopBarContainerColor()
    
    // 统计各状态任务数量
    val activeCount = tasks.count { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PENDING }
    val completedCount = tasks.count { it.status == DownloadStatus.SUCCESSFUL }
    val failedCount = tasks.count { it.status == DownloadStatus.FAILED }
    
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                title = {
                    Column {
                        Text(
                            text = "下载管理",
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Medium
                            )
                        )
                        // 显示任务统计信息
                        if (tasks.isNotEmpty()) {
                            Text(
                                text = "下载中: $activeCount  已完成: $completedCount  失败: $failedCount",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    // 清除已完成任务按钮
                    IconButton(onClick = { viewModel.clearCompletedTasks() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = "清除已完成")
                    }
                }
            )
        }
    ) { paddingValues ->
        if (tasks.isEmpty()) {
            // 空状态提示
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Schedule,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        text = "暂无下载任务",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "在浏览器中下载的文件会显示在这里",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            // 任务列表
            LazyColumn(
                modifier = Modifier.padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(tasks, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        onCancelClick = { viewModel.cancelDownload(task.id) },
                        onRetryClick = { viewModel.retryDownload(context, task.id) }
                    )
                }
                
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

/**
 * 下载任务卡片
 * 显示单个下载任务的信息和操作按钮
 */
@Composable
fun DownloadTaskCard(
    task: DownloadTask,
    onCancelClick: () -> Unit,
    onRetryClick: () -> Unit
) {
    val containerColor = pageCardContainerColor()
    
    Card(
        modifier = Modifier.fillMaxWidth(),
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
                                contentDescription = "取消",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    DownloadStatus.PAUSED -> {
                        // 继续按钮
                        IconButton(onClick = onRetryClick) {
                            Icon(
                                Icons.Default.PlayArrow,
                                contentDescription = "继续",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    DownloadStatus.FAILED -> {
                        // 重试按钮
                        IconButton(onClick = onRetryClick) {
                            Icon(
                                Icons.Default.Refresh,
                                contentDescription = "重试",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    DownloadStatus.SUCCESSFUL -> {
                        // 删除按钮
                        IconButton(onClick = onCancelClick) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "删除",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
            
            // 下载中显示已下载/总大小
            if (task.downloadedSize > 0 && task.totalSize > 0 && task.status == DownloadStatus.RUNNING) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = ConvertUtils.formatFileSize(task.downloadedSize.toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = ConvertUtils.formatFileSize(task.totalSize.toLong()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
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
        DownloadStatus.SUCCESSFUL -> Icons.Default.CheckCircle to Color(0xFF4CAF50)
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
        DownloadStatus.RUNNING -> "下载中"
        DownloadStatus.PENDING -> "等待中"
        DownloadStatus.PAUSED -> "已暂停"
        DownloadStatus.SUCCESSFUL -> "已完成"
        DownloadStatus.FAILED -> "下载失败"
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
        DownloadStatus.SUCCESSFUL -> Color(0xFF4CAF50)
        DownloadStatus.FAILED -> MaterialTheme.colorScheme.error
    }
}
