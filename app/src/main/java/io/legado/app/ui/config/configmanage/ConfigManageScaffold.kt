package io.legado.app.ui.config.configmanage

import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.theme.pageTopBarBackground
import io.legado.app.ui.theme.pageTopBarColors

/**
 * 配置管理页 Scaffold 骨架（通用 Composable）。
 *
 * 封装了所有配置管理页共享的 Scaffold + TopAppBar 结构：
 * - 顶部导航栏（标题 + 返回按钮 + 自定义 actions）
 * - 多选模式下的返回行为（退出多选而非返回上一页）
 * - 可选的底部操作栏（多选模式下显示）
 * - 透明的 Scaffold 容器（让主题背景透出）
 *
 * 调用方只需提供标题、actions 内容、底部栏内容和主体内容，
 * 不需要重复编写 TopAppBar 样式和返回逻辑。
 *
 * @param title         顶部标题文本
 * @param isMultiSelectMode 当前是否处于多选模式（影响返回按钮行为）
 * @param onBackClick   返回回调（仅在非多选模式下触发）
 * @param onExitMultiSelect 退出多选模式回调（仅在多选模式下触发）
 * @param actions       TopAppBar 右侧操作区内容
 * @param bottomBar     底部栏内容（通常为多选操作栏，为空时不显示）
 * @param content       主体内容
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigManageScaffold(
    title: String,
    isMultiSelectMode: Boolean,
    onBackClick: () -> Unit,
    onExitMultiSelect: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    content: @Composable () -> Unit
) {
    val topBarColors = pageTopBarColors()

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                modifier = Modifier.pageTopBarBackground(topBarColors),
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent,
                    scrolledContainerColor = Color.Transparent,
                    navigationIconContentColor = topBarColors.contentColor,
                    titleContentColor = topBarColors.contentColor,
                    actionIconContentColor = topBarColors.contentColor
                ),
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    )
                },
                navigationIcon = {
                    // 返回按钮：多选模式下退出多选，否则返回上一页
                    IconButton(onClick = {
                        if (isMultiSelectMode) {
                            onExitMultiSelect()
                        } else {
                            onBackClick()
                        }
                    }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = actions
            )
        },
        bottomBar = bottomBar
    ) { paddingValues ->
        androidx.compose.foundation.layout.Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            content()
        }
    }
}

/**
 * 多选模式下 TopAppBar 右侧的全选/取消全选按钮。
 *
 * 通用组件，供各配置管理页在 [ConfigManageScaffold] 的 actions 插槽中使用。
 *
 * @param isAllSelected  当前可见列表是否已全选
 * @param onSelectAll    点击全选/取消全选的回调
 */
@Composable
fun RowScope.SelectAllAction(
    isAllSelected: Boolean,
    onSelectAll: () -> Unit
) {
    Text(
        text = stringResource(R.string.select_all),
        modifier = Modifier.padding(horizontal = 12.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.bodyMedium,
        fontWeight = FontWeight.Medium
    )
    IconButton(onClick = onSelectAll) {
        Text(
            text = if (isAllSelected)
                stringResource(R.string.cancel)
            else
                stringResource(R.string.select_all),
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

/**
 * 普通模式下 TopAppBar 右侧的导入按钮（从剪贴板导入）。
 *
 * @param onClick 点击回调
 */
@Composable
fun RowScope.ImportFromClipboardAction(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            Icons.Default.ContentPaste,
            contentDescription = stringResource(R.string.top_bar_import_clipboard)
        )
    }
}
