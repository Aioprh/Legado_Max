package io.legado.app.ui.config

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
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
        onExitMultiSelect = onBackClick
    ) { paddingValues ->
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
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
