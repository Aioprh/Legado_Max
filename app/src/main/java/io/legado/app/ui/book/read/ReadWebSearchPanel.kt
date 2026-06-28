package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
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
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.widget.PopupMenu
import androidx.core.net.toUri
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import io.legado.app.R
import io.legado.app.help.source.SourceRecycleBinHelp
import io.legado.app.help.webView.PooledWebView
import io.legado.app.help.webView.WebViewPool
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.utils.GSON
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getPrefString
import io.legado.app.utils.gone
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setDarkeningAllowed
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible
import java.net.URLEncoder
import java.util.Collections
import kotlin.math.roundToInt

class ReadWebSearchPanel @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    data class SearchEngine(
        val title: String = "",
        val url: String = ""
    )

    private val panelBackgroundColor: Int
        get() = context.backgroundColor
    private val panelTextColor: Int
        get() = if (ColorUtils.isColorLight(panelBackgroundColor)) Color.BLACK else Color.WHITE
    private val panelSecondaryTextColor: Int
        get() = if (ColorUtils.isColorLight(panelBackgroundColor)) Color.DKGRAY else Color.LTGRAY
    private val panelControlColor: Int
        get() = if (ColorUtils.isColorLight(panelBackgroundColor)) {
            Color.argb(18, 0, 0, 0)
        } else {
            Color.argb(32, 255, 255, 255)
        }
    private val accentTextColor: Int
        get() = if (ColorUtils.isColorLight(context.accentColor)) Color.BLACK else Color.WHITE

    private val sheet = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(panelBackgroundColor)
        isClickable = true
    }
    private val handle = View(context).apply {
        setBackgroundColor(Color.argb(96, 128, 128, 128))
    }
    private val searchEdit = EditText(context).apply {
        setSingleLine(true)
        imeOptions = EditorInfo.IME_ACTION_SEARCH
        setTextColor(panelTextColor)
        setHintTextColor(panelSecondaryTextColor)
        hint = context.getString(R.string.web_search)
        textSize = 16f
        setPadding(14.dpToPx(), 0, 14.dpToPx(), 0)
        setBackgroundColor(panelControlColor)
    }
    private val backButton = ImageButton(context).apply {
        setImageResource(R.drawable.ic_arrow_back)
        setColorFilter(panelTextColor)
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
        setColorFilter(panelTextColor)
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
        webView.resumeTimers()//恢复定时器
        webView.onResume()//恢复WebView状态
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
            setBackgroundColor(Color.WHITE)
            settings.apply {
                useWideViewPort = true
                loadWithOverviewMode = true
                setDarkeningAllowed(false)
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
            child.setTextColor(if (selected) accentTextColor else panelTextColor)
            child.setTypeface(Typeface.DEFAULT, if (selected) Typeface.BOLD else Typeface.NORMAL)
            child.setBackgroundColor(if (selected) context.accentColor else panelControlColor)
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
        val dialog = BottomSheetDialog(context)
        val adapter = EngineManageAdapter(engines.toMutableList())
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            overScrollMode = OVER_SCROLL_IF_CONTENT_SCROLLS
        }
        ItemTouchHelper(adapter.itemTouchCallback).attachToRecyclerView(recyclerView)
        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
            setBackgroundColor(panelBackgroundColor)
            addView(
                TextView(context).apply {
                    text = "搜索引擎"
                    textSize = 20f
                    typeface = Typeface.DEFAULT_BOLD
                    setTextColor(panelTextColor)
                },
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            )
            addView(
                TextView(context).apply {
                    text = "长按拖动排序，URL 使用 {query} 表示选中文字"
                    textSize = 13f
                    setTextColor(panelSecondaryTextColor)
                    setPadding(0, 4.dpToPx(), 0, 8.dpToPx())
                },
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            )
            addView(
                recyclerView,
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f)
            )
            addView(
                Button(context).apply {
                    text = "添加搜索引擎"
                    setTextColor(panelTextColor)
                    setOnClickListener {
                        showEngineItemDialog(
                            index = -1,
                            engine = SearchEngine("新搜索", BING_TEMPLATE.url),
                            onChanged = { adapter.replaceItems(engines) }
                        )
                    }
                },
                LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 44.dpToPx()).apply {
                    topMargin = 8.dpToPx()
                }
            )
        }
        dialog.setContentView(content)
        dialog.setOnShowListener {
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.layoutParams = bottomSheet?.layoutParams?.apply {
                height = (resources.displayMetrics.heightPixels * 0.82f).roundToInt()
            }
            bottomSheet?.let { sheetView ->
                BottomSheetBehavior.from(sheetView).state = BottomSheetBehavior.STATE_EXPANDED
            }
        }
        dialog.show()
    }

    private fun showEngineItemDialog(
        index: Int,
        engine: SearchEngine,
        onChanged: (() -> Unit)? = null
    ) {
        val nameEdit = EditText(context).apply {
            setSingleLine(true)
            hint = "名称"
            setTextColor(panelTextColor)
            setHintTextColor(panelSecondaryTextColor)
            setText(engine.title)
        }
        val urlEdit = EditText(context).apply {
            setSingleLine(false)
            minLines = 2
            hint = "搜索 URL，使用 {query} 表示关键词"
            setTextColor(panelTextColor)
            setHintTextColor(panelSecondaryTextColor)
            setText(engine.url)
        }
        val templateRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            addView(
                Button(context).apply {
                    text = "必应模板"
                    setTextColor(panelTextColor)
                    setOnClickListener {
                        nameEdit.setText(BING_TEMPLATE.title)
                        urlEdit.setText(BING_TEMPLATE.url)
                    }
                },
                LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginEnd = 6.dpToPx()
                }
            )
            addView(
                Button(context).apply {
                    text = "百度模板"
                    setTextColor(panelTextColor)
                    setOnClickListener {
                        nameEdit.setText(BAIDU_TEMPLATE.title)
                        urlEdit.setText(BAIDU_TEMPLATE.url)
                    }
                },
                LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f).apply {
                    marginStart = 6.dpToPx()
                }
            )
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 8.dpToPx(), 24.dpToPx(), 0)
            addView(nameEdit, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(urlEdit, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            addView(templateRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
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
            onChanged?.invoke()
            dialog.dismiss()
        }
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            val engineTitle = engines.getOrNull(index)?.title.orEmpty()
            AlertDialog.Builder(context)
                .setTitle("删除搜索引擎")
                .setMessage("确认删除“$engineTitle”？")
                .setPositiveButton(R.string.delete) { _, _ ->
                    engines.getOrNull(index)?.let {
                        SourceRecycleBinHelp.recycleSearchEngines(listOf(it))
                    }
                    engines = engines.toMutableList().apply {
                        if (index in indices) {
                            removeAt(index)
                        }
                    }
                    selectedEngineIndex = selectedEngineIndex.coerceIn(0, (engines.size - 1).coerceAtLeast(0))
                    saveEngines(context, engines)
                    saveDefaultEngineUrl(context, engines.getOrNull(selectedEngineIndex)?.url)
                    refreshEngineButtons()
                    onChanged?.invoke()
                    dialog.dismiss()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }
    }

    private inner class EngineManageAdapter(
        private val items: MutableList<SearchEngine>
    ) : RecyclerView.Adapter<EngineManageAdapter.EngineViewHolder>() {

        val itemTouchCallback = object : ItemTouchHelper.Callback() {
            override fun isLongPressDragEnabled(): Boolean = true

            override fun isItemViewSwipeEnabled(): Boolean = false

            override fun getMovementFlags(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                return makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false
                }
                Collections.swap(items, from, to)
                notifyItemMoved(from, to)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                persistItems()
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EngineViewHolder {
            val root = LinearLayout(parent.context).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(12.dpToPx(), 10.dpToPx(), 12.dpToPx(), 10.dpToPx())
                background = GradientDrawable().apply {
                    cornerRadius = 8.dpToPx().toFloat()
                    setColor(panelControlColor)
                }
                layoutParams = RecyclerView.LayoutParams(
                    RecyclerView.LayoutParams.MATCH_PARENT,
                    RecyclerView.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = 8.dpToPx()
                }
            }
            return EngineViewHolder(root)
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: EngineViewHolder, position: Int) {
            val engine = items[position]
            val isDefault = isDefaultEngine(position, engine)
            holder.titleView.text = engine.title
            holder.urlView.text = engine.url
            holder.defaultTag.visibility = if (isDefault) VISIBLE else GONE
            holder.defaultButton.text = if (isDefault) "默认" else "设默认"
            holder.defaultButton.setOnClickListener {
                saveDefaultEngineUrl(context, engine.url)
                selectedEngineIndex = engines.indexOfFirst { it.url == engine.url }.takeIf { it >= 0 } ?: position
                refreshEngineButtons()
                notifyDataSetChanged()
            }
            holder.editButton.setOnClickListener {
                showEngineItemDialog(position, engine) {
                    replaceItems(engines)
                }
            }
            holder.deleteButton.setOnClickListener {
                confirmDelete(position, engine)
            }
        }

        fun replaceItems(newItems: List<SearchEngine>) {
            items.clear()
            items.addAll(newItems)
            notifyDataSetChanged()
        }

        private fun persistItems() {
            val selectedUrl = engines.getOrNull(selectedEngineIndex)?.url
            engines = items.toList()
            selectedEngineIndex = engines.indexOfFirst { it.url == selectedUrl }
                .takeIf { it >= 0 }
                ?: defaultEngineIndex(context, engines)
            ensureValidDefaultEngine()
            saveEngines(context, engines)
            refreshEngineButtons()
            loadSearch(searchEdit.text.toString())
        }

        private fun confirmDelete(position: Int, engine: SearchEngine) {
            AlertDialog.Builder(context)
                .setTitle("删除搜索引擎")
                .setMessage("确认删除“${engine.title}”？")
                .setPositiveButton(R.string.delete) { _, _ ->
                    if (position !in items.indices) {
                        return@setPositiveButton
                    }
                    SourceRecycleBinHelp.recycleSearchEngines(listOf(engine))
                    items.removeAt(position)
                    engines = items.toList()
                    selectedEngineIndex = selectedEngineIndex.coerceIn(0, (engines.size - 1).coerceAtLeast(0))
                    ensureValidDefaultEngine()
                    saveEngines(context, engines)
                    notifyItemRemoved(position)
                    notifyItemRangeChanged(position, items.size - position)
                    refreshEngineButtons()
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        private fun isDefaultEngine(position: Int, engine: SearchEngine): Boolean {
            val defaultUrl = getDefaultEngineUrl(context)
            return if (defaultUrl.isNullOrBlank()) {
                position == 0
            } else {
                engine.url == defaultUrl
            }
        }

        inner class EngineViewHolder(root: LinearLayout) : RecyclerView.ViewHolder(root) {
            val titleView = TextView(root.context).apply {
                textSize = 16f
                typeface = Typeface.DEFAULT_BOLD
                setTextColor(panelTextColor)
            }
            val defaultTag = TextView(root.context).apply {
                text = "默认"
                textSize = 12f
                setTextColor(accentTextColor)
                gravity = Gravity.CENTER
                setPadding(8.dpToPx(), 2.dpToPx(), 8.dpToPx(), 2.dpToPx())
                background = GradientDrawable().apply {
                    cornerRadius = 8.dpToPx().toFloat()
                    setColor(context.accentColor)
                }
            }
            val urlView = TextView(root.context).apply {
                textSize = 12f
                setTextColor(panelSecondaryTextColor)
                maxLines = 2
            }
            val defaultButton = TextView(root.context).actionText()
            val editButton = TextView(root.context).actionText().apply { text = "编辑" }
            val deleteButton = TextView(root.context).actionText().apply {
                text = "删除"
                setTextColor(Color.rgb(210, 64, 64))
            }

            init {
                val titleRow = LinearLayout(root.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    addView(titleView, LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
                    addView(defaultTag, LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
                }
                val actionRow = LinearLayout(root.context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.END
                    setPadding(0, 8.dpToPx(), 0, 0)
                    addView(defaultButton)
                    addView(editButton)
                    addView(deleteButton)
                }
                root.addView(titleRow)
                root.addView(urlView, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
                root.addView(actionRow, LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
            }

            private fun TextView.actionText(): TextView {
                textSize = 14f
                setTextColor(context.accentColor)
                setPadding(12.dpToPx(), 6.dpToPx(), 0, 6.dpToPx())
                return this
            }
        }
    }

    private fun ensureValidDefaultEngine() {
        val defaultUrl = getDefaultEngineUrl(context)
        if (defaultUrl.isNullOrBlank() || engines.none { it.url == defaultUrl }) {
            saveDefaultEngineUrl(context, engines.firstOrNull()?.url)
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
        private val BING_TEMPLATE = SearchEngine("必应", "https://www.bing.com/search?q={query}")
        private val BAIDU_TEMPLATE = SearchEngine("百度", "https://www.baidu.com/s?wd={query}")

        // 自定义反序列化器：确保 JSON null 被替换为默认值
        private val searchEngineDeserializer = object : com.google.gson.JsonDeserializer<SearchEngine> {
            override fun deserialize(
                json: com.google.gson.JsonElement,
                typeOfT: java.lang.reflect.Type,
                context: com.google.gson.JsonDeserializationContext
            ): SearchEngine {
                val obj = json.asJsonObject
                return SearchEngine(
                    title = obj.get("title")?.takeIf { !it.isJsonNull }?.asString ?: "",
                    url = obj.get("url")?.takeIf { !it.isJsonNull }?.asString ?: ""
                )
            }
        }

        // 使用自定义反序列化器的 GSON 实例
        private val engineGson: com.google.gson.Gson = com.google.gson.GsonBuilder()
            .registerTypeAdapter(SearchEngine::class.java, searchEngineDeserializer)
            .create()

        private fun defaultEngines(): List<SearchEngine> {
            return listOf(BING_TEMPLATE, BAIDU_TEMPLATE)
        }

        fun loadSearchEngines(context: Context): List<SearchEngine> {
            val stored = context.getPrefString(ENGINE_PREF_KEY) ?: return defaultEngines()
            if (stored.isBlank()) return defaultEngines()
            return try {
                engineGson.fromJson(stored, Array<SearchEngine>::class.java)
                    ?.toList()
                    ?.filter { it.title.isNotBlank() && it.url.contains(QUERY_PLACEHOLDER) }
                    .orEmpty()
                    .ifEmpty { defaultEngines() }
            } catch (e: Exception) {
                defaultEngines()
            }
        }

        fun saveSearchEngines(context: Context, engines: List<SearchEngine>) {
            context.putPrefString(ENGINE_PREF_KEY, engineGson.toJson(engines))
        }

        private fun loadEngines(context: Context): List<SearchEngine> {
            return loadSearchEngines(context)
        }

        private fun saveEngines(context: Context, engines: List<SearchEngine>) {
            saveSearchEngines(context, engines)
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
