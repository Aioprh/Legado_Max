package io.legado.app.ui.download

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.service.DownloadStatus
import io.legado.app.service.DownloadTask
import io.legado.app.ui.theme.PageDimens
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
    val allTasks by viewModel.tasks.collectAsStateWithLifecycle()
    val filteredTasks by viewModel.filteredTasks.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    val topBarColor = pageTopBarContainerColor()

    val activeCount = allTasks.count { it.status == DownloadStatus.RUNNING || it.status == DownloadStatus.PENDING }
    val completedCount = allTasks.count { it.status == DownloadStatus.SUCCESSFUL }
    val failedCount = allTasks.count { it.status == DownloadStatus.FAILED }

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
                            text = stringResource(R.string.download_manage_title),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Medium
                        )
                        if (allTasks.isNotEmpty()) {
                            Text(
                                text = stringResource(
                                    R.string.download_manage_stats,
                                    activeCount, completedCount, failedCount
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.clearCompletedTasks() }) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.download_manage_clear_completed))
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(modifier = Modifier.padding(paddingValues)) {
            // TabRow
            val tabs = DownloadTab.values()
            TabRow(
                selectedTabIndex = tabs.indexOf(selectedTab),
                containerColor = topBarColor,
                contentColor = MaterialTheme.colorScheme.onSecondary
            ) {
                tabs.forEach { tab ->
                    val count = when (tab) {
                        DownloadTab.ALL -> allTasks.size
                        DownloadTab.DOWNLOADING -> activeCount
                        DownloadTab.PAUSED -> allTasks.count { it.status == DownloadStatus.PAUSED }
                        DownloadTab.COMPLETED -> completedCount
                        DownloadTab.FAILED -> failedCount
                    }
                    Tab(
                        selected = selectedTab == tab,
                        onClick = { viewModel.selectTab(tab) },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(text = stringResource(tab.labelRes), style = MaterialTheme.typography.bodySmall)
                                if (count > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Badge(containerColor = MaterialTheme.colorScheme.primary) {
                                        Text(text = count.toString(), style = MaterialTheme.typography.labelSmall)
                                    }
                                }
                            }
                        }
                    )
                }
            }

            // 任务列表或空状态
            if (filteredTasks.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
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
                        val (emptyTitleRes, emptySubtitleRes) = when (selectedTab) {
                            DownloadTab.ALL -> R.string.download_empty_all_title to R.string.download_empty_all_subtitle
                            DownloadTab.DOWNLOADING -> R.string.download_empty_downloading_title to R.string.download_empty_downloading_subtitle
                            DownloadTab.PAUSED -> R.string.download_empty_paused_title to R.string.download_empty_paused_subtitle
                            DownloadTab.COMPLETED -> R.string.download_empty_completed_title to R.string.download_empty_completed_subtitle
                            DownloadTab.FAILED -> R.string.download_empty_failed_title to R.string.download_empty_failed_subtitle
                        }
                        Text(
                            text = stringResource(emptyTitleRes),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = stringResource(emptySubtitleRes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(PageDimens.screenPadding),
                    verticalArrangement = Arrangement.spacedBy(PageDimens.cardSpacing)
                ) {
                    items(filteredTasks, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            onCancelClick = { viewModel.cancelDownload(task.id) },
                            onRetryClick = { viewModel.retryDownload(task.id) },
                            onOpenFileClick = { viewModel.openFile(task.id) },
                            onOpenFolderClick = { viewModel.openFolder() },
                            onCopyPathClick = { viewModel.copyPath(task.id) }
                        )
                    }
                    item { Spacer(modifier = Modifier.height(16.dp)) }
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
