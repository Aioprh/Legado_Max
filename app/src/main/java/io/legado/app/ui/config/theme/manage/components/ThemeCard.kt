package io.legado.app.ui.config.theme.manage.components

import android.widget.ImageView
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.toColorInt
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import io.legado.app.R
import io.legado.app.ui.config.theme.manage.ThemeItem

/**
 * 基于 Glide 的 Compose 图片加载封装
 * 
 * 为什么不用原生的 produceState 读磁盘：
 * 列表滚动时会频繁触发 Compose 重组。如果在这里用协程+文件 IO 解码位图，
 * 必然会导致不可挽回的列表掉帧和内存泄漏。
 * 改造为 AndroidView 包裹 ImageView，交由项目已有的 Glide 接管后，
 * 借由 Glide 底层的内存/磁盘多级缓存机制与 Bitmap 复用池，彻底根治列表滑动时的卡顿顽疾。
 */
@Composable
fun GlideImage(path: String?, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier,
        factory = { ctx -> 
            ImageView(ctx).apply { 
                scaleType = ImageView.ScaleType.CENTER_CROP 
            } 
        },
        update = { imageView ->
            if (path.isNullOrBlank()) {
                imageView.setImageDrawable(null)
            } else {
                Glide.with(context)
                    .load(path)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .transition(DrawableTransitionOptions.withCrossFade())
                    .into(imageView)
            }
        }
    )
}

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
    val primaryColor = remember(config.primaryColor) {
        runCatching { config.primaryColor.toColorInt() }.getOrDefault(0xFF607D8B.toInt())
    }
    val accentColor = remember(config.accentColor) {
        runCatching { config.accentColor.toColorInt() }.getOrDefault(0xFF8BC34A.toInt())
    }
    val backgroundColor = remember(config.backgroundColor) {
        runCatching { config.backgroundColor.toColorInt() }.getOrDefault(0xFFF5F5F5.toInt())
    }

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
            .border(0.5.dp, borderColor, RoundedCornerShape(12.dp)),
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = {
                        if (isMultiSelectMode) onToggleSelect()
                    },
                    onLongClick = {
                        if (!isMultiSelectMode) onLongClick()
                    }
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
    Box(
        modifier = Modifier
            .size(width = 74.dp, height = 102.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
    ) {
        if (!backgroundImgPath.isNullOrBlank()) {
            GlideImage(
                path = backgroundImgPath,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )
        }

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
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(primaryColor)
            )

            Box(
                modifier = Modifier
                    .padding(top = 36.dp)
                    .size(width = 56.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor)
            )

            Box(
                modifier = Modifier
                    .padding(top = 48.dp)
                    .size(width = 40.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.5f))
            )

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