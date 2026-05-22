package io.legado.app.ui.book.source.manage

import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogRuleSearchBinding
import io.legado.app.databinding.ItemRuleSearchHeaderBinding
import io.legado.app.databinding.ItemRuleSearchResultBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
/**
 * 书源内容查询对话窗
 */
class SourceContentSearchDialog : BaseDialogFragment(R.layout.dialog_rule_search) {

    private val binding by viewBinding(DialogRuleSearchBinding::bind)
    private val viewModel by viewModels<SourceContentSearchViewModel>()

    private var searchJob: Job? = null
    private var currentSearchTerm = ""
    private val expandedGroups = mutableSetOf<String>()
    private val adapter by lazy { SearchAdapter() }

    // 搜索模式: true=规则字段, false=JSON全文
    private var searchByRuleField = true
    // 搜索范围: true=所有源, false=仅启用
    private var searchAllSources = true

    // 所有源数据: List of (sourceName, sourceUrl, jsonObject)
    private var allSources: List<Triple<String, String, JsonObject>> = emptyList()
    private var sourcesLoaded = false

    companion object {
        private const val DEBOUNCE_DELAY = 300L
        private const val VIEW_TYPE_HEADER = 0
        private const val VIEW_TYPE_RESULT = 1

        // 板块配置
        private val TAB_NAMES = mapOf(
            "base" to "基本",
            "search" to "搜索",
            "explore" to "发现",
            "info" to "详情",
            "toc" to "目录",
            "content" to "正文"
        )

        private val TAB_FIELDS = mapOf(
            "base" to listOf(
                "bookSourceUrl" to "源地址",
                "bookSourceName" to "源名称",
                "bookSourceGroup" to "源分组",
                "bookSourceComment" to "源注释",
                "loginUrl" to "登录地址",
                "loginUi" to "登录界面",
                "loginCheckJs" to "登录检查JS",
                "coverDecodeJs" to "封面解密JS",
                "bookUrlPattern" to "书籍URL正则",
                "header" to "请求头",
                "variableComment" to "变量说明",
                "concurrentRate" to "并发率",
                "jsLib" to "jsLib"
            ),
            "search" to listOf(
                "searchUrl" to "搜索地址",
                "checkKeyWord" to "校验关键字",
                "bookList" to "书籍列表",
                "name" to "书名",
                "author" to "作者",
                "kind" to "分类",
                "wordCount" to "字数",
                "lastChapter" to "最新章节",
                "intro" to "简介规则",
                "coverUrl" to "封面规则",
                "bookUrl" to "书籍URL"
            ),
            "explore" to listOf(
                "exploreUrl" to "发现地址",
                "bookList" to "书籍列表",
                "name" to "书名",
                "author" to "作者",
                "kind" to "分类",
                "wordCount" to "字数",
                "lastChapter" to "最新章节",
                "intro" to "简介规则",
                "coverUrl" to "封面规则",
                "bookUrl" to "书籍URL"
            ),
            "info" to listOf(
                "init" to "初始化",
                "name" to "书名",
                "author" to "作者",
                "kind" to "分类",
                "wordCount" to "字数",
                "lastChapter" to "最新章节",
                "intro" to "简介规则",
                "coverUrl" to "封面规则",
                "tocUrl" to "目录URL",
                "canReName" to "允许修改书名作者",
                "downloadUrls" to "下载地址"
            ),
            "toc" to listOf(
                "preUpdateJs" to "更新之前JS",
                "chapterList" to "目录列表规则",
                "chapterName" to "章节名称",
                "chapterUrl" to "章节URL",
                "formatJs" to "格式化规则",
                "isVolume" to "Volume标识",
                "updateTime" to "更新时间",
                "isVip" to "是否VIP",
                "isPay" to "购买标识",
                "nextTocUrl" to "目录下一页规则"
            ),
            "content" to listOf(
                "content" to "正文内容",
                "nextContentUrl" to "正文下一页URL规则",
                "subContent" to "副文规则",
                "replaceRegex" to "替换正则",
                "ChapterName" to "章节名称规则",
                "sourceRegex" to "资源正则",
                "imageStyle" to "图片样式",
                "imageDecode" to "图片解密",
                "webJs" to "WebView JS",
                "payAction" to "购买操作",
                "callBackJs" to "回调操作"
            )
        )
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = "书源内容查询"
        binding.toolBar.inflateMenu(R.menu.dialog_help_search)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_close -> {
                    dismissAllowingStateLoss()
                    true
                }
                else -> false
            }
        }

        // 在 Toolbar 和搜索栏之间插入模式/范围切换栏
        setupToggleBar()

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        setupSearchInput()
        loadSources()
    }

    /**
     * 创建模式和范围切换按钮栏，插入到 Toolbar 和搜索栏之间
     */
    private fun setupToggleBar() {
        val rootLayout = binding.root as ViewGroup

        // 搜索栏在 root 中的索引
        val searchBarIndex = rootLayout.indexOfChild(binding.searchBarLayout)

        val toggleLayout = LinearLayout(requireContext()).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            val lp = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            layoutParams = lp
        }

        // 模式切换行
        val modeRow = createToggleRow(
            "模式",
            listOf("规则字段" to true, "JSON全文" to false),
            selectedValue = searchByRuleField
        ) { value ->
            searchByRuleField = value
            val query = binding.searchEditText.text.toString().trim()
            if (query.isNotEmpty()) performSearch(query)
        }
        toggleLayout.addView(modeRow)

        // 范围切换行
        val scopeRow = createToggleRow(
            "范围",
            listOf("所有源" to true, "仅启用" to false),
            selectedValue = searchAllSources
        ) { value ->
            searchAllSources = value
            loadSources()
        }
        toggleLayout.addView(scopeRow)

        // 用 ConstraintLayout.LayoutParams 设置约束：toggle 插在 toolbar 和 search_bar 之间
        val toggleLp = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToBottom = binding.toolBar.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
        rootLayout.addView(toggleLayout, searchBarIndex, toggleLp)

        // 更新 search_bar 的 top 约束，从 tool_bar 改为 toggleLayout
        val searchBarLp = binding.searchBarLayout.layoutParams as ConstraintLayout.LayoutParams
        searchBarLp.topToBottom = toggleLayout.id
        binding.searchBarLayout.layoutParams = searchBarLp
    }

    /**
     * 创建一行切换按钮
     */
    private fun <T> createToggleRow(
        label: String,
        options: List<Pair<String, T>>,
        selectedValue: T,
        onSelectionChanged: (T) -> Unit
    ): View {
        val context = requireContext()
        val scrollView = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dpToPx(12), dpToPx(4), dpToPx(12), dpToPx(4))
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val labelView = TextView(context).apply {
            text = label
            textSize = 13f
            setTextColor(ContextCompat.getColor(context, R.color.secondaryText))
            setPadding(0, 0, dpToPx(8), 0)
        }
        row.addView(labelView)

        val buttons = mutableListOf<TextView>()
        val allValues = options.map { it.second }
        var current = selectedValue

        for ((text, _) in options) {
            val btn = TextView(context).apply {
                this.text = text
                textSize = 13f
                gravity = Gravity.CENTER
                setPadding(dpToPx(12), dpToPx(6), dpToPx(12), dpToPx(6))
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginEnd = dpToPx(6)
                }
                isClickable = true
                isFocusable = true
            }
            buttons.add(btn)
            row.addView(btn)
        }

        for ((index, btn) in buttons.withIndex()) {
            btn.setOnClickListener {
                current = allValues[index]
                updateToggleButtons(buttons, current, allValues)
                onSelectionChanged(current)
            }
        }

        // 初始化选中状态
        updateToggleButtons(buttons, selectedValue, allValues)

        scrollView.addView(row)
        return scrollView
    }

    /**
     * 更新切换按钮的样式
     */
    private fun <T> updateToggleButtons(
        buttons: List<TextView>,
        selectedValue: T,
        allValues: List<T>
    ) {
        val context = requireContext()
        val primaryClr = primaryColor
        buttons.forEachIndexed { index, btn ->
            val isSelected = allValues[index] == selectedValue
            if (isSelected) {
                btn.setTextColor(primaryClr)
                btn.setBackgroundResource(R.drawable.bg_edit)
            } else {
                btn.setTextColor(ContextCompat.getColor(context, R.color.secondaryText))
                btn.setBackgroundResource(0)
            }
        }
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * requireContext().resources.displayMetrics.density).toInt()
    }

    /**
     * 加载书源数据
     */
    private fun loadSources() {
        sourcesLoaded = false
        viewModel.loadSources(searchAllSources) { sourceList ->
            allSources = sourceList.mapNotNull { (name, url, jsonStr) ->
                try {
                    Triple(name, url, JsonParser.parseString(jsonStr).asJsonObject)
                } catch (e: Exception) {
                    null
                }
            }
            sourcesLoaded = true
            // 重新执行当前搜索
            val query = binding.searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                performSearch(query)
            }
        }
    }

    private fun setupSearchInput() {
        binding.searchEditText.hint = "输入关键词搜索所有书源"
        binding.searchEditText.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val query = binding.searchEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchJob?.cancel()
                    currentSearchTerm = query
                    performSearch(query)
                }
                true
            } else {
                false
            }
        })

        binding.searchEditText.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: android.text.Editable?) {
                searchJob?.cancel()
                binding.clearBtn.visibility = if (s.isNullOrEmpty()) View.GONE else View.VISIBLE

                val query = s?.toString()?.trim() ?: ""
                if (query.isEmpty()) {
                    showInitialState()
                    return
                }

                searchJob = lifecycleScope.launch {
                    delay(DEBOUNCE_DELAY)
                    currentSearchTerm = query
                    performSearch(query)
                }
            }
        })

        binding.clearBtn.setOnClickListener {
            binding.searchEditText.text.clear()
            binding.clearBtn.visibility = View.GONE
            showInitialState()
        }
    }

    /**
     * 执行搜索
     */
    private fun performSearch(query: String) {
        if (!sourcesLoaded || allSources.isEmpty()) return

        binding.initialStateLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.resultCountText.visibility = View.GONE

        lifecycleScope.launch {
            val results = withContext(Dispatchers.Default) {
                if (searchByRuleField) {
                    searchRuleFields(query)
                } else {
                    searchJsonFull(query)
                }
            }
            updateResults(results)
        }
    }

    /**
     * 规则字段模式搜索
     */
    private fun searchRuleFields(query: String): List<SourceSearchResult> {
        val results = mutableListOf<SourceSearchResult>()
        val queryLower = query.lowercase()
        val contextChars = 50

        for ((sourceName, sourceUrl, jsonObj) in allSources) {
            val matchedFields = mutableListOf<FieldResult>()

            for ((tabKey, fields) in TAB_FIELDS) {
                for ((fieldKey, fieldName) in fields) {
                    val value = getFieldValue(jsonObj, tabKey, fieldKey) ?: continue
                    if (value.lowercase().contains(queryLower)) {
                        var startIndex = 0
                        val valueLower = value.lowercase()
                        while (true) {
                            val matchIndex = valueLower.indexOf(queryLower, startIndex)
                            if (matchIndex == -1) break

                            val start = maxOf(0, matchIndex - contextChars)
                            val end = minOf(value.length, matchIndex + query.length + contextChars)
                            val contextText = buildString {
                                if (start > 0) append("...")
                                append(value.substring(start, end))
                                if (end < value.length) append("...")
                            }

                            matchedFields.add(FieldResult(
                                tabKey = tabKey,
                                tabName = TAB_NAMES[tabKey] ?: tabKey,
                                fieldKey = fieldKey,
                                fieldName = fieldName,
                                matchedText = contextText,
                                fullValue = value,
                                matchIndex = matchIndex
                            ))
                            startIndex = matchIndex + 1
                        }
                    }
                }
            }

            if (matchedFields.isNotEmpty()) {
                results.add(SourceSearchResult(sourceName, sourceUrl, matchedFields))
            }
        }

        return results
    }

    /**
     * JSON 全文模式搜索
     */
    private fun searchJsonFull(query: String): List<SourceSearchResult> {
        val results = mutableListOf<SourceSearchResult>()
        val queryLower = query.lowercase()
        val contextChars = 50

        for ((sourceName, sourceUrl, jsonObj) in allSources) {
            val jsonStr = jsonObj.toString()
            val matchedFields = mutableListOf<FieldResult>()

            if (jsonStr.lowercase().contains(queryLower)) {
                var startIndex = 0
                val jsonLower = jsonStr.lowercase()
                while (true) {
                    val matchIndex = jsonLower.indexOf(queryLower, startIndex)
                    if (matchIndex == -1) break

                    val start = maxOf(0, matchIndex - contextChars)
                    val end = minOf(jsonStr.length, matchIndex + query.length + contextChars)
                    val contextText = buildString {
                        if (start > 0) append("...")
                        append(jsonStr.substring(start, end))
                        if (end < jsonStr.length) append("...")
                    }

                    matchedFields.add(FieldResult(
                        tabKey = "json",
                        tabName = "JSON",
                        fieldKey = "json",
                        fieldName = "JSON全文",
                        matchedText = contextText,
                        fullValue = jsonStr,
                        matchIndex = matchIndex
                    ))
                    startIndex = matchIndex + 1
                }
            }

            if (matchedFields.isNotEmpty()) {
                results.add(SourceSearchResult(sourceName, sourceUrl, matchedFields))
            }
        }

        return results
    }

    /**
     * 获取字段值（同 RuleSearchDialog 的逻辑）
     */
    private fun getFieldValue(jsonObj: JsonObject, tabKey: String, fieldKey: String): String? {
        return when (tabKey) {
            "base" -> {
                if (!jsonObj.has(fieldKey)) return null
                val element = jsonObj.get(fieldKey)
                when {
                    element.isJsonNull -> null
                    element.isJsonPrimitive -> element.asString
                    else -> element.toString()
                }
            }
            "search" -> {
                if (fieldKey == "searchUrl") {
                    if (!jsonObj.has("searchUrl")) return null
                    val element = jsonObj.get("searchUrl")
                    when {
                        element.isJsonNull -> null
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }
                } else {
                    val rule = jsonObj.getAsJsonObject("ruleSearch")
                    if (rule == null || !rule.has(fieldKey)) return null
                    val element = rule.get(fieldKey)
                    when {
                        element.isJsonNull -> null
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }
                }
            }
            "explore" -> {
                if (fieldKey == "exploreUrl") {
                    if (!jsonObj.has("exploreUrl")) return null
                    val element = jsonObj.get("exploreUrl")
                    when {
                        element.isJsonNull -> null
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }
                } else {
                    val rule = jsonObj.getAsJsonObject("ruleExplore")
                    if (rule == null || !rule.has(fieldKey)) return null
                    val element = rule.get(fieldKey)
                    when {
                        element.isJsonNull -> null
                        element.isJsonPrimitive -> element.asString
                        else -> element.toString()
                    }
                }
            }
            "info" -> {
                val rule = jsonObj.getAsJsonObject("ruleBookInfo")
                if (rule == null || !rule.has(fieldKey)) return null
                val element = rule.get(fieldKey)
                when {
                    element.isJsonNull -> null
                    element.isJsonPrimitive -> element.asString
                    else -> element.toString()
                }
            }
            "toc" -> {
                val rule = jsonObj.getAsJsonObject("ruleToc")
                if (rule == null || !rule.has(fieldKey)) return null
                val element = rule.get(fieldKey)
                when {
                    element.isJsonNull -> null
                    element.isJsonPrimitive -> element.asString
                    else -> element.toString()
                }
            }
            "content" -> {
                val rule = jsonObj.getAsJsonObject("ruleContent")
                if (rule == null || !rule.has(fieldKey)) return null
                val element = rule.get(fieldKey)
                when {
                    element.isJsonNull -> null
                    element.isJsonPrimitive -> element.asString
                    else -> element.toString()
                }
            }
            else -> null
        }
    }

    private fun updateResults(results: List<SourceSearchResult>) {
        if (results.isEmpty()) {
            showEmptyState()
        } else {
            showResultsState(results)
        }
    }

    private fun showInitialState() {
        binding.recyclerView.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.resultCountText.visibility = View.GONE
        binding.initialStateLayout.visibility = View.VISIBLE
    }

    private fun showEmptyState() {
        binding.recyclerView.visibility = View.GONE
        binding.initialStateLayout.visibility = View.GONE
        binding.resultCountText.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.VISIBLE
    }

    private fun showResultsState(results: List<SourceSearchResult>) {
        binding.initialStateLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.resultCountText.visibility = View.VISIBLE

        val totalCount = results.sumOf { it.fields.size }
        val sourceCount = results.size
        binding.resultCountText.text = "在 $sourceCount 个源中找到 $totalCount 个匹配"

        expandedGroups.clear()
        results.forEach { expandedGroups.add(it.sourceName) }

        adapter.setData(results)
        binding.recyclerView.scrollToPosition(0)
    }

    /**
     * 高亮显示匹配文本
     */
    private fun highlightText(text: String, searchTerm: String): SpannableString {
        val spannable = SpannableString(text)
        val termLower = searchTerm.lowercase()
        val textLower = text.lowercase()
        var startIndex = 0
        val highlightColor = ContextCompat.getColor(requireContext(), R.color.accent)
        val bgColor = android.graphics.Color.argb(
            60,
            android.graphics.Color.red(highlightColor),
            android.graphics.Color.green(highlightColor),
            android.graphics.Color.blue(highlightColor)
        )

        while (true) {
            val index = textLower.indexOf(termLower, startIndex)
            if (index == -1) break
            spannable.setSpan(
                BackgroundColorSpan(bgColor),
                index,
                index + searchTerm.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = index + searchTerm.length
        }
        return spannable
    }

    /**
     * 显示内容预览弹窗
     */
    private fun showPreviewDialog(result: FieldResult, sourceUrl: String) {
        val scrollView = android.widget.ScrollView(requireContext()).apply {
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(8))
        }
        val textView = TextView(requireContext()).apply {
            text = result.fullValue
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.primaryText))
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("${result.tabName} · ${result.fieldName}")
            .setView(scrollView)
            .setPositiveButton("跳转") { _, _ ->
                dismiss()
                startActivity<BookSourceEditActivity> {
                    putExtra("sourceUrl", sourceUrl)
                }
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    /**
     * RecyclerView 适配器
     */
    private inner class SearchAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<SearchListItem>()
        private var sourceResults: List<SourceSearchResult> = emptyList()

        fun setData(results: List<SourceSearchResult>) {
            sourceResults = results
            rebuildItems()
            notifyDataSetChanged()
        }

        private fun rebuildItems() {
            items.clear()
            for (result in sourceResults) {
                items.add(SearchListItem.Header(result))
                if (expandedGroups.contains(result.sourceName)) {
                    result.fields.forEach { field ->
                        items.add(SearchListItem.Result(result, field))
                    }
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return when (items[position]) {
                is SearchListItem.Header -> VIEW_TYPE_HEADER
                is SearchListItem.Result -> VIEW_TYPE_RESULT
            }
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return when (viewType) {
                VIEW_TYPE_HEADER -> {
                    val binding = ItemRuleSearchHeaderBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                    HeaderViewHolder(binding)
                }
                else -> {
                    val binding = ItemRuleSearchResultBinding.inflate(
                        LayoutInflater.from(parent.context), parent, false
                    )
                    ResultViewHolder(binding)
                }
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            when (val item = items[position]) {
                is SearchListItem.Header -> {
                    (holder as HeaderViewHolder).bind(item.result)
                }
                is SearchListItem.Result -> {
                    (holder as ResultViewHolder).bind(item.sourceResult, item.fieldResult)
                }
            }
        }

        override fun getItemCount() = items.size

        private inner class HeaderViewHolder(
            private val binding: ItemRuleSearchHeaderBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(result: SourceSearchResult) {
                binding.tabNameText.text = result.sourceName
                binding.matchCountText.text = "${result.fields.size} 个匹配"

                val isExpanded = expandedGroups.contains(result.sourceName)
                binding.expandIcon.rotation = if (isExpanded) 180f else 0f

                binding.root.setOnClickListener {
                    val key = result.sourceName
                    if (expandedGroups.contains(key)) {
                        expandedGroups.remove(key)
                    } else {
                        expandedGroups.add(key)
                    }
                    rebuildItems()
                    notifyDataSetChanged()
                }
            }
        }

        private inner class ResultViewHolder(
            private val binding: ItemRuleSearchResultBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(sourceResult: SourceSearchResult, fieldResult: FieldResult) {
                binding.fieldNameText.text = "[${fieldResult.tabName}] ${fieldResult.fieldName}"
                binding.matchedTextText.text = highlightText(fieldResult.matchedText, currentSearchTerm)

                binding.root.setOnClickListener {
                    showPreviewDialog(fieldResult, sourceResult.sourceUrl)
                }
            }
        }
    }
}

private sealed class SearchListItem {
    data class Header(val result: SourceSearchResult) : SearchListItem()
    data class Result(val sourceResult: SourceSearchResult, val fieldResult: FieldResult) : SearchListItem()
}

private data class SourceSearchResult(
    val sourceName: String,
    val sourceUrl: String,
    val fields: List<FieldResult>
)

private data class FieldResult(
    val tabKey: String,
    val tabName: String,
    val fieldKey: String,
    val fieldName: String,
    val matchedText: String,
    val fullValue: String,
    val matchIndex: Int = 0
)
