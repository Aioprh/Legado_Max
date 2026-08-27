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
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.service.DownloadStatus
import io.legado.app.ui.download.components.DownloadTaskCard
import io.legado.app.ui.theme.PageDimens
import io.legado.app.ui.theme.pageTopBarContainerColor
import io.legado.app.ui.widget.components.AppPageTopBar

/**
 * 下载管理主界面
 * 显示下载任务列表，支持取消、重试、清除等操作
 */
@Composable
fun DownloadManageScreen(
    viewModel: DownloadManageViewModel,
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
            // 统一顶栏（theme-styles.md §14.2）
            AppPageTopBar(
                title = stringResource(R.string.download_manage_title),
                subtitle = if (allTasks.isNotEmpty()) {
                    stringResource(
                        R.string.download_manage_stats,
                        activeCount, completedCount, failedCount
                    )
                } else {
                    null
                },
                onBackClick = onBackClick
            ) {
                IconButton(onClick = { viewModel.clearCompletedTasks() }) {
                    Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.download_manage_clear_completed))
                }
            }
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


