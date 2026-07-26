package io.legado.app.ui.widget.dialog

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.Spannable
import android.text.SpannableString
import android.text.style.BackgroundColorSpan
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogContentEditBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.utils.applyTint
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 内容编辑对话框基类
 *
 * 提供以下公共功能：
 * - CodeView 配置（tint、禁用自动补全、禁用 autoIndent）
 * - 菜单（搜索、全屏编辑、保存、重置、复制全部）
 * - 搜索面板（高亮、上一个/下一个导航、结果计数）
 * - 全屏代码编辑器跳转（CodeEditActivity）
 *
 * 子类需实现：
 * - [getTitle]：对话框标题
 * - [onSave]：保存逻辑，返回 true 则关闭对话框
 * - [onReset]：重置逻辑
 *
 * 子类可覆写：
 * - [setupContentView]：额外 CodeView 配置（如语法高亮）
 * - [onContentReady]：内容加载入口
 * - [showFullscreenEdit]：是否显示全屏编辑按钮
 * - [getSourceType] / [getSourceKey]：全屏编辑器参数
 */
abstract class BaseContentEditDialog :
    BaseDialogFragment(R.layout.dialog_content_edit) {

    val binding by viewBinding(DialogContentEditBinding::bind)

    // region 搜索状态

    protected var searchKeyword: String = ""
    protected var currentIndex: Int = -1
    protected var matchPositions: MutableList<Int> = mutableListOf()
    protected var originalContent: SpannableString? = null

    // endregion

    // region 全屏编辑器

    private val editCodeLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.getStringExtra("text")?.let {
                binding.contentView.setText(it)
                originalContent = null
            }
        }
    }

    // endregion

    // region 生命周期

    override fun onStart() {
        super.onStart()
        setLayout(1f, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = getTitle()
        // CodeView 通用配置
        binding.contentView.applyTint(accentColor)
        binding.contentView.threshold = Int.MAX_VALUE
        binding.contentView.filters = arrayOfNulls(0)
        // 子类额外配置（如语法高亮）
        setupContentView()
        // 初始化菜单和搜索
        initMenu()
        initSearchPanel()
        // 子类加载内容
        onContentReady()
    }

    // endregion

    // region 子类钩子

    /** 对话框标题 */
    abstract fun getTitle(): CharSequence

    /** 额外的 CodeView 配置（如语法高亮），在通用配置之后调用 */
    open fun setupContentView() {}

    /** 内容加载入口，在菜单和搜索面板初始化之后调用 */
    open fun onContentReady() {}

    /**
     * 保存逻辑
     * @return true 表示保存成功，关闭对话框；false 表示不关闭（如校验失败）
     */
    abstract fun onSave(content: String): Boolean

    /** 重置逻辑 */
    abstract fun onReset()

    /** 是否显示全屏编辑按钮，默认 true */
    open val showFullscreenEdit: Boolean get() = true

    /** 全屏编辑器的 sourceType 参数 */
    open fun getSourceType(): String = "content"

    /** 全屏编辑器的 sourceKey 参数 */
    open fun getSourceKey(): String = ""

    // endregion

    // region 菜单

    private fun initMenu() {
        binding.toolBar.inflateMenu(R.menu.content_edit)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.menu.findItem(R.id.menu_fullscreen_edit)?.isVisible = showFullscreenEdit
        binding.toolBar.setOnMenuItemClickListener {
            when (it.itemId) {
                R.id.menu_search -> toggleSearchPanel()
                R.id.menu_fullscreen_edit -> openCodeEditor()
                R.id.menu_save -> {
                    val content = binding.contentView.text?.toString() ?: ""
                    if (onSave(content)) dismiss()
                }
                R.id.menu_reset -> onReset()
                R.id.menu_copy_all -> copyAll()
            }
            true
        }
    }

    private fun openCodeEditor() {
        val text = binding.contentView.text?.toString() ?: return
        val title = binding.toolBar.title?.toString() ?: "content"
        val intent = Intent(requireContext(), CodeEditActivity::class.java).apply {
            putExtra("text", text)
            putExtra("title", title)
            putExtra("sourceType", getSourceType())
            putExtra("sourceKey", getSourceKey())
        }
        editCodeLauncher.launch(intent)
    }

    private fun copyAll() {
        val title = binding.toolBar.title?.toString() ?: ""
        val content = binding.contentView.text?.toString() ?: ""
        val text = if (title.isNotEmpty()) "$title\n$content" else content
        requireContext().sendToClip(text)
    }

    // endregion

    // region 搜索功能

    private fun toggleSearchPanel() {
        if (binding.searchPanel.isVisible) {
            binding.searchPanel.visibility = View.GONE
            clearSearchHighlight()
        } else {
            binding.searchPanel.visibility = View.VISIBLE
            binding.etSearch.requestFocus()
            if (searchKeyword.isNotEmpty()) {
                binding.etSearch.setText(searchKeyword)
            }
        }
    }

    private fun initSearchPanel() {
        binding.etSearch.addTextChangedListener { text ->
            searchKeyword = text?.toString() ?: ""
            performSearch()
        }
        binding.etSearch.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                performSearch()
                true
            } else {
                false
            }
        }
        binding.btnCloseSearch.setOnClickListener {
            binding.searchPanel.visibility = View.GONE
            clearSearchHighlight()
        }
        binding.btnPrev.setOnClickListener {
            navigateToMatch(-1)
        }
        binding.btnNext.setOnClickListener {
            navigateToMatch(1)
        }
    }

    private fun performSearch() {
        if (searchKeyword.isEmpty()) {
            clearSearchHighlight()
            updateSearchResultText()
            return
        }
        val content = binding.contentView.text?.toString() ?: return
        matchPositions.clear()
        var startIndex = 0
        while (true) {
            val index = content.indexOf(searchKeyword, startIndex, true)
            if (index == -1) break
            matchPositions.add(index)
            startIndex = index + 1
        }
        if (matchPositions.isNotEmpty()) {
            currentIndex = 0
            highlightMatches()
            scrollToMatch(0)
        } else {
            currentIndex = -1
            clearSearchHighlight()
        }
        updateSearchResultText()
    }

    private fun highlightMatches() {
        val content = binding.contentView.text?.toString() ?: return
        if (originalContent == null) {
            originalContent = SpannableString(content)
        }
        val spannable = SpannableString(content)
        matchPositions.forEach { pos ->
            spannable.setSpan(
                BackgroundColorSpan(0xFFFFFF00.toInt()),
                pos,
                pos + searchKeyword.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        if (currentIndex >= 0 && currentIndex < matchPositions.size) {
            val currentPos = matchPositions[currentIndex]
            spannable.setSpan(
                BackgroundColorSpan(0xFF00FFFF.toInt()),
                currentPos,
                currentPos + searchKeyword.length,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
        binding.contentView.setText(spannable)
    }

    private fun clearSearchHighlight() {
        originalContent?.let {
            binding.contentView.setText(it)
        }
        matchPositions.clear()
        currentIndex = -1
    }

    private fun navigateToMatch(direction: Int) {
        if (matchPositions.isEmpty()) return
        currentIndex = (currentIndex + direction + matchPositions.size) % matchPositions.size
        highlightMatches()
        scrollToMatch(currentIndex)
        updateSearchResultText()
    }

    private fun scrollToMatch(index: Int) {
        if (index < 0 || index >= matchPositions.size) return
        val pos = matchPositions[index]
        binding.contentView.post {
            val layout = binding.contentView.layout ?: return@post
            val line = layout.getLineForOffset(pos)
            val lineHeight = layout.getLineTop(line)
            binding.contentView.scrollTo(0, lineHeight - binding.contentView.height / 3)
        }
    }

    private fun updateSearchResultText() {
        binding.tvSearchResult.text = if (matchPositions.isEmpty()) {
            if (searchKeyword.isEmpty()) "" else "0"
        } else {
            "${currentIndex + 1}/${matchPositions.size}"
        }
    }

    // endregion
}
