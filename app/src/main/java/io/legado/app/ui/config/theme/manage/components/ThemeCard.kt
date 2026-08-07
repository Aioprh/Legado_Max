package io.legado.app.ui.config.theme.manage.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.config.theme.manage.ThemeItem
import io.legado.app.utils.BitmapUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 主题卡片（Compose）。
 *
 * 展示单个主题的预览与操作入口，包含：
 * - 左侧 [ThemePreviewCard]：以色块形式预览主色、强调色、背景色
 * - 中间信息区：主题名称 + 日/夜标签 + 「应用」「编辑」按钮
 * - 右侧操作区：普通模式下显示「分享」「删除」图标按钮；多选模式下显示 [Checkbox]
 *
 * 交互：
 * - 短按：多选模式下切换选中状态
 * - 长按：普通模式下进入多选模式
 *
 * @param item 主题条目（含原始索引）
 * @param isMultiSelectMode 是否处于多选模式
 * @param isSelected 当前条目是否被选中（多选模式下生效）
 * @param isCurrent 当前条目是否为正在应用的主题
 * @param onApply 应用主题回调
 * @param onEdit 编辑主题回调
 * @param onShare 分享主题回调
 * @param onDelete 删除主题回调
 * @param onCopy 复制主题 JSON 到剪贴板回调
 * @param onLongClick 长按回调（进入多选模式）
 * @param onToggleSelect 切换选中状态回调（多选模式下短按触发）
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeCard(
    item: ThemeItem,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    isCurrent: Boolean = false,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val config = item.config
    // remember 缓存颜色解析结果，避免每次 recomposition 重复 toColorInt()
    val primaryColor = remember(config.primaryColor) {
        runCatching { config.primaryColor.toColorInt() }.getOrDefault(0xFF607D8B.toInt())
    }
    val accentColor = remember(config.accentColor) {
        runCatching { config.accentColor.toColorInt() }.getOrDefault(0xFF8BC34A.toInt())
    }
    val backgroundColor = remember(config.backgroundColor) {
        runCatching { config.backgroundColor.toColorInt() }.getOrDefault(0xFFF5F5F5.toInt())
    }

    // 自适应透明度：亮色背景略高（保持可读），暗色背景略低（透出背景图）
    val isLightBg = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val cardAlpha = if (isLightBg) 0.55f else 0.42f
    val cardColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = cardAlpha)
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = if (isLightBg) 0.06f else 0.10f)
    val onColor = MaterialTheme.colorScheme.onSurfaceVariant
    val iconTint = onColor.copy(alpha = 0.85f)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(0.5.dp, borderColor, RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) onToggleSelect()
                },
                onLongClick = {
                    if (!isMultiSelectMode) onLongClick()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 预览卡片
            ThemePreviewCard(
                primaryColor = Color(primaryColor),
                accentColor = Color(accentColor),
                backgroundColor = Color(backgroundColor),
                backgroundImgPath = config.backgroundImgPath,
                isCurrent = isCurrent,
                isMultiSelectMode = isMultiSelectMode,
                isNightTheme = config.isNightTheme
            )

            Spacer(Modifier.width(12.dp))

            // 信息区域
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = config.themeName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = onColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(Modifier.height(4.dp))

                // 日/夜标签胶囊（半透明背景）
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.surface.copy(alpha = if (isLightBg) 0.15f else 0.12f)
                ) {
                    Text(
                        text = if (config.isNightTheme) stringResource(R.string.night) else stringResource(R.string.day),
                        style = MaterialTheme.typography.bodySmall,
                        color = onColor.copy(alpha = 0.8f),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }

                Spacer(Modifier.height(8.dp))

                // 操作按钮
                val buttonTextColor = if (isLightBg) Color.Black else Color.White
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onApply) {
                        Text(
                            text = if (isCurrent) stringResource(R.string.applied)
                                   else stringResource(R.string.apply_theme),
                            fontSize = 13.sp,
                            color = buttonTextColor
                        )
                    }
                    TextButton(onClick = onEdit) {
                        Text(
                            text = stringResource(R.string.edit_theme),
                            fontSize = 13.sp,
                            color = buttonTextColor
                        )
                    }
                    TextButton(onClick = onCopy) {
                        Text(
                            text = stringResource(R.string.copy),
                            fontSize = 13.sp,
                            color = buttonTextColor
                        )
                    }
                }
            }

            // 单行操作图标
            if (!isMultiSelectMode) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onShare, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = stringResource(R.string.share),
                            modifier = Modifier.size(18.dp),
                            tint = iconTint
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            modifier = Modifier.size(18.dp),
                            tint = iconTint
                        )
                    }
                }
            } else {
                Checkbox(
                    checked = isSelected,
                    onCheckedChange = { onToggleSelect() }
                )
            }
        }
    }
}

