/**
 * 首页网格排行榜模块
 *
 * 文件作用：提供首页排行榜模块的 UI 组件实现。
 * 主要功能：
 * - 以分页卡片形式展示排行榜书籍，每页 5 行
 * - 每行包含封面、排名编号、书名和分类/作者信息
 * - 前 3 名使用特殊样式（主色 + 斜体）
 * - 支持点击和长按交互
 * - 支持加载更多（滚动到底自动触发）
 * - 支持页码记忆（切换Tab后恢复位置）
 */
package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.domain.model.BookShelfState
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.homepage.HomepageBookItemUi
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.widget.components.card.GlassCard
import kotlinx.coroutines.launch

/** 每页显示的行数 */
private const val ROWS_PER_PAGE = 5
/** 占位项高度 */
private val PLACEHOLDER_HEIGHT = 80.dp

/**
 * 网格排行榜模块
 *
 * @param books 书籍列表数据（全部显示，无数量限制）
 * @param onClick 点击书籍回调（用于跳转看书页面）
 * @param onLongClick 长按书籍回调
 * @param onLoadMore 加载更多回调（滚动到底自动触发）
 * @param initialPage 初始页码（从0开始，用于记忆翻页位置）
 * @param onPageChanged 页码变化回调（用于外部保存状态）
 * @param modifier 布局修饰符
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun GridRankingModule(
    books: List<HomepageBookItemUi>,
    onClick: (HomepageBookItemUi) -> Unit,
    onLongClick: (HomepageBookItemUi) -> Unit,
    onLoadMore: (() -> Unit)? = null,
    initialPage: Int = 0,
    onPageChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (books.isEmpty()) return

    val pages = books.chunked(ROWS_PER_PAGE)
    val pagerState = rememberPagerState(
        initialPage = initialPage.coerceIn(0, pages.size - 1),
        pageCount = { pages.size }
    )
    val coroutineScope = rememberCoroutineScope()

    // 页码变化时通知外部（用于记忆翻页位置）
    LaunchedEffect(pagerState.currentPage) {
        onPageChanged(pagerState.currentPage)
    }

    // 滑到最后一页时自动加载更多
    LaunchedEffect(pagerState.currentPage, pages.size) {
        if (onLoadMore != null && pages.size > 1 && pagerState.currentPage >= pages.lastIndex) {
            onLoadMore()
        }
    }

    HorizontalPager(
        state = pagerState,
        contentPadding = PaddingValues(end = 100.dp),
        pageSpacing = 12.dp,
        modifier = modifier.fillMaxWidth()
    ) { pageIndex ->
        val page = pages.getOrNull(pageIndex) ?: return@HorizontalPager
        GlassCard(
            modifier = Modifier.fillMaxWidth(),
            cornerRadius = 20.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 12.dp)
            ) {
                for ((rowIndex, item) in page.withIndex()) {
                    val itemIndex = pageIndex * ROWS_PER_PAGE + rowIndex
                    GridRankingItem(
                        rank = itemIndex + 1,
                        item = item,
                        onClick = { onClick(item) },
                        onLongClick = { onLongClick(item) }
                    )
                }
                // 占位逻辑：不足一页时用空占位填充，保持每页高度一致
                repeat(ROWS_PER_PAGE - page.size) {
                    Spacer(modifier = Modifier.height(PLACEHOLDER_HEIGHT))
                }
            }
        }
    }
}

/**
 * 网格排行榜单个项目组件
 *
 * 以横向行布局展示单本排行榜书籍，包含封面、排名编号和文字信息。
 * 前 3 名使用主色和斜体样式突出显示。
 *
 * @param rank 排名序号（从 1 开始）
 * @param item 书籍 UI 数据
 * @param onClick 点击回调
 * @param onLongClick 长按回调
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun GridRankingItem(
    rank: Int,
    item: HomepageBookItemUi,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val book = item.book
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. 封面（带书架状态图标）
        Box {
            HomepageBookCover(
                name = book.name,
                author = book.author,
                coverUrl = book.coverUrl,
                modifier = Modifier
                    .width(48.dp)
                    .aspectRatio(5f / 7f),
                cornerRadius = 4.dp,
                identity = book.bookUrl
            )
            // 新版样式：显示图标
            if (AppConfig.bookshelfIconStyle == 0) {
                val shelfIcon = when (item.shelfState) {
                    BookShelfState.IN_SHELF -> Icons.Default.Check
                    BookShelfState.SAME_NAME_AUTHOR -> Icons.Default.Shuffle
                    else -> null
                }
                if (shelfIcon != null) {
                    // 亮色主题：白色背景+黑色图标；暗色主题：黑色背景+白色图标
                    val isLight = !AppConfig.isNightTheme
                    val bgColor = if (isLight) Color.White else Color.Black
                    val iconColor = if (isLight) Color.Black else Color.White
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(bgColor)
                            .padding(horizontal = 2.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            imageVector = shelfIcon,
                            contentDescription = null,
                            tint = iconColor,
                            modifier = Modifier.height(14.dp)
                        )
                    }
                }
            }
        }

        // 2. 排名编号，前3名使用主色和斜体样式
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            fontStyle = if (rank <= 3) FontStyle.Italic else FontStyle.Normal,
            color = if (rank <= 3) pageAccentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(32.dp)
        )

        // 3. 文字信息：书名 + 分类/作者
        Column(
            modifier = Modifier
                .padding(start = 4.dp)
                .weight(1f)
        ) {
            // 书名区域
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 经典样式：显示小绿点
                if (AppConfig.bookshelfIconStyle == 1) {
                    if (item.shelfState == BookShelfState.IN_SHELF || item.shelfState == BookShelfState.SAME_NAME_AUTHOR) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF4CAF50))
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                    }
                }
                Text(
                    text = book.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }
            val subTitle = buildString {
                append(book.kind?.split(",")?.firstOrNull() ?: "")
                if (book.author.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(book.author)
                }
            }
            if (subTitle.isNotBlank()) {
                Text(
                    text = subTitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
