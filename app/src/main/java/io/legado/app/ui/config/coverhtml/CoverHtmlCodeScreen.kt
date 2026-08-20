package io.legado.app.ui.config.coverhtml

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.content.Context
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.Image
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.createBitmap
import androidx.core.widget.doAfterTextChanged
import io.legado.app.R
import io.legado.app.help.DefaultData
import io.legado.app.help.config.CoverHtmlTemplateConfig
import io.legado.app.constant.EventBus
import io.legado.app.model.BookCover
import io.legado.app.ui.widget.code.CodeView
import io.legado.app.ui.widget.code.addHtmlPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.ui.widget.components.dialog.AppConfirmDialog
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.postEvent
import io.legado.app.utils.toastOnUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * 封面HTML代码编辑器屏幕
 * 
 * 提供HTML模板编辑、实时预览、保存等功能
 * 支持新建模板和编辑已有模板两种模式
 * 
 * @param template 当前编辑的模板，为空表示新建
 * @param isNewTemplate 是否为新建模板模式
 * @param onBackClick 返回点击回调
 * @param onShowTemplateList 显示模板列表回调
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CoverHtmlCodeScreen(
    template: CoverHtmlTemplateConfig.Template?,
    isNewTemplate: Boolean,
    onBackClick: () -> Unit,
    onShowTemplateList: () -> Unit
) {
    val context = LocalContext.current
    val containerColor = coverHtmlCardContainerColor()
    val topBarColor = coverHtmlTopBarContainerColor()
    
    //region 状态管理
    /** 模板名称 */
    var templateName by remember { mutableStateOf("") }
    /** HTML代码内容 */
    var htmlCode by remember { mutableStateOf("") }
    /** 预览用书名 */
    var bookName by remember { mutableStateOf("示例书名") }
    /** 预览用作者 */
    var author by remember { mutableStateOf("示例作者") }
    
    /** 原始模板名称（用于判断是否有修改） */
    var originalName by remember { mutableStateOf("") }
    /** 原始HTML代码（用于判断是否有修改） */
    var originalHtmlCode by remember { mutableStateOf("") }
    
    /** 当前编辑的模板 */
    var currentTemplate by remember { mutableStateOf(template) }
    /** 当前是否为新建模板模式 */
    var currentIsNewTemplate by remember { mutableStateOf(isNewTemplate) }
    
    /** 显示保存确认对话框 */
    var showSaveDialog by remember { mutableStateOf(false) }
    /** 待切换的模板（用于未保存时切换模板） */
    var pendingTemplateSwitch by remember { mutableStateOf<CoverHtmlTemplateConfig.Template?>(null) }
    
    /** CodeView引用 */
    var codeView by remember { mutableStateOf<CodeView?>(null) }
    /** 预览版本号（用于触发重新渲染） */
    var previewVersion by remember { mutableIntStateOf(0) }
    /** 预览位图 */
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    //endregion
    
    //region 初始化逻辑
    LaunchedEffect(currentTemplate, currentIsNewTemplate) {
        if (currentIsNewTemplate) {
            // 新建模板：使用默认模板代码
            templateName = ""
            htmlCode = DefaultData.coverHtmlTemplate
            originalName = ""
            originalHtmlCode = DefaultData.coverHtmlTemplate
        } else {
            // 编辑模板：加载已有模板数据
            val t = currentTemplate ?: CoverHtmlTemplateConfig.getSelectedTemplate()
            templateName = t.name
            htmlCode = t.htmlCode
            originalName = t.name
            originalHtmlCode = t.htmlCode
            currentTemplate = t
        }
    }
    //endregion
    
    //region 业务逻辑方法

    /**
     * 触发封面预览渲染
     * 通过增加版本号触发LaunchedEffect重新渲染
     */
    fun previewCover() {
        previewVersion++
    }
    
    /**
     * 保存当前模板
     * 新建模式下创建新模板，编辑模式下更新已有模板
     */
    fun doSaveTemplate() {
        if (htmlCode.isNotBlank()) {
            if (currentIsNewTemplate) {
                // 创建新模板
                val newTemplate = CoverHtmlTemplateConfig.Template(
                    id = CoverHtmlTemplateConfig.generateId(),
                    name = templateName.ifEmpty { "未命名模板" },
                    htmlCode = htmlCode,
                    isSelected = true
                )
                CoverHtmlTemplateConfig.addTemplate(newTemplate)
                CoverHtmlTemplateConfig.setSelectedTemplate(newTemplate.id)
                currentTemplate = newTemplate
                currentIsNewTemplate = false
            } else {
                // 更新已有模板
                val existingTemplate = currentTemplate?.copy(
                    name = templateName.ifEmpty { "未命名模板" },
                    htmlCode = htmlCode
                )
                if (existingTemplate != null) {
                    CoverHtmlTemplateConfig.updateTemplate(existingTemplate)
                    currentTemplate = existingTemplate
                }
            }
            originalName = templateName
            originalHtmlCode = htmlCode
            // 清除缓存并通知刷新
            CoverImageView.clearHtmlCoverCache()
            postEvent(EventBus.COVER_HTML_TEMPLATE_CHANGED, "")
            postEvent(EventBus.BOOKSHELF_REFRESH, "")
        }
    }
    //endregion
    
    //region 未保存确认对话框
    if (showSaveDialog && pendingTemplateSwitch != null) {
        AppConfirmDialog(
            title = stringResource(R.string.cover_html_save_changes),
            text = stringResource(R.string.cover_html_unsaved_hint),
            confirmText = stringResource(R.string.action_save),
            dismissText = stringResource(R.string.discard),
            onConfirm = {
                doSaveTemplate()
                currentTemplate = pendingTemplateSwitch
                currentIsNewTemplate = false
                showSaveDialog = false
                pendingTemplateSwitch = null
            },
            onDismissRequest = {
                currentTemplate = pendingTemplateSwitch
                currentIsNewTemplate = false
                showSaveDialog = false
                pendingTemplateSwitch = null
            }
        )
    }
    //endregion

    //region 预览渲染逻辑
    // 模板切换时自动触发预览
    LaunchedEffect(currentTemplate, currentIsNewTemplate) {
        previewCover()
    }

    // 根据版本号渲染预览图
    LaunchedEffect(previewVersion) {
        previewBitmap = null
        if (htmlCode.isNotBlank()) {
            val renderedHtml = BookCover.renderHtmlTemplate(htmlCode, bookName, author)
            previewBitmap = renderCoverPreviewBitmap(context, renderedHtml)
        }
    }
    //endregion
    
    //region UI布局
    Scaffold(
        containerColor = androidx.compose.ui.graphics.Color.Transparent,
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
                        text = stringResource(R.string.cover_html_code),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.Default.Close, contentDescription = "关闭")
                    }
                },
                actions = {
                    IconButton(onClick = onShowTemplateList) {
                        Icon(Icons.Default.Sort, contentDescription = "模板列表")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 可滚动内容区域
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp)
            ) {
                // 模板名称输入
                OutlinedTextField(
                    value = templateName,
                    onValueChange = { templateName = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.cover_html_template_name)) },
                    singleLine = true
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 预览区域标题
                Text(
                    text = stringResource(R.string.cover_html_preview),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 预览参数输入
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = bookName,
                        onValueChange = { bookName = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.cover_html_book_name)) },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = author,
                        onValueChange = { author = it },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.cover_html_author)) },
                        singleLine = true
                    )
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 预览按钮
                TextButton(onClick = { previewCover() }) {
                    Text(stringResource(R.string.cover_html_preview))
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // 封面预览图
                Box(
                    modifier = Modifier
                        .size(180.dp, 270.dp)
                        .align(Alignment.CenterHorizontally)
                        .clip(RoundedCornerShape(4.dp))
                        .background(androidx.compose.ui.graphics.Color.White),
                    contentAlignment = Alignment.Center
                ) {
                    previewBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = stringResource(R.string.cover_html_preview),
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } ?: CircularProgressIndicator()
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // 代码编辑区域标题
                Text(
                    text = stringResource(R.string.html_code),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                // HTML代码编辑器
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp),
                    color = containerColor,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    AndroidView(
                        factory = { ctx ->
                            CodeView(ctx).apply {
                                addHtmlPattern()
                                addJsPattern()
                                setPadding(16, 16, 16, 16)
                                doAfterTextChanged { text ->
                                    val newCode = text?.toString().orEmpty()
                                    if (newCode != htmlCode) {
                                        htmlCode = newCode
                                    }
                                }
                                codeView = this
                            }
                        },
                        update = { view ->
                            if (view.text.toString() != htmlCode) {
                                view.setText(htmlCode)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 200.dp)
                    )
                }
            }
            
            // 底部操作栏
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // 恢复默认按钮
                    TextButton(onClick = {
                        templateName = ""
                        htmlCode = DefaultData.coverHtmlTemplate
                        codeView?.setText(DefaultData.coverHtmlTemplate)
                    }) {
                        Text(stringResource(R.string.btn_default_s))
                    }
                    
                    Row {
                        // 取消按钮
                        TextButton(onClick = onBackClick) {
                            Text(
                                stringResource(R.string.cancel),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        // 保存按钮
                        TextButton(onClick = {
                            if (htmlCode.isBlank()) {
                                context.toastOnUi(R.string.cover_html_code_empty)
                            } else {
                                doSaveTemplate()
                                onBackClick()
                            }
                        }) {
                            Text(stringResource(R.string.ok))
                        }
                    }
                }
            }
        }
    }
    //endregion
}