/**
 * 主题预览卡片：在背景色（+可选背景图片）之上叠加主色块、强调色条。
 *
 * 背景图片使用 [produceState] 在 IO 线程异步解码，解码失败时仅显示背景色。
 */
@Composable
private fun ThemePreviewCard(
    primaryColor: Color,
    accentColor: Color,
    backgroundColor: Color,
    backgroundImgPath: String? = null,
    isCurrent: Boolean,
    isMultiSelectMode: Boolean = false,
    isNightTheme: Boolean = false
) {
    // 异步解码背景图片缩略图
    val backgroundImage: ImageBitmap? by produceState<ImageBitmap?>(initialValue = null, backgroundImgPath) {
        value = withContext(Dispatchers.IO) {
            if (backgroundImgPath.isNullOrBlank()) {
                null
            } else {
                runCatching { BitmapUtils.decodeBitmap(backgroundImgPath, 128)?.asImageBitmap() }.getOrNull()
            }
        }
    }

    Box(
        modifier = Modifier
            .size(width = 74.dp, height = 102.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
    ) {
        // 背景图片（铺满整个预览卡片）
        backgroundImage?.let { bmp ->
            Image(
                bitmap = bmp,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        // 半透明遮罩层，确保色块在背景图上仍然可读
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.08f))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            // 主色块
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(primaryColor)
            )

            // 强调色条
            Box(
                modifier = Modifier
                    .padding(top = 36.dp)
                    .size(width = 56.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor)
            )

            // 次要色条
            Box(
                modifier = Modifier
                    .padding(top = 48.dp)
                    .size(width = 40.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.5f))
            )

            // 当前标记（多选模式下不显示）
            if (isCurrent && !isMultiSelectMode) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp),
                    tint = if (isNightTheme) Color.White else Color.Black
                )
            }
        }
    }
}

// ── 预览 ────────────────────────────────────────────────────
//日间主题卡片预览
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ThemeCardDayPreview() {
    MaterialTheme {
        ThemeCard(
            item = ThemeItem(
                config = ThemeConfig.Config(
                    themeName = "日间主题",
                    isNightTheme = false,
                    primaryColor = "#FF607D8B",
                    accentColor = "#FF8BC34A",
                    backgroundColor = "#FFF5F5F5",
                    bottomBackground = "#FF424242",
                    transparentNavBar = true,
                    backgroundImgPath = null,
                    backgroundImgBlur = 0
                ),
                originalIndex = 0
            ),
            isMultiSelectMode = false,
            isSelected = false,
            isCurrent = true,
            onApply = {},
            onEdit = {},
            onShare = {},
            onDelete = {},
            onCopy = {},
            onLongClick = {},
            onToggleSelect = {}
        )
    }
}

//夜间主题卡片预览
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ThemeCardNightPreview() {
    MaterialTheme {
        ThemeCard(
            item = ThemeItem(
                config = ThemeConfig.Config(
                    themeName = "夜间主题",
                    isNightTheme = true,
                    primaryColor = "#FF1A1A2E",
                    accentColor = "#FFE94560",
                    backgroundColor = "#FF16213E",
                    bottomBackground = "#FF0F3460",
                    transparentNavBar = false,
                    backgroundImgPath = null,
                    backgroundImgBlur = 10
                ),
                originalIndex = 1
            ),
            isMultiSelectMode = false,
            isSelected = false,
            isCurrent = true,
            onApply = {},
            onEdit = {},
            onShare = {},
            onDelete = {},
            onCopy = {},
            onLongClick = {},
            onToggleSelect = {}
        )
    }
}

//多选模式（Checkbox 选中状态）
@Preview(showBackground = true, widthDp = 360)
@Composable
private fun ThemeCardMultiSelectPreview() {
    MaterialTheme {
        ThemeCard(
            item = ThemeItem(
                config = ThemeConfig.Config(
                    themeName = "多选模式主题",
                    isNightTheme = false,
                    primaryColor = "#FF607D8B",
                    accentColor = "#FF8BC34A",
                    backgroundColor = "#FFF5F5F5",
                    bottomBackground = "#FF424242",
                    transparentNavBar = true,
                    backgroundImgPath = null,
                    backgroundImgBlur = 0
                ),
                originalIndex = 2
            ),
            isMultiSelectMode = true,
            isSelected = true,
            isCurrent = false,
            onApply = {},
            onEdit = {},
            onShare = {},
            onDelete = {},
            onCopy = {},
            onLongClick = {},
            onToggleSelect = {}
        )
    }
}
