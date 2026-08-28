package io.legado.app.ui.widget.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import io.legado.app.R
import io.legado.app.ui.theme.pageTopBarContainerColor

/**
 * 页面统一顶栏（theme-styles.md §14.2 脚手架项）
 *
 * 收敛各页面 TopAppBar 的重复配色样板：
 * - 容器色统一取 [pageTopBarContainerColor]
 * - 导航/标题/操作图标统一用 colorScheme.onSecondary
 * - 标题用 typography.titleLarge + Medium 字重，副标题用 bodySmall
 *
 * @param title 标题文案（必须已资源化）
 * @param onBackClick 返回键点击
 * @param modifier Modifier
 * @param subtitle 副标题（可选，单行省略）
 * @param backIcon 返回图标（默认 ArrowBack，选择态等场景可换 Close）
 * @param backContentDescription 返回键无障碍描述（默认"返回"）
 * @param containerColor 容器色（默认 [pageTopBarContainerColor]；需与下方内容连成一体时可传透明）
 * @param actions 右侧操作区
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppPageTopBar(
    title: String,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    backIcon: ImageVector = Icons.AutoMirrored.Filled.ArrowBack,
    backContentDescription: String? = null,
    containerColor: Color = pageTopBarContainerColor(),
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        modifier = modifier,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = containerColor,
            scrolledContainerColor = containerColor,
            navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
            titleContentColor = MaterialTheme.colorScheme.onSecondary,
            actionIconContentColor = MaterialTheme.colorScheme.onSecondary
        ),
        title = {
            Column {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 1
                    )
                }
            }
        },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(
                    backIcon,
                    contentDescription = backContentDescription
                        ?: stringResource(R.string.back)
                )
            }
        },
        actions = actions
    )
}

// ── 预览（§10.1 强制）────────────────────────────────────────

@Preview(showBackground = true)
@Composable
private fun AppPageTopBarPreview() {
    MaterialTheme {
        AppPageTopBar(
            title = "存储管理",
            subtitle = "共 5 项缓存 · 128.5 MB",
            onBackClick = {}
        )
    }
}
