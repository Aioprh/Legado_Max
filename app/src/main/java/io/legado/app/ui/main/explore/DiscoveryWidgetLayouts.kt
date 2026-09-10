package io.legado.app.ui.main.explore

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.image.CoverImageView
import androidx.compose.ui.viewinterop.AndroidView

/**
 * 独立的发现组件布局原语。
 *
 * 先把上游的组件语义拆开，避免所有组件继续挤在 DiscoveryModernScreen 中。
 * 后续 DiscoveryModernScreen 只负责数据状态和事件分发。
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
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { onClick(book) }
            ) {
                Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    AndroidView(
                        modifier = Modifier.fillMaxWidth().height(190.dp),
                        factory = { context -> CoverImageView(context) },
                        update = { view -> view.load(book, AppConfig.loadCoverOnlyWifi) }
                    )
                    Text(
                        book.name.ifBlank { "未命名" },
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2
                    )
                    if (book.author.isNotBlank()) {
                        Text(book.author, style = MaterialTheme.typography.bodySmall, maxLines = 1)
                    }
                    book.kind?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                    }
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
            Text(
                rank.toString(),
                modifier = Modifier.width(24.dp),
                style = MaterialTheme.typography.titleMedium
            )
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
