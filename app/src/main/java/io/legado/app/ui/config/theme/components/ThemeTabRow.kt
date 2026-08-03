package io.legado.app.ui.config.theme.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.legado.app.R
import io.legado.app.ui.config.theme.ThemeTab

@Composable
fun ThemeTabRow(
    selectedTab: ThemeTab,
    onTabClick: (ThemeTab) -> Unit
) {
    val tabs = ThemeTab.entries
    val selectedIndex = tabs.indexOf(selectedTab)

    Column(modifier = Modifier.fillMaxWidth()) {
        TabRow(
            selectedTabIndex = selectedIndex,
            containerColor = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            indicator = { tabPositions ->
                TabRowDefaults.SecondaryIndicator(
                    modifier = Modifier.tabIndicatorOffset(tabPositions[selectedIndex]),
                    color = MaterialTheme.colorScheme.primary
                )
            }
        ) {
            tabs.forEach { tab ->
                Tab(
                    selected = selectedTab == tab,
                    onClick = { onTabClick(tab) },
                    text = {
                        Text(
                            text = when (tab) {
                                ThemeTab.DAY -> stringResource(R.string.day)
                                ThemeTab.NIGHT -> stringResource(R.string.night)
                            }
                        )
                    }
                )
            }
        }
    }
}