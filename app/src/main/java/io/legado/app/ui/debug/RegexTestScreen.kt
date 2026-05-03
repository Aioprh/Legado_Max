package io.legado.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RegexTestScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val containerColor = debugToolsCardContainerColor()
    val topBarColor = debugToolsTopBarContainerColor()
    
    var pattern by remember { mutableStateOf("") }
    var input by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var highlightedText by remember { mutableStateOf<AnnotatedString?>(null) }
    
    var ignoreCase by remember { mutableStateOf(false) }
    var multiline by remember { mutableStateOf(false) }
    var dotAll by remember { mutableStateOf(false) }

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
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
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
                    
                    OutlinedTextField(
                        value = pattern,
                        onValueChange = { pattern = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.debug_regex_pattern_hint)) },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
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
                            val regexOptions = mutableSetOf<RegexOption>()
                            if (ignoreCase) regexOptions.add(RegexOption.IGNORE_CASE)
                            if (multiline) regexOptions.add(RegexOption.MULTILINE)
                            if (dotAll) regexOptions.add(RegexOption.DOT_MATCHES_ALL)
                            
                            val regex = Regex(pattern, regexOptions)
                            val matches = regex.findAll(input).toList()
                            
                            if (matches.isEmpty()) {
                                result = context.getString(R.string.debug_no_match)
                                highlightedText = null
                                return@Button
                            }
                            
                            val sb = StringBuilder()
                            matches.forEachIndexed { index, match ->
                                sb.append("匹配 ${index + 1}:\n")
                                sb.append("  完整匹配: ${match.value}\n")
                                match.groupValues.forEachIndexed { groupIndex, groupValue ->
                                    if (groupIndex > 0) {
                                        sb.append("  分组 $groupIndex: $groupValue\n")
                                    }
                                }
                                sb.append("\n")
                            }
                            result = sb.toString()
                            
                            val highlightColor = Color(0x40FFEB3B)
                            highlightedText = buildAnnotatedString {
                                var lastIndex = 0
                                val sortedMatches = matches.sortedBy { it.range.first }
                                
                                for (match in sortedMatches) {
                                    if (match.range.first > lastIndex) {
                                        append(input.substring(lastIndex, match.range.first))
                                    }
                                    withStyle(style = SpanStyle(background = highlightColor)) {
                                        append(match.value)
                                    }
                                    lastIndex = match.range.last + 1
                                }
                                
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
