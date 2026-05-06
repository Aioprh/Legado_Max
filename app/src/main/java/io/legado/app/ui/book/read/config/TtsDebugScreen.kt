package io.legado.app.ui.book.read.config

import android.annotation.SuppressLint
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.AppCompatSpinner
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.size
import androidx.compose.ui.unit.width
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.core.view.setPadding
import androidx.core.widget.addTextChangedListener
import com.google.android.flexbox.FlexboxLayout
import com.script.rhino.runScriptWithContext
import io.legado.app.R
import io.legado.app.constant.AppLog
import io.legado.app.data.appDb
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.rule.RowUi
import io.legado.app.databinding.ItemFilletTextBinding
import io.legado.app.databinding.ItemSourceEditBinding
import io.legado.app.databinding.ItemSelectorSingleBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import splitties.init.appCtx

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

class TtsDebugActivity : AppCompatActivity() {

    private var bgDrawable: Drawable? = null
    private var ttsId: Long = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        loadBackgroundImage()
        enableEdgeToEdge()
        
        ttsId = intent.getLongExtra("ttsId", 0)
        
        setContent {
            TtsDebugContent(
                bgDrawable = bgDrawable,
                ttsId = ttsId,
                onBackClick = { finish() }
            )
        }
    }

    @Suppress("DEPRECATION")
    private fun loadBackgroundImage() {
        try {
            val metrics = android.util.DisplayMetrics()
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                val windowMetrics = windowManager.currentWindowMetrics
                val bounds = windowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
            } else {
                windowManager.defaultDisplay.getMetrics(metrics)
            }
            bgDrawable = ThemeConfig.getBgImage(this, metrics)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun initTheme() {
        val theme = ThemeConfig.getTheme()
        when (theme) {
            io.legado.app.constant.Theme.Dark -> {
                setTheme(io.legado.app.R.style.AppTheme_Dark)
            }
            io.legado.app.constant.Theme.Light -> {
                setTheme(io.legado.app.R.style.AppTheme_Light)
            }
            else -> {
                if (ColorUtils.isColorLight(primaryColor)) {
                    setTheme(io.legado.app.R.style.AppTheme_Light)
                } else {
                    setTheme(io.legado.app.R.style.AppTheme_Dark)
                }
            }
        }
    }

    private fun setupSystemBar() {
        fullScreen()
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = ThemeStore.statusBarColor(this, isTransparentStatusBar)
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, true)
        setLightStatusBar(ColorUtils.isColorLight(backgroundColor))
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(ThemeStore.navigationBarColor(this))
        } else {
            val nbColor = ColorUtils.darkenColor(ThemeStore.navigationBarColor(this))
            setNavigationBarColorAuto(nbColor)
        }
    }
}

