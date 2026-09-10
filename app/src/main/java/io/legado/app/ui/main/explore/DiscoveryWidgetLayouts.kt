package io.legado.app.ui.main.explore

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.CoverLoader
import android.widget.ImageView
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 现代发现页组件的统一布局层。
 * 数据和加载状态由 DiscoveryModernScreen 管理，这里只负责呈现和交互事件。
 */
enum class DiscoveryWidgetLayout {
    List,
    Horizontal,
    RankedList,
    Waterfall
}

fun DiscoverySuiteWidget.layout(): DiscoveryWidgetLayout = when (type) {
    DiscoverySuiteWidgetType.HorizontalBooks.value,
    DiscoverySuiteWidgetType.RandomBooks.value -> DiscoveryWidgetLayout.Horizontal
    DiscoverySuiteWidgetType.RankedList.value,
    DiscoverySuiteWidgetType.RankButtons.value -> DiscoveryWidgetLayout.RankedList
    DiscoverySuiteWidgetType.WaterfallBooks.value -> DiscoveryWidgetLayout.Waterfall
    else -> DiscoveryWidgetLayout.List
}

@Composable
fun DiscoveryTagBar(
    tags: List<DiscoverTagItem>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        tags.forEachIndexed { index, tag ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelected(index) },
                label = { Text(tag.text) }
            )
        }
    }
}

@Composable
fun DiscoveryRankButtons(
    labels: List<String>,
    selected: Int,
    onSelected: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        labels.forEachIndexed { index, label ->
            FilterChip(
                selected = index == selected,
                onClick = { onSelected(index) },
                label = { Text(label) }
            )
        }
    }
}

@Composable
fun DiscoveryHorizontalBooks(
    books: List<SearchBook>,
    onClick: (SearchBook) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(books.size, key = { "${books[it].origin}|${books[it].bookUrl}" }) { index ->
            val book = books[index]
            Card(Modifier.width(150.dp), onClick = { onClick(book) }) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(190.dp),
                        factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                        update = { view -> CoverLoader.load(view, book, AppConfig.loadCoverOnlyWifi) }
                    )
                    Text(book.name.ifBlank { "未命名" }, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                    if (book.author.isNotBlank()) Text(book.author, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun DiscoveryWaterfallBooks(
    books: List<SearchBook>,
    onClick: (SearchBook) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        userScrollEnabled = false
    ) {
        items(books, key = { "${it.origin}|${it.bookUrl}" }) { book ->
            Card(Modifier.fillMaxWidth(), onClick = { onClick(book) }) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(190.dp),
                        factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                        update = { view -> CoverLoader.load(view, book, AppConfig.loadCoverOnlyWifi) }
                    )
                    Text(book.name.ifBlank { "未命名" }, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                    if (book.author.isNotBlank()) Text(book.author, maxLines = 1, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun DiscoveryRankedBookRow(
    rank: Int,
    book: SearchBook,
    onClick: (SearchBook) -> Unit,
    modifier: Modifier = Modifier
) {
    Card(modifier.fillMaxWidth(), onClick = { onClick(book) }) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 7.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(rank.toString(), Modifier.width(24.dp), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f)) {
                Text(book.name.ifBlank { "未命名" }, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                val meta = listOf(book.author, book.kind.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
