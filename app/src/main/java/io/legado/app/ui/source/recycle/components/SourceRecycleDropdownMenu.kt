package io.legado.app.ui.source.recycle.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp

/**
 * 回收站下拉菜单容器
 */
@Composable
fun SourceRecycleDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset.Zero,
    content: @Composable ColumnScope.() -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier,
        offset = offset,
        containerColor = MaterialTheme.colorScheme.surface,
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
        content = content
    )
}

/**
 * 回收站下拉菜单项
 */
@Composable
fun SourceRecycleDropdownMenuItem(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selected: Boolean = false,
    destructive: Boolean = false,
    leadingIcon: @Composable (() -> Unit)? = null
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val textColor = when {
        !enabled -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        destructive -> MaterialTheme.colorScheme.error
        selected -> primaryColor
        else -> MaterialTheme.colorScheme.onSurface
    }
    DropdownMenuItem(
        text = {
            Text(
                text = text,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        onClick = onClick,
        modifier = modifier.background(
            if (selected) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
            else Color.Transparent
        ),
        enabled = enabled,
        leadingIcon = leadingIcon,
        trailingIcon = {
            if (selected) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = primaryColor
                )
            }
        },
        colors = MenuDefaults.itemColors(
            textColor = textColor,
            leadingIconColor = textColor,
            trailingIconColor = primaryColor,
            disabledTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            disabledLeadingIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    )
}
