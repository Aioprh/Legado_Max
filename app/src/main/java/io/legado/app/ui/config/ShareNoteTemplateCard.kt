package io.legado.app.ui.config

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.help.config.ShareNoteTemplateManager
import io.legado.app.ui.book.read.ShareNoteTemplatePreview
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val noteTemplateDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/** 模板更多操作项 */
data class ShareNoteMenuAction(
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 摘录分享模板卡片。
 *
 * 展示模板头部预览图、名称、画布/尺寸/来源/更新时间，
 * 操作区为 应用 / 编辑（仅本地） / 更多（下拉菜单）。
 */
@Composable
internal fun ShareNoteTemplateItemCard(
    entry: ShareNoteTemplateManager.Entry,
    isActive: Boolean,
    previewFile: File?,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    moreActions: List<ShareNoteMenuAction>
) {
    val canEdit = entry.source == ShareNoteTemplateManager.Source.LOCAL
    var moreMenuExpanded by remember { mutableStateOf(false) }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ShareNoteTemplatePreview(
                    modifier = Modifier
                        .width(60.dp)
                        .height(84.dp),
                    previewFile = previewFile
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = entry.meta.name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (isActive) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.share_note_applied),
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = buildInfoText(
                            entry = entry,
                            builtinLabel = stringResource(R.string.share_note_source_builtin),
                            localLabel = stringResource(R.string.share_note_source_local)
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onApply,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = if (isActive) {
                            stringResource(R.string.share_note_applied)
                        } else {
                            stringResource(R.string.apply)
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = onEdit,
                    enabled = canEdit,
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.edit),
                        color = if (canEdit) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f)
                        }
                    )
                }
                Box {
                    TextButton(
                        onClick = { moreMenuExpanded = true },
                        contentPadding = PaddingValues(horizontal = 8.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.more),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    DropdownMenu(
                        expanded = moreMenuExpanded,
                        onDismissRequest = { moreMenuExpanded = false }
                    ) {
                        moreActions.forEach { action ->
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = action.label,
                                        color = if (action.danger) {
                                            MaterialTheme.colorScheme.error
                                        } else {
                                            MaterialTheme.colorScheme.onSurface
                                        }
                                    )
                                },
                                onClick = {
                                    moreMenuExpanded = false
                                    action.onClick()
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 分享样式快捷卡片。
 *
 * 快速切换摘录分享图片的配色和字体，预览与分享图片同步更新。
 */
@Composable
internal fun ShareNoteStyleQuickCard(
    shareStyle: ShareNoteTemplateManager.ShareStyle,
    onStyleChange: (ShareNoteTemplateManager.ShareStyle) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Text(
                text = stringResource(R.string.share_note_style),
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.share_note_style_summary),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = stringResource(R.string.share_note_palette),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShareNoteTemplateManager.stylePalettes.forEach { stylePalette ->
                    ShareNoteActionButton(
                        text = stylePalette.name,
                        selected = stylePalette.id == shareStyle.paletteId,
                        onClick = { onStyleChange(shareStyle.copy(paletteId = stylePalette.id)) }
                    )
                }
            }
            Spacer(modifier = Modifier.size(8.dp))
            Text(
                text = stringResource(R.string.share_note_font),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ShareNoteTemplateManager.fontFamilies.forEach { font ->
                    ShareNoteActionButton(
                        text = ShareNoteTemplateManager.fontLabel(font),
                        selected = font == shareStyle.fontFamily,
                        onClick = { onStyleChange(shareStyle.copy(fontFamily = font)) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ShareNoteActionButton(
    text: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surface
                },
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 6.dp)
    ) {
        Text(
            text = text,
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )
    }
}

private fun buildInfoText(
    entry: ShareNoteTemplateManager.Entry,
    builtinLabel: String,
    localLabel: String
): String {
    val source = when (entry.source) {
        ShareNoteTemplateManager.Source.BUILTIN -> builtinLabel
        ShareNoteTemplateManager.Source.LOCAL -> localLabel
    }
    val time = entry.meta.updatedAt.takeIf { it > 0L }?.let {
        noteTemplateDateFormat.format(Date(it))
    }
    return listOfNotNull(
        entry.meta.canvasLabel(),
        entry.meta.sizeLabel(),
        source,
        time
    ).joinToString(" · ")
}
