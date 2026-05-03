package io.legado.app.ui.debug

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.StrResponse
import io.legado.app.help.http.newCallStrResponse
import io.legado.app.utils.sendToClip
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.RequestBody.Companion.toRequestBody

private val methods = listOf("GET", "POST")

private val uaNames = listOf(
    R.string.debug_ua_default,
    R.string.debug_ua_chrome_pc,
    R.string.debug_ua_chrome_mobile,
    R.string.debug_ua_safari_ios,
    R.string.debug_ua_firefox,
    R.string.debug_ua_custom
)

private fun getUaValues(): List<String> = listOf(
    "",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${BuildConfig.Cronet_Main_Version} Safari/537.36",
    "Mozilla/5.0 (Linux; Android 13; SM-G991B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/${BuildConfig.Cronet_Main_Version} Mobile Safari/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_0 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.0 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:121.0) Gecko/20100101 Firefox/121.0",
    ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HttpDebugScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val containerColor = debugToolsCardContainerColor()
    val topBarColor = debugToolsTopBarContainerColor()
    val coroutineScope = rememberCoroutineScope()
    
    val client = remember { OkHttpClient.Builder().build() }
    val uaValues = remember { getUaValues() }
    
    var url by remember { mutableStateOf("") }
    var headers by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }
    var methodIndex by remember { mutableStateOf(0) }
    var uaIndex by remember { mutableStateOf(0) }
    var customUa by remember { mutableStateOf("") }
    
    var responseHeaders by remember { mutableStateOf("") }
    var responseBody by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    
    var lastResponse by remember { mutableStateOf<StrResponse?>(null) }
    var lastRequestSrc by remember { mutableStateOf<String?>(null) }
    
    var showMenu by remember { mutableStateOf(false) }
    var showUaDialog by remember { mutableStateOf(false) }
    var showResponseSrcDialog by remember { mutableStateOf(false) }
    var showRequestSrcDialog by remember { mutableStateOf(false) }

    if (showUaDialog) {
        var tempUa by remember { mutableStateOf(customUa.ifEmpty { AppConfig.userAgent }) }
        AlertDialog(
            onDismissRequest = { 
                showUaDialog = false
                uaIndex = 0
            },
            title = { Text(stringResource(R.string.debug_ua_custom)) },
            text = {
                OutlinedTextField(
                    value = tempUa,
                    onValueChange = { tempUa = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.debug_user_agent)) }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    customUa = tempUa.trim()
                    showUaDialog = false
                }) {
                    Text(stringResource(android.R.string.ok))
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showUaDialog = false
                    uaIndex = 0
                }) {
                    Text(stringResource(android.R.string.cancel))
                }
            }
        )
    }

    if (showResponseSrcDialog && lastResponse != null) {
        val response = lastResponse!!
        val sb = StringBuilder()
        sb.append("=== 响应行 ===\n")
        sb.append("HTTP/1.1 ${response.code()} ${response.message()}\n\n")
        sb.append("=== 响应头 ===\n")
        response.raw.headers.forEach { (name, value) ->
            sb.append("$name: $value\n")
        }
        sb.append("\n=== 响应体 ===\n")
        sb.append(response.body)
        
        AlertDialog(
            onDismissRequest = { showResponseSrcDialog = false },
            title = { Text(stringResource(R.string.debug_response_src)) },
            text = {
                Text(
                    text = sb.toString(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showResponseSrcDialog = false }) {
                    Text(stringResource(android.R.string.ok))
                }
            }
        )
    }

    if (showRequestSrcDialog && lastRequestSrc != null) {
        AlertDialog(
            onDismissRequest = { showRequestSrcDialog = false },
            title = { Text(stringResource(R.string.debug_request_src)) },
            text = {
                Text(
                    text = lastRequestSrc!!,
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                )
            },
            confirmButton = {
                TextButton(onClick = { showRequestSrcDialog = false }) {
                    Text(stringResource(android.R.string.ok))
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
                    Text(
                        text = stringResource(R.string.debug_http_request),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "返回")
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
                                text = { Text(stringResource(R.string.debug_response_src)) },
                                onClick = {
                                    showMenu = false
                                    showResponseSrcDialog = true
                                },
                                enabled = lastResponse != null,
                                leadingIcon = {
                                    Icon(Icons.Default.Http, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.debug_request_src)) },
                                onClick = {
                                    showMenu = false
                                    showRequestSrcDialog = true
                                },
                                enabled = lastRequestSrc != null,
                                leadingIcon = {
                                    Icon(Icons.Default.Upload, contentDescription = null)
                                }
                            )
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("清空") },
                                onClick = {
                                    showMenu = false
                                    url = ""
                                    headers = ""
                                    body = ""
                                    responseHeaders = ""
                                    responseBody = ""
                                    lastResponse = null
                                    lastRequestSrc = null
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
            Surface(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        var methodExpanded by remember { mutableStateOf(false) }
                        
                        ExposedDropdownMenuBox(
                            expanded = methodExpanded,
                            onExpandedChange = { methodExpanded = !methodExpanded },
                            modifier = Modifier.width(100.dp)
                        ) {
                            OutlinedTextField(
                                value = methods[methodIndex],
                                onValueChange = {},
                                readOnly = true,
                                modifier = Modifier.menuAnchor(),
                                trailingIcon = {
                                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = methodExpanded)
                                }
                            )
                            
                            ExposedDropdownMenu(
                                expanded = methodExpanded,
                                onDismissRequest = { methodExpanded = false }
                            ) {
                                methods.forEachIndexed { index, method ->
                                    DropdownMenuItem(
                                        text = { Text(method) },
                                        onClick = {
                                            methodIndex = index
                                            methodExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        
                        OutlinedTextField(
                            value = url,
                            onValueChange = { url = it },
                            modifier = Modifier.weight(1f),
                            placeholder = { Text(stringResource(R.string.debug_url_hint)) },
                            singleLine = true
                        )
                    }
                }
            }

            Surface(
                color = containerColor,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    var uaExpanded by remember { mutableStateOf(false) }
                    
                    Text(
                        text = stringResource(R.string.debug_user_agent),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    ExposedDropdownMenuBox(
                        expanded = uaExpanded,
                        onExpandedChange = { uaExpanded = !uaExpanded }
                    ) {
                        OutlinedTextField(
                            value = stringResource(uaNames[uaIndex]),
                            onValueChange = {},
                            readOnly = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .menuAnchor(),
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = uaExpanded)
                            }
                        )
                        
                        ExposedDropdownMenu(
                            expanded = uaExpanded,
                            onDismissRequest = { uaExpanded = false }
                        ) {
                            uaNames.forEachIndexed { index, nameRes ->
                                DropdownMenuItem(
                                    text = { Text(stringResource(nameRes)) },
                                    onClick = {
                                        uaIndex = index
                                        uaExpanded = false
                                        if (index == uaNames.size - 1) {
                                            showUaDialog = true
                                        }
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
                    Text(
                        text = stringResource(R.string.debug_headers),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                    
                    Spacer(modifier = Modifier.height(8.dp))
                    
                    OutlinedTextField(
                        value = headers,
                        onValueChange = { headers = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 80.dp),
                        placeholder = { Text(stringResource(R.string.debug_headers_hint)) }
                    )
                }
            }

            if (methodIndex == 1) {
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = stringResource(R.string.debug_body),
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        OutlinedTextField(
                            value = body,
                            onValueChange = { body = it },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 80.dp)
                        )
                    }
                }
            }

            Button(
                onClick = {
                    if (url.isEmpty()) {
                        context.toastOnUi(R.string.debug_url_empty)
                        return@Button
                    }
                    
                    isLoading = true
                    responseBody = context.getString(R.string.debug_loading)
                    responseHeaders = ""
                    
                    coroutineScope.launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                doHttpRequest(
                                    client = client,
                                    url = url,
                                    methodIndex = methodIndex,
                                    headersText = headers,
                                    bodyText = body,
                                    uaIndex = uaIndex,
                                    uaValues = uaValues,
                                    customUa = customUa
                                )
                            }
                            
                            lastResponse = response
                            lastRequestSrc = buildRequestSrc(
                                url = url,
                                methodIndex = methodIndex,
                                headersText = headers,
                                bodyText = body,
                                userAgent = getSelectedUa(uaIndex, uaValues, customUa)
                            )
                            
                            val sb = StringBuilder()
                            sb.append("状态码: ${response.code()}\n")
                            sb.append("消息: ${response.message()}\n")
                            sb.append("耗时: ${response.raw.receivedResponseAtMillis - response.raw.sentRequestAtMillis}ms")
                            responseHeaders = sb.toString()
                            responseBody = response.body ?: ""
                            
                        } catch (e: Exception) {
                            responseBody = "错误: ${e.message}"
                        } finally {
                            isLoading = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = MaterialTheme.colorScheme.onPrimary,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(stringResource(R.string.debug_send))
            }

            if (responseHeaders.isNotEmpty()) {
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
                                text = stringResource(R.string.debug_response_headers),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { context.sendToClip(responseHeaders) }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = responseHeaders,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }

            if (responseBody.isNotEmpty()) {
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
                                text = stringResource(R.string.debug_response_body),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            IconButton(
                                onClick = { context.sendToClip(responseBody) }
                            ) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "复制")
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        Text(
                            text = responseBody,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 300.dp)
                        )
                    }
                }
            }
        }
    }
}

