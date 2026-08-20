package io.legado.app.ui.book.readRecord

import android.os.Bundle
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.base.BaseComposeActivity
import io.legado.app.data.appDb
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.repository.ReadRecordRepository
import io.legado.app.utils.formatReadDuration
import kotlinx.coroutines.flow.emptyFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BookReadRecordActivity : BaseComposeActivity() {

    companion object {
        const val EXTRA_BOOK_NAME = "bookName"
        const val EXTRA_BOOK_AUTHOR = "bookAuthor"
    }

    private var bookName: String = ""
    private var bookAuthor: String = ""

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        bookName = intent.getStringExtra(EXTRA_BOOK_NAME).orEmpty()
        bookAuthor = intent.getStringExtra(EXTRA_BOOK_AUTHOR).orEmpty()
    }

    @Composable
    override fun ComposeContent() {
        BookReadRecordScreen(
            bookName = bookName,
            bookAuthor = bookAuthor,
            onBackClick = { finish() }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookReadRecordScreen(
    bookName: String,
    bookAuthor: String,
    onBackClick: () -> Unit
) {
    val repository = remember { ReadRecordRepository(appDb.readRecordDao) }

    // SQL 聚合：每天的会话数和总时长（不全量加载 Session）
    val dailyStats = repository.getBookDailySessionStats(bookName, bookAuthor)
        .collectAsStateWithLifecycle(emptyList())
        .value

    // SQL 聚合：总阅读时间
    val totalReadTime = repository.getBookReadTime(bookName, bookAuthor)
        .collectAsStateWithLifecycle(0L)
        .value

    val totalSessionCount = dailyStats.sumOf { it.sessionCount }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = bookName,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            color = MaterialTheme.colorScheme.background
        ) {
            if (dailyStats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Filled.MenuBook,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "暂无阅读记录",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val listState = rememberLazyListState()
                val scrollbarColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize()
                        .drawWithContent {
                            drawContent()
                            val totalItems = listState.layoutInfo.totalItemsCount
                            if (totalItems > 0) {
                                val visibleItems = listState.layoutInfo.visibleItemsInfo.size
                                val fraction = visibleItems.toFloat() / totalItems.toFloat()
                                val barHeight = size.height * fraction.coerceIn(0.04f, 1f)
                                val progress = listState.firstVisibleItemIndex.toFloat()
                                    .coerceAtMost((totalItems - visibleItems).coerceAtLeast(0).toFloat())
                                val maxScroll = (totalItems - visibleItems).coerceAtLeast(1).toFloat()
                                val barY = (size.height - barHeight) * (progress / maxScroll).coerceIn(0f, 1f)
                                drawRoundRect(
                                    color = scrollbarColor,
                                    topLeft = Offset(size.width - 5.dp.toPx(), barY),
                                    size = Size(3.dp.toPx(), barHeight),
                                    cornerRadius = CornerRadius(1.5.dp.toPx())
                                )
                            }
                        },
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "summary") {
                        SummaryHeader(
                            totalReadTime = totalReadTime,
                            dayCount = dailyStats.size,
                            sessionCount = totalSessionCount
                        )
                    }

                    items(
                        items = dailyStats,
                        key = { it.date }
                    ) { stat ->
                        DaySection(
                            date = stat.date,
                            sessionCount = stat.sessionCount,
                            totalDuration = stat.totalDuration,
                            bookName = bookName,
                            bookAuthor = bookAuthor,
                            repository = repository
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryHeader(
    totalReadTime: Long,
    dayCount: Int,
    sessionCount: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StatChip(
            icon = Icons.Filled.Timer,
            label = formatReadDuration(totalReadTime),
            suffix = "阅读"
        )
        StatChip(
            label = sessionCount.toString(),
            suffix = "次"
        )
        StatChip(
            label = dayCount.toString(),
            suffix = "天"
        )
    }
}

@Composable
private fun StatChip(
    icon: ImageVector? = null,
    label: String,
    suffix: String
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.width(2.dp))
        Text(
            text = suffix,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun DaySection(
    date: String,
    sessionCount: Int,
    totalDuration: Long,
    bookName: String,
    bookAuthor: String,
    repository: ReadRecordRepository
) {
    var expanded by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

    // 按需加载：仅在展开时加载该天的会话列表
    val sessionsFlow = remember(expanded, date) {
        if (expanded) repository.getBookSessionsByDate(bookName, bookAuthor, date)
        else emptyFlow()
    }
    val sessions = sessionsFlow.collectAsStateWithLifecycle(emptyList()).value

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 14.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = date,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${sessionCount}次",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = formatReadDuration(totalDuration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(
                modifier = Modifier.padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                sessions.forEach { session ->
                    val duration = (session.endTime - session.startTime).coerceAtLeast(0L)
                    SessionRow(session, timeFormat, duration)
                }
            }
        }
    }
}

@Composable
private fun SessionRow(session: ReadRecordSession, timeFormat: SimpleDateFormat, duration: Long) {
    val start = remember(session.startTime) { Date(session.startTime) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.45f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = timeFormat.format(start),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = formatReadDuration(duration),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Medium
        )
        session.durChapterTitle.takeIf { it.isNotBlank() }?.let { title ->
            Spacer(modifier = Modifier.width(8.dp))
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState())
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }
    }
}