/**
 * 渲染封面预览位图
 * 
 * 使用WebView渲染HTML并转换为Bitmap用于预览
 * 
 * @param context 上下文
 * @param html 要渲染的HTML内容
 * @return 渲染后的位图，失败返回null
 */
private suspend fun renderCoverPreviewBitmap(context: Context, html: String): Bitmap? {
    val renderWidth = 600
    val renderHeight = 900
    return withContext(Dispatchers.Main) {
        var webView: WebView? = null
        try {
            var renderComplete = false
            webView = WebView(context).apply {
                settings.javaScriptEnabled = true
                settings.useWideViewPort = false
                settings.loadWithOverviewMode = false
                settings.setSupportZoom(false)
                settings.displayZoomControls = false
                setInitialScale(100)
                setBackgroundColor(Color.WHITE)
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        view?.postDelayed({
                            renderComplete = true
                        }, 300)
                    }
                }
            }
            webView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(renderWidth, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(renderHeight, android.view.View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, renderWidth, renderHeight)
            webView.loadDataWithBaseURL("about:blank", html, "text/html", "UTF-8", null)

            // 等待渲染完成，最多等待2秒
            var attempts = 0
            while (!renderComplete && attempts < 40) {
                delay(50)
                attempts++
            }

            // 重新测量并绘制到Bitmap
            webView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(renderWidth, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(renderHeight, android.view.View.MeasureSpec.EXACTLY)
            )
            webView.layout(0, 0, renderWidth, renderHeight)
            createBitmap(renderWidth, renderHeight).also { bitmap ->
                webView.draw(Canvas(bitmap))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        } finally {
            // 清理WebView资源
            try {
                webView?.stopLoading()
                webView?.destroy()
            } catch (_: Exception) {
            }
        }
    }
}

//region Preview

/**
 * CoverHtmlCodeScreen 预览 - 新建模板模式
 */
@Preview(showBackground = true)
@Composable
private fun CoverHtmlCodeScreenNewPreview() {
    MaterialTheme {
        CoverHtmlCodeScreen(
            template = null,
            isNewTemplate = true,
            onBackClick = {},
            onShowTemplateList = {}
        )
    }
}

/**
 * CoverHtmlCodeScreen 预览 - 编辑模板模式
 */
@Preview(showBackground = true)
@Composable
private fun CoverHtmlCodeScreenEditPreview() {
    MaterialTheme {
        CoverHtmlCodeScreen(
            template = CoverHtmlTemplateConfig.Template(
                id = "preview_id",
                name = "预览模板",
                htmlCode = "<html><body><h1>{{bookName}}</h1><p>{{author}}</p></body></html>",
                isSelected = true
            ),
            isNewTemplate = false,
            onBackClick = {},
            onShowTemplateList = {}
        )
    }
}
//endregion
