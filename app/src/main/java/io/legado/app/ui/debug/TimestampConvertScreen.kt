package io.legado.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val formats = listOf(
    "yyyy-MM-dd HH:mm:ss",
    "yyyy-MM-dd",
    "yyyy/MM/dd HH:mm:ss",
    "yyyy/MM/dd",
    "MM-dd HH:mm:ss",
    "HH:mm:ss",
    "yyyyMMddHHmmss",
    "yyyyMMdd"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimestampConvertScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val containerColor = debugToolsCardContainerColor()
    val topBarColor = debugToolsTopBarContainerColor()
    
    var timestamp by remember { mutableStateOf("") }
    var dateStr by remember { mutableStateOf("") }
    var result by remember { mutableStateOf("") }
    var currentFormatIndex by remember { mutableStateOf(0) }
    var formatExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        val now = System.currentTimeMillis()
        timestamp = now.toString()
        result = formatTimestamp(now)
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
                        text = stringResource(R.string.debug_timestamp),
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = stringResource(R.string.debug_timestamp_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { context.sendToClip(timestamp) }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = timestamp,
                        onValueChange = { timestamp = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.debug_timestamp_hint)) },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            if (timestamp.isEmpty()) {
                                context.toastOnUi(R.string.input_is_empty)
                                return@Button
                            }
                            
                            try {
                                var ts = timestamp.trim().toLong()
                                if (timestamp.trim().length == 10) {
                                    ts *= 1000
                                }
                                result = formatTimestamp(ts)
                            } catch (e: Exception) {
                                result = "错误: ${e.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.debug_timestamp_to_date))
                    }
                }
            }

            Surface(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "日期格式",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = formatExpanded,
                        onExpandedChange = { formatExpanded = !formatExpanded }
                    ) {
                        OutlinedTextField(
                            value = formats[currentFormatIndex],
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = formatExpanded)
                            }
                        )
                        
                        ExposedDropdownMenu(
                            expanded = formatExpanded,
                            onDismissRequest = { formatExpanded = false }
                        ) {
                            formats.forEachIndexed { index, format ->
                                DropdownMenuItem(
                                    text = { Text(format) },
                                    onClick = {
                                        currentFormatIndex = index
                                        formatExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

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
                            text = stringResource(R.string.debug_date_title),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        IconButton(
                            onClick = { context.sendToClip(dateStr) }
                        ) {
                            Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = dateStr,
                        onValueChange = { dateStr = it },
                        modifier = Modifier.fillMaxWidth(),
                        placeholder = { Text(stringResource(R.string.debug_date_hint)) },
                        singleLine = true
                    )
                    
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Button(
                        onClick = {
                            if (dateStr.isEmpty()) {
                                context.toastOnUi(R.string.input_is_empty)
                                return@Button
                            }
                            
                            try {
                                val format = SimpleDateFormat(formats[currentFormatIndex], Locale.getDefault())
                                format.timeZone = TimeZone.getDefault()
                                val date = format.parse(dateStr.trim())
                                date?.let {
                                    val ts = it.time
                                    timestamp = ts.toString()
                                    result = formatTimestamp(ts)
                                }
                            } catch (e: Exception) {
                                result = "错误: ${e.message}"
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.debug_date_to_timestamp))
                    }
                }
            }

            OutlinedButton(
                onClick = {
                    val now = System.currentTimeMillis()
                    timestamp = now.toString()
                    result = formatTimestamp(now)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Schedule, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.debug_now))
            }

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
                        text = result.ifEmpty { "暂无结果" },
                        style = MaterialTheme.typography.titleMedium,
                        color = if (result.isEmpty()) 
                            MaterialTheme.colorScheme.onSurfaceVariant 
                        else 
                            MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

private fun formatTimestamp(timestamp: Long): String {
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
    sdf.timeZone = TimeZone.getDefault()
    return sdf.format(date)
}
