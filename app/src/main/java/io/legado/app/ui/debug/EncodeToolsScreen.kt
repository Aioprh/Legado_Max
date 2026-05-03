/**
 * 编码转换工具界面 - Jetpack Compose实现
 * 
 * 功能说明：
 * 提供多种编码/解码功能：
 * - Base64 编码/解码
 * - MD5 编码（32位/16位）
 * - URL 编码/解码
 * - Hex 编码/解码
 * - Unicode 编码/解码
 * 
 * 界面结构：
 * - 编码类型选择器（下拉菜单）
 * - 输入文本框
 * - 操作按钮（转换、交换、清空、复制）
 * - 结果显示区域
 */
package io.legado.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.utils.EncoderUtils
import io.legado.app.utils.MD5Utils
import io.legado.app.utils.encodeURI
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi

/**
 * 编码类型列表
 * 索引对应转换逻辑中的when分支
 */
private val encodeTypes = listOf(
    "Base64 编码",
    "Base64 解码",
    "MD5 编码 (32位)",
    "MD5 编码 (16位)",
    "URL 编码",
    "URL 解码",
    "Hex 编码",
    "Hex 解码",
    "Unicode 编码",
    "Unicode 解码"
)

/**
 * 编码转换工具界面
 * 
 * @param onBackClick 返回按钮点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EncodeToolsScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val containerColor = debugToolsCardContainerColor()
    val topBarColor = debugToolsTopBarContainerColor()
    
    // 当前选中的编码类型索引
    var currentType by remember { mutableStateOf(0) }
    // 输入文本
    var input by remember { mutableStateOf("") }
    // 转换结果
    var result by remember { mutableStateOf("") }
    // 下拉菜单是否展开
    var expanded by remember { mutableStateOf(false) }

    // 页面骨架
    Scaffold(
        containerColor = Color.Transparent,
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
                title = {
                    Text(
                        text = stringResource(R.string.debug_encode_tools),
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
        // 主内容区域：可滚动列布局
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 编码类型选择卡片
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.debug_encode_type),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    // 下拉选择框：ExposedDropdownMenuBox
                    ExposedDropdownMenuBox(
                        expanded = expanded,
                        onExpandedChange = { expanded = !expanded }
                    ) {
                        // 显示当前选中的编码类型
                        OutlinedTextField(
                            value = encodeTypes[currentType],
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent
                            )
                        )
                        
                        // 下拉菜单
                        ExposedDropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false }
                        ) {
                            encodeTypes.forEachIndexed { index, type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        currentType = index
                                        expanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }

            // 输入区域卡片
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
                    
                    // 输入文本框
                    OutlinedTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 120.dp),
                        placeholder = { Text(stringResource(R.string.debug_input_hint)) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        )
                    )
                }
            }

            // 操作按钮行1：转换 + 交换
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 转换按钮：执行编码/解码操作
                Button(
                    onClick = {
                        if (input.isEmpty()) {
                            context.toastOnUi(R.string.input_is_empty)
                            return@Button
                        }
                        try {
                            result = when (currentType) {
                                0 -> EncoderUtils.base64Encode(input) ?: "编码失败"
                                1 -> EncoderUtils.base64Decode(input)
                                2 -> MD5Utils.md5Encode(input)
                                3 -> MD5Utils.md5Encode16(input)
                                4 -> input.encodeURI()
                                5 -> java.net.URLDecoder.decode(input, "UTF-8")
                                6 -> bytesToHex(input.toByteArray())
                                7 -> String(hexToBytes(input))
                                8 -> stringToUnicode(input)
                                9 -> unicodeToString(input)
                                else -> input
                            }
                        } catch (e: Exception) {
                            result = "错误: ${e.message}"
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.debug_convert))
                }
                
                // 交换按钮：交换输入和结果
                OutlinedButton(
                    onClick = {
                        val temp = input
                        if (result.isNotEmpty()) {
                            input = result
                            result = temp
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.SwapHoriz, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.debug_swap))
                }
            }

            // 操作按钮行2：清空 + 复制
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // 清空按钮
                OutlinedButton(
                    onClick = {
                        input = ""
                        result = ""
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Clear, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("清空")
                }
                
                // 复制按钮：复制结果到剪贴板
                OutlinedButton(
                    onClick = {
                        if (result.isNotEmpty()) {
                            context.sendToClip(result)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("复制")
                }
            }

            // 结果显示卡片
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = stringResource(R.string.debug_result),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    Text(
                        text = result.ifEmpty { "暂无结果" },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (result.isEmpty()) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp)
                    )
                }
            }
        }
    }
}

/**
 * 字节数组转十六进制字符串
 * 
 * @param bytes 字节数组
 * @return 十六进制字符串（小写）
 */
private fun bytesToHex(bytes: ByteArray): String {
    return bytes.joinToString("") { "%02x".format(it) }
}

/**
 * 十六进制字符串转字节数组
 * 
 * @param hex 十六进制字符串（可包含空格和换行）
 * @return 字节数组
 */
private fun hexToBytes(hex: String): ByteArray {
    val cleanHex = hex.replace(" ", "").replace("\n", "")
    return ByteArray(cleanHex.length / 2) {
        cleanHex.substring(it * 2, it * 2 + 2).toInt(16).toByte()
    }
}

/**
 * 字符串转Unicode编码
 * 
 * 将非ASCII字符转换为\uXXXX格式
 * 例如："你好" -> "\u4f60\u597d"
 * 
 * @param str 输入字符串
 * @return Unicode编码字符串
 */
private fun stringToUnicode(str: String): String {
    return str.map { char ->
        if (char.code > 127) {
            "\\u${char.code.toString(16).padStart(4, '0')}"
        } else {
            char.toString()
        }
    }.joinToString("")
}

/**
 * Unicode编码转字符串
 * 
 * 将\uXXXX格式转换回原始字符
 * 例如："\u4f60\u597d" -> "你好"
 * 
 * @param unicode Unicode编码字符串
 * @return 解码后的字符串
 */
private fun unicodeToString(unicode: String): String {
    val regex = Regex("\\\\u([0-9a-fA-F]{4})")
    return regex.replace(unicode) { match ->
        match.groupValues[1].toInt(16).toChar().toString()
    }
}
