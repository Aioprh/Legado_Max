/**
 * 直链上传配置界面
 * 
 * 该文件实现了直链上传功能的配置界面,包括:
 * - 上传规则的管理(添加、编辑、删除、测试)
 * - 上传历史的查看和管理
 * - 规则的导入导出功能
 * 
 * 主要组件:
 * - DirectLinkUploadScreen: 主界面,包含规则管理和上传历史两个标签页
 * - RuleListTab: 规则列表标签页
 * - HistoryListTab: 上传历史标签页
 * - RuleCard: 单个规则卡片
 * - HistoryCard: 单条历史记录卡片
 * - RuleEditDialog: 规则编辑对话框
 */
package io.legado.app.ui.upload

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.R
import io.legado.app.data.entities.DirectLinkUploadRule
import io.legado.app.data.entities.UploadHistory
import io.legado.app.data.entities.UploadHistoryWithRule
import io.legado.app.ui.upload.DirectLinkUploadViewModel.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * 直链上传配置主界面
 * 
 * 该 Composable 是直链上传功能的入口界面,提供:
 * - 顶部应用栏,包含返回按钮、添加规则按钮和更多操作菜单
 * - 标签页布局,切换规则管理和上传历史
 * - 各种对话框(添加/编辑规则、清除历史、导入默认规则、测试结果)
 * 
 * @param viewModel 直链上传的 ViewModel,负责业务逻辑和状态管理
 * @param onBackClick 返回按钮点击回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DirectLinkUploadScreen(
    viewModel: DirectLinkUploadViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    // 获取上下文和剪贴板管理器
    val context = LocalContext.current
    val clipboardManager = remember {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }
    
    // 从 ViewModel 收集状态
    val rules by viewModel.rules.collectAsState(initial = emptyList())  // 上传规则列表
    val histories by viewModel.histories.collectAsState(initial = emptyList())  // 上传历史列表
    val uiState by viewModel.uiState.collectAsState()  // UI 状态
    val uploadState by viewModel.uploadState.collectAsState()  // 上传/测试状态
    
    // 本地 UI 状态
    var selectedTab by remember { mutableStateOf(0) }  // 当前选中的标签页索引
    var showAddDialog by remember { mutableStateOf(false) }  // 是否显示添加规则对话框
    var editingRule by remember { mutableStateOf<DirectLinkUploadRule?>(null) }  // 正在编辑的规则
    var showClearDialog by remember { mutableStateOf(false) }  // 是否显示清除历史确认对话框
    var showImportDialog by remember { mutableStateOf(false) }  // 是否显示导入默认规则对话框
    var testingRule by remember { mutableStateOf<DirectLinkUploadRule?>(null) }  // 正在测试的规则
    var testResult by remember { mutableStateOf<String?>(null) }  // 测试结果
    
    // 标签页标题
    val tabs = listOf("规则管理", "上传历史")
    
    // 主界面布局
    Scaffold(
        containerColor = Color.Transparent,
        // 顶部应用栏
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "直链上传配置",
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = 20.sp, fontWeight = FontWeight.Medium)
                    )
                },
                // 顶部栏颜色配置
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.secondary,
                    scrolledContainerColor = MaterialTheme.colorScheme.secondary,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSecondary,
                    titleContentColor = MaterialTheme.colorScheme.onSecondary,
                    actionIconContentColor = MaterialTheme.colorScheme.onSecondary
                ),
                // 导航图标(返回按钮)
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回")
                    }
                },
                // 操作按钮区域
                actions = {
                    // 添加规则按钮
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, "添加规则")
                    }
                    // 更多操作菜单
                    var showMenu by remember { mutableStateOf(false) }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                    // 下拉菜单
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        // 粘贴规则选项
                        DropdownMenuItem(
                            text = { Text("粘贴规则") },
                            onClick = {
                                showMenu = false
                                val clip = clipboardManager.primaryClip
                                if (clip != null && clip.itemCount > 0) {
                                    val json = clip.getItemAt(0).text?.toString() ?: ""
                                    if (json.isNotBlank() && viewModel.pasteRule(json)) {
                                        // 粘贴成功
                                    }
                                }
                            },
                            leadingIcon = { 
                                Icon(Icons.Default.ContentPaste, null, tint = MaterialTheme.colorScheme.primary) 
                            }
                        )
                        // 导入默认规则选项
                        DropdownMenuItem(
                            text = { Text("导入默认规则") },
                            onClick = { showImportDialog = true; showMenu = false },
                            leadingIcon = { 
                                Icon(Icons.Default.CloudDownload, null, tint = MaterialTheme.colorScheme.primary) 
                            }
                        )
                        HorizontalDivider()
                        // 清除历史选项
                        DropdownMenuItem(
                            text = { Text("清除历史") },
                            onClick = { showClearDialog = true; showMenu = false },
                            leadingIcon = { 
                                Icon(Icons.Default.DeleteSweep, null, tint = MaterialTheme.colorScheme.primary) 
                            }
                        )
                    }
                }
            )
        }
    ) { paddingValues ->
        // 主内容区域
        Column(modifier = Modifier.padding(paddingValues)) {
            // 标签页布局
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = MaterialTheme.colorScheme.onSurface
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { 
                            Text(
                                title,
                                color = if (selectedTab == index) MaterialTheme.colorScheme.primary 
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                            ) 
                        }
                    )
                }
            }
            
            // 根据选中的标签页显示不同内容
            when (selectedTab) {
                // 规则管理标签页
                0 -> RuleListTab(
                    rules = rules,
                    onEdit = { editingRule = it },
                    onDelete = { viewModel.deleteRule(it) },
                    onSetDefault = { viewModel.setDefaultRule(it.id) },
                    onTest = { rule ->
                        testingRule = rule
                        viewModel.testRule(rule)
                    },
                    onCopy = { rule ->
                        val json = viewModel.copyRule(rule)
                        val clip = ClipData.newPlainText("上传规则", json)
                        clipboardManager.setPrimaryClip(clip)
                    }
                )
                // 上传历史标签页
                1 -> HistoryListTab(
                    histories = histories,
                    onDelete = { historyWithRule ->
                        viewModel.deleteHistory(historyWithRule.toUploadHistory())
                        Toast.makeText(context, "已删除历史记录", Toast.LENGTH_SHORT).show()
                    },
                    onCopy = { historyWithRule ->
                        if (historyWithRule.downloadUrl.isNotBlank()) {
                            val clip = ClipData.newPlainText("下载链接", historyWithRule.downloadUrl)
                            clipboardManager.setPrimaryClip(clip)
                            Toast.makeText(context, "已复制下载链接", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
        
        if (showAddDialog) {
            RuleEditDialog(
                onDismiss = { showAddDialog = false },
                onSave = { 
                    viewModel.addRule(it)
                    showAddDialog = false
                }
            )
        }
        
        editingRule?.let { rule ->
            RuleEditDialog(
                rule = rule,
                onDismiss = { editingRule = null },
                onSave = { 
                    viewModel.updateRule(it)
                    editingRule = null
                }
            )
        }
        
        if (showClearDialog) {
            AlertDialog(
                onDismissRequest = { showClearDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("清除历史") },
                text = { Text("确定要清除所有上传历史记录吗？") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.clearAllHistories()
                            showClearDialog = false
                        }
                    ) {
                        Text("确定", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showClearDialog = false }) {
                        Text("取消", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
        
        if (showImportDialog) {
            AlertDialog(
                onDismissRequest = { showImportDialog = false },
                containerColor = MaterialTheme.colorScheme.surface,
                title = { Text("导入默认规则") },
                text = { Text("将导入2个预置的网盘规则（喵公子网盘①、喵公子网盘②）。\n\n注意：如果已有规则，将不会重复导入。") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.importDefaultRules()
                            showImportDialog = false
                        }
                    ) {
                        Text("导入", color = MaterialTheme.colorScheme.primary)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showImportDialog = false }) {
                        Text("取消", color = MaterialTheme.colorScheme.primary)
                    }
                }
            )
        }
        
        uploadState.let { state ->
            when (state) {
                is UploadState.Testing -> {
                    AlertDialog(
                        onDismissRequest = { },
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = { Text("测试中") },
                        text = { 
                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(16.dp))
                                Text("正在测试上传规则...")
                            }
                        },
                        confirmButton = {}
                    )
                }
                is UploadState.TestSuccess -> {
                    AlertDialog(
                        onDismissRequest = { 
                            testingRule = null
                            viewModel.resetUploadState()
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = { Text("测试成功") },
                        text = { 
                            Column {
                                Text("下载链接：")
                                Spacer(modifier = Modifier.height(8.dp))
                                Surface(
                                    color = MaterialTheme.colorScheme.surfaceVariant,
                                    shape = MaterialTheme.shapes.small
                                ) {
                                    Text(
                                        text = state.downloadUrl,
                                        style = MaterialTheme.typography.bodySmall,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(
                                onClick = { 
                                    testingRule = null
                                    viewModel.resetUploadState()
                                }
                            ) {
                                Text("确定", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
                is UploadState.TestError -> {
                    AlertDialog(
                        onDismissRequest = { 
                            testingRule = null
                            viewModel.resetUploadState()
                        },
                        containerColor = MaterialTheme.colorScheme.surface,
                        title = { Text("测试失败") },
                        text = { Text(state.message) },
                        confirmButton = {
                            TextButton(
                                onClick = { 
                                    testingRule = null
                                    viewModel.resetUploadState()
                                }
                            ) {
                                Text("确定", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                }
                else -> {}
            }
        }
    }
}

@Composable
fun RuleListTab(
    rules: List<DirectLinkUploadRule>,
    onEdit: (DirectLinkUploadRule) -> Unit,
    onDelete: (DirectLinkUploadRule) -> Unit,
    onSetDefault: (DirectLinkUploadRule) -> Unit,
    onTest: (DirectLinkUploadRule) -> Unit,
    onCopy: (DirectLinkUploadRule) -> Unit
) {
    if (rules.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.CloudOff,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无上传规则",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "点击右上角 + 添加规则",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(rules) { rule ->
                RuleCard(
                    rule = rule,
                    onEdit = { onEdit(rule) },
                    onDelete = { onDelete(rule) },
                    onSetDefault = { onSetDefault(rule) },
                    onTest = { onTest(rule) },
                    onCopy = { onCopy(rule) }
                )
            }
        }
    }
}

@Composable
fun RuleCard(
    rule: DirectLinkUploadRule,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit,
    onTest: () -> Unit,
    onCopy: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }
    
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = rule.summary,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (rule.isDefault) {
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = "默认",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, "更多")
                    }
                    
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false },
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        DropdownMenuItem(
                            text = { Text("设为默认") },
                            onClick = { onSetDefault(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Star, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text("编辑") },
                            onClick = { onEdit(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.Edit, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text("测试") },
                            onClick = { onTest(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        DropdownMenuItem(
                            text = { Text("拷贝规则") },
                            onClick = { onCopy(); showMenu = false },
                            leadingIcon = { Icon(Icons.Default.ContentCopy, null, tint = MaterialTheme.colorScheme.primary) }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text("删除", color = MaterialTheme.colorScheme.error) },
                            onClick = { onDelete(); showMenu = false },
                            leadingIcon = { 
                                Icon(Icons.Default.Delete, null, tint = MaterialTheme.colorScheme.error) 
                            }
                        )
                    }
                }
            }
            
            if (rule.uploadCount > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "上传 ${rule.uploadCount} 次",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (rule.lastUsedTime > 0) {
                        Text(
                            text = formatTime(rule.lastUsedTime),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HistoryListTab(
    histories: List<UploadHistoryWithRule>,
    onDelete: (UploadHistoryWithRule) -> Unit,
    onCopy: (UploadHistoryWithRule) -> Unit
) {
    if (histories.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    modifier = Modifier.size(64.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "暂无上传历史",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            items(histories) { historyWithRule ->
                HistoryCard(
                    historyWithRule = historyWithRule,
                    onDelete = { onDelete(historyWithRule) },
                    onCopy = { onCopy(historyWithRule) }
                )
            }
        }
    }
}

@Composable
fun HistoryCard(
    historyWithRule: UploadHistoryWithRule,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    val history = historyWithRule.toUploadHistory()
    var showFullFileNameDialog by remember { mutableStateOf(false) }
    var showFullRuleSummaryDialog by remember { mutableStateOf(false) }

    if (showFullFileNameDialog) {
        AlertDialog(
            onDismissRequest = { showFullFileNameDialog = false },
            shape = RectangleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("完整名称") },
            text = {
                SelectionContainer {
                    Text(
                        text = history.fileName,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFullFileNameDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    if (showFullRuleSummaryDialog) {
        AlertDialog(
            onDismissRequest = { showFullRuleSummaryDialog = false },
            shape = RectangleShape,
            containerColor = MaterialTheme.colorScheme.surface,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            textContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            title = { Text("完整规则") },
            text = {
                SelectionContainer {
                    Text(
                        text = history.ruleSummary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showFullRuleSummaryDialog = false }) {
                    Text("确定")
                }
            }
        )
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .weight(1f)
                        .pointerInput(history.fileName) {
                            detectTapGestures(
                                onLongPress = { showFullFileNameDialog = true }
                            )
                        }
                ) {
                    Icon(
                        imageVector = if (history.success) Icons.Default.CheckCircle 
                                      else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (history.success) MaterialTheme.colorScheme.primary
                               else MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = history.fileName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val displayRuleSummary = historyWithRule.getDisplayRuleSummary()
                    if (displayRuleSummary.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = MaterialTheme.shapes.small,
                            modifier = Modifier.pointerInput(history.ruleSummary) {
                                detectTapGestures(
                                    onLongPress = { showFullRuleSummaryDialog = true }
                                )
                            }
                        ) {
                            Text(
                                text = displayRuleSummary,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    
                    if (!history.success) {
                        Surface(
                            color = MaterialTheme.colorScheme.errorContainer,
                            shape = MaterialTheme.shapes.small
                        ) {
                            Text(
                                text = "失败",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(
                    text = formatFileSize(history.fileSize),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (history.success) {
                    Text(
                        text = "耗时 ${history.duration}ms",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = formatDateTime(history.uploadTime),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            if (history.success && history.downloadUrl.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Link,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = history.downloadUrl,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            if (!history.success && !history.errorMsg.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = MaterialTheme.shapes.small
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = history.errorMsg,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                if (history.success && history.downloadUrl.isNotBlank()) {
                    TextButton(onClick = onCopy) {
                        Icon(Icons.Default.ContentCopy, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("复制链接", color = MaterialTheme.colorScheme.primary)
                    }
                }
                TextButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RuleEditDialog(
    rule: DirectLinkUploadRule? = null,
    onDismiss: () -> Unit,
    onSave: (DirectLinkUploadRule) -> Unit
) {
    var uploadUrl by remember { mutableStateOf(rule?.uploadUrl ?: "") }
    var downloadUrlRule by remember { mutableStateOf(rule?.downloadUrlRule ?: "") }
    var summary by remember { mutableStateOf(rule?.summary ?: "") }
    var compress by remember { mutableStateOf(rule?.compress ?: false) }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text(if (rule == null) "添加上传规则" else "编辑上传规则") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                OutlinedTextField(
                    value = uploadUrl,
                    onValueChange = { uploadUrl = it },
                    label = { Text("上传URL *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = downloadUrlRule,
                    onValueChange = { downloadUrlRule = it },
                    label = { Text("下载URL规则 *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                OutlinedTextField(
                    value = summary,
                    onValueChange = { summary = it },
                    label = { Text("注释说明 *") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 3,
                    maxLines = 6
                )
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = compress,
                        onCheckedChange = { compress = it }
                    )
                    Text("自动压缩文件")
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newRule = DirectLinkUploadRule(
                        id = rule?.id ?: 0,
                        uploadUrl = uploadUrl,
                        downloadUrlRule = downloadUrlRule,
                        summary = summary,
                        compress = compress,
                        isDefault = rule?.isDefault ?: false,
                        sortOrder = rule?.sortOrder ?: 0
                    )
                    onSave(newRule)
                },
                enabled = uploadUrl.isNotBlank() && 
                          downloadUrlRule.isNotBlank() && 
                          summary.isNotBlank()
            ) {
                Text("保存", color = MaterialTheme.colorScheme.primary)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("取消", color = MaterialTheme.colorScheme.primary)
            }
        }
    )
}

private fun formatTime(timestamp: Long): String {
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    
    return when {
        diff < 60_000 -> "刚刚"
        diff < 3600_000 -> "${diff / 60_000}分钟前"
        diff < 86400_000 -> "${diff / 3600_000}小时前"
        diff < 2592000_000 -> "${diff / 86400_000}天前"
        else -> SimpleDateFormat("MM-dd", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatDateTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

private fun formatFileSize(size: Long): String {
    return when {
        size < 1024 -> "$size B"
        size < 1024 * 1024 -> String.format("%.1f KB", size / 1024.0)
        size < 1024 * 1024 * 1024 -> String.format("%.1f MB", size / (1024.0 * 1024))
        else -> String.format("%.1f GB", size / (1024.0 * 1024 * 1024))
    }
}
