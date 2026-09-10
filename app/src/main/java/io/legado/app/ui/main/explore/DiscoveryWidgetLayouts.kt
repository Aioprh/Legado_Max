package io.legado.app.ui.main.explore

import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.help.glide.CoverLoader

enum class DiscoveryWidgetLayout { List, Horizontal, RankedList, Waterfall }

fun DiscoverySuiteWidget.layout(): DiscoveryWidgetLayout = when (type) {
    DiscoverySuiteWidgetType.HorizontalBooks.value,
    DiscoverySuiteWidgetType.RandomBooks.value -> DiscoveryWidgetLayout.Horizontal
    DiscoverySuiteWidgetType.RankedList.value,
    DiscoverySuiteWidgetType.RankButtons.value -> DiscoveryWidgetLayout.RankedList
    DiscoverySuiteWidgetType.WaterfallBooks.value -> DiscoveryWidgetLayout.Waterfall
    else -> DiscoveryWidgetLayout.List
}

@Composable
fun DiscoveryTagBar(tags: List<DiscoverTagItem>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        tags.forEachIndexed { index, tag ->
            FilterChip(selected = index == selected, onClick = { onSelected(index) }, label = { Text(tag.text) })
        }
    }
}

@Composable
fun DiscoveryRankButtons(labels: List<String>, selected: Int, onSelected: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        labels.forEachIndexed { index, label ->
            FilterChip(selected = index == selected, onClick = { onSelected(index) }, label = { Text(label) })
        }
    }
}

@Composable
fun DiscoveryHorizontalBooks(
    books: List<SearchBook>,
    onClick: (SearchBook) -> Unit,
    modifier: Modifier = Modifier,
    onLoadMore: () -> Unit = {},
    loading: Boolean = false,
    listState: LazyListState = rememberLazyListState(),
    onLongClick: (SearchBook) -> Unit = onClick
) {
    val shouldLoadMore by remember(books.size, loading, listState) {
        derivedStateOf {
            books.isNotEmpty() && !loading &&
                (listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1) >= books.lastIndex - 3
        }
    }
    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) onLoadMore()
    }
    LazyRow(
        state = listState,
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        items(books, key = { "${it.origin}|${it.bookUrl}" }) { book ->
            DiscoveryHorizontalCard(book, onClick, onLongClick)
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoveryHorizontalCard(
    book: SearchBook,
    onClick: (SearchBook) -> Unit,
    onLongClick: (SearchBook) -> Unit
) {
    Card(
        modifier = Modifier.width(150.dp).combinedClickable(
            onClick = { onClick(book) },
            onLongClick = { onLongClick(book) }
        )
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                update = { view -> CoverLoader.load(view, book, AppConfig.loadCoverOnlyWifi) }
            )
            Text(book.name.ifBlank { "未命名" }, maxLines = 2, style = MaterialTheme.typography.titleSmall)
            if (book.author.isNotBlank()) Text(book.author, maxLines = 1, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
fun DiscoveryWaterfallBooks(
    books: List<SearchBook>,
    onClick: (SearchBook) -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (SearchBook) -> Unit = onClick
) {
    val left = books.filterIndexed { index, _ -> index % 2 == 0 }
    val right = books.filterIndexed { index, _ -> index % 2 != 0 }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            left.forEach { DiscoveryWaterfallCard(it, onClick, onLongClick) }
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            right.forEach { DiscoveryWaterfallCard(it, onClick, onLongClick) }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DiscoveryWaterfallCard(
    book: SearchBook,
    onClick: (SearchBook) -> Unit,
    onLongClick: (SearchBook) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().combinedClickable(
            onClick = { onClick(book) },
            onLongClick = { onLongClick(book) }
        )
    ) {
        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
            AndroidView(
                modifier = Modifier.fillMaxWidth(),
                factory = { context -> ImageView(context).apply { scaleType = ImageView.ScaleType.CENTER_CROP } },
                update = { view -> CoverLoader.load(view, book, AppConfig.loadCoverOnlyWifi) }
            )
            Text(book.name.ifBlank { "未命名" }, maxLines = 2, style = MaterialTheme.typography.titleSmall)
            if (book.author.isNotBlank()) Text(book.author, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            book.kind?.takeIf { it.isNotBlank() }?.let { Text(it, maxLines = 1, style = MaterialTheme.typography.labelSmall) }
        }
    }
}

@Composable
fun DiscoveryRankedBookRow(rank: Int, book: SearchBook, onClick: (SearchBook) -> Unit, modifier: Modifier = Modifier) {
    Card(onClick = { onClick(book) }, modifier = modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 8.dp, vertical = 7.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(rank.toString(), Modifier.width(24.dp), style = MaterialTheme.typography.titleMedium)
            Column(Modifier.weight(1f)) {
                Text(book.name.ifBlank { "未命名" }, maxLines = 2, style = MaterialTheme.typography.titleSmall)
                val meta = listOf(book.author, book.kind.orEmpty()).filter { it.isNotBlank() }.joinToString(" · ")
                if (meta.isNotBlank()) Text(meta, maxLines = 1, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
