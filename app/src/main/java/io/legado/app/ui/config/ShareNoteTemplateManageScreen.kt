package io.legado.app.ui.config

import android.widget.ImageView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.bumptech.glide.Glide
import com.bumptech.glide.signature.ObjectKey
import io.legado.app.R
import io.legado.app.help.config.ShareNoteTemplateManager
import io.legado.app.ui.config.widget.ConfigManageScaffold
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val noteTemplateDateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

/** 模板操作项 */
data class ShareNoteMenuAction(
    val label: String,
    val danger: Boolean = false,
    val onClick: () -> Unit
)

/**
 * 摘录分享模板管理界面。
 *
 * 顶部为分享样式快捷卡片（配色/字体），下方为模板列表：
 * 每个模板展示头部预览图、名称、画布/尺寸/来源/更新时间，
 * 支持应用、编辑（仅本地）、更多操作（预览、复制、导出、删除）。
 */
@Composable
fun ShareNoteTemplateManageScreen(
    entries: List<ShareNoteTemplateManager.Entry>,
    activeDirName: String,
    shareStyle: ShareNoteTemplateManager.ShareStyle,
    previewFiles: Map<String, File>,
    onBackClick: () -> Unit,
    onApply: (ShareNoteTemplateManager.Entry) -> Unit,
    onStyleChange: (ShareNoteTemplateManager.ShareStyle) -> Unit,
    onEdit: (ShareNoteTemplateManager.Entry) -> Unit,
    onMoreActions: (ShareNoteTemplateManager.Entry) -> List<ShareNoteMenuAction>,
    onAddClick: () -> Unit
) {
    ConfigManageScaffold(
        title = "摘录分享模板",
        isMultiSelectMode = false,
        onBackClick = onBackClick,
        onExitMultiSelect = onBackClick
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ShareNoteStyleQuickCard(
                    shareStyle = shareStyle,
                    onStyleChange = onStyleChange
                )
            }
            items(entries, key = { it.dirName }) { entry ->
                val active = activeDirName == entry.dirName
                ShareNoteTemplateItemCard(
                    entry = entry,
                    isActive = active,
                    previewFile = previewFiles[entry.dirName],
                    onApply = { onApply(entry) },
                    onEdit = { onEdit(entry) },
                    moreActions = onMoreActions(entry)
                )
            }
        }
    }
}

@Composable
private fun ShareNoteStyleQuickCard(
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
                text = "分享样式",
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "快速切换摘录分享图片的配色和字体，预览与分享图片会同步更新。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(top = 4.dp)
            )
            Spacer(modifier = Modifier.size(10.dp))
            Text(
                text = "配色",
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
                text = "字体",
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

@Composable
private fun ShareNoteTemplateItemCard(
    entry: ShareNoteTemplateManager.Entry,
    isActive: Boolean,
    previewFile: File?,
    onApply: () -> Unit,
    onEdit: () -> Unit,
    moreActions: List<ShareNoteMenuAction>
) {
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
                    previewFile = previewFile,
                    modifier = Modifier
                        .width(60.dp)
                        .height(84.dp)
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
                                text = "已应用",
                                color = MaterialTheme.colorScheme.primary,
                                fontSize = 12.sp,
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = buildInfoText(entry),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onApply) {
                    Text(if (isActive) "已应用" else "应用")
                }
                TextButton(onClick = onEdit, enabled = entry.source == ShareNoteTemplateManager.Source.LOCAL) {
                    Text("编辑")
                }
                moreActions.forEach { action ->
                    TextButton(onClick = action.onClick) {
                        Text(
                            text = action.label,
                            color = if (action.danger) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            }
                        )
                    }
                }
            }
        }
    }
}

/**
 * 模板头部预览图。
 *
 * 预览文件由 [ShareNoteImageRenderer.renderPreview] 生成，缓存于模板目录 .preview 下。
 */
@Composable
internal fun ShareNoteTemplatePreview(
    previewFile: File?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (previewFile != null && previewFile.exists() && previewFile.length() > 0L) {
            AndroidView(
                factory = { context ->
                    ImageView(context).apply {
                        scaleType = ImageView.ScaleType.CENTER_CROP
                    }
                },
                update = { imageView ->
                    Glide.with(imageView)
                        .load(previewFile)
                        .signature(
                            ObjectKey("${previewFile.absolutePath}:${previewFile.length()}:${previewFile.lastModified()}")
                        )
                        .centerCrop()
                        .into(imageView)
                },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "预览",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

private fun buildInfoText(entry: ShareNoteTemplateManager.Entry): String {
    val source = when (entry.source) {
        ShareNoteTemplateManager.Source.BUILTIN -> "内置"
        ShareNoteTemplateManager.Source.LOCAL -> "本地"
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
