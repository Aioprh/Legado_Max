package io.legado.app.ui.debuglog.components

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DataObject
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.legado.app.model.debug.FlowLogItem
import io.legado.app.model.debug.FlowStage
import io.legado.app.model.debug.RuleExecutionTree
import io.legado.app.model.debug.RuleExecutionNode
import io.legado.app.model.debug.JsExecutionRecord
import io.legado.app.model.debug.RuleType
import io.legado.app.model.debug.VariableOperation
import io.legado.app.model.debug.VariableOperationType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun FlowLogDetailDialog(
    log: FlowLogItem,
    onDismiss: () -> Unit,
    onCopy: () -> Unit
) {
    var showSearch by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .heightIn(max = 600.dp),
            shape = MaterialTheme.shapes.large,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = getStageIcon(log.stage),
                                tint = getStageColor(log.stage),
                                contentDescription = null
                            )
                            Spacer(Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "流程日志详情",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = log.stage.displayName,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = getStageColor(log.stage)
                                )
                            }
                        }

                        Row {
                            IconButton(onClick = { showSearch = !showSearch }) {
                                Icon(Icons.Default.Search, contentDescription = "查找")
                            }
                            IconButton(onClick = onDismiss) {
                                Icon(Icons.Default.Close, contentDescription = "关闭")
                            }
                        }
                    }
                }

                if (showSearch) {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = { searchQuery = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            placeholder = { Text("在日志中查找...") },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null)
                            },
                            trailingIcon = {
                                if (searchQuery.isNotEmpty()) {
                                    IconButton(onClick = { searchQuery = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "清除")
                                    }
                                }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                            keyboardActions = KeyboardActions(onSearch = {})
                        )
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    DetailSection(title = "基本信息", searchQuery = searchQuery) {
                        DetailRow("时间", formatFullTime(log.startTime), searchQuery)
                        DetailRow("阶段", log.stage.displayName, searchQuery)
                        log.operation?.let { DetailRow("操作", it, searchQuery) }
                        log.sourceName?.let { DetailRow("书源", it, searchQuery) }
                        log.sourceUrl?.let { DetailRow("书源URL", it, searchQuery) }
                        if (log.duration != null && log.duration > 0) {
                            DetailRow("耗时", "${log.duration}ms", searchQuery)
                        }
                        log.ruleType?.let { 
                            DetailRow("规则类型", "${it.icon}${it.displayName}", searchQuery) 
                        }
                        log.matchCount?.let { 
                            DetailRow("匹配数量", "$it 个", searchQuery) 
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    DetailSection(title = "消息", searchQuery = searchQuery) {
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.small,
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = highlightText(log.message, searchQuery),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    if (log.url != null || log.method != null || log.statusCode != null) {
                        Spacer(Modifier.height(12.dp))
                        DetailSection(title = "请求信息", searchQuery = searchQuery) {
                            log.url?.let { DetailRow("URL", it, searchQuery) }
                            log.method?.let { DetailRow("方法", it, searchQuery) }
                            log.statusCode?.let { DetailRow("状态码", it.toString(), searchQuery) }
                        }
                    }

                    val hasRuleInfo = listOfNotNull(
                        log.rule, log.inputPreview, log.originalValue, log.outputPreview, log.result
                    ).any { it.isNotBlank() }
                    
                    if (hasRuleInfo) {
                        Spacer(Modifier.height(12.dp))
                        DetailSection(title = "规则信息", searchQuery = searchQuery) {
                            log.rule?.takeIf { it.isNotBlank() }?.let { rule ->
                                DetailRow("规则", rule, searchQuery)
                            }
                            log.inputPreview?.takeIf { it.isNotBlank() }?.let { input ->
                                DetailRow("输入", input, searchQuery)
                            }
                            log.originalValue?.takeIf { it.isNotBlank() }?.let { originalValue ->
                                DetailRow("原始数据", originalValue, searchQuery)
                            }
                            log.outputPreview?.takeIf { it.isNotBlank() }?.let { output ->
                                DetailRow("输出", output, searchQuery)
                            }
                            log.result?.takeIf { it.isNotBlank() }?.let { result ->
                                DetailRow("结果", result, searchQuery)
                            }
                        }
                    }

                    log.executionTree?.takeIf { it.root.children.isNotEmpty() }?.let { tree ->
                        Spacer(Modifier.height(12.dp))
                        DetailSection(title = "规则执行路径", searchQuery = searchQuery) {
                            RuleExecutionTreeView(tree, searchQuery)
                        }
                    }

                    log.jsExecution?.let { jsExec ->
                        Spacer(Modifier.height(12.dp))
                        DetailSection(title = "JS执行环境", searchQuery = searchQuery) {
                            JsExecutionView(jsExec, searchQuery)
                        }
                    }

                    if (log.variableOperations.isNotEmpty()) {
                        Spacer(Modifier.height(12.dp))
                        DetailSection(title = "变量操作", searchQuery = searchQuery) {
                            VariableOperationsView(log.variableOperations, searchQuery)
                        }
                    }

                    if (!log.detail.isNullOrBlank()) {
                        Spacer(Modifier.height(12.dp))
                        DetailSection(title = "详细内容", searchQuery = searchQuery) {
                            Text(
                                text = highlightText(log.detail ?: "", searchQuery),
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (log.error != null) {
                        Spacer(Modifier.height(12.dp))
                        DetailSection(title = "异常信息", searchQuery = searchQuery) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .horizontalScroll(rememberScrollState()),
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
                            ) {
                                Text(
                                    text = highlightText(
                                        log.error?.stackTraceToString() ?: "",
                                        searchQuery
                                    ),
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                    color = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.padding(12.dp)
                                )
                            }
                        }
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    tonalElevation = 2.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        OutlinedButton(onClick = onDismiss) {
                            Text("关闭")
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(onClick = onCopy) {
                            Icon(
                                Icons.Default.ContentCopy,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text("复制全部")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun RuleExecutionTreeView(
    tree: RuleExecutionTree,
    searchQuery: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DetailRow("完整规则", tree.fullRule, searchQuery)
        DetailRow("总耗时", tree.formatTotalDuration(), searchQuery)
        DetailRow("执行状态", if (tree.isSuccess()) "✅ 成功" else "❌ 失败", searchQuery)
        
        Spacer(Modifier.height(8.dp))
        Text(
            text = "执行步骤:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(4.dp))
        
        tree.root.children.forEach { node ->
            RuleExecutionNodeView(node, searchQuery, indent = 0)
        }
    }
}

@Composable
private fun RuleExecutionNodeView(
    node: RuleExecutionNode,
    searchQuery: String,
    indent: Int
) {
    val indentStr = "  ".repeat(indent)
    val prefix = if (indent == 0) "├──" else "│  ├──"
    
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (indent * 16).dp),
        shape = MaterialTheme.shapes.small,
        color = if (node.error != null) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.1f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${node.ruleType.icon} ${node.ruleType.displayName}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (node.error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
                node.duration?.let {
                    Text(
                        text = "${it}ms",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            
            if (node.ruleContent.isNotBlank()) {
                Text(
                    text = "规则: ${node.ruleContent.take(100)}${if (node.ruleContent.length > 100) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            node.input?.let { input ->
                Text(
                    text = "输入: ${input.take(50)}${if (input.length > 50) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            node.output?.let { output ->
                Text(
                    text = "输出: ${output.take(50)}${if (output.length > 50) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            node.matchCount?.let { count ->
                Text(
                    text = "匹配: $count 个",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            node.error?.let { error ->
                Text(
                    text = "错误: ${error.localizedMessage}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
            
            node.children.forEach { child ->
                Spacer(Modifier.height(4.dp))
                RuleExecutionNodeView(child, searchQuery, indent + 1)
            }
        }
    }
}

@Composable
private fun JsExecutionView(
    jsExec: JsExecutionRecord,
    searchQuery: String
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        DetailRow("执行状态", if (jsExec.isSuccess()) "✅ 成功" else "❌ 失败", searchQuery)
        jsExec.duration?.let {
            DetailRow("执行耗时", jsExec.formatDuration() ?: "${it}ms", searchQuery)
        }
        
        Spacer(Modifier.height(8.dp))
        Text(
            text = "JS代码:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(4.dp))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                text = jsExec.jsCode,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.padding(8.dp)
            )
        }
        
        Spacer(Modifier.height(8.dp))
        Text(
            text = "执行环境:",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(4.dp))
        
        val context = jsExec.context
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                context.result?.let {
                    EnvVarRow("result", it)
                }
                context.src?.let {
                    EnvVarRow("src", it)
                }
                context.baseUrl?.let {
                    EnvVarRow("baseUrl", it)
                }
                context.book?.let { book ->
                    EnvVarRow("book", "{name=\"${book.name}\", author=\"${book.author}\"}")
                }
                context.chapter?.let { chapter ->
                    EnvVarRow("chapter", "{title=\"${chapter.title}\", index=${chapter.index}}")
                }
                context.source?.let { source ->
                    EnvVarRow("source", "{name=\"${source.name}\"}")
                }
                context.nextChapterUrl?.let {
                    EnvVarRow("nextChapterUrl", it)
                }
                if (context.fromBookInfo) {
                    EnvVarRow("fromBookInfo", "true")
                }
                context.variables.forEach { (key, value) ->
                    EnvVarRow(key, value)
                }
            }
        }
        
        jsExec.result?.let { result ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "执行结果:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.outline
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ) {
                Text(
                    text = result,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
        
        jsExec.error?.let { error ->
            Spacer(Modifier.height(8.dp))
            Text(
                text = "执行错误:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error
            )
            Spacer(Modifier.height(4.dp))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.small,
                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            ) {
                Text(
                    text = error.localizedMessage ?: error.toString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun EnvVarRow(name: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "$name:",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.width(100.dp)
        )
        Text(
            text = value.take(100) + if (value.length > 100) "..." else "",
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DetailSection(
    title: String,
    searchQuery: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Text(
        text = highlightText(title, searchQuery),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp)
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            content()
        }
    }
}

@Composable
private fun DetailRow(
    label: String,
    value: String,
    searchQuery: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = highlightText(label, searchQuery),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.outline,
            modifier = Modifier.width(80.dp)
        )

        Text(
            text = highlightText(value, searchQuery),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
            overflow = TextOverflow.Ellipsis,
            maxLines = 5
        )
    }
}

@Composable
private fun getStageColor(stage: FlowStage) = when (stage) {
    FlowStage.NETWORK -> MaterialTheme.colorScheme.primary
    FlowStage.PARSE -> MaterialTheme.colorScheme.tertiary
    FlowStage.EXTRACT -> MaterialTheme.colorScheme.secondary
    FlowStage.REPLACE -> MaterialTheme.colorScheme.error
    FlowStage.VARIABLE -> MaterialTheme.colorScheme.tertiary
}

@Composable
private fun getStageIcon(stage: FlowStage) = when (stage) {
    FlowStage.NETWORK -> Icons.Default.Language
    FlowStage.PARSE -> Icons.Default.Code
    FlowStage.EXTRACT -> Icons.Default.DataObject
    FlowStage.REPLACE -> Icons.Default.SwapHoriz
    FlowStage.VARIABLE -> Icons.Default.Inventory
}

@Composable
private fun highlightText(text: String, query: String): androidx.compose.ui.text.AnnotatedString {
    if (query.isBlank() || !text.contains(query, ignoreCase = true)) {
        return buildAnnotatedString { append(text) }
    }

    return buildAnnotatedString {
        var startIndex = 0
        var foundIndex = text.indexOf(query, ignoreCase = true)

        while (foundIndex >= 0) {
            append(text.substring(startIndex, foundIndex))

            withStyle(
                SpanStyle(
                    background = Color.Yellow.copy(alpha = 0.3f),
                    fontWeight = FontWeight.Bold
                )
            ) {
                append(text.substring(foundIndex, foundIndex + query.length))
            }

            startIndex = foundIndex + query.length
            foundIndex = text.indexOf(query, startIndex, ignoreCase = true)
        }

        if (startIndex < text.length) {
            append(text.substring(startIndex))
        }
    }
}

private fun formatFullTime(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.getDefault()).format(Date(timestamp))
}

@Composable
private fun VariableOperationsView(
    operations: List<VariableOperation>,
    searchQuery: String
) {
    val readOps = operations.filter { it.operationType == VariableOperationType.READ }
    val writeOps = operations.filter { it.operationType == VariableOperationType.WRITE }
    val deleteOps = operations.filter { it.operationType == VariableOperationType.DELETE }
    
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "统计: 读${readOps.size}次, 写${writeOps.size}次, 删${deleteOps.size}次",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            Text(
                text = "涉及${operations.map { it.key }.toSet().size}个变量",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
        
        Spacer(Modifier.height(12.dp))
        
        if (writeOps.isNotEmpty()) {
            Text(
                text = "📤 写入操作:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            
            writeOps.forEach { op ->
                VariableOperationItem(op, searchQuery)
                Spacer(Modifier.height(4.dp))
            }
            
            Spacer(Modifier.height(8.dp))
        }
        
        if (readOps.isNotEmpty()) {
            Text(
                text = "📥 读取操作:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            
            readOps.forEach { op ->
                VariableOperationItem(op, searchQuery)
                Spacer(Modifier.height(4.dp))
            }
        }
        
        if (deleteOps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = "🗑️ 删除操作:",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.error,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            
            deleteOps.forEach { op ->
                VariableOperationItem(op, searchQuery)
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

@Composable
private fun VariableOperationItem(
    operation: VariableOperation,
    searchQuery: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.small,
        color = when (operation.operationType) {
            VariableOperationType.READ -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            VariableOperationType.WRITE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            VariableOperationType.DELETE -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        }
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = operation.key,
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.Monospace),
                    color = when (operation.operationType) {
                        VariableOperationType.READ -> MaterialTheme.colorScheme.secondary
                        VariableOperationType.WRITE -> MaterialTheme.colorScheme.primary
                        VariableOperationType.DELETE -> MaterialTheme.colorScheme.error
                    }
                )
                Text(
                    text = operation.storage.displayName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            
            operation.value?.let { value ->
                Text(
                    text = "值: ${value.take(100)}${if (value.length > 100) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
            
            operation.oldValue?.let { oldValue ->
                Text(
                    text = "原值: ${oldValue.take(50)}${if (oldValue.length > 50) "..." else ""}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
