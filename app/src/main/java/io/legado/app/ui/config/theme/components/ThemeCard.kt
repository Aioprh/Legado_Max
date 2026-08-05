package io.legado.app.ui.config.theme.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.help.config.ThemeConfig
import io.legado.app.ui.config.theme.ThemeItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ThemeCard(
    item: ThemeItem,
    isMultiSelectMode: Boolean,
    isSelected: Boolean,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    onShare: () -> Unit,
    onDelete: () -> Unit,
    onLongClick: () -> Unit,
    onToggleSelect: () -> Unit
) {
    val config = item.config
    val primaryColor = runCatching { config.primaryColor.toColorInt() }.getOrDefault(0xFF607D8B.toInt())
    val accentColor = runCatching { config.accentColor.toColorInt() }.getOrDefault(0xFF8BC34A.toInt())
    val backgroundColor = runCatching { config.backgroundColor.toColorInt() }.getOrDefault(0xFFF5F5F5.toInt())

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(
                onClick = {
                    if (isMultiSelectMode) onToggleSelect()
                },
                onLongClick = {
                    if (!isMultiSelectMode) onLongClick()
                }
            ),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shadowElevation = 2.dp
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
                isCurrent = false // TODO: compare with current applied theme
            )

            Spacer(Modifier.width(12.dp))

            // 信息区域
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = config.themeName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }

                Spacer(Modifier.height(4.dp))

                Text(
                    text = if (config.isNightTheme) stringResource(R.string.night) else stringResource(R.string.day),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(Modifier.height(8.dp))

                // 操作按钮
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = onApply) {
                        Text(
                            text = stringResource(R.string.apply_theme),
                            fontSize = 13.sp
                        )
                    }
                    TextButton(onClick = onEdit) {
                        Text(
                            text = stringResource(R.string.edit_theme),
                            fontSize = 13.sp
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Default.Delete,
                            contentDescription = stringResource(R.string.delete),
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
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
    isCurrent: Boolean
) {
    Box(
        modifier = Modifier
            .size(width = 74.dp, height = 102.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
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

        // 当前标记
        if (isCurrent) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(20.dp),
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}