@Composable
fun TtsDebugContent(
    bgDrawable: Drawable?,
    ttsId: Long,
    onBackClick: () -> Unit
) {
    val context = LocalContext.current

    val primaryColorValue = remember { ThemeStore.primaryColor(context) }
    val accentColor = remember { ThemeStore.accentColor(context) }
    val bgColor = remember { ThemeStore.backgroundColor(context) }
    val textPrimaryColor = remember { ThemeStore.textColorPrimary(context) }
    val textSecondaryColor = remember { ThemeStore.textColorSecondary(context) }

    val isLight = ColorUtils.isColorLight(bgColor)
    val background = remember(bgColor) { Color(bgColor) }
    val primary = remember(primaryColorValue) { Color(primaryColorValue) }
    val secondary = remember(accentColor) { Color(accentColor) }
    val onBackground = remember(textPrimaryColor) { Color(textPrimaryColor) }
    val onBackgroundVariant = remember(textSecondaryColor) { Color(textSecondaryColor) }
    
    val surface = remember(background, isLight) {
        lerp(background, Color.White, if (isLight) 0.04f else 0.10f)
    }
    
    val surfaceVariant = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.05f else 0.14f)
    }
    
    val outline = remember(background, onBackground, isLight) {
        lerp(background, onBackground, if (isLight) 0.12f else 0.24f)
    }
    
    val pagePrimary = remember(primary, isLight) {
        if (isLight) primary else lerp(primary, Color.White, 0.20f)
    }
    
    val pageOnBackgroundVariant = remember(onBackgroundVariant, onBackground, isLight) {
        if (isLight) onBackgroundVariant else lerp(onBackgroundVariant, onBackground, 0.32f)
    }
    
    val pageSurfaceVariant = remember(surfaceVariant, onBackground, isLight) {
        if (isLight) surfaceVariant else lerp(surfaceVariant, onBackground, 0.08f)
    }

    val colorScheme = remember(
        isLight,
        pagePrimary,
        secondary,
        background,
        onBackground,
        pageOnBackgroundVariant,
        surface,
        pageSurfaceVariant,
        outline
    ) {
        if (isLight) {
            lightColorScheme(
                primary = pagePrimary,
                secondary = secondary,
                tertiary = secondary,
                background = background,
                surface = surface,
                surfaceVariant = pageSurfaceVariant,
                secondaryContainer = pageSurfaceVariant,
                tertiaryContainer = pageSurfaceVariant,
                outline = outline,
                outlineVariant = outline.copy(alpha = 0.75f),
                onPrimary = if (ColorUtils.isColorLight(primaryColorValue)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = pageOnBackgroundVariant,
                error = Color(0xFFE53935),
                onError = Color.White
            )
        } else {
            darkColorScheme(
                primary = pagePrimary,
                secondary = secondary,
                tertiary = secondary,
                background = background,
                surface = surface,
                surfaceVariant = pageSurfaceVariant,
                secondaryContainer = pageSurfaceVariant,
                tertiaryContainer = pageSurfaceVariant,
                outline = outline,
                outlineVariant = outline.copy(alpha = 0.8f),
                onPrimary = if (ColorUtils.isColorLight(primaryColorValue)) Color.Black else Color.White,
                onSecondary = if (ColorUtils.isColorLight(accentColor)) Color.Black else Color.White,
                onBackground = onBackground,
                onSurface = onBackground,
                onSurfaceVariant = pageOnBackgroundVariant,
                error = Color(0xFFFF5252),
                onError = Color.Black
            )
        }
    }

    MaterialTheme(colorScheme = colorScheme) {
        TtsDebugBoxWithBackground(
            bgDrawable = bgDrawable,
            bgColor = background
        ) {
            TtsDebugScreen(
                ttsId = ttsId,
                onBackClick = onBackClick
            )
        }
    }
}

