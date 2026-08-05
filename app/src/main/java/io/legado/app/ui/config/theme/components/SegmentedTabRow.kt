package io.legado.app.ui.config.theme.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 通用胶囊分段按钮 Tab 行。
 *
 * @param tabs Tab 数据列表
 * @param selected 当前选中的 Tab
 * @param onTabClick 点击 Tab 的回调
 * @param labelText 将 Tab 数据转换为显示文本的函数
 * @param iconContent 可选，将 Tab 数据转换为图标的 Composable 函数
 */
@Composable
fun <T> SegmentedTabRow(
    tabs: List<T>,
    selected: T,
    onTabClick: (T) -> Unit,
    labelText: @Composable (T) -> String,
    iconContent: (@Composable (T) -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        SingleChoiceSegmentedButtonRow(
            modifier = Modifier.fillMaxWidth(),
            space = 0.dp
        ) {
            tabs.forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selected == tab,
                    onClick = { onTabClick(tab) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = tabs.size,
                        baseShape = RoundedCornerShape(12.dp)
                    ),
                    icon = if (iconContent != null) {
                        { iconContent(tab) }
                    } else {
                        {}
                    },
                    label = { Text(text = labelText(tab)) }
                )
            }
        }
    }
}
