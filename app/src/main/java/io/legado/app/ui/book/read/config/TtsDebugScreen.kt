package io.legado.app.ui.book.read.config

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.legado.app.R
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.util.concurrent.TimeUnit

data class TestHistory(
    val timestamp: Long,
    val text: String,
    val success: Boolean,
    val duration: Long,
    val errorMessage: String? = null
)

data class TtsTestResult(
    val success: Boolean,
    val audioUrl: String? = null,
    val duration: Long = 0,
    val errorMessage: String? = null,
    val requestUrl: String? = null,
    val responseJson: String? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TtsDebugScreen(
    ttsId: Long,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val containerColor = debugToolsCardContainerColor()
    val topBarColor = debugToolsTopBarContainerColor()
    val coroutineScope = rememberCoroutineScope()
    
    var httpTTS by remember { mutableStateOf<HttpTTS?>(null) }
    var testText by remember { mutableStateOf("这是一段测试文本") }
    var speed by remember { mutableStateOf(5) }
    var selectedSpeaker by remember { mutableStateOf("") }
    var pitch by remember { mutableStateOf(0) }
    
    var isTesting by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<TtsTestResult?>(null) }
    var testHistory by remember { mutableStateOf<List<TestHistory>>(emptyList()) }
    
    var showLogDialog by remember { mutableStateOf(false) }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var showMenu by remember { mutableStateOf(false) }
    
    val speakers = remember { mutableStateListOf<String>() }
    
    LaunchedEffect(ttsId) {
        httpTTS = withContext(Dispatchers.IO) {
            appDb.httpTTSDao.get(ttsId)
        }
        
        httpTTS?.jsLib?.let { jsLib ->
            parseSpeakersFromJsLib(jsLib)?.let { speakerList ->
                speakers.clear()
                speakers.addAll(speakerList)
                if (speakers.isNotEmpty() && selectedSpeaker.isEmpty()) {
                    selectedSpeaker = speakers[0]
                }
            }
        }
    }
    
    if (showLogDialog && testResult != null) {
        Dialog(onDismissRequest = { showLogDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f),
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "调试日志",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showLogDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        testResult?.requestUrl?.let { url ->
                            LogSection(title = "请求URL", content = url)
                        }
                        
                        testResult?.responseJson?.let { json ->
                            LogSection(title = "响应数据", content = json)
                        }
                        
                        testResult?.errorMessage?.let { error ->
                            LogSection(title = "错误信息", content = error, isError = true)
                        }
                    }
                }
            }
        }
    }
    
    if (showHistoryDialog) {
        Dialog(onDismissRequest = { showHistoryDialog = false }) {
            Surface(
                modifier = Modifier.fillMaxWidth().fillMaxHeight(0.7f),
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "测试历史",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        IconButton(onClick = { showHistoryDialog = false }) {
                            Icon(Icons.Default.Close, contentDescription = "关闭")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    if (testHistory.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "暂无测试记录",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(testHistory.reversed()) { history ->
                                HistoryItem(history = history)
                            }
                        }
                    }
                }
            }
        }
    }
    
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
                        text = "TTS调试工具",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("查看日志") },
                                onClick = {
                                    showMenu = false
                                    if (testResult != null) {
                                        showLogDialog = true
                                    } else {
                                        context.toastOnUi("暂无测试结果")
                                    }
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Description, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("测试历史") },
                                onClick = {
                                    showMenu = false
                                    showHistoryDialog = true
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.History, contentDescription = null)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("清空历史") },
                                onClick = {
                                    showMenu = false
                                    testHistory = emptyList()
                                    context.toastOnUi("已清空测试历史")
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.Clear, contentDescription = null)
                                }
                            )
                        }
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            httpTTS?.let { tts ->
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "引擎信息",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "名称: ${tts.name}",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "ID: ${tts.id}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "测试文本",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "${testText.length} 字",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = testText,
                            onValueChange = { testText = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp, max = 200.dp),
                            placeholder = { Text("输入要测试的文本") }
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = { testText = "这是一段测试文本" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("默认文本")
                            }
                            Button(
                                onClick = { testText = "床前明月光，疑是地上霜。举头望明月，低头思故乡。" },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("诗词")
                            }
                        }
                    }
                }
                
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "参数配置",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        
                        Text(
                            text = "语速: $speed",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = speed.toFloat(),
                            onValueChange = { speed = it.toInt() },
                            valueRange = -10f..10f,
                            steps = 20,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = "音调: $pitch",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Slider(
                            value = pitch.toFloat(),
                            onValueChange = { pitch = it.toInt() },
                            valueRange = -100f..100f,
                            steps = 200,
                            modifier = Modifier.fillMaxWidth()
                        )
                        
                        if (speakers.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            var speakerExpanded by remember { mutableStateOf(false) }
                            
                            Text(
                                text = "音色",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            
                            ExposedDropdownMenuBox(
                                expanded = speakerExpanded,
                                onExpandedChange = { speakerExpanded = !speakerExpanded }
                            ) {
                                OutlinedTextField(
                                    value = selectedSpeaker,
                                    onValueChange = {},
                                    readOnly = true,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .menuAnchor(MenuAnchorType.PrimaryNotEditable),
                                    trailingIcon = {
                                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = speakerExpanded)
                                    }
                                )
                                
                                ExposedDropdownMenu(
                                    expanded = speakerExpanded,
                                    onDismissRequest = { speakerExpanded = false }
                                ) {
                                    speakers.forEach { speaker ->
                                        DropdownMenuItem(
                                            text = { Text(speaker) },
                                            onClick = {
                                                selectedSpeaker = speaker
                                                speakerExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isTesting = true
                                val startTime = System.currentTimeMillis()
                                
                                try {
                                    val result = withContext(Dispatchers.IO) {
                                        testTtsEngine(
                                            tts = tts,
                                            text = testText,
                                            speed = speed,
                                            speaker = selectedSpeaker,
                                            pitch = pitch
                                        )
                                    }
                                    
                                    testResult = result
                                    val duration = System.currentTimeMillis() - startTime
                                    
                                    testHistory = testHistory + TestHistory(
                                        timestamp = System.currentTimeMillis(),
                                        text = testText,
                                        success = result.success,
                                        duration = duration,
                                        errorMessage = result.errorMessage
                                    )
                                    
                                    if (result.success) {
                                        context.toastOnUi("测试成功！耗时 ${duration}ms")
                                    } else {
                                        context.toastOnUi("测试失败: ${result.errorMessage}")
                                    }
                                } catch (e: Exception) {
                                    val duration = System.currentTimeMillis() - startTime
                                    testResult = TtsTestResult(
                                        success = false,
                                        errorMessage = e.message ?: "未知错误"
                                    )
                                    testHistory = testHistory + TestHistory(
                                        timestamp = System.currentTimeMillis(),
                                        text = testText,
                                        success = false,
                                        duration = duration,
                                        errorMessage = e.message
                                    )
                                    context.toastOnUi("测试失败: ${e.message}")
                                } finally {
                                    isTesting = false
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                        enabled = !isTesting && testText.isNotEmpty()
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        Icon(Icons.Default.PlayArrow, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(if (isTesting) "测试中..." else "开始测试")
                    }
                    
                    OutlinedButton(
                        onClick = {
                            speed = 5
                            pitch = 0
                            if (speakers.isNotEmpty()) {
                                selectedSpeaker = speakers[0]
                            }
                            testResult = null
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("重置")
                    }
                }
                
                testResult?.let { result ->
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "测试结果",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            result.audioUrl?.let { url ->
                                                context.sendToClip(url)
                                            }
                                        },
                                        enabled = result.audioUrl != null
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "复制URL")
                                    }
                                    IconButton(onClick = { showLogDialog = true }) {
                                        Icon(Icons.Default.Description, contentDescription = "查看日志")
                                    }
                                }
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = if (result.success) Icons.Default.CheckCircle else Icons.Default.Error,
                                    contentDescription = null,
                                    tint = if (result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error
                                )
                                Text(
                                    text = if (result.success) "成功" else "失败",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = if (result.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            result.duration.let { duration ->
                                Text(
                                    text = "耗时: ${duration}ms",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                            }
                            
                            result.audioUrl?.let { url ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "音频URL:",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = url,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                            
                            result.errorMessage?.let { error ->
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "错误: $error",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun LogSection(
    title: String,
    content: String,
    isError: Boolean = false
) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))
        Surface(
            color = MaterialTheme.colorScheme.surfaceVariant,
            shape = RoundedCornerShape(8.dp)
        ) {
            Text(
                text = content,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(8.dp),
                color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun HistoryItem(history: TestHistory) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = history.text,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1
                )
                Text(
                    text = formatTimestamp(history.timestamp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (history.success) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (history.success) Color(0xFF4CAF50) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "${history.duration}ms",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
    }
}

fun parseSpeakersFromJsLib(jsLib: String): List<String>? {
    return try {
        val speakerMapRegex = Regex("""var\s+speakerMap\s*=\s*\{([^}]+)\}""")
        val match = speakerMapRegex.find(jsLib) ?: return null
        
        val mapContent = match.groupValues[1]
        val speakerRegex = Regex("""'([^']+)'\s*:""")
        speakerRegex.findAll(mapContent).map { it.groupValues[1] }.toList()
    } catch (e: Exception) {
        null
    }
}

suspend fun testTtsEngine(
    tts: HttpTTS,
    text: String,
    speed: Int,
    speaker: String,
    pitch: Int
): TtsTestResult {
    return try {
        val rhino = Context.enter()
        val scope: Scriptable = rhino.initStandardObjects()
        
        val result = mutableMapOf<String, Any>()
        result["音色"] = speaker
        result["音调"] = pitch
        
        scope.put("result", scope, result)
        scope.put("speakText", scope, text)
        scope.put("speakSpeed", scope, speed)
        
        tts.jsLib?.let { jsLib ->
            rhino.evaluateString(scope, jsLib, "jsLib", 1, null)
        }
        
        val ttsFunction = scope.get("doubaoTTS", scope)
        if (ttsFunction !is org.mozilla.javascript.Function) {
            return TtsTestResult(
                success = false,
                errorMessage = "未找到doubaoTTS函数"
            )
        }
        
        val audioUrl = ttsFunction.call(rhino, scope, scope, arrayOf<Any?>(text, speed))
        
        if (audioUrl is String && audioUrl.startsWith("http")) {
            TtsTestResult(
                success = true,
                audioUrl = audioUrl
            )
        } else {
            TtsTestResult(
                success = false,
                errorMessage = "未获取到有效的音频URL: $audioUrl"
            )
        }
    } catch (e: Exception) {
        TtsTestResult(
            success = false,
            errorMessage = e.message ?: "测试失败"
        )
    } finally {
        Context.exit()
    }
}

fun formatTimestamp(timestamp: Long): String {
    val sdf = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
    return sdf.format(java.util.Date(timestamp))
}

@Composable
fun debugToolsCardContainerColor(): Color {
    val context = LocalContext.current
    val bgColor = remember { ThemeStore.backgroundColor(context) }
    val isLight = ColorUtils.isColorLight(bgColor)
    val background = remember(bgColor) { Color(bgColor) }
    return remember(background, isLight) {
        lerp(background, if (isLight) Color.White else Color.Black, if (isLight) 0.08f else 0.12f)
    }
}

@Composable
fun debugToolsTopBarContainerColor(): Color {
    val context = LocalContext.current
    val bgColor = remember { ThemeStore.backgroundColor(context) }
    val isLight = ColorUtils.isColorLight(bgColor)
    val background = remember(bgColor) { Color(bgColor) }
    return remember(background, isLight) {
        lerp(background, if (isLight) Color.White else Color.Black, if (isLight) 0.04f else 0.08f)
    }
}
