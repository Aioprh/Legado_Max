package io.legado.app.ui.file.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.legado.app.ui.theme.pageCardContainerColor
import java.io.File

/**
 * 路径导航条
 * 显示当前路径，支持点击跳转
 * 格式：root > folder1 > folder2 > ...
 */
@Composable
fun PathBreadcrumb(
    subDocs: List<File>,
    onRootClick: () -> Unit,
    onPathClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val containerColor = pageCardContainerColor()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(32.dp)
            .background(containerColor)
            .horizontalScroll(scrollState)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 根目录项
        PathItem(
            text = "root",
            onClick = onRootClick
        )

        // 子目录项
        subDocs.forEachIndexed { index, file ->
            PathItem(
                text = file.name,
                onClick = { onPathClick(index) }
            )
        }
    }
}

/**
 * 单个路径项
 * 格式：文本 + 箭头图标
 * 触控目标：纵向 padding 扩大命中区（accessibility.md §15.3）
 */
@Composable
private fun PathItem(
    text: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false)
            ) { onClick() }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            modifier = Modifier
                .width(20.dp)
                .height(24.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
