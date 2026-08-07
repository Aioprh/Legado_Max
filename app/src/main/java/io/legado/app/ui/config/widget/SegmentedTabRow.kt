package io.legado.app.ui.config.widget

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.dp

/**
 * 泛型胶囊分段按钮 Tab 行（单选分段控件）。
 *
 * 使用 Material3 [SingleChoiceSegmentedButtonRow] 实现单选分段控件，
 * 选中胶囊的背景色通过 [lerp] 在 [surfaceVariant] 与 [primary] 之间插值计算，
 * 产生一个带主色调倾向的浅色背景，既明显区别于未选中状态，又严格跟随应用自定义主色调。
 *
 * ## 颜色配置
 * - **选中状态**：背景 = `lerp(surfaceVariant, primary, 0.25f)`，文字 = `primary`
 * - **未选中状态**：背景 = `surfaceVariant`，文字 = `onSurfaceVariant`
 *
 * @param tabs Tab 枚举列表
 * @param selected 当前选中的 Tab
 * @param onTabClick Tab 点击回调
 * @param labelText Tab 标签文本
 * @param iconContent Tab 图标内容（可选）
 */
@Composable
fun <T> SegmentedTabRow(
    tabs: List<T>,
    selected: T,
    onTabClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelText: @Composable (T) -> String,
    iconContent: (@Composable (T) -> Unit)? = null
) {
    require(tabs.isNotEmpty()) { "tabs must not be empty" }

    // 自适应透明度：与 ThemeCard 风格保持一致
    val isLightBg = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val containerAlpha = if (isLightBg) 0.15f else 0.12f
    val borderAlpha = if (isLightBg) 0.06f else 0.10f

    Row(
        modifier = modifier
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
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = lerp(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.primary,
                            0.25f
                        ).copy(alpha = containerAlpha),
                        activeContentColor = MaterialTheme.colorScheme.primary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = containerAlpha),
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        activeBorderColor = lerp(
                            MaterialTheme.colorScheme.surfaceVariant,
                            MaterialTheme.colorScheme.primary,
                            0.25f
                        ).copy(alpha = borderAlpha),
                        inactiveBorderColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = borderAlpha)
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
