package io.legado.app.ui.config.widget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R

/**
 * 多选操作项定义。
 *
 * @param icon 图标
 * @param contentDescription 无障碍描述（通常为 stringResource）
 * @param tint 图标着色，默认跟随主题色
 * @param onClick 点击回调
 */
data class MultiSelectAction(
    val icon: ImageVector,
    val contentDescription: String,
    val tint: Color = Color.Unspecified,
    val onClick: () -> Unit
)

/**
 * 全选操作图标按钮。
 */
@Composable
fun SelectAllAction(
    isAllSelected: Boolean,
    onSelectAll: () -> Unit
) {
    val tint = if (isAllSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    IconButton(onClick = onSelectAll) {
        Icon(
            imageVector = Icons.Default.DoneAll,
            contentDescription = stringResource(R.string.select_all),
            tint = tint
        )
    }
}

/**
 * 从剪贴板导入操作图标按钮。
 */
@Composable
fun ImportFromClipboardAction(
    onImport: () -> Unit
) {
    IconButton(onClick = onImport) {
        Icon(
            imageVector = Icons.Default.ContentPaste,
            contentDescription = stringResource(R.string.import_from_clipboard),
            tint = MaterialTheme.colorScheme.onSurface
        )
    }
}

/**
 * 可配置操作项的多选底栏。
 *
 * 各管理页按需传入不同的 [MultiSelectAction] 列表来定制批量操作按钮。
 *
 * @param selectedCount 当前选中的数量
 * @param actions 操作项列表
 */
@Composable
fun ConfigMultiSelectBar(
    selectedCount: Int,
    actions: List<MultiSelectAction>,
    modifier: Modifier = Modifier
) {
    BottomAppBar(
        modifier = modifier.fillMaxWidth(),
        actions = {
            actions.forEach { action ->
                IconButton(onClick = action.onClick) {
                    Icon(
                        imageVector = action.icon,
                        contentDescription = action.contentDescription,
                        tint = if (action.tint == Color.Unspecified) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            action.tint
                        }
                    )
                }
            }
        },
        tonalElevation = 3.dp
    )
}
