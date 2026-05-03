/**
 * 正则表达式测试工具界面 - Jetpack Compose实现
 * 
 * 功能说明：
 * 测试正则表达式匹配效果，支持：
 * - 输入正则表达式模式
 * - 输入待匹配文本
 * - 设置匹配选项（忽略大小写、多行模式、点匹配换行）
 * - 显示匹配结果（完整匹配、分组信息）
 * - 高亮显示匹配内容
 * 
 * 界面结构：
 * - 正则表达式输入区（含选项复选框）
 * - 待匹配文本输入区
 * - 操作按钮（测试、清空）
 * - 匹配结果显示区
 * - 高亮显示区
 */
package io.legado.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi

/**
 * 正则表达式测试界面
 * 
 * @param onBackClick 返回按钮点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegexTestScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val containerColor = debugToolsCardContainerColor()
    val topBarColor = debugToolsTopBarContainerColor()
    
    // 正则表达式模式
    var pattern by remember { mutableStateOf("") }
    // 待匹配文本
    var input by remember { mutableStateOf("") }
    // 匹配结果文本
    var result by remember { mutableStateOf("") }
    // 高亮显示的带样式文本
    var highlightedText by remember { mutableStateOf<AnnotatedString?>(null) }
    
    // 正则选项
    var ignoreCase by remember { mutableStateOf(false) } // 忽略大小写
    var multiline by remember { mutableStateOf(false) }   // 多行模式
    var dotAll by remember { mutableStateOf(false) }      // 点匹配换行

    // 页面骨架
    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                title = {
                    Text(
                        text = stringResource(R.string.debug_regex_test),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { paddingValues ->
        // 主内容区域
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 正则表达式输入卡片
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.debug_regex_pattern),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 正则表达式输入框
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.debug_regex_pattern_hint)) },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    // 正则选项复选框
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 忽略大小写选项
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = ignoreCase,
                                onCheckedChange = { ignoreCase = it }
                            )
                            Text(
                                text = stringResource(R.string.debug_regex_ignore_case),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        // 多行模式选项
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = multiline,
                                onCheckedChange = { multiline = it }
                            )
                            Text(
                                text = stringResource(R.string.debug_regex_multiline),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        
                        // 点匹配换行选项
                        Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                            Checkbox(
                                checked = dotAll,
                                onCheckedChange = { dotAll = it }
                            )
                            Text(
                                text = stringResource(R.string.debug_regex_dot_all),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // 待匹配文本输入卡片
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.debug_input),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 待匹配文本输入框
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp),
                        placeholder = { Text(stringResource(R.string.debug_input_hint)) }
                    )
                }
            }

            // 操作按钮行
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 测试按钮：执行正则匹配
                Button(
                    onClick = {
                        if (pattern.isEmpty()) {
                            context.toastOnUi(R.string.debug_pattern_empty)
                            return@Button
                        }
                        if (input.isEmpty()) {
                            context.toastOnUi(R.string.input_is_empty)
                            return@Button
                        }
                        
                        try {
                            // 构建正则选项
                            val regexOptions = mutableSetOf<RegexOption>()
                            if (ignoreCase) regexOptions.add(RegexOption.IGNORE_CASE)
                            if (multiline) regexOptions.add(RegexOption.MULTILINE)
                            if (dotAll) regexOptions.add(RegexOption.DOT_MATCHES_ALL)
                            
                            // 创建正则表达式并执行匹配
                            val regex = Regex(pattern, regexOptions)
                            val matches = regex.findAll(input).toList()
                            
                            // 无匹配结果
                            if (matches.isEmpty()) {
                                result = context.getString(R.string.debug_no_match)
                                highlightedText = null
                                return@Button
                            }
                            
                            // 构建匹配结果文本
                            val sb = StringBuilder()
                            matches.forEachIndexed { index, match ->
                                sb.append("匹配 ${index + 1}:\n")
                                sb.append("  完整匹配: ${match.value}\n")
                                // 显示分组信息
                                match.groupValues.forEachIndexed { groupIndex, groupValue ->
                                    if (groupIndex > 0) {
                                        sb.append("  分组 $groupIndex: $groupValue\n")
                                    }
                                }
                                sb.append("\n")
                            }
                            result = sb.toString()
                            
                            // 构建高亮显示文本
                            val highlightColor = Color(0x40FFEB3B) // 半透明黄色
                            highlightedText = buildAnnotatedString {
                                var lastIndex = 0
                                val sortedMatches = matches.sortedBy { it.range.first }
                                
                                for (match in sortedMatches) {
                                    // 添加匹配前的普通文本
                                    if (match.range.first > lastIndex) {
                                        append(input.substring(lastIndex, match.range.first))
                                    }
                                    // 添加高亮的匹配文本
                                    withStyle(style = SpanStyle(background = highlightColor)) {
                                        append(match.value)
                                    }
                                    lastIndex = match.range.last + 1
                                }
                                
                                // 添加最后的普通文本
                                if (lastIndex < input.length) {
                                    append(input.substring(lastIndex))
                                }
                            }
                            
                        } catch (e: Exception) {
                            result = "错误: ${e.message}"
                            highlightedText = null
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.debug_test))
                }
                
                // 清空按钮
                OutlinedButton(
                    onClick = {
                        pattern = ""
                        input = ""
                        result = ""
                        highlightedText = null
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空")
                }
            }

            // 匹配结果显示卡片
            if (result.isNotEmpty()) {
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = stringResource(R.string.debug_result),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            // 复制按钮
                            IconButton(
                                onClick = { context.sendToClip(result) }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = result,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            // 高亮显示卡片
            if (highlightedText != null) {
                val annotatedText = highlightedText
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.debug_highlight),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // SelectionContainer使文本可选择
                        SelectionContainer {
                            Text(
                                text = annotatedText!!,
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }
}
