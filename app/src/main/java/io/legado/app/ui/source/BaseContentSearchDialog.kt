package io.legado.app.ui.source

import android.os.Bundle
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
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogRuleSearchBinding
import io.legado.app.databinding.ItemRuleSearchHeaderBinding
import io.legado.app.databinding.ItemRuleSearchResultBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 内容查询对话窗基类。
 * 子类只需实现数据加载和搜索逻辑，UI 由基类统一管理。
 */
abstract class BaseContentSearchDialog : BaseDialogFragment(R.layout.dialog_rule_search) {

    protected val binding by viewBinding(DialogRuleSearchBinding::bind)

    protected var searchJob: Job? = null
    protected var currentSearchTerm = ""
    protected val expandedGroups = mutableSetOf<String>()
    protected val adapter by lazy { SearchAdapter() }

    protected var searchByRuleField = true
    protected var searchAllSources = true

    /** 所有可搜索的字段条目，由子类通过 loadSourceItems 填充 */
    protected var allSourceItems: List<SourceFieldItem> = emptyList()
    protected var sourcesLoaded = false

    // ========== 子类实现 ==========

    /** 对话窗标题 */
    abstract fun getDialogTitle(): String

    /** 搜索输入框 hint */
    abstract fun getSearchHint(): String

    /**
     * 加载所有源的可搜索字段。
     * 子类应根据 allSources 决定加载范围，通过 callback 返回结果。
     */
    abstract fun loadSourceItems(allSources: Boolean, callback: (List<SourceFieldItem>) -> Unit)

    /**
     * 对 allSourceItems 执行搜索过滤。
     * 返回匹配的 SourceFieldItem 列表，value 可包含上下文截断。
     */
    abstract fun performSearch(query: String, allItems: List<SourceFieldItem>): List<SourceFieldItem>

    /** 点击"跳转"后导航到对应的源编辑界面 */
    abstract fun navigateToEdit(sourceUrl: String)

    // ========== 生命周期 ==========

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = getDialogTitle()
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

        setupToggleBar()

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter

