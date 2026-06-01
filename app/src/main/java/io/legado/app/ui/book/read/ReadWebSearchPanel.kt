package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.net.http.SslError
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.webkit.SslErrorHandler
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import io.legado.app.R
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebViewPool
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefString
import io.legado.app.utils.gone
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefString
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import java.net.URLEncoder
import kotlin.math.roundToInt

class ReadWebSearchPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    data class SearchEngine(
        val title: String = "",
        val url: String = ""
    )

    private val sheet = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(context.backgroundColor)
        isClickable = true
    }
    private val handle = View(context).apply {
        setBackgroundColor(Color.argb(96, 128, 128, 128))
    }
    private val searchEdit = EditText(context).apply {
        setSingleLine(true)
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setTextColor(context.primaryTextColor)
        setHintTextColor(Color.GRAY)
        hint = context.getString(R.string.web_search)
        textSize = 16f
        setPadding(14.dpToPx(), 0, 14.dpToPx(), 0)
        setBackgroundColor(Color.argb(18, 128, 128, 128))
    }
    private val backButton = ImageButton(context).apply {
        setImageResource(R.drawable.ic_arrow_back)
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = "返回"
        setOnClickListener {
            if (canGoBack()) {
                goBack()
            }
        }
    }
    private val moreButton = ImageButton(context).apply {
        setImageResource(R.drawable.ic_more_vert)
        setBackgroundColor(Color.TRANSPARENT)
        contentDescription = "更多"
        setOnClickListener { showMoreMenu() }
    }
    private val engineRow = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        setPadding(12.dpToPx(), 6.dpToPx(), 12.dpToPx(), 8.dpToPx())
    }
    private val progressBar = ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
        max = 100
        progress = 0
        gone()
    }
    private var pooledWebView: PooledWebView? = null
    private val webView: WebView
        get() = pooledWebView!!.realWebView
    private var engines = loadEngines(context)
    private var selectedEngineIndex = 0
    private var startRawY = 0f
    private var startHeight = 0
    private val collapsedRatio = 0.58f
    private val expandedRatio = 0.92f
    private val minRatioBeforeDismiss = 0.35f

    init {
        visibility = GONE
        setBackgroundColor(Color.TRANSPARENT)
        setOnClickListener { close() }
        addView(
            sheet,
            LayoutParams(LayoutParams.MATCH_PARENT, 0, Gravity.BOTTOM)
        )
        buildSheet()
    }

    fun open(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return
        }
        ensureWebView()
        selectedEngineIndex = defaultEngineIndex(context, engines)
        visible()
        bringToFront()
        setSheetHeight((resources.displayMetrics.heightPixels * collapsedRatio).roundToInt())
        searchEdit.setText(normalizedQuery)
        searchEdit.setSelection(searchEdit.text.length)
        loadSearch(normalizedQuery)
    }

    fun close() {
        pooledWebView?.realWebView?.stopLoading()
        visibility = GONE
    }

    fun onDestroy() {
        pooledWebView?.let(WebViewPool::release)
        pooledWebView = null
    }

    fun canGoBack(): Boolean {
        return isShown && pooledWebView != null && webView.canGoBack()
    }

    fun goBack() {
        if (canGoBack()) {
            webView.goBack()
        }
    }

    private fun buildSheet() {
        sheet.setOnClickListener { }
        sheet.addView(
            FrameLayout(context).apply {
                addView(
                    handle,
                    LayoutParams(42.dpToPx(), 4.dpToPx(), Gravity.CENTER)
                )
                setOnTouchListener(::onDragTouch)
            },
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 22.dpToPx())
        )
        sheet.addView(
            LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                addView(backButton, LinearLayout.LayoutParams(44.dpToPx(), 44.dpToPx()))
                addView(searchEdit, LinearLayout.LayoutParams(0, 44.dpToPx(), 1f))
                addView(moreButton, LinearLayout.LayoutParams(44.dpToPx(), 44.dpToPx()))
            },
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 44.dpToPx()).apply {
                marginStart = 12.dpToPx()
                marginEnd = 12.dpToPx()
            }
        )
        sheet.addView(
            HorizontalScrollView(context).apply {
                isHorizontalScrollBarEnabled = false
                addView(engineRow)
            },
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 48.dpToPx())
        )
        sheet.addView(
            progressBar,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 2.dpToPx())
        )
        refreshEngineButtons()
        searchEdit.setOnEditorActionListener { _, actionId, event ->
            val enterPressed = event?.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_UP
            if (actionId == EditorInfo.IME_ACTION_SEARCH || enterPressed) {
                loadSearch(searchEdit.text.toString())
                true
            } else {
                false
            }
        }
    }

    private fun showMoreMenu() {
        PopupMenu(context, moreButton).apply {
            menu.add(R.string.refresh).setOnMenuItemClickListener {
                pooledWebView?.realWebView?.reload()
                true
            }
            menu.add(R.string.edit).setOnMenuItemClickListener {
                showEngineListDialog()
                true
            }
        }.show()
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun ensureWebView() {
        if (pooledWebView != null) {
            return
        }
        pooledWebView = WebViewPool.acquire(context)
        webView.apply {
            webViewClient = object : WebViewClient() {
                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return shouldOverrideUrlLoading(request?.url)
                }

                @Suppress("DEPRECATION", "OVERRIDE_DEPRECATION")
                override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                    return shouldOverrideUrlLoading(url?.toUri())
                }

                override fun onReceivedSslError(
                    view: WebView?,
                    handler: SslErrorHandler?,
                    error: SslError?
                ) {
                    handler?.proceed()
                }

                private fun shouldOverrideUrlLoading(uri: Uri?): Boolean {
                    return when (uri?.scheme) {
                        "http", "https" -> false
                        null -> true
                        else -> {
                            context.openUrl(uri)
                            true
                        }
                    }
                }
            }
            webChromeClient = object : android.webkit.WebChromeClient() {
                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                    progressBar.progress = newProgress
                    progressBar.gone(newProgress >= 100)
                    if (newProgress < 100) {
                        progressBar.visible()
                    }
                }
            }
            settings.apply {
                useWideViewPort = true
                loadWithOverviewMode = true
            }
        }
        sheet.addView(
            webView,
            LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
        )
    }

    private fun refreshEngineButtons() {
        engineRow.removeAllViews()
        selectedEngineIndex = selectedEngineIndex.coerceIn(0, (engines.size - 1).coerceAtLeast(0))
        engines.forEachIndexed { index, engine ->
            engineRow.addView(createEngineButton(index, engine))
        }
        updateEngineButtons()
    }

    private fun createEngineButton(index: Int, engine: SearchEngine): TextView {
        return TextView(context).apply {
            text = engine.title
            gravity = Gravity.CENTER
            textSize = 15f
            setPadding(18.dpToPx(), 0, 18.dpToPx(), 0)
            setOnClickListener {
                selectedEngineIndex = index
                updateEngineButtons()
                loadSearch(searchEdit.text.toString())
            }
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                34.dpToPx()
            ).apply {
                marginEnd = 8.dpToPx()
            }
        }
    }

    private fun updateEngineButtons() {
        for (index in 0 until engineRow.childCount) {
            val child = engineRow.getChildAt(index) as? TextView ?: continue
            val selected = index == selectedEngineIndex
            child.setTextColor(if (selected) Color.WHITE else context.primaryTextColor)
            child.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            child.setBackgroundColor(if (selected) context.accentColor else Color.argb(18, 128, 128, 128))
        }
    }

    private fun loadSearch(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            return
        }
        searchEdit.setText(normalizedQuery)
        searchEdit.setSelection(searchEdit.text.length)
        val engine = engines.getOrNull(selectedEngineIndex) ?: return
        webView.loadUrl(engine.buildUrl(normalizedQuery))
    }

    private fun showEngineListDialog() {
        AlertDialog.Builder(context)
            .setTitle("搜索引擎")
            .setItems(engines.map { it.title }.toTypedArray()) { _, which ->
                engines.getOrNull(which)?.let { showEngineItemDialog(which, it) }
            }
            .setPositiveButton("添加") { _, _ ->
                showEngineItemDialog(
                    index = -1,
                    engine = SearchEngine("新搜索", "https://www.bing.com/search?q={query}")
                )
            }
            .setNegativeButton(R.string.close, null)
            .show()
    }

    private fun showEngineItemDialog(index: Int, engine: SearchEngine) {
        val nameEdit = EditText(context).apply {
            setSingleLine(true)
            hint = "名称"
            setText(engine.title)
        }
        val urlEdit = EditText(context).apply {
            setSingleLine(false)
            minLines = 2
            hint = "搜索 URL，使用 {query} 表示关键词"
            setText(engine.url)
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 0)
            addView(nameEdit, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(urlEdit, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        }
        val builder = AlertDialog.Builder(context)
            .setTitle(if (index >= 0) R.string.edit else R.string.add)
            .setView(container)
            .setPositiveButton(R.string.action_save, null)
            .setNegativeButton(R.string.cancel, null)
        if (index >= 0) {
            builder.setNeutralButton(R.string.delete, null)
        }
        val dialog = builder.show()

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
            val newEngine = SearchEngine(
                title = nameEdit.text.toString().trim(),
                url = urlEdit.text.toString().trim()
            )
            if (newEngine.title.isBlank() || newEngine.url.isBlank()) {
                context.toastOnUi(R.string.non_null_name_url)
                return@setOnClickListener
            }
            if (!newEngine.url.contains(QUERY_PLACEHOLDER)) {
                context.toastOnUi("搜索 URL 必须包含 $QUERY_PLACEHOLDER")
                return@setOnClickListener
            }
            engines = engines.toMutableList().apply {
                if (index >= 0) {
                    set(index, newEngine)
                } else {
                    add(newEngine)
                    selectedEngineIndex = lastIndex
                }
            }
            if (index >= 0 && getDefaultEngineUrl(context).isNullOrBlank()) {
                saveDefaultEngineUrl(context, newEngine.url)
            }
            saveEngines(context, engines)
            refreshEngineButtons()
            loadSearch(searchEdit.text.toString())
            dialog.dismiss()
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            engines = engines.toMutableList().apply {
                if (index in indices) {
                    removeAt(index)
                }
            }
            selectedEngineIndex = selectedEngineIndex.coerceIn(0, (engines.size - 1).coerceAtLeast(0))
            saveEngines(context, engines)
            saveDefaultEngineUrl(context, engines.getOrNull(selectedEngineIndex)?.url)
            refreshEngineButtons()
            dialog.dismiss()
            showEngineListDialog()
        }
    }

    private fun onDragTouch(view: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                startRawY = event.rawY
                startHeight = sheet.layoutParams.height
                view.parent.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val delta = startRawY - event.rawY
                setSheetHeight((startHeight + delta).toInt())
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                view.parent.requestDisallowInterceptTouchEvent(false)
                settleSheet()
                return true
            }
        }
        return false
    }

    private fun settleSheet() {
        val screenHeight = resources.displayMetrics.heightPixels
        val currentHeight = sheet.layoutParams.height
        if (currentHeight < screenHeight * minRatioBeforeDismiss) {
            close()
            return
        }
        val targetRatio = if (currentHeight > screenHeight * 0.72f) expandedRatio else collapsedRatio
        setSheetHeight((screenHeight * targetRatio).roundToInt())
    }

    private fun setSheetHeight(height: Int) {
        val screenHeight = resources.displayMetrics.heightPixels
        val minHeight = (screenHeight * 0.18f).roundToInt()
        val maxHeight = (screenHeight * expandedRatio).roundToInt()
        val targetHeight = height.coerceIn(minHeight, maxHeight)
        sheet.layoutParams = sheet.layoutParams.apply {
            this.height = targetHeight
        }
    }

    companion object {
        private const val ENGINE_PREF_KEY = "readWebSearchEngines"
        private const val DEFAULT_ENGINE_PREF_KEY = "readWebSearchDefaultEngine"
        private const val QUERY_PLACEHOLDER = "{query}"

        private fun defaultEngines(): List<SearchEngine> {
            return listOf(
                SearchEngine("必应", "https://www.bing.com/search?q={query}"),
                SearchEngine("百度", "https://www.baidu.com/s?wd={query}")
            )
        }

        private fun loadEngines(context: Context): List<SearchEngine> {
            val stored = context.getPrefString(ENGINE_PREF_KEY)
            val engines = GSON.fromJsonArray<SearchEngine>(stored).getOrNull()
                ?.filter { it.title.isNotBlank() && it.url.contains(QUERY_PLACEHOLDER) }
                .orEmpty()
            return engines.ifEmpty { defaultEngines() }
        }

        private fun saveEngines(context: Context, engines: List<SearchEngine>) {
            context.putPrefString(ENGINE_PREF_KEY, GSON.toJson(engines))
        }

        private fun defaultEngineIndex(context: Context, engines: List<SearchEngine>): Int {
            val defaultUrl = getDefaultEngineUrl(context)
            val index = engines.indexOfFirst { it.url == defaultUrl }
            return if (index >= 0) index else 0
        }

        private fun getDefaultEngineUrl(context: Context): String? {
            return context.getPrefString(DEFAULT_ENGINE_PREF_KEY)
        }

        private fun saveDefaultEngineUrl(context: Context, url: String?) {
            context.putPrefString(DEFAULT_ENGINE_PREF_KEY, url.orEmpty())
        }

        private fun SearchEngine.buildUrl(query: String): String {
            return url.replace(QUERY_PLACEHOLDER, encodeQuery(query))
        }

        private fun encodeQuery(query: String): String {
            return URLEncoder.encode(query, Charsets.UTF_8.name())
        }
    }
}
