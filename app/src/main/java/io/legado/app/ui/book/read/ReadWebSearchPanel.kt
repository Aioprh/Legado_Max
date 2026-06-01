package io.legado.app.ui.book.read

import android.annotation.SuppressLint
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
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.net.toUri
import io.legado.app.R
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebViewPool
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.utils.dpToPx
import io.legado.app.utils.gone
import io.legado.app.utils.openUrl
import io.legado.app.utils.visible
import java.net.URLEncoder
import kotlin.math.roundToInt

class ReadWebSearchPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    private enum class SearchEngine(
        val title: String,
        val buildUrl: (String) -> String
    ) {
        Bing("必应", { query -> "https://www.bing.com/search?q=${encodeQuery(query)}" }),
        Baidu("百度", { query -> "https://www.baidu.com/s?wd=${encodeQuery(query)}" })
    }

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
    private var selectedEngine = SearchEngine.Bing
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
            searchEdit,
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
        SearchEngine.entries.forEach { engine ->
            engineRow.addView(createEngineButton(engine))
        }
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

    private fun createEngineButton(engine: SearchEngine): TextView {
        return TextView(context).apply {
            text = engine.title
            gravity = Gravity.CENTER
            textSize = 15f
            setPadding(18.dpToPx(), 0, 18.dpToPx(), 0)
            setOnClickListener {
                selectedEngine = engine
                updateEngineButtons()
                loadSearch(searchEdit.text.toString())
            }
            engineRow.post { updateEngineButtons() }
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
            val engine = SearchEngine.entries[index]
            val selected = engine == selectedEngine
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
        webView.loadUrl(selectedEngine.buildUrl(normalizedQuery))
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
        private fun encodeQuery(query: String): String {
            return URLEncoder.encode(query, Charsets.UTF_8.name())
        }
    }
}
