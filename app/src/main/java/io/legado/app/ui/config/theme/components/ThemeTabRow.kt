package io.legado.app.ui.config.theme.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import io.legado.app.R
import io.legado.app.ui.config.theme.ThemeTab

@Composable
fun ThemeTabRow(
    selectedTab: ThemeTab,
    onTabClick: (ThemeTab) -> Unit
) {
    val tabs = ThemeTab.entries

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
                    selected = selectedTab == tab,
                    onClick = { onTabClick(tab) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = tabs.size,
                        baseShape = RoundedCornerShape(12.dp)  // ← 改这里
                    ),
                    icon = {
                        Icon(
                            imageVector = when (tab) {
                                ThemeTab.DAY -> Icons.Default.LightMode
                                ThemeTab.NIGHT -> Icons.Default.DarkMode
                            },
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    },
                    label = {
                        Text(
                            text = when (tab) {
                                ThemeTab.DAY -> stringResource(R.string.day)
                                ThemeTab.NIGHT -> stringResource(R.string.night)
                            },
                            style = MaterialTheme.typography.labelMedium
                        )
                    },
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = MaterialTheme.colorScheme.primary,
                        activeContentColor = MaterialTheme.colorScheme.onPrimary,
                        inactiveContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }
        }
    }
}

@Preview
@Composable
private fun ThemeTabRowPreview() {
    ThemeTabRow(
        selectedTab = ThemeTab.DAY,
        onTabClick = {}
    )
}