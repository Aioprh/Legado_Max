package io.legado.app.ui.widget.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import io.legado.app.data.entities.SearchBook
import io.legado.app.domain.model.BookShelfState
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.widget.image.CoverImageView

/**
 * 书籍底部弹窗
 *
 * 长按书籍时从底部弹出，显示书籍信息和操作按钮。
 * 可用于首页、发现界面、搜索界面等。
 * 固定显示半屏高度，内容过多时可滚动，适配主题颜色。
 *
 * @param show 是否显示弹窗
 * @param book 书籍信息
 * @param shelfState 书籍在书架中的状态
 * @param onDismiss 关闭弹窗的回调
 * @param onAddToShelf 加入书架的回调
 * @param onShowInfo 查看书籍详情的回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookBottomSheet(
    show: Boolean,
    book: SearchBook?,
    shelfState: BookShelfState,
    onDismiss: () -> Unit,
    onAddToShelf: (SearchBook) -> Unit,
    onShowInfo: (SearchBook) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    if (show && book != null) {
        ModalBottomSheet(
            onDismissRequest = onDismiss,
            sheetState = sheetState,
            modifier = Modifier.fillMaxWidth(),
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp)
            ) {
                // 可滚动的内容区域
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(scrollState)
                ) {
                    // 书籍信息区域
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // 封面图片
                        AndroidView(
                            modifier = Modifier
                                .width(90.dp)
                                .height(120.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            factory = { ctx ->
                                CoverImageView(ctx).apply {
                                    load(book, AppConfig.loadCoverOnlyWifi)
                                }
                            },
                            update = { view ->
                                view.load(book, AppConfig.loadCoverOnlyWifi)
                            }
                        )

                        // 书籍基本信息
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .align(Alignment.Top),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            // 书名
                            Text(
                                text = book.name,
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )

                            // 作者
                            if (book.author.isNotBlank()) {
                                Text(
                                    text = "作者: ${book.author}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            // 书架状态提示
                            if (shelfState == BookShelfState.IN_SHELF) {
                                Text(
                                    text = "✓ 已在书架中",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium
                                )
                            } else if (shelfState == BookShelfState.SAME_NAME_AUTHOR) {
                                Text(
                                    text = "! 书架中有同名书籍",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.tertiary,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }

                    // 分隔线
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    // 详细信息区域
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 分类
                        val kindText = book.kind
                        if (!kindText.isNullOrEmpty()) {
                            InfoRow(label = "分类", value = kindText)
                        }

                        // 字数
                        val wordCountText = book.wordCount
                        if (!wordCountText.isNullOrEmpty()) {
                            InfoRow(label = "字数", value = wordCountText)
                        }

                        // 最新章节
                        val latestChapterText = book.latestChapterTitle
                        if (!latestChapterText.isNullOrEmpty()) {
                            InfoRow(label = "最新章节", value = latestChapterText)
                        }

                        // 书源
                        if (book.originName.isNotBlank()) {
                            InfoRow(label = "书源", value = book.originName)
                        }
                    }

                    // 简介
                    val introText = book.intro
                    if (!introText.isNullOrEmpty()) {
                        val trimmedIntro = introText.trim()
                        if (trimmedIntro.isNotBlank()) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(1.dp)
                                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Text(
                                text = "简介",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = trimmedIntro,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                }

                // 固定的操作按钮区域（不滚动）
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // 加入书架按钮
                    TextButton(
                        onClick = {
                            onAddToShelf(book)
                            Toast.makeText(context, "已加入书架: ${book.name}", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "加入书架",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (shelfState == BookShelfState.IN_SHELF) "已在书架" else "加入书架",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    // 查看详情按钮
                    TextButton(
                        onClick = {
                            onShowInfo(book)
                            onDismiss()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "查看详情",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "查看详情",
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

/**
 * 信息行组件
 * 显示标签和值，适配主题颜色
 */
@Composable
private fun InfoRow(
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "$label:",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.width(80.dp)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f)
        )
    }
}