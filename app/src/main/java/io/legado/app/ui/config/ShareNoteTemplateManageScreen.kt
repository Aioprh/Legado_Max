package io.legado.app.ui.config

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.help.config.ShareNoteTemplateManager
import io.legado.app.ui.config.widget.ConfigManageScaffold
import java.io.File

/**
 * 摘录分享模板管理界面的导航/回调参数。
 */
data class ShareNoteTemplateManageArgs(
    val onApply: (ShareNoteTemplateManager.Entry) -> Unit,
    val onStyleChange: (ShareNoteTemplateManager.ShareStyle) -> Unit,
    val onEdit: (ShareNoteTemplateManager.Entry) -> Unit,
    val onMoreActions: (ShareNoteTemplateManager.Entry) -> List<ShareNoteMenuAction>,
    val onAddClick: () -> Unit
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
    modifier: Modifier = Modifier,
    entries: List<ShareNoteTemplateManager.Entry>,
    activeDirName: String,
    shareStyle: ShareNoteTemplateManager.ShareStyle,
    previewFiles: Map<String, File>,
    onBackClick: () -> Unit,
    args: ShareNoteTemplateManageArgs
) {
    ConfigManageScaffold(
        title = stringResource(R.string.share_note_template_manage),
        isMultiSelectMode = false,
        onBackClick = onBackClick,
        onExitMultiSelect = onBackClick,
        bottomBar = {
            ShareNoteTemplateAddButton(
                onClick = args.onAddClick
            )
        }
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = 16.dp
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                ShareNoteStyleQuickCard(
                    shareStyle = shareStyle,
                    onStyleChange = args.onStyleChange
                )
            }
            items(entries, key = { it.dirName }) { entry ->
                val active = activeDirName == entry.dirName
                ShareNoteTemplateItemCard(
                    entry = entry,
                    isActive = active,
                    previewFile = previewFiles[entry.dirName],
                    onApply = { args.onApply(entry) },
                    onEdit = { args.onEdit(entry) },
                    moreActions = args.onMoreActions(entry)
                )
            }
        }
    }
}

/**
 * 添加模板按钮。
 *
 * 位于管理页底部，点击后弹出添加方式选择（复制内置模板/导入 HTML/导入 ZIP）。
 */
@Composable
private fun ShareNoteTemplateAddButton(
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Text(
            text = stringResource(R.string.share_note_add_template),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 13.dp),
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