@Composable
fun TtsDebugBoxWithBackground(
    bgDrawable: Drawable?,
    bgColor: Color,
    content: @Composable () -> Unit
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (bgDrawable != null) {
            val overlayAlpha = if (bgColor.luminance() > 0.5f) 0.22f else 0.40f
            
            Image(
                bitmap = bgDrawable.toBitmap().asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(bgColor.copy(alpha = overlayAlpha))
            )
        } else {
            Box(
                modifier = Modifier.fillMaxSize().background(bgColor)
            )
        }

        content()
    }
}

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
    
    val speakers = remember { mutableStateListOf<String>() }
    val loginInfo = remember { mutableStateMapOf<String, String>() }
    val rowUis = remember { mutableStateListOf<RowUi>() }
    
    LaunchedEffect(ttsId) {
        httpTTS = withContext(Dispatchers.IO) {
            appDb.httpTTSDao.get(ttsId)
        }
        
        httpTTS?.let { tts ->
            loginInfo.putAll(tts.getLoginInfoMap())
            
            tts.jsLib?.let { jsLib ->
                parseSpeakersFromJsLib(jsLib)?.let { speakerList ->
                    speakers.clear()
                    speakers.addAll(speakerList)
                    if (speakers.isNotEmpty() && selectedSpeaker.isEmpty()) {
                        selectedSpeaker = speakers[0]
                    }
                }
            }
            
            tts.loginUi?.let { loginUiStr ->
                val codeStr = loginUiStr.let {
                    when {
                        it.startsWith("@js:") -> it.substring(4)
                        it.startsWith("<js>") -> it.substring(4, it.lastIndexOf("<"))
                        else -> null
                    }
                }
                
                if (codeStr != null) {
                    withContext(Dispatchers.IO) {
                        try {
                            val loginUiJson = evalLoginUiJs(tts, codeStr, loginInfo.toMap())
                            val rows = GSON.fromJsonArray<RowUi>(loginUiJson).getOrNull()
                            rows?.let {
                                rowUis.clear()
                                rowUis.addAll(it)
                            }
                        } catch (e: Exception) {
                            AppLog.put("解析loginUi失败: ${e.message}", e)
                        }
                    }
                } else {
                    val rows = GSON.fromJsonArray<RowUi>(loginUiStr).getOrNull()
                    rows?.let {
                        rowUis.clear()
                        rowUis.addAll(it)
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
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            httpTTS?.let { tts ->
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(16.dp)
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
                
                if (rowUis.isNotEmpty()) {
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "登录配置",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            AndroidView(
                                factory = { ctx ->
                                    FlexboxLayout(ctx).apply {
                                        layoutParams = ViewGroup.LayoutParams(
                                            ViewGroup.LayoutParams.MATCH_PARENT,
                                            ViewGroup.LayoutParams.WRAP_CONTENT
                                        )
                                        
                                        rowUis.forEachIndexed { index, rowUi ->
                                            when (rowUi.type) {
                                                RowUi.Type.text, RowUi.Type.password -> {
                                                    val binding = ItemSourceEditBinding.inflate(
                                                        android.view.LayoutInflater.from(context),
                                                        this,
                                                        false
                                                    )
                                                    binding.root.id = index + 1000
                                                    binding.textInputLayout.hint = rowUi.viewName ?: rowUi.name
                                                    binding.editText.setText(loginInfo[rowUi.name] ?: rowUi.default ?: "")
                                                    
                                                    if (rowUi.type == RowUi.Type.password) {
                                                        binding.editText.inputType = InputType.TYPE_TEXT_VARIATION_PASSWORD or InputType.TYPE_CLASS_TEXT
                                                    }
                                                    
                                                    binding.editText.addTextChangedListener {
                                                        loginInfo[rowUi.name] = it?.toString() ?: ""
                                                    }
                                                    
                                                    addView(binding.root)
                                                }
                                                
                                                RowUi.Type.select -> {
                                                    val binding = ItemSelectorSingleBinding.inflate(
                                                        android.view.LayoutInflater.from(context),
                                                        this,
                                                        false
                                                    )
                                                    binding.root.id = index + 1000
                                                    binding.spName.text = rowUi.viewName ?: rowUi.name
                                                    
                                                    val chars = rowUi.chars?.filterNotNull() ?: listOf()
                                                    val adapter = ArrayAdapter(
                                                        context,
                                                        R.layout.item_text_common,
                                                        chars
                                                    )
                                                    adapter.setDropDownViewResource(R.layout.item_spinner_dropdown)
                                                    binding.spType.adapter = adapter
                                                    
                                                    val currentValue = loginInfo[rowUi.name] ?: rowUi.default
                                                    val selectedIndex = chars.indexOf(currentValue)
                                                    if (selectedIndex >= 0) {
                                                        binding.spType.setSelection(selectedIndex)
                                                    }
                                                    
                                                    binding.spType.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                                                        override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                                                            loginInfo[rowUi.name] = chars[position]
                                                        }
                                                        override fun onNothingSelected(parent: AdapterView<*>?) {}
                                                    }
                                                    
                                                    addView(binding.root)
                                                }
                                                
                                                RowUi.Type.button -> {
                                                    val binding = ItemFilletTextBinding.inflate(
                                                        android.view.LayoutInflater.from(context),
                                                        this,
                                                        false
                                                    )
                                                    binding.root.id = index + 1000
                                                    binding.textView.text = rowUi.viewName ?: rowUi.name
                                                    binding.textView.setPadding(16.dpToPx())
                                                    
                                                    binding.root.setOnClickListener {
                                                        coroutineScope.launch {
                                                            executeLoginAction(tts, rowUi.action, loginInfo.toMap())
                                                        }
                                                    }
                                                    
                                                    addView(binding.root)
                                                }
                                                
                                                RowUi.Type.toggle -> {
                                                    val binding = ItemFilletTextBinding.inflate(
                                                        android.view.LayoutInflater.from(context),
                                                        this,
                                                        false
                                                    )
                                                    binding.root.id = index + 1000
                                                    
                                                    val chars = rowUi.chars?.filterNotNull() ?: listOf()
                                                    var currentIndex = chars.indexOf(loginInfo[rowUi.name] ?: rowUi.default)
                                                    if (currentIndex < 0) currentIndex = 0
                                                    
                                                    fun updateText() {
                                                        val char = chars.getOrNull(currentIndex) ?: ""
                                                        val name = rowUi.viewName ?: rowUi.name
                                                        binding.textView.text = "$char $name"
                                                    }
                                                    
                                                    updateText()
                                                    binding.textView.setPadding(16.dpToPx())
                                                    
                                                    binding.root.setOnClickListener {
                                                        currentIndex = (currentIndex + 1) % chars.size
                                                        loginInfo[rowUi.name] = chars[currentIndex]
                                                        updateText()
                                                        
                                                        coroutineScope.launch {
                                                            executeLoginAction(tts, rowUi.action, loginInfo.toMap())
                                                        }
                                                    }
                                                    
                                                    addView(binding.root)
                                                }
                                            }
                                        }
                                    }
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(12.dp))
                }
                
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
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
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Surface(
                    color = containerColor,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.padding(horizontal = 16.dp)
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
                
                Spacer(modifier = Modifier.height(12.dp))
                
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            coroutineScope.launch {
                                isTesting = true
                                val startTime = System.currentTimeMillis()
                                
                                try {
                                    tts.putLoginInfo(GSON.toJson(loginInfo))
                                    
                                    val result = withContext(Dispatchers.IO) {
                                        testTtsEngine(
                                            tts = tts,
                                            text = testText,
                                            speed = speed,
                                            speaker = selectedSpeaker,
                                            pitch = pitch,
                                            loginInfo = loginInfo.toMap()
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
                    Spacer(modifier = Modifier.height(12.dp))
                    
                    Surface(
                        color = containerColor,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.padding(horizontal = 16.dp)
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
                                
                                result.audioUrl?.let { url ->
                                    IconButton(
                                        onClick = { context.sendToClip(url) }
                                    ) {
                                        Icon(Icons.Default.ContentCopy, contentDescription = "复制URL")
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
                            
                            Text(
                                text = "耗时: ${result.duration}ms",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            
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
                                    color = MaterialTheme.colorScheme.primary
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
                
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

suspend fun evalLoginUiJs(tts: HttpTTS, jsStr: String, loginInfo: Map<String, String>): String? {
    return try {
        runScriptWithContext {
            tts.evalJS(jsStr) {
                put("result", loginInfo.toMutableMap())
            }.toString()
        }
    } catch (e: Exception) {
        AppLog.put("evalLoginUiJs error: ${e.message}", e)
        null
    }
}

suspend fun executeLoginAction(tts: HttpTTS, action: String?, loginInfo: Map<String, String>) {
    if (action.isAbsUrl()) {
        appCtx.openUrl(action!!)
    } else if (action != null) {
        try {
            runScriptWithContext {
                tts.evalJS(action) {
                    put("result", loginInfo.toMutableMap())
                }
            }
        } catch (e: Exception) {
            AppLog.put("executeLoginAction error: ${e.message}", e)
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
    pitch: Int,
    loginInfo: Map<String, String>
): TtsTestResult {
    return try {
        val rhino = Context.enter()
        val scope: Scriptable = rhino.initStandardObjects()
        
        val result = mutableMapOf<String, Any>()
        result.putAll(loginInfo)
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
