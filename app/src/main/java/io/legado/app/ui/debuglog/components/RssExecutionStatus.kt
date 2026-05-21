package io.legado.app.ui.debuglog.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.model.debug.RssExecutionRecord
import io.legado.app.model.debug.RssExecutionStatus
import io.legado.app.model.debug.RssExecutionStep
import io.legado.app.ui.widget.components.VerticalScrollbar

/**
 * 订阅源执行情况组件
 *
 * 按顺序展示订阅源每个配置字段和执行步骤的状态，
 * 格式如：✔ 源名称执行正确（legado源）
 *        ⊘ 源分组为空跳过执行
 *        ✘ 网络请求执行失败（连接超时）
 */
@Composable
fun RssExecutionStatus(
    records: List<RssExecutionRecord>,
    modifier: Modifier = Modifier
) {
    if (records.isEmpty()) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "暂无执行记录，运行订阅源调试后将在此显示",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    // 按步骤定义的顺序重新排列（最新的执行记录在前，但显示时按步骤顺序）
    val orderedRecords = remember(records) {
        // 取每个步骤最新的一条记录，按步骤定义顺序排列
        val latestByStep = mutableMapOf<RssExecutionStep, RssExecutionRecord>()
        for (record in records) {
            if (!latestByStep.containsKey(record.step)) {
                latestByStep[record.step] = record
            }
        }
        RssExecutionStep.entries.mapNotNull { latestByStep[it] }
    }

    // 统计
    val successCount = orderedRecords.count { it.status == RssExecutionStatus.SUCCESS }
    val failedCount = orderedRecords.count { it.status == RssExecutionStatus.FAILED }
    val skippedCount = orderedRecords.count {
        it.status == RssExecutionStatus.SKIPPED || it.status == RssExecutionStatus.EMPTY_SKIP
    }

    val listState = rememberLazyListState()

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // 汇总卡片
            item {
                ExecutionSummaryCard(
                    totalCount = orderedRecords.size,
                    successCount = successCount,
                    failedCount = failedCount,
                    skippedCount = skippedCount
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // 配置检查分组
            val configRecords = orderedRecords.filter { it.step.isConfigCheck }
            if (configRecords.isNotEmpty()) {
                item {
                    SectionHeader("配置检查")
                }
                items(configRecords.size) { index ->
                    ExecutionStepRow(record = configRecords[index])
                }
            }

            // 执行步骤分组
            val executionRecords = orderedRecords.filter { !it.step.isConfigCheck }
            if (executionRecords.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(4.dp))
                    SectionHeader("执行步骤")
                }
                items(executionRecords.size) { index ->
                    ExecutionStepRow(record = executionRecords[index])
                }
            }
        }
        VerticalScrollbar(
            state = listState,
            modifier = Modifier.align(Alignment.CenterEnd)
        )
    }
}

@Composable
private fun ExecutionSummaryCard(
    totalCount: Int,
    successCount: Int,
    failedCount: Int,
    skippedCount: Int
) {
    var expanded by remember { mutableStateOf(true) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (failedCount > 0)
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (failedCount > 0) "执行存在异常" else "执行正常",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (failedCount > 0)
                        MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "$totalCount 步骤",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    SummaryChip("✔ $successCount 成功", MaterialTheme.colorScheme.primary)
                    if (failedCount > 0) {
                        SummaryChip("✘ $failedCount 失败", MaterialTheme.colorScheme.error)
                    }
                    if (skippedCount > 0) {
                        SummaryChip("⊘ $skippedCount 跳过", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryChip(text: String, color: androidx.compose.ui.graphics.Color) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        fontWeight = FontWeight.Medium
    )
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 6.dp)
    )
}

@Composable
private fun ExecutionStepRow(record: RssExecutionRecord) {
    var expanded by remember { mutableStateOf(false) }
    val hasDetail = record.detail != null || record.error != null

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (hasDetail) Modifier.clickable { expanded = !expanded }
                else Modifier
            ),
        color = when (record.status) {
            RssExecutionStatus.FAILED -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.15f)
            else -> MaterialTheme.colorScheme.surface
        }
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = record.status.icon,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.width(24.dp)
                )
                Text(
                    text = buildString {
                        append(record.step.displayName)
                        when (record.status) {
                            RssExecutionStatus.SUCCESS -> append("执行正确")
                            RssExecutionStatus.FAILED -> append("执行失败")
                            RssExecutionStatus.SKIPPED -> append("跳过执行")
                            RssExecutionStatus.EMPTY_SKIP -> append("为空跳过执行")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = when (record.status) {
                        RssExecutionStatus.FAILED -> MaterialTheme.colorScheme.error
                        RssExecutionStatus.SUCCESS -> MaterialTheme.colorScheme.onSurface
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (record.status == RssExecutionStatus.FAILED)
                        FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                record.duration?.let {
                    Text(
                        text = "${it}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AnimatedVisibility(
                visible = expanded && hasDetail,
                enter = expandVertically(),
                exit = shrinkVertically()
            ) {
                Column(modifier = Modifier.padding(start = 24.dp, top = 4.dp)) {
                    record.detail?.let { detail ->
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    record.error?.let { error ->
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace
                            ),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }
        }
    }
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = 12.dp),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
    )
}
