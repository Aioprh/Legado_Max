/**
 * 首页网格排行榜模块
 *
 * 文件作用：提供首页排行榜模块的 UI 组件实现。
 * 主要功能：
 * - 以两列网格形式展示排行榜书籍
 * - 最多显示 10 个项目
 * - 显示排名编号，前 3 名使用特殊样式（主色 + 斜体）
 * - 支持点击和长按交互
 */
package io.legado.app.ui.main.homepage.modules

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.ui.main.homepage.HomepageBookItemUi
import io.legado.app.ui.theme.pageAccentColor
import io.legado.app.ui.theme.pageSecondaryTextColor
import io.legado.app.ui.widget.components.card.GlassCard

/** 排行榜最多显示的书籍数量 */
private const val MAX_COUNT = 10

/**
 * 网格排行榜模块
 *
 * 以网格形式显示排行榜书籍，最多显示 10 个项目。
 * 根据书籍数量动态计算网格高度，禁用滚动以避免与外层滚动冲突。
 *
 * @param books 书籍列表数据
 * @param onClick 点击书籍回调
 * @param onLongClick 长按书籍回调
 * @param modifier 布局修饰符
 */
@Composable
fun GridRankingModule(
    books: List<HomepageBookItemUi>,
    onClick: (HomepageBookItemUi) -> Unit,
    onLongClick: (HomepageBookItemUi) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (books.isEmpty()) return
    // 取前 MAX_COUNT 个书籍进行展示
    val displayBooks = books.take(MAX_COUNT)
    // 计算行数（每行两列）
    val rowCount = (displayBooks.size + 1) / 2
    val rowHeight = 140.dp
    val rowSpacing = 8.dp
    // 计算网格总高度：行高 * 行数 + 行间距 * (行数 - 1)
    val gridHeight = rowHeight * rowCount + rowSpacing * (rowCount - 1).coerceAtLeast(0)

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = modifier
            .fillMaxWidth()
            .height(gridHeight),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(rowSpacing),
        userScrollEnabled = false
    ) {
        items(displayBooks.size) { index ->
            GridRankingItem(
                rank = index + 1,
                item = displayBooks[index],
                onClick = { onClick(displayBooks[index]) },
                onLongClick = { onLongClick(displayBooks[index]) }
            )
        }
    }
}

/**
 * 网格排行榜单个项目组件
 *
 * 展示单本排行榜书籍信息，包含排名编号、封面、书名和作者。
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
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 8.dp
    ) {
        Column(
            modifier = Modifier
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 排名数字，前3名使用 primary 颜色和斜体样式突出显示
                Text(
                    text = "$rank",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Black,
                    fontStyle = if (rank <= 3) FontStyle.Italic else FontStyle.Normal,
                    color = if (rank <= 3) pageAccentColor() else MaterialTheme.colorScheme.outline,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(28.dp)
                )
                HomepageBookCover(
                    name = book.name,
                    author = book.author,
                    coverUrl = book.coverUrl,
                    modifier = Modifier
                        .width(60.dp)
                        .aspectRatio(5f / 7f),
                    cornerRadius = 4.dp
                )
            }
            // 书名，单行显示，超出部分省略
            Text(
                text = book.name,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            // 作者，单行显示，超出部分省略
            Text(
                text = book.author,
                style = MaterialTheme.typography.labelSmall,
                color = pageSecondaryTextColor(),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp)
            )
        }
    }
}
