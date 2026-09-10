package io.legado.app.ui.main.explore

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.data.entities.SearchBook
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.homepage.modules.HomepageBookCover

private const val BOOK_COVER_ASPECT_RATIO = 0.75f

/**
 * 发现套件首页。
 *
 * 按选中套件的 widgets 顺序渲染：标签条 / 排行按钮 / 随机书 / 书单 / 横排书 /
 * 榜单 / 瀑布流。数据由 [DiscoverySuiteHomeViewModel] 抓取并提供。
 */
@Composable
fun DiscoverySuiteHomeScreen(
    uiState: DiscoverySuiteHomeUiState,
    onSearchClick: () -> Unit,
    onSuiteClick: () -> Unit,
    onSuiteSelect: (DiscoverySuite) -> Unit,
    onBookClick: (SearchBook) -> Unit,
    onTagClick: (DiscoverySuiteWidgetTarget) -> Unit,
    onRefreshWidget: (DiscoverySuiteWidget) -> Unit,
    onHorizontalLoadMore: (DiscoverySuiteWidget) -> Unit,
    onRankedLoadMore: (DiscoverySuiteWidget, DiscoverySuiteWidgetTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()
    LaunchedEffect(uiState.scrollToTopSignal) {
        if (uiState.scrollToTopSignal > 0) {
            listState.scrollToItem(0)
        }
    }
    val selectedSuite = uiState.selectedSuite
    Column(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        DiscoverySuiteSearchBar(
            suites = uiState.suites,
            selectedSuiteId = uiState.selectedSuiteId,
            onSearchClick = onSearchClick,
            onSuiteClick = onSuiteClick,
            onSuiteSelect = onSuiteSelect
        )
        if (selectedSuite == null) {
            DiscoverySuiteEmptyState(
                title = stringResource(R.string.discovery_suite_empty_title),
                summary = stringResource(R.string.discovery_suite_empty_summary),
                action = stringResource(R.string.discovery_suite_manage),
                onActionClick = onSuiteClick
            )
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 16.dp)
            ) {
                if (selectedSuite.widgets.isEmpty()) {
                    item(key = "no_widgets") {
                        DiscoverySuiteEmptyState(
                            title = stringResource(R.string.discovery_suite_no_widgets_title),
                            summary = stringResource(R.string.discovery_suite_no_widgets_summary),
                            action = stringResource(R.string.discovery_suite_manage),
                            onActionClick = onSuiteClick
                        )
                    }
                } else {
                    items(selectedSuite.widgets, key = { it.id }) { widget ->
                        DiscoverySuiteWidgetSection(
                            widget = widget,
                            books = uiState.widgetBooks[widget.id].orEmpty(),
                            rankedBooks = uiState.rankedWidgetBooks[widget.id].orEmpty(),
                            isLoading = widget.id in uiState.loadingWidgetIds,
                            onBookClick = onBookClick,
                            onTagClick = onTagClick,
                            onRefreshWidget = onRefreshWidget,
                            onHorizontalLoadMore = onHorizontalLoadMore,
                            onRankedLoadMore = onRankedLoadMore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiscoverySuiteSearchBar(
    suites: List<DiscoverySuite>,
    selectedSuiteId: String,
    onSearchClick: () -> Unit,
    onSuiteClick: () -> Unit,
    onSuiteSelect: (DiscoverySuite) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var menuExpanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .height(44.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            modifier = Modifier
                .weight(1f)
                .fillMaxSize()
                .clip(RoundedCornerShape(22.dp))
                .clickable(onClick = onSearchClick),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Outlined.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = context.getString(R.string.search),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(modifier = Modifier.width(50.dp).height(44.dp), contentAlignment = Alignment.Center) {
            Surface(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .clickable { menuExpanded = true },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Filled.MoreVert,
                        contentDescription = context.getString(R.string.more_menu),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(context.getString(R.string.discovery_suite_manage)) },
                    onClick = {
                        menuExpanded = false
                        onSuiteClick()
                    }
                )
                suites.forEach { suite ->
                    DropdownMenuItem(
                        text = {
                            Text(
                                if (suite.id == selectedSuiteId) {
                                    context.getString(R.string.discovery_suite_current, suite.displayName)
                                } else {
                                    suite.displayName
                                }
                            )
                        },
                        onClick = {
                            menuExpanded = false
                            onSuiteSelect(suite)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverySuiteEmptyState(
    title: String,
    summary: String,
    action: String,
    onActionClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 22.dp, vertical = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(title, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
        Text(summary, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Surface(
            modifier = Modifier
                .height(42.dp)
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onActionClick),
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(action, fontWeight = FontWeight.Medium, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun DiscoverySuiteWidgetSection(
    widget: DiscoverySuiteWidget,
    books: List<SearchBook>,
    rankedBooks: Map<String, List<SearchBook>>,
    isLoading: Boolean,
    onBookClick: (SearchBook) -> Unit,
    onTagClick: (DiscoverySuiteWidgetTarget) -> Unit,
    onRefreshWidget: (DiscoverySuiteWidget) -> Unit,
    onHorizontalLoadMore: (DiscoverySuiteWidget) -> Unit,
    onRankedLoadMore: (DiscoverySuiteWidget, DiscoverySuiteWidgetTarget) -> Unit
) {
    val showTitle = widget.type == DiscoverySuiteWidgetType.RandomBooks.value ||
        widget.type == DiscoverySuiteWidgetType.BookList.value
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(11.dp)
    ) {
        if (showTitle) {
            Text(
                text = widget.displayTitle(),
                fontSize = 21.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        when {
            widget.type == DiscoverySuiteWidgetType.TagBar.value ->
                if (widget.targets.isEmpty()) PendingText()
                else DiscoverySuiteTagBarWidget(widget, onTagClick)

            widget.type == DiscoverySuiteWidgetType.RankButtons.value ->
                if (widget.targets.isEmpty()) PendingText()
                else DiscoverySuiteRankButtonsWidget(widget, onTagClick)

            widget.type == DiscoverySuiteWidgetType.RankedList.value ->
                if (widget.targets.isEmpty()) PendingText()
                else if (isLoading && rankedBooks.isEmpty()) LoadingText()
                else DiscoverySuiteRankedListWidget(
                    widget = widget,
                    rankedBooks = rankedBooks,
                    onBookClick = onBookClick,
                    onLoadMore = { target -> onRankedLoadMore(widget, target) }
                )

            isLoading && books.isEmpty() -> LoadingText()
            books.isEmpty() -> PendingText()
            widget.type == DiscoverySuiteWidgetType.HorizontalBooks.value ->
                DiscoverySuiteHorizontalBooksWidget(
                    widget = widget,
                    books = books,
                    onBookClick = onBookClick,
                    onLoadMore = { onHorizontalLoadMore(widget) }
                )

            widget.type == DiscoverySuiteWidgetType.WaterfallBooks.value ->
                DiscoverySuiteWaterfallBooksWidget(widget, books, onBookClick)

            else -> DiscoverySuiteRandomBooksWidget(
                widget = widget,
                books = books,
                onBookClick = onBookClick,
                onRefreshClick = { onRefreshWidget(widget) }
            )
        }
    }
}

@Composable
private fun PendingText() {
    Text(stringResource(R.string.discovery_suite_widget_pending), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun LoadingText() {
    Text(stringResource(R.string.discovery_suite_widget_loading), fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun DiscoverySuiteTagBarWidget(
    widget: DiscoverySuiteWidget,
    onTagClick: (DiscoverySuiteWidgetTarget) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        items(widget.targets, key = { "${it.sourceUrl}|${it.tagUrl}" }) { target ->
            Surface(
                modifier = Modifier
                    .height(36.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .clickable { onTagClick(target) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = target.title.compactTagTitle(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun DiscoverySuiteRankButtonsWidget(
    widget: DiscoverySuiteWidget,
    onTagClick: (DiscoverySuiteWidgetTarget) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        widget.targets.take(RANK_BUTTON_MAX_COUNT).chunked(3).forEach { rowTargets ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowTargets.forEach { target ->
                    Surface(
                        modifier = Modifier
                            .weight(1f)
                            .height(54.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onTagClick(target) },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = target.title.compactTagTitle(),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                fontSize = 15.sp,
                                lineHeight = 18.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
                repeat(3 - rowTargets.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DiscoverySuiteHorizontalBooksWidget(
    widget: DiscoverySuiteWidget,
    books: List<SearchBook>,
    onBookClick: (SearchBook) -> Unit,
    onLoadMore: () -> Unit
) {
    val rowState = rememberLazyListState()
    val displayBooks = remember(widget.id, books) {
        books.take(HORIZONTAL_WIDGET_MAX_RENDER_COUNT)
    }
    val shouldLoadMore by remember {
        derivedStateOf {
            val last = rowState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            displayBooks.isNotEmpty() && last >= displayBooks.lastIndex - 4
        }
    }
    LaunchedEffect(shouldLoadMore, displayBooks.size) {
        if (shouldLoadMore) onLoadMore()
    }
    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 8.dp)
    ) {
        itemsIndexed(displayBooks, key = { _, book -> "${widget.id}|${book.suiteDeckKey()}" }) { _, book ->
            Box(modifier = Modifier.width(74.dp)) {
                DiscoverySuiteCoverBookItem(book, modifier = Modifier.fillMaxWidth(), onBookClick = onBookClick)
            }
        }
    }
}

@Composable
private fun DiscoverySuiteWaterfallBooksWidget(
    widget: DiscoverySuiteWidget,
    books: List<SearchBook>,
    onBookClick: (SearchBook) -> Unit
) {
    val displayBooks = remember(widget.id, books) { books.take(WATERFALL_WIDGET_DISPLAY_COUNT) }
    val columns = remember(displayBooks) {
        displayBooks.withIndex().partition { it.index % 2 == 0 }.let { (left, right) ->
            listOf(left.map { it.value }, right.map { it.value })
        }
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        columns.forEach { columnBooks ->
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                columnBooks.forEach { book ->
                    DiscoverySuiteWaterfallBookCard(book, onBookClick)
                }
            }
        }
    }
}

@Composable
private fun DiscoverySuiteWaterfallBookCard(
    book: SearchBook,
    onBookClick: (SearchBook) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable { onBookClick(book) }
            .padding(bottom = 2.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BOOK_COVER_ASPECT_RATIO)
        ) {
            HomepageBookCover(
                name = book.name,
                author = book.author,
                coverUrl = book.coverUrl,
                modifier = Modifier.fillMaxSize(),
                identity = book.suiteDeckKey()
            )
        }
        Text(
            text = book.name,
            modifier = Modifier.padding(top = 8.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 14.sp,
            lineHeight = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )
        val meta = listOf(book.author, book.originName).filter { it.isNotBlank() }.joinToString(" · ")
        if (meta.isNotBlank()) {
            Text(
                text = meta,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiscoverySuiteRandomBooksWidget(
    widget: DiscoverySuiteWidget,
    books: List<SearchBook>,
    onBookClick: (SearchBook) -> Unit,
    onRefreshClick: () -> Unit
) {
    val displayBooks = remember(widget.id, books) { books.take(RANDOM_WIDGET_DISPLAY_COUNT) }
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        DiscoverySuiteCoverGrid(displayBooks, onBookClick)
        DiscoverySuiteRefreshButton(onRefreshClick)
    }
}

@Composable
private fun DiscoverySuiteCoverGrid(
    books: List<SearchBook>,
    onBookClick: (SearchBook) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
        books.chunked(3).forEach { rowBooks ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                rowBooks.forEach { book ->
                    DiscoverySuiteCoverBookItem(book, modifier = Modifier.weight(1f), onBookClick = onBookClick)
                }
                repeat(3 - rowBooks.size) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DiscoverySuiteRefreshButton(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.discovery_suite_refresh_batch),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DiscoverySuiteCoverBookItem(
    book: SearchBook,
    modifier: Modifier = Modifier,
    onBookClick: (SearchBook) -> Unit
) {
    Column(
        modifier = modifier.clickable { onBookClick(book) }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(BOOK_COVER_ASPECT_RATIO)
        ) {
            HomepageBookCover(
                name = book.name,
                author = book.author,
                coverUrl = book.coverUrl,
                modifier = Modifier.fillMaxSize(),
                identity = book.suiteDeckKey()
            )
        }
        Text(
            text = book.name,
            modifier = Modifier.padding(top = 6.dp),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontSize = 13.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground
        )
    }
}

@Composable
private fun DiscoverySuiteRankedListWidget(
    widget: DiscoverySuiteWidget,
    rankedBooks: Map<String, List<SearchBook>>,
    onBookClick: (SearchBook) -> Unit,
    onLoadMore: (DiscoverySuiteWidgetTarget) -> Unit
) {
    val targets = remember(widget.targets) {
        widget.targets.filter { it.sourceUrl.isNotBlank() && it.tagUrl.isNotBlank() }.take(RANKED_LIST_MAX_TARGET_COUNT)
    }
    if (targets.isEmpty()) return
    var selectedIndex by remember(widget.id, targets) { mutableStateOf(0) }
    val index = selectedIndex.coerceIn(0, targets.lastIndex)
    val selectedTarget = targets[index]
    val selectedBooks = rankedBooks[selectedTarget.deckKey()].orEmpty()
    val displayBooks = remember(selectedTarget.deckKey(), selectedBooks) {
        selectedBooks.chunked(RANKED_LIST_PAGE_BOOK_COUNT).flatten()
    }
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .padding(vertical = 0.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(22.dp),
                contentPadding = PaddingValues(end = 10.dp)
            ) {
                itemsIndexed(targets, key = { _, target -> target.deckKey() }) { i, target ->
                    val selected = i == index
                    Column(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { selectedIndex = i }
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = target.title.compactTagTitle(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = if (selected) 16.sp else 15.sp,
                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Box(
                            modifier = Modifier
                                .width(if (selected) 42.dp else 0.dp)
                                .height(3.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (selected) MaterialTheme.colorScheme.primary else androidx.compose.ui.graphics.Color.Transparent)
                        )
                    }
                }
            }
            if (displayBooks.isEmpty()) {
                PendingText()
            } else {
                displayBooks.forEachIndexed { displayIndex, book ->
                    DiscoverySuiteRankedListBookRow(displayIndex + 1, book, onBookClick)
                }
            }
            DiscoverySuiteRefreshButton { onLoadMore(selectedTarget) }
        }
    }
}

@Composable
private fun DiscoverySuiteRankedListBookRow(
    rank: Int,
    book: SearchBook,
    onBookClick: (SearchBook) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(RANKED_LIST_ROW_HEIGHT)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onBookClick(book) }
            .padding(horizontal = 2.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = rank.toString(),
            modifier = Modifier.width(32.dp),
            fontSize = if (rank <= 3) 25.sp else 23.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.88f)
        )
        Box(
            modifier = Modifier
                .width(40.dp)
                .aspectRatio(BOOK_COVER_ASPECT_RATIO)
        ) {
            HomepageBookCover(
                name = book.name,
                author = book.author,
                coverUrl = book.coverUrl,
                modifier = Modifier.fillMaxSize(),
                identity = book.suiteDeckKey()
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = book.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            val meta = listOf(book.author, book.originName, book.latestChapterTitle.orEmpty())
                .filter { it.isNotBlank() }
                .joinToString(" · ")
            if (meta.isNotBlank()) {
                Text(
                    text = meta,
                    modifier = Modifier.padding(top = 3.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private fun String.compactTagTitle(): String {
    val value = trim()
    val index = value.lastIndexOf(" - ")
    return if (index >= 0 && index + 3 < value.length) {
        value.substring(index + 3)
    } else {
        value
    }.take(12)
}

private const val RANDOM_WIDGET_DISPLAY_COUNT = 6
private const val WATERFALL_WIDGET_DISPLAY_COUNT = 24
private const val HORIZONTAL_WIDGET_MAX_RENDER_COUNT = 72
private const val RANK_BUTTON_MAX_COUNT = 9
private const val RANKED_LIST_MAX_TARGET_COUNT = 9
private const val RANKED_LIST_PAGE_BOOK_COUNT = 6
private val RANKED_LIST_ROW_HEIGHT = 68.dp