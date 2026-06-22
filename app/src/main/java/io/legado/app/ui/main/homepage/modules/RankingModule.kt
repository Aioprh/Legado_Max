package io.legado.app.ui.main.homepage.modules

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.legado.app.data.entities.SearchBook
import io.legado.app.ui.main.homepage.HomepageBookItemUi
import io.legado.app.ui.widget.components.card.GlassCard
import io.legado.app.utils.StringUtils

private const val INITIAL_COUNT = 5
private const val MAX_COUNT = 20

@Composable
fun RankingModule(
    books: List<HomepageBookItemUi>,
    onClick: (SearchBook, String?) -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    var visibleCount by rememberSaveable { mutableIntStateOf(INITIAL_COUNT) }
    val displayBooks = books.take(visibleCount)

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier
                .padding(top = 12.dp)
                .animateContentSize()
        ) {
            displayBooks.forEachIndexed { index, item ->
                RankingItem(
                    rank = index + 1,
                    item = item,
                    onClick = onClick,
                    onLongClick = onLongClick,
                )
            }
            if (books.size > INITIAL_COUNT) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            visibleCount = if (visibleCount == INITIAL_COUNT) MAX_COUNT else INITIAL_COUNT
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val isExpanded = visibleCount > INITIAL_COUNT
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = if (isExpanded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = if (isExpanded) "收起" else "展开全部",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isExpanded) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun RankingItem(
    rank: Int,
    item: HomepageBookItemUi,
    onClick: (SearchBook, String?) -> Unit,
    onLongClick: ((SearchBook, String?) -> Unit)? = null,
) {
    val book = item.book
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onClick(book, null) },
                onLongClick = onLongClick?.let { cb -> { cb(book, null) } }
            )
            .padding(vertical = 4.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$rank",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Black,
            fontStyle = if (rank <= 3) FontStyle.Italic else FontStyle.Normal,
            color = if (rank <= 3) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .width(42.dp)
                .padding(start = 2.dp, end = 10.dp),
        )
        HomepageBookCover(
            name = book.name,
            author = book.author,
            coverUrl = book.coverUrl,
            modifier = Modifier
                .width(52.dp)
                .aspectRatio(5f / 7f),
            cornerRadius = 4.dp
        )
        Column(
            modifier = Modifier
                .padding(start = 8.dp)
                .weight(1f)
        ) {
            Text(
                text = book.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            // 字数和作者在同一行显示
            val authorLine = buildString {
                val wc = StringUtils.wordCountFormat(book.wordCount)
                if (wc.isNotEmpty()) append(wc)
                if (book.author.isNotBlank()) {
                    if (isNotEmpty()) append(" · ")
                    append(book.author)
                }
            }
            if (authorLine.isNotBlank()) {
                Text(
                    text = authorLine,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // 分类单独一行显示
            val kind = book.kind?.split(",")?.firstOrNull()
            if (!kind.isNullOrBlank()) {
                Text(
                    text = kind,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            // 简介，最多两行显示，超出部分省略
            val intro = book.intro?.takeIf { it.isNotBlank() }?.replace("\\s+".toRegex(), " ")
            if (!intro.isNullOrBlank()) {
                Text(
                    text = intro,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
