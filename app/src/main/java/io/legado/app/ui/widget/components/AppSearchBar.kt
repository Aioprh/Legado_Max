package io.legado.app.ui.widget.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.ui.theme.pageCardContainerColor

/**
 * 通用搜索栏（theme-styles.md §14.2 脚手架项）
 *
 * 收敛各页面重复的搜索框实现：
 * - 圆角卡片背景 + 左侧搜索图标 + 占位文字
 * - 可选右侧清除按钮
 * - 对外 API 稳定，供各 Feature 复用（structure.md §6：跨 Feature 组件提升至 ui/widget/components/）
 *
 * @param query 当前输入
 * @param onQueryChange 输入变化回调
 * @param hint 占位提示（必须已资源化）
 * @param showClearButton 是否显示右侧清除按钮
 * @param onClear 点击清除按钮回调（null 时不渲染；默认为清空 query）
 * @param modifier Modifier
 */
@Composable
fun AppSearchBar(
    query: String,
    onQueryChange: (String) -> Unit,
    hint: String,
    modifier: Modifier = Modifier,
    showClearButton: Boolean = true,
    onClear: (() -> Unit)? = { onQueryChange("") }
) {
    val containerColor = pageCardContainerColor()

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .height(36.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 搜索图标
            Icon(
                imageVector = Icons.Default.Search,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            // 输入框
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart
            ) {
                // 占位提示文字
                if (query.isEmpty()) {
                    Text(
                        text = hint,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
                // 实际输入框
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = MaterialTheme.colorScheme.onSurface
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { })
                )
            }
            // 右侧清除按钮（可选）
            if (showClearButton && query.isNotEmpty() && onClear != null) {
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onClear) {
                    Icon(
                        imageVector = Icons.Default.Clear,
                        contentDescription = stringResource(R.string.clear),
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                }
            }
        }
    }
}

// ── 预览（§10.1 强制）────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun AppSearchBarPreview() {
    MaterialTheme {
        AppSearchBar(
            query = "",
            onQueryChange = {},
            hint = "搜索"
        )
    }
}