private fun getSelectedUa(uaIndex: Int, uaValues: List<String>, customUa: String): String {
    return when {
        uaIndex == 0 -> AppConfig.userAgent
        uaIndex == uaValues.size - 1 -> customUa.ifEmpty { AppConfig.userAgent }
        else -> uaValues[uaIndex]
    }
}

private suspend fun doHttpRequest(
    client: OkHttpClient,
    url: String,
    methodIndex: Int,
    headersText: String,
    bodyText: String,
    uaIndex: Int,
    uaValues: List<String>,
    customUa: String
): StrResponse {
    val userAgent = getSelectedUa(uaIndex, uaValues, customUa)
    
    return client.newCallStrResponse {
        url(url)
        when (methodIndex) {
            0 -> get()
            1 -> {
                if (bodyText.isNotEmpty()) {
                    val requestBody = bodyText.toRequestBody("application/json; charset=UTF-8".toMediaType())
                    post(requestBody)
                }
            }
        }
        addHeader("User-Agent", userAgent)
        if (headersText.isNotEmpty()) {
            parseHeaders(headersText).forEach { (key, value) ->
                if (!key.equals("User-Agent", ignoreCase = true)) {
                    addHeader(key, value)
                }
            }
        }
    }
}

private fun parseHeaders(headersText: String): Map<String, String> {
    val headers = mutableMapOf<String, String>()
    headersText.lines().forEach { line ->
        val parts = line.split(":", limit = 2)
        if (parts.size == 2) {
            headers[parts[0].trim()] = parts[1].trim()
        }
    }
    return headers
}

private fun buildRequestSrc(
    url: String,
    methodIndex: Int,
    headersText: String,
    bodyText: String,
    userAgent: String
): String {
    val sb = StringBuilder()
    sb.append("=== 请求行 ===\n")
    sb.append("${if (methodIndex == 0) "GET" else "POST"} $url\n\n")
    sb.append("=== 请求头 ===\n")
    sb.append("User-Agent: $userAgent\n")
    if (headersText.isNotEmpty()) {
        headersText.lines().forEach { line ->
            if (line.split(":").firstOrNull()?.trim()?.equals("User-Agent", ignoreCase = true) != true) {
                sb.append("$line\n")
            }
        }
    }
    if (methodIndex == 1 && bodyText.isNotEmpty()) {
        sb.append("Content-Type: application/json; charset=UTF-8\n")
        sb.append("\n=== 请求体 ===\n")
        sb.append(bodyText)
    }
    return sb.toString()
}
