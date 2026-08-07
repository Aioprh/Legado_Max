package io.legado.app.ui.config.configmanage

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R

/**
 * 多选操作项定义。
 *
 * 每个操作项包含图标、内容描述文案和点击回调，
 * 供 [ConfigMultiSelectBar] 动态组装操作按钮。
 */
data class MultiSelectAction(
    val icon: ImageVector,
    val contentDescription: String,
    val tint: Color = Color.Unspecified,
    val onClick: () -> Unit
)

/**
 * 通用多选模式底部操作栏。
 *
 * 与主题管理的 [MultiSelectBottomBar] 不同，此组件不写死操作按钮，
 * 而是接受 [actions] 列表动态渲染，各管理页可按需配置不同的批量操作。
 *
 * @param selectedCount 已选中的条目数量
 * @param countLabel    已选数量的前缀文案（如"已选择"）
 * @param actions       操作按钮列表
 */
@Composable
fun ConfigMultiSelectBar(
    selectedCount: Int,
    countLabel: String = stringResource(R.string.select_theme),
    actions: List<MultiSelectAction>
) {
    BottomAppBar(
        containerColor = MaterialTheme.colorScheme.surfaceVariant,
        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$countLabel ($selectedCount)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                actions.forEach { action ->
                    IconButton(onClick = action.onClick) {
                        val iconTint = if (action.tint == Color.Unspecified)
                            MaterialTheme.colorScheme.onSurfaceVariant
                        else
                            action.tint
                        Icon(
                            action.icon,
                            contentDescription = action.contentDescription,
                            tint = iconTint
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun ConfigMultiSelectBarPreview() {
    MaterialTheme {
        ConfigMultiSelectBar(
            selectedCount = 3,
            actions = listOf(
                MultiSelectAction(
                    icon = Icons.Default.Share,
                    contentDescription = "导出",
                    onClick = {}
                ),
                MultiSelectAction(
                    icon = Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error,
                    onClick = {}
                )
            )
        )
    }
}