        setupSearchInput()
        loadSources()
    }

    // ========== Toggle 栏 ==========

    private fun setupToggleBar() {
        val rootLayout = binding.root as ViewGroup
        val searchBarIndex = rootLayout.indexOfChild(binding.searchBarLayout)

        val toggleLayout = LinearLayout(requireContext()).apply {
            id = View.generateViewId()
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val modeRow = createToggleRow(
            "模式",
            listOf("规则字段" to true, "JSON全文" to false),
            selectedValue = searchByRuleField
        ) { value ->
            searchByRuleField = value
            val query = binding.searchEditText.text.toString().trim()
            if (query.isNotEmpty()) doSearch(query)
        }
        toggleLayout.addView(modeRow)

        val scopeRow = createToggleRow(
            "范围",
            listOf("所有源" to true, "仅启用" to false),
            selectedValue = searchAllSources
        ) { value ->
            searchAllSources = value
            loadSources()
        }
        toggleLayout.addView(scopeRow)

        val toggleLp = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToBottom = binding.toolBar.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
        rootLayout.addView(toggleLayout, searchBarIndex, toggleLp)

        val searchBarLp = binding.searchBarLayout.layoutParams as ConstraintLayout.LayoutParams
        searchBarLp.topToBottom = toggleLayout.id
        binding.searchBarLayout.layoutParams = searchBarLp
    }

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
                val current = allValues[index]
                updateToggleButtons(buttons, current, allValues)
                onSelectionChanged(current)
            }
        }

        updateToggleButtons(buttons, selectedValue, allValues)
        scrollView.addView(row)
        return scrollView
    }

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

    protected fun dpToPx(dp: Int): Int {
        return (dp * requireContext().resources.displayMetrics.density).toInt()
    }

    // ========== 搜索输入 ==========

    private fun setupSearchInput() {
        binding.searchEditText.hint = getSearchHint()
        binding.searchEditText.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                val query = binding.searchEditText.text.toString().trim()
                if (query.isNotEmpty()) {
                    searchJob?.cancel()
                    currentSearchTerm = query
                    doSearch(query)
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
                    doSearch(query)
                }
            }
        })

        binding.clearBtn.setOnClickListener {
            binding.searchEditText.text.clear()
            binding.clearBtn.visibility = View.GONE
            showInitialState()
        }
    }

    // ========== 数据加载 ==========

    private fun loadSources() {
        sourcesLoaded = false
        loadSourceItems(searchAllSources) { items ->
            allSourceItems = items
            sourcesLoaded = true
            val query = binding.searchEditText.text.toString().trim()
            if (query.isNotEmpty()) {
                doSearch(query)
            }
        }
    }

    // ========== 搜索与结果展示 ==========

    private fun doSearch(query: String) {
        if (!sourcesLoaded || allSourceItems.isEmpty()) return

        binding.initialStateLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.GONE
        binding.resultCountText.visibility = View.GONE

        performSearch(query, allSourceItems)
    }

    /** 子类搜索完成后调用此方法展示结果 */
    protected fun showResults(results: List<SourceFieldItem>) {
        if (results.isEmpty()) {
            showEmptyState()
            return
        }

        binding.initialStateLayout.visibility = View.GONE
        binding.emptyStateLayout.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
        binding.resultCountText.visibility = View.VISIBLE

        val totalCount = results.size
        val grouped = results.groupBy { it.sourceName }
        val sourceCount = grouped.size
        binding.resultCountText.text = "在 $sourceCount 个源中找到 $totalCount 个匹配"

        expandedGroups.clear()
        grouped.keys.forEach { expandedGroups.add(it) }

        adapter.setData(grouped)
        binding.recyclerView.scrollToPosition(0)
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

    // ========== 高亮 ==========

    protected fun highlightText(text: String, searchTerm: String): SpannableString {
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
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            startIndex = index + searchTerm.length
        }
        return spannable
    }

    // ========== 预览弹窗 ==========

    private fun showPreviewDialog(item: SourceFieldItem) {
        val scrollView = android.widget.ScrollView(requireContext()).apply {
            setPadding(dpToPx(16), dpToPx(16), dpToPx(16), dpToPx(8))
        }
        val textView = TextView(requireContext()).apply {
            text = item.fullValue
            textSize = 14f
            setTextColor(ContextCompat.getColor(requireContext(), R.color.primaryText))
            typeface = android.graphics.Typeface.MONOSPACE
            setTextIsSelectable(true)
        }
        scrollView.addView(textView)

        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("${item.tabName} · ${item.fieldName}")
            .setView(scrollView)
            .setPositiveButton("跳转") { _, _ ->
                dismiss()
                navigateToEdit(item.sourceUrl)
            }
            .setNegativeButton("关闭", null)
            .show()
    }

    // ========== Adapter ==========

    protected inner class SearchAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        private val items = mutableListOf<SearchListItem>()
        private var groupedResults: Map<String, List<SourceFieldItem>> = emptyMap()

        fun setData(grouped: Map<String, List<SourceFieldItem>>) {
            groupedResults = grouped
            rebuildItems()
            notifyDataSetChanged()
        }

        private fun rebuildItems() {
            items.clear()
            for ((sourceName, fieldItems) in groupedResults) {
                items.add(SearchListItem.Header(sourceName, fieldItems.size))
                if (expandedGroups.contains(sourceName)) {
                    fieldItems.forEach { field ->
                        items.add(SearchListItem.Item(field))
                    }
                }
            }
        }

        override fun getItemViewType(position: Int) = when (items[position]) {
            is SearchListItem.Header -> VIEW_TYPE_HEADER
            is SearchListItem.Item -> VIEW_TYPE_RESULT
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
                is SearchListItem.Header -> (holder as HeaderViewHolder).bind(item)
                is SearchListItem.Item -> (holder as ResultViewHolder).bind(item)
            }
        }

        override fun getItemCount() = items.size

        private inner class HeaderViewHolder(
            private val binding: ItemRuleSearchHeaderBinding
        ) : RecyclerView.ViewHolder(binding.root) {

            fun bind(header: SearchListItem.Header) {
                binding.tabNameText.text = header.sourceName
                binding.matchCountText.text = "${header.matchCount} 个匹配"

                val isExpanded = expandedGroups.contains(header.sourceName)
                binding.expandIcon.rotation = if (isExpanded) 180f else 0f

                binding.root.setOnClickListener {
                    val key = header.sourceName
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

            fun bind(item: SearchListItem.Item) {
                val field = item.field
                binding.fieldNameText.text = "[${field.tabName}] ${field.fieldName}"
                binding.matchedTextText.text = highlightText(field.value, currentSearchTerm)

                binding.root.setOnClickListener {
                    showPreviewDialog(field)
                }
            }
        }
    }

    protected sealed class SearchListItem {
        data class Header(val sourceName: String, val matchCount: Int) : SearchListItem()
        data class Item(val field: SourceFieldItem) : SearchListItem()
    }

    companion object {
        protected const val DEBOUNCE_DELAY = 300L
        protected const val VIEW_TYPE_HEADER = 0
        protected const val VIEW_TYPE_RESULT = 1
    }
}

/**
 * 可搜索的源字段条目。
 * @param value     显示文本（可含上下文截断），用于列表展示和高亮
 * @param fullValue 完整字段文本，用于预览弹窗
 */
data class SourceFieldItem(
    val sourceName: String,
    val sourceUrl: String,
    val tabKey: String,
    val tabName: String,
    val fieldKey: String,
    val fieldName: String,
    val value: String,
    val fullValue: String = value
)
