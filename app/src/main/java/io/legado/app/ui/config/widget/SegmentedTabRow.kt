package io.legado.app.ui.config.widget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 胶囊分段 Tab 行（滑动指示器版）。
 *
 * 视觉态由连续 [progress]（0f~1f，来自 HorizontalPager 的实时偏移）驱动：
 * 滑块位置逐帧跟随滑动，文字/图标选中色在过半阈值处切换，无 settledPage 式滞后。
 *
 * 配色沿用旧版 [androidx.compose.material3.SingleChoiceSegmentedButtonRow] 实现：
 * 选中 = `lerp(surfaceVariant, primary, 0.25f)` + 自适应透明度，严格跟随应用自定义主色调。
 *
 * 设计约定（后续修改务必遵守）：
 * 1. 选中填充必须是「整段填充」，由外层容器 clip 切出外缘圆角、内侧保持直角，
 *    严禁让滑块自带全圆角而变成悬浮在半段中的小胶囊（椭圆感）。
 * 2. 分段之间不画显式分隔线，依靠滑块色块与未选区域的背景对比区分边界；
 *    每段内容（图标+文字）在各自半段内整体居中，不要左起对齐，避免两 Tab 文字视觉不对称。
 *
 * @param tabs Tab 枚举列表（当前使用场景均为双 Tab，n 段时滑块居中于各分段）
 * @param progress 0f=完全左侧 Tab，1f=完全右侧 Tab，中间值=滑动过程
 * @param onTabClick Tab 点击回调
 * @param labelText Tab 标签文本
 * @param iconContent Tab 图标内容（可选，tint 跟随选中态切换）
 */
@Composable
fun <T> SegmentedTabRow(
    tabs: List<T>,
    progress: Float,
    onTabClick: (T) -> Unit,
    modifier: Modifier = Modifier,
    labelText: @Composable (T) -> String,
    iconContent: (@Composable (T) -> Unit)? = null
) {
    require(tabs.isNotEmpty()) { "tabs must not be empty" }

    val containerColor = MaterialTheme.colorScheme.surfaceVariant
    val isLightBg = MaterialTheme.colorScheme.background.luminance() > 0.5f
    val containerAlpha = if (isLightBg) 0.15f else 0.12f
    val activeColor = lerp(containerColor, MaterialTheme.colorScheme.primary, 0.25f)
    val onActiveColor = MaterialTheme.colorScheme.primary
    val onInactiveColor = MaterialTheme.colorScheme.onSurfaceVariant

    var trackWidth by remember { mutableStateOf(0.dp) }
    val density = LocalDensity.current
    val clamped = progress.coerceIn(0f, 1f)
    // 滑块中心：progress 0→1 映射到首段中心(1/4)→末段中心(3/4)（n=2 时）
    val indicatorCenterFrac = (clamped * (tabs.size - 1) + 0.5f) / tabs.size

    // 选中分段取最近档位：滑动过半即切换，与滑块位置保持视觉一致
    val nearest = if (tabs.size == 1) 0
    else (clamped * (tabs.size - 1)).roundToInt().coerceIn(0, tabs.size - 1)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(48.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor.copy(alpha = containerAlpha))
            .onSizeChanged { trackWidth = with(density) { it.width.toDp() } }
    ) {
        if (trackWidth > 0.dp) {
            val pillWidth = trackWidth / tabs.size
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(pillWidth)
                    .offset(x = trackWidth * indicatorCenterFrac - pillWidth / 2f)
                    .background(activeColor.copy(alpha = containerAlpha))
            )
        }

        // 内容行：图标 + 文字 + 点击命中区
        Row(modifier = Modifier.fillMaxSize()) {
            tabs.forEachIndexed { index, tab ->
                val isActive = index == nearest
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = { onTabClick(tab) })
                ) {
                    // Icon 默认 tint = LocalContentColor，用 CompositionLocalProvider 控制选中态颜色
                    CompositionLocalProvider(LocalContentColor provides (if (isActive) onActiveColor else onInactiveColor)) {
                        Row(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (iconContent != null) {
                                iconContent(tab)
                                Spacer(Modifier.width(6.dp))
                            }
                            Text(
                                text = labelText(tab),
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isActive) onActiveColor else onInactiveColor
                            )
                        }
                    }
                }
            }
        }
    }
}
