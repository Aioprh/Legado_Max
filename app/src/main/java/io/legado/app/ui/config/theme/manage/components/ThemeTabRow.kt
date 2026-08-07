package io.legado.app.ui.config.theme.manage.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.config.configmanage.ConfigTab
import io.legado.app.ui.config.configmanage.SegmentedTabRow

/**
 * 主题列表的日间/夜间 Tab 行。
 *
 * @deprecated [DayNightPager] 已内置 Tab 渲染，此组件仅保留兼容。
 * 新代码请直接使用 [DayNightPager] 或 [SegmentedTabRow] + [ConfigTab]。
 */
@Composable
fun ThemeTabRow(
    selectedTab: ConfigTab,
    onTabClick: (ConfigTab) -> Unit
) {
    SegmentedTabRow(
        tabs = ConfigTab.entries,
        selected = selectedTab,
        onTabClick = onTabClick,
        labelText = { tab ->
            when (tab) {
                ConfigTab.DAY -> stringResource(R.string.day)
                ConfigTab.NIGHT -> stringResource(R.string.night)
            }
        },
        iconContent = { tab ->
            Icon(
                imageVector = when (tab) {
                    ConfigTab.DAY -> Icons.Default.LightMode
                    ConfigTab.NIGHT -> Icons.Default.DarkMode
                },
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
        }
    )
}

@Preview
@Composable
private fun ThemeTabRowPreview() {
    ThemeTabRow(
        selectedTab = ConfigTab.DAY,
        onTabClick = {}
    )
}
