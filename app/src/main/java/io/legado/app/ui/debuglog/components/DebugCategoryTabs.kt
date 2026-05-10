package io.legado.app.ui.debuglog.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.model.debug.DebugCategory

/**
 * 调试日志分类 Tab 组件
 */
@Composable
fun DebugCategoryTabs(
    selectedCategory: DebugCategory,
    categories: List<DebugCategory>,
    onCategorySelected: (DebugCategory) -> Unit
) {
    ScrollableTabRow(
        selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        edgePadding = 16.dp,
        divider = {}
    ) {
        categories.forEach { category ->
            val isSelected = category == selectedCategory

            Tab(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                text = {
                    Text(
                        text = category.displayName,
                        style = if (isSelected)
                            MaterialTheme.typography.titleSmall
                        else
                            MaterialTheme.typography.bodyMedium,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                        color = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                icon = {
                    Icon(
                        imageVector = getIconForCategory(category),
                        contentDescription = category.displayName,
                        tint = if (isSelected)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .height(20.dp)
                            .width(20.dp)
                    )
                }
            )
        }
    }
}

/**
 * 根据分类获取对应的图标
 */
@Composable
private fun getIconForCategory(category: DebugCategory) = when (category) {
    DebugCategory.ALL -> Icons.Default.List
    DebugCategory.APP -> Icons.Default.Info
    DebugCategory.NETWORK -> Icons.Default.Cloud
    DebugCategory.RULE -> Icons.Default.Code
    DebugCategory.SOURCE -> Icons.Default.Code
    DebugCategory.RSS -> Icons.Default.Code
    DebugCategory.TOAST -> Icons.Default.Notifications
    DebugCategory.CHECK -> Icons.Default.CheckCircle
    DebugCategory.CRASH -> Icons.Default.Error
}
