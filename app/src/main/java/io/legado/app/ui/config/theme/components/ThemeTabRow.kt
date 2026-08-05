package io.legado.app.ui.config.theme.components

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
import io.legado.app.ui.config.theme.ThemeTab

/**
 * 主题列表的日间/夜间 Tab 行。
 * 内部使用通用的 [SegmentedTabRow]。
 */
@Composable
fun ThemeTabRow(
    selectedTab: ThemeTab,
    onTabClick: (ThemeTab) -> Unit
) {
    SegmentedTabRow(
        tabs = ThemeTab.entries,
        selected = selectedTab,
        onTabClick = onTabClick,
        labelText = { tab ->
            when (tab) {
                ThemeTab.DAY -> stringResource(R.string.day)
                ThemeTab.NIGHT -> stringResource(R.string.night)
            }
        },
        iconContent = { tab ->
            Icon(
                imageVector = when (tab) {
                    ThemeTab.DAY -> Icons.Default.LightMode
                    ThemeTab.NIGHT -> Icons.Default.DarkMode
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
        selectedTab = ThemeTab.DAY,
        onTabClick = {}
    )
}
