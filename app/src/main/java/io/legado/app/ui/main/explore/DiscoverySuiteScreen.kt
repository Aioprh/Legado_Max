package io.legado.app.ui.main.explore

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.UiCorner
import io.legado.app.ui.main.bookshelf.compose.rememberBookshelfListRenderConfig
import io.legado.app.ui.widget.compose.BookCoverImage
import io.legado.app.ui.widget.compose.SearchBookPreviewOverlay
import io.legado.app.ui.widget.compose.SearchBookPreviewState
import io.legado.app.ui.widget.compose.appSettingPanelBackground
import io.legado.app.ui.widget.image.CoverImageView

@Composable
fun MaxDiscoverySuiteHomeScreen(
    selectedSuite: DiscoverySuite?,
    suites: List<DiscoverySuite>,
    books: Map<String, List<SearchBook>>,
    onSearchClick: () -> Unit,
    onManageClick: () -> Unit,
    onSelectSuite: (DiscoverySuite) -> Unit,
    onTargetClick: (DiscoverySuiteWidgetTarget) -> Unit,
    onRefresh: (DiscoverySuiteWidget) -> Unit,
    onBookClick: (SearchBook) -> Unit,
    fragment: Fragment,
    lifecycle: Lifecycle,
    modifier: Modifier = Modifier
) {
    val renderConfig = rememberBookshelfListRenderConfig()
    val palette = renderConfig.palette
    var preview by remember { mutableStateOf<SearchBookPreviewState?>(null) }
    val listState = rememberLazyListState()
    Column(modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp)
                .height(46.dp)
                .appSettingPanelBackground(
                    normalColor = palette.rowColor,
                    panelImage = renderConfig.panelImage,
                    borderColor = palette.borderColor,
                    radiusPx = 22.dp.value * 3f
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("⌕", fontSize = 22.sp, color = palette.secondaryText,
                modifier = Modifier.padding(start = 16.dp).clickable(onClick = onSearchClick))
            Text("搜索", fontSize = 16.sp, color = palette.secondaryText,
                modifier = Modifier.weight(1f).clickable(onClick = onSearchClick).padding(start = 8.dp))
            Text("⋮", fontSize = 24.sp, color = palette.primaryText,
                modifier = Modifier.clickable(onClick = onManageClick).padding(horizontal = 16.dp))
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)
        ) {
            items(suites, key = { it.id }) { suite ->
                val selected = suite.id == selectedSuite?.id
                Box(
                    modifier = Modifier.clip(RoundedCornerShape(18.dp)).background(
                        if (selected) palette.accent.copy(alpha = 0.16f) else palette.rowColor
                    ).clickable { onSelectSuite(suite) }.padding(horizontal = 14.dp, vertical = 8.dp)
                ) { Text(suite.displayName, color = if (selected) palette.accent else palette.primaryText, fontSize = 14.sp) }
            }
        }
        if (selectedSuite == null) {
            Column(Modifier.fillMaxSize().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                Text("还没有发现套件", fontSize = 18.sp, color = palette.primaryText, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp)); Text("创建一个套件后，可以把不同书源的发现内容组合到一个页面。", color = palette.secondaryText)
                Spacer(Modifier.height(18.dp)); Text("管理套件", color = palette.accent, modifier = Modifier.clickable(onClick = onManageClick))
            }
        } else {
            LazyColumn(state = listState, contentPadding = PaddingValues(bottom = 24.dp)) {
                selectedSuite.widgets.forEach { widget ->
                    item(key = widget.id) {
                        MaxDiscoverySuiteWidget(
                            widget = widget, books = books[widget.id].orEmpty(), renderConfig = renderConfig,
                            onTargetClick = onTargetClick, onRefresh = onRefresh,
                            onBookClick = onBookClick, onPreview = { preview = SearchBookPreviewState(it, null) },
                            fragment = fragment, lifecycle = lifecycle
                        )
                    }
                }
            }
        }
    }
    SearchBookPreviewOverlay(
        state = preview, renderConfig = renderConfig, fragment = fragment, lifecycle = lifecycle,
        onDismissed = { preview = null }, onOpen = { preview = null; onBookClick(it) }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaxDiscoverySuiteWidget(
    widget: DiscoverySuiteWidget,
    books: List<SearchBook>,
    renderConfig: io.legado.app.ui.main.bookshelf.compose.BookshelfListRenderConfig,
    onTargetClick: (DiscoverySuiteWidgetTarget) -> Unit,
    onRefresh: (DiscoverySuiteWidget) -> Unit,
    onBookClick: (SearchBook) -> Unit,
    onPreview: (SearchBook) -> Unit,
    fragment: Fragment,
    lifecycle: Lifecycle
) {
    val palette = renderConfig.palette
    Column(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(widget.title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = palette.primaryText, modifier = Modifier.weight(1f))
            if (widget.type == DiscoverySuiteWidgetType.RandomBooks.value) {
                Text("换一批", fontSize = 13.sp, color = palette.accent, modifier = Modifier.clickable { onRefresh(widget) }.padding(8.dp))
            }
        }
        when (widget.type) {
            DiscoverySuiteWidgetType.TagBar.value -> LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(widget.validTargets(), key = { it.key() }) { target ->
                    Box(Modifier.clip(RoundedCornerShape(16.dp)).background(palette.rowColor).clickable { onTargetClick(target) }.padding(horizontal = 14.dp, vertical = 9.dp)) {
                        Text(target.title.ifBlank { "分类" }, color = palette.primaryText, fontSize = 14.sp)
                    }
                }
            }
            DiscoverySuiteWidgetType.HorizontalBooks.value -> LazyRow(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                itemsIndexed(books.take(widget.displayLimit), key = { _, book -> book.name + book.author }) { _, book ->
                    MaxSuiteBookCard(book, renderConfig, onBookClick, onPreview, fragment, lifecycle, Modifier.width(78.dp))
                }
            }
            DiscoverySuiteWidgetType.WaterfallBooks.value -> Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                val columns = books.take(widget.displayLimit).withIndex().partition { it.index % 2 == 0 }
                columns.toList().forEach { column ->
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        column.forEach { item -> MaxSuiteWaterfallCard(item.value, renderConfig, onBookClick, onPreview, fragment, lifecycle) }
                    }
                }
            }
            else -> Row(horizontalArrangement = Arrangement.spacedBy(9.dp)) {
                books.take(widget.displayLimit).forEach { book ->
                    MaxSuiteBookCard(book, renderConfig, onBookClick, onPreview, fragment, lifecycle, Modifier.width(78.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaxSuiteBookCard(book: SearchBook, config: io.legado.app.ui.main.bookshelf.compose.BookshelfListRenderConfig, onClick: (SearchBook) -> Unit, onPreview: (SearchBook) -> Unit, fragment: Fragment, lifecycle: Lifecycle, modifier: Modifier) {
    val p = config.palette
    Column(modifier.combinedClickable(onClick = { onClick(book) }, onLongClick = { onPreview(book) })) {
        BookCoverImage(book = book, modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(10.dp)), style = CoverImageView.CoverStyle.GRID, loadOnlyWifi = AppConfig.loadCoverOnlyWifi, fragment = fragment, lifecycle = lifecycle, preferThumb = true, fillBounds = true)
        Text(book.name, modifier = Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 13.sp, color = p.primaryText)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MaxSuiteWaterfallCard(book: SearchBook, config: io.legado.app.ui.main.bookshelf.compose.BookshelfListRenderConfig, onClick: (SearchBook) -> Unit, onPreview: (SearchBook) -> Unit, fragment: Fragment, lifecycle: Lifecycle) {
    val p = config.palette
    Column(Modifier.fillMaxWidth().combinedClickable(onClick = { onClick(book) }, onLongClick = { onPreview(book) })) {
        BookCoverImage(book = book, modifier = Modifier.fillMaxWidth().aspectRatio(0.75f).clip(RoundedCornerShape(12.dp)), style = CoverImageView.CoverStyle.GRID, loadOnlyWifi = AppConfig.loadCoverOnlyWifi, fragment = fragment, lifecycle = lifecycle, preferThumb = true, fillBounds = true)
        Text(book.name, modifier = Modifier.padding(top = 6.dp), maxLines = 2, overflow = TextOverflow.Ellipsis, fontSize = 14.sp, color = p.primaryText)
    }
}
