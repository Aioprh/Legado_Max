package io.legado.app.ui.urlRecord

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.viewmodel.compose.viewModel
import io.legado.app.data.entities.UrlRecord
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UrlRecordScreen(
    viewModel: UrlRecordViewModel = viewModel(),
    onBackClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val domains by viewModel.domains.collectAsState()
    val recordCount by viewModel.recordCount.collectAsState()
    val isRecordEnabled by viewModel.isRecordEnabled.collectAsState()
    
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDomainFilter by remember { mutableStateOf(false) }
    var showClearDialog by remember { mutableStateOf<Int?>(null) }
    var showMenu by remember { mutableStateOf(false) }
    
    val scrollBehavior = TopAppBarDefaults.enterAlwaysScrollBehavior()
    val containerColor = urlRecordCardContainerColor()
    val topBarColor = urlRecordTopBarContainerColor()
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(searchQuery, viewModel.currentDomain) {
        viewModel.setSearchQuery(searchQuery.ifBlank { null })
    }

    if (showClearDialog != null) {
        val days = showClearDialog!!
        AlertDialog(
            onDismissRequest = { showClearDialog = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (days == 0) "清除所有记录" else "清除${days}天前的记录") },
            text = {
                Text(if (days == 0) "确定要清除所有URL访问记录吗？" else "确定要清除${days}天前的记录吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            if (days == 0) {
                                viewModel.clearAll()
                            } else {
                                viewModel.deleteOldRecords(days)
                            }
                        }
                        showClearDialog = null
                    }
                ) {
                    Text("确定", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = null }) {
                    Text("取消")
                }
            }
        )
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
                    Column {
                        Text(
                            text = "URL访问记录",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                        if (recordCount > 0) {
                            Text(
                                text = "共 $recordCount 条记录",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showSearch = !showSearch }) {
                        Icon(Icons.Default.Search, contentDescription = "搜索")
                    }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, contentDescription = "更多")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            DropdownMenuItem(
                                text = { Text("按域名筛选") },
                                onClick = {
                                    showDomainFilter = !showDomainFilter
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.FilterList, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Text("开启URL记录")
                                        Spacer(Modifier.weight(1f))
                                        if (isRecordEnabled) {
                                            Icon(
                                                Icons.Default.Check,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                },
                                onClick = {
                                    viewModel.setRecordUrl(!isRecordEnabled)
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        if (isRecordEnabled) Icons.Default.ToggleOn 
                                        else Icons.Default.ToggleOff,
                                        contentDescription = null
                                    )
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("清除7天前的记录") },
                                onClick = {
                                    showClearDialog = 7
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("清除30天前的记录") },
                                onClick = {
                                    showClearDialog = 30
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(Icons.Default.DeleteSweep, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Text("清除所有记录", color = MaterialTheme.colorScheme.error) 
                                },
                                onClick = {
                                    showClearDialog = 0
                                    showMenu = false
                                },
                                leadingIcon = {
                                    Icon(
                                        Icons.Default.DeleteForever, 
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error
                                    )
                                }
                            )
                        }
                    }
                },
                scrollBehavior = scrollBehavior
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            AnimatedVisibility(visible = showSearch) {
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("搜索URL/域名/来源") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "清除")
                            }
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = containerColor,
                        unfocusedContainerColor = containerColor,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline
                    ),
                    singleLine = true
                )
            }

            AnimatedVisibility(visible = showDomainFilter && domains.isNotEmpty()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ) {
                    LazyColumn(
                        modifier = Modifier.heightIn(max = 200.dp),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        item {
                            DomainFilterItem(
                                domain = "全部",
                                isSelected = viewModel.currentDomain == null,
                                onClick = {
                                    viewModel.filterByDomain(null)
                                    showDomainFilter = false
                                }
                            )
                        }
                        items(domains) { domain ->
                            DomainFilterItem(
                                domain = domain,
                                isSelected = viewModel.currentDomain == domain,
                                onClick = {
                                    viewModel.filterByDomain(domain)
                                    showDomainFilter = false
                                }
                            )
                        }
                    }
                }
            }

            when (val state = uiState) {
                is UrlRecordUIState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                is UrlRecordUIState.Empty -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Link,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = "暂无URL访问记录",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "开启URL记录后，所有网络请求都会被记录",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                is UrlRecordUIState.Error -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = Icons.Default.Error,
                                contentDescription = null,
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                text = state.message,
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
                is UrlRecordUIState.Success -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(vertical = 8.dp)
                    ) {
                        items(state.records, key = { it.id }) { record ->
                            UrlRecordItem(record = record)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DomainFilterItem(
    domain: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = domain,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isSelected) MaterialTheme.colorScheme.primary 
                    else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun UrlRecordItem(record: UrlRecord) {
    val dateFormat = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault()) }
    val containerColor = urlRecordCardContainerColor()

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        color = containerColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                MethodBadge(method = record.method)
                Spacer(modifier = Modifier.width(8.dp))
                StatusBadge(
                    responseCode = record.responseCode,
                    errorMsg = record.errorMsg
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${record.duration}ms",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = dateFormat.format(Date(record.timestamp)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = record.domain,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = Color(0xFF009688)
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = record.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            record.sourceName?.let { source ->
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFF9800).copy(alpha = 0.15f),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = source,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color(0xFFFF9800),
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun MethodBadge(method: String) {
    val isPost = method.equals("POST", ignoreCase = true)
    Surface(
        color = if (isPost) Color(0xFF9C27B0).copy(alpha = 0.15f) 
                else Color(0xFF2196F3).copy(alpha = 0.15f),
        shape = RoundedCornerShape(4.dp)
    ) {
        Text(
            text = method.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = if (isPost) Color(0xFF9C27B0) else Color(0xFF2196F3),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun StatusBadge(responseCode: Int, errorMsg: String?) {
    val (color, text) = when {
        errorMsg != null -> Color(0xFFF44336) to "错误"
        responseCode in 200..299 -> Color(0xFF4CAF50) to "$responseCode"
        else -> Color(0xFFFF9800) to "$responseCode"
    }
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Medium,
        color = color
    )
}

@Composable
fun urlRecordCardContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val alpha = if (background.luminance() > 0.5f) 0.9f else 0.9f
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

@Composable
fun urlRecordTopBarContainerColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val alpha = if (background.luminance() > 0.5f) 0.82f else 0.94f
    return MaterialTheme.colorScheme.surface.copy(alpha = alpha)
}

private fun Color.luminance(): Float {
    return (0.299f * red + 0.587f * green + 0.114f * blue)
}
