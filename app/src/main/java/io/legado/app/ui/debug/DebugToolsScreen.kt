/**
 * 调试工具主界面 - Jetpack Compose实现
 * 
 * 功能说明：
 * 作为调试工具的入口界面，提供4个调试工具的导航列表：
 * - 编码转换：Base64/MD5/URL/Hex/Unicode 编解码
 * - HTTP请求调试：发送HTTP请求并查看响应
 * - 正则测试：测试正则表达式匹配
 * - 时间戳转换：时间戳与日期互相转换
 * 
 * 架构说明：
 * - 使用Scaffold构建页面骨架，包含TopAppBar
 * - 使用LazyColumn展示工具列表（类似RecyclerView）
 * - 点击列表项跳转到对应的Activity
 */
package io.legado.app.ui.debug

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Http
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R

/**
 * 调试工具数据类
 * 
 * @param titleRes 工具名称的字符串资源ID
 * @param descRes 工具描述的字符串资源ID
 * @param icon 工具图标（Material Icons）
 * @param activityClass 点击后跳转的Activity类
 */
data class DebugTool(
    val titleRes: Int,
    val descRes: Int,
    val icon: ImageVector,
    val activityClass: Class<*>
)

/**
 * 调试工具主界面
 * 
 * 显示调试工具列表，点击跳转到对应工具界面
 * 
 * @param onBackClick 返回按钮点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugToolsScreen(
    onBackClick: () -> Unit
) {
    // 获取Context用于启动Activity
    val context = LocalContext.current
    // 卡片背景色
    val containerColor = debugToolsCardContainerColor()
    // 标题栏背景色
    val topBarColor = debugToolsTopBarContainerColor()

    // 调试工具列表
    val tools = listOf(
        DebugTool(
            titleRes = R.string.debug_encode_tools,
            descRes = R.string.debug_encode_tools_desc,
            icon = Icons.Default.Code,
            activityClass = EncodeToolsActivity::class.java
        ),
        DebugTool(
            titleRes = R.string.debug_http_request,
            descRes = R.string.debug_http_request_desc,
            icon = Icons.Default.Http,
            activityClass = HttpDebugActivity::class.java
        ),
        DebugTool(
            titleRes = R.string.debug_regex_test,
            descRes = R.string.debug_regex_test_desc,
            icon = Icons.Default.TextFields,
            activityClass = RegexTestActivity::class.java
        ),
        DebugTool(
            titleRes = R.string.debug_timestamp,
            descRes = R.string.debug_timestamp_desc,
            icon = Icons.Default.Schedule,
            activityClass = TimestampConvertActivity::class.java
        )
    )

    // 页面骨架：Scaffold提供TopAppBar和内容区域
    Scaffold(
        containerColor = Color.Transparent, // 透明背景，让Activity的背景显示
        topBar = {
            // 标题栏
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                // 标题：主标题 + 副标题
                title = {
                    Column {
                        Text(
                            text = stringResource(R.string.debug_tools),
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.debug_tools_desc),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                // 导航图标：返回按钮
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        // 工具列表：LazyColumn类似RecyclerView，只渲染可见项
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            // 遍历工具列表，生成列表项
            items(tools) { tool ->
                DebugToolItem(
                    tool = tool,
                    containerColor = containerColor,
                    onClick = {
                        // 点击跳转到对应Activity
                        context.startActivity(Intent(context, tool.activityClass))
                    }
                )
            }
        }
    }
}

/**
 * 调试工具列表项组件
 * 
 * 显示单个调试工具的信息，包括图标、名称、描述
 * 点击后跳转到对应的工具界面
 * 
 * @param tool 调试工具数据
 * @param containerColor 卡片背景色
 * @param onClick 点击回调
 */
@Composable
private fun DebugToolItem(
    tool: DebugTool,
    containerColor: Color,
    onClick: () -> Unit
) {
    // Surface是一个带背景和阴影的容器
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick), // 添加点击效果
        color = containerColor,
        shape = RoundedCornerShape(12.dp) // 圆角
    ) {
        // Row是水平布局，子元素从左到右排列
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 左侧图标容器
            Surface(
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(48.dp)
            ) {
                // Box是叠加布局，用于居中图标
                Box(
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = tool.icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            
            // 图标和文字之间的间距
            Spacer(modifier = Modifier.width(16.dp))
            
            // 右侧文字区域
            Column(modifier = Modifier.weight(1f)) {
                // 工具名称
                Text(
                    text = stringResource(tool.titleRes),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                // 工具描述
                Text(
                    text = stringResource(tool.descRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/**
 * 计算卡片背景色
 * 根据主题亮度自动调整透明度
 */
@Composable
fun debugToolsCardContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val alpha = if (background.luminance() > 0.5f) 0.9f else 0.9f
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

/**
 * 计算标题栏背景色
 * 根据主题亮度自动调整透明度
 */
@Composable
fun debugToolsTopBarContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val alpha = if (background.luminance() > 0.5f) 0.82f else 0.94f
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

/**
 * 计算颜色亮度
 * 使用ITU-R BT.601标准计算感知亮度
 * 
 * @return 亮度值，范围0-1
 */
private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
