package io.legado.app.ui.code

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.annotation.RequiresApi
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.addTextChangedListener
import androidx.lifecycle.lifecycleScope
import com.google.android.material.textfield.TextInputEditText
import io.github.rosemoe.sora.event.PublishSearchResultEvent
import io.github.rosemoe.sora.event.SelectionChangeEvent
import io.github.rosemoe.sora.langs.textmate.registry.ThemeRegistry
import io.github.rosemoe.sora.util.regex.RegexBackrefGrammar
import io.github.rosemoe.sora.widget.CodeEditor
import io.github.rosemoe.sora.widget.EditorSearcher
import io.github.rosemoe.sora.widget.EditorSearcher.SearchOptions
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ActivityCodeEditBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.about.AppLogDialog
import io.legado.app.ui.code.config.ChangeThemeDialog
import io.legado.app.ui.code.config.SettingsDialog
import io.legado.app.ui.widget.keyboard.KeyboardToolPop
import io.legado.app.utils.imeHeight
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 代码编辑活动
 * 提供代码编辑功能，支持语法高亮、搜索替换、格式化等
 * 使用 Sora Editor 作为编辑器核心，支持 TextMate 语法高亮
 */
class CodeEditActivity :
    VMBaseActivity<ActivityCodeEditBinding, CodeEditViewModel>(),
    KeyboardToolPop.CallBack, ChangeThemeDialog.CallBack, SettingsDialog.CallBack {
    companion object {
        private var isInitialized = false
        private var findText = ""
        private var replaceText = ""
        private var isRegex = true
    }
    override val binding by viewBinding(ActivityCodeEditBinding::inflate)
    override val viewModel by viewModels<CodeEditViewModel>()
    private val softKeyboardTool by lazy {
        KeyboardToolPop(this, lifecycleScope, binding.root, this)
    }
    private val editor: CodeEditor by lazy { binding.editText }
    private val editorSearcher: EditorSearcher by lazy { editor.searcher }
    private var searchOptions: SearchOptions? = null
    private var menuSaveBtn: MenuItem? = null

    private val isDark
        get() = AppConfig.editTemeAuto && ThemeConfig.isDarkTheme()
    private var themeIndex = -1

    /**
     * 活动创建时初始化
     * 配置编辑器、加载文本内容、设置光标位置
     */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        softKeyboardTool.attachToWindow(window)
        editor.colorScheme = TextMateColorScheme2.create(ThemeRegistry.getInstance()) //先设置颜色,避免一开始的白屏
        viewModel.initData(intent) {
            editor.apply {
                viewModel.title?.let {
                    binding.titleBar.title = it
                }
                nonPrintablePaintingFlags = AppConfig.editNonPrintable
                setEditorLanguage(viewModel.language)
                upEdit(AppConfig.editFontScale, null, AppConfig.editAutoWrap)
                setText(viewModel.initialText)
                editable = viewModel.writable
                menuSaveBtn?.isVisible = viewModel.writable
                requestFocus()
                postDelayed({
                    val pos = cursor.indexer.getCharPosition(viewModel.cursorPosition)
                    setSelection(pos.line, pos.column, true)
                }, 360) // 进行延时,确保加载渲染完成,从而确保光标能显示跳转到长文本最后
            }
        }
        initView()
    }

    /**
     * 初始化视图
     * 设置软键盘监听器
     */
    private fun initView() {
        binding.root.setOnApplyWindowInsetsListenerCompat { _, windowInsets ->
            softKeyboardTool.initialPadding = windowInsets.imeHeight
            windowInsets
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        editorSearcher.stopSearch()
        editor.release()
    }

    /**
     * 使用super.finish(),防止循环回调
     * 保存编辑内容并退出
     * @param check 是否检查未保存更改
     */
    private fun save(check: Boolean) {
        if (!viewModel.writable) return super.finish()
        val text = editor.text.toString()
        val cursorPos = editor.cursor?.left ?: 0
        val fieldKey = viewModel.fieldKey
        val tabKey = viewModel.tabKey
        when {
            text == viewModel.initialText -> {
                if (cursorPos > 0) {
                    val result = Intent().apply {
                        putExtra("cursorPosition", cursorPos)
                    }
                    setResult(RESULT_OK, result)
                }
                super.finish()
            }
            check -> {
                alert(R.string.exit) {
                    setMessage(R.string.exit_no_save)
                    positiveButton(R.string.yes)
                    negativeButton(R.string.no) {
                        if (cursorPos > 0) {
                            val result = Intent().apply {
                                putExtra("cursorPosition", cursorPos)
                            }
                            setResult(RESULT_OK, result)
                        }
                        super.finish()
                    }
                }
            }
            else -> {
                val result = Intent().apply {
                    putExtra("text", text)
                    putExtra("cursorPosition", cursorPos)
                    putExtra("fieldKey", fieldKey)
                    putExtra("tabKey", tabKey)
                }
                setResult(RESULT_OK, result)
                super.finish()
            }
        }
    }

    /**
     * 更新编辑器设置
     * @param fontSize 字体大小
     * @param autoComplete 是否启用自动补全
     * @param autoWarp 是否启用自动换行
     * @param editNonPrintable 不可见字符显示标志
     */
    override fun upEdit(fontSize: Int?, autoComplete: Boolean?, autoWarp: Boolean?, editNonPrintable: Int?) {
        if (fontSize != null) {
            editor.setTextSize(fontSize.toFloat())
        }
        if (autoComplete != null) {
            viewModel.language?.isAutoCompleteEnabled = autoComplete
            editor.setEditorLanguage(viewModel.language)
        }
        if (autoWarp != null) {
            editor.isWordwrap = autoWarp
        }
        if (editNonPrintable != null) {
            editor.nonPrintablePaintingFlags = editNonPrintable
        }
    }

    /**
     * 初始化主题
     * 根据系统主题自动切换编辑器主题
     */
    override fun initTheme() {
        super.initTheme()
        if (!isInitialized) {
            viewModel.initSora()
            isInitialized = true
        }
        val index = if (isDark) {
            AppConfig.editThemeDark
        } else {
            AppConfig.editTheme
        }
        upTheme(index)
        themeIndex = index
    }

    /**
     * 更新编辑器主题
     * @param index 主题索引
     */
    override fun upTheme(index: Int) {
        if (themeIndex != index) {
            viewModel.loadTextMateThemes(index)
            editor.setEditorLanguage(viewModel.language) //每次更改颜色后需要再执行一次语言设置,防止切换主题后高亮颜色不正确
            themeIndex = index
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.code_edit_activity, menu)
        menuSaveBtn = menu.findItem(R.id.menu_save).apply {
            isVisible = viewModel.writable
        }
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_auto_wrap)?.isChecked = AppConfig.editAutoWrap
        return super.onPrepareOptionsMenu(menu)
    }

    /**
     * 设置搜索选项
     * 根据是否使用正则表达式配置搜索参数
     */
    private fun setSearchOptions() {
        searchOptions =  SearchOptions(
            if (isRegex) SearchOptions.TYPE_REGULAR_EXPRESSION else SearchOptions.TYPE_NORMAL,
            !isRegex,
            RegexBackrefGrammar.DEFAULT
        )
    }

    /**
     * 显示搜索界面
     * 配置搜索、替换功能的事件监听器
     */
    private fun search() {
        if (binding.searchGroup.isVisible) return
        binding.switchRegex.run {
            isChecked = isRegex
            setSearchOptions()
            setOnCheckedChangeListener { _, isChecked ->
                isRegex = isChecked
                setSearchOptions()
                searchTxt(binding.etFind.text.toString())
            }
        }
        val receiptSearch =
            editor.subscribeEvent(PublishSearchResultEvent::class.java) { event, _ ->
                if (event.editor == editor) {
                    updateSearchResults()
                }
            }
        val receiptChange = editor.subscribeEvent(SelectionChangeEvent::class.java) { event, _ ->
            if (event.cause == SelectionChangeEvent.CAUSE_SEARCH) {
                updateSearchResults()
            }
        }
        binding.searchGroup.visibility = View.VISIBLE
        binding.btnCloseFind.setOnClickListener {
            binding.searchGroup.visibility = View.GONE
            editorSearcher.stopSearch()
            receiptSearch.unsubscribe()
            receiptChange.unsubscribe()
            editor.requestFocus()
            editor.invalidate()
        }
        searchTxt(findText)
        binding.etFind.run {
            requestFocus()
            setText(findText)
            addTextChangedListener { text ->
                if (!text.isNullOrEmpty()) {
                    findText = text.toString()
                    searchTxt(findText)
                } else {
                    editorSearcher.stopSearch()
                    editor.invalidate()
                }
            }

        }
        binding.etReplace.run {
            setText(replaceText)
            addTextChangedListener { text ->
                if (!text.isNullOrEmpty()) {
                    replaceText = text.toString()
                }
            }
        }
        binding.btnPrevious.setOnClickListener {
            if (editorSearcher.hasQuery()) {
                editorSearcher.gotoPrevious()
            }
        }
        binding.btnNext.setOnClickListener {
            if (editorSearcher.hasQuery()) {
                editorSearcher.gotoNext()
            }
        }
        binding.btnReplace.setOnClickListener {
            if (binding.replaceGroup.isGone) {
                binding.replaceGroup.visibility = View.VISIBLE
                binding.btnReplaceAll.isEnabled = true
                binding.etReplace.requestFocus()
            } else {
                if (editorSearcher.hasQuery()) {
                    editorSearcher.replaceCurrentMatch(binding.etReplace.text.toString())
                }
            }
        }
        binding.btnCloseReplace.setOnClickListener {
            binding.replaceGroup.visibility = View.GONE
            binding.btnReplaceAll.isEnabled = false
            binding.etFind.requestFocus()
        }
        binding.btnReplaceAll.setOnClickListener {
            if (editorSearcher.hasQuery()) {
                editorSearcher.replaceAll(binding.etReplace.text.toString())
            }
        }
    }

    /**
     * 执行搜索
     * @param txt 搜索文本
     */
    private fun searchTxt(txt: String) {
        if (txt.isNotEmpty()) {
            try {
                searchOptions?.let {
                    editorSearcher.search(txt, it)
                }
            } catch (_: java.util.regex.PatternSyntaxException) {
                // 忽略正则表达式语法错误
                editorSearcher.stopSearch()
                editor.invalidate()
            }
        }
    }

    @SuppressLint("SetTextI18n")
    /**
     * 更新搜索结果显示
     * 显示当前匹配位置和总匹配数
     */
    private fun updateSearchResults() {
        if (editorSearcher.hasQuery()) {
            val totalResults = editorSearcher.matchedPositionCount
            val currentPosition = editorSearcher.currentMatchedPositionIndex + 1
            binding.tvSearchResult.text =
                "${if (currentPosition > 0) "$currentPosition/" else ""}$totalResults"
        }
    }

    /**
     * 处理菜单选项点击事件
     * @param item 被点击的菜单项
     * @return 是否消耗了事件
     */
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_search -> search()
            R.id.menu_save -> save(false)
            R.id.menu_format_code -> viewModel.formatCode(editor)
            R.id.menu_change_theme -> showDialogFragment(ChangeThemeDialog())
            R.id.menu_config_settings -> showDialogFragment(SettingsDialog(this, this))
            R.id.menu_auto_wrap -> {
                item.isChecked = !AppConfig.editAutoWrap
                upEdit(autoWarp = !AppConfig.editAutoWrap)
                putPrefBoolean(PreferKey.editAutoWrap, !AppConfig.editAutoWrap)
            }
            R.id.menu_log -> showDialogFragment<AppLogDialog>()
            R.id.menu_switch_rule -> showSwitchRuleDialog()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    /**
     * 显示切换规则对话框
     * 多级选择：源类型 -> 板块 -> 字段
     */
    private fun showSwitchRuleDialog() {
        val sourceType = viewModel.sourceType
        if (sourceType.isNullOrEmpty() || viewModel.sourceJson.isNullOrEmpty()) {
            return
        }
        when (sourceType) {
            "bookSource" -> showBookSourceRuleSelector()
            "rssSource" -> showRssSourceRuleSelector()
        }
    }

    /**
     * 书源规则选择器
     */
    private fun showBookSourceRuleSelector() {
        val tabs = listOf(
            SelectItem("基本", "base"),
            SelectItem("搜索", "search"),
            SelectItem("发现", "explore"),
            SelectItem("详情", "info"),
            SelectItem("目录", "toc"),
            SelectItem("正文", "content")
        )
        selector(tabs.map { it.title }) { _, position ->
            showBookSourceFieldSelector(tabs[position].value)
        }
    }

    /**
     * 书源字段选择器
     */
    private fun showBookSourceFieldSelector(tabKey: String) {
        val fields = when (tabKey) {
            "base" -> listOf(
                SelectItem("源地址", "bookSourceUrl"),
                SelectItem("源名称", "bookSourceName"),
                SelectItem("源分组", "bookSourceGroup"),
                SelectItem("源注释", "bookSourceComment"),
                SelectItem("登录地址", "loginUrl"),
                SelectItem("登录界面", "loginUi"),
                SelectItem("登录检查JS", "loginCheckJs"),
                SelectItem("封面解密JS", "coverDecodeJs"),
                SelectItem("书籍URL正则", "bookUrlPattern"),
                SelectItem("请求头", "header"),
                SelectItem("变量注释", "variableComment"),
                SelectItem("并发率", "concurrentRate"),
                SelectItem("jsLib", "jsLib")
            )
            "search" -> listOf(
                SelectItem("搜索地址", "searchUrl"),
                SelectItem("验证关键字", "checkKeyWord"),
                SelectItem("书籍列表", "bookList"),
                SelectItem("书名", "name"),
                SelectItem("作者", "author"),
                SelectItem("分类", "kind"),
                SelectItem("字数", "wordCount"),
                SelectItem("最新章节", "lastChapter"),
                SelectItem("简介", "intro"),
                SelectItem("封面", "coverUrl"),
                SelectItem("书籍URL", "bookUrl")
            )
            "explore" -> listOf(
                SelectItem("发现地址", "exploreUrl"),
                SelectItem("书籍列表", "bookList"),
                SelectItem("书名", "name"),
                SelectItem("作者", "author"),
                SelectItem("分类", "kind"),
                SelectItem("字数", "wordCount"),
                SelectItem("最新章节", "lastChapter"),
                SelectItem("简介", "intro"),
                SelectItem("封面", "coverUrl"),
                SelectItem("书籍URL", "bookUrl")
            )
            "info" -> listOf(
                SelectItem("初始化", "init"),
                SelectItem("书名", "name"),
                SelectItem("作者", "author"),
                SelectItem("分类", "kind"),
                SelectItem("字数", "wordCount"),
                SelectItem("最新章节", "lastChapter"),
                SelectItem("简介", "intro"),
                SelectItem("封面", "coverUrl"),
                SelectItem("目录URL", "tocUrl"),
                SelectItem("可重命名", "canReName"),
                SelectItem("下载地址", "downloadUrls")
            )
            "toc" -> listOf(
                SelectItem("预处理JS", "preUpdateJs"),
                SelectItem("章节列表", "chapterList"),
                SelectItem("章节名称", "chapterName"),
                SelectItem("章节URL", "chapterUrl"),
                SelectItem("格式化JS", "formatJs"),
                SelectItem("是否分卷", "isVolume"),
                SelectItem("更新时间", "updateTime"),
                SelectItem("是否VIP", "isVip"),
                SelectItem("是否付费", "isPay"),
                SelectItem("下页目录URL", "nextTocUrl")
            )
            "content" -> listOf(
                SelectItem("正文内容", "content"),
                SelectItem("下页内容URL", "nextContentUrl"),
                SelectItem("子内容", "subContent"),
                SelectItem("替换正则", "replaceRegex"),
                SelectItem("标题", "title"),
                SelectItem("资源正则", "sourceRegex"),
                SelectItem("图片样式", "imageStyle"),
                SelectItem("图片解码", "imageDecode"),
                SelectItem("网页JS", "webJs"),
                SelectItem("付费操作", "payAction"),
                SelectItem("回调JS", "callBackJs")
            )
            else -> emptyList()
        }
        if (fields.isEmpty()) return
        selector(fields.map { it.title }) { _, position ->
            val fieldKey = fields[position].value
            switchToField(tabKey, fieldKey)
        }
    }

    /**
     * 订阅源规则选择器
     */
    private fun showRssSourceRuleSelector() {
        val tabs = listOf(
            SelectItem("基本", "base"),
            SelectItem("搜索", "search"),
            SelectItem("发现", "explore"),
            SelectItem("文章", "article")
        )
        selector(tabs.map { it.title }) { _, position ->
            showRssSourceFieldSelector(tabs[position].value)
        }
    }

    /**
     * 订阅源字段选择器
     */
    private fun showRssSourceFieldSelector(tabKey: String) {
        val fields = when (tabKey) {
            "base" -> listOf(
                SelectItem("源地址", "sourceUrl"),
                SelectItem("源名称", "sourceName"),
                SelectItem("源分组", "sourceGroup"),
                SelectItem("源注释", "sourceComment"),
                SelectItem("登录地址", "loginUrl"),
                SelectItem("登录界面", "loginUi"),
                SelectItem("请求头", "header"),
                SelectItem("并发率", "concurrentRate")
            )
            "search" -> listOf(
                SelectItem("搜索地址", "searchUrl"),
                SelectItem("验证关键字", "checkKeyWord"),
                SelectItem("文章列表", "ruleArticles"),
                SelectItem("下一篇", "ruleNextPage"),
                SelectItem("标题", "ruleTitle"),
                SelectItem("描述", "ruleDescription"),
                SelectItem("链接", "ruleLink"),
                SelectItem("内容", "ruleContent"),
                SelectItem("图片", "ruleImage"),
                SelectItem("日期", "rulePubDate")
            )
            "explore" -> listOf(
                SelectItem("发现地址", "exploreUrl"),
                SelectItem("文章列表", "ruleArticles"),
                SelectItem("下一篇", "ruleNextPage"),
                SelectItem("标题", "ruleTitle"),
                SelectItem("描述", "ruleDescription"),
                SelectItem("链接", "ruleLink"),
                SelectItem("内容", "ruleContent"),
                SelectItem("图片", "ruleImage"),
                SelectItem("日期", "rulePubDate")
            )
            "article" -> listOf(
                SelectItem("正文内容", "ruleContent"),
                SelectItem("下一篇", "ruleNextPage"),
                SelectItem("标题", "ruleTitle"),
                SelectItem("描述", "ruleDescription"),
                SelectItem("链接", "ruleLink"),
                SelectItem("图片", "ruleImage"),
                SelectItem("日期", "rulePubDate"),
                SelectItem("样式", "style")
            )
            else -> emptyList()
        }
        if (fields.isEmpty()) return
        selector(fields.map { it.title }) { _, position ->
            val fieldKey = fields[position].value
            switchToField(tabKey, fieldKey)
        }
    }

    /**
     * 切换到指定字段
     */
    private fun switchToField(tabKey: String, fieldKey: String) {
        val json = viewModel.sourceJson ?: return
        try {
            val jsonObj = com.google.gson.JsonParser.parseString(json).asJsonObject
            val value = when (tabKey) {
                "base" -> {
                    if (jsonObj.has(fieldKey)) jsonObj.get(fieldKey).asString else ""
                }
                "search" -> {
                    val rule = jsonObj.getAsJsonObject("ruleSearch")
                    if (fieldKey == "searchUrl") {
                        if (jsonObj.has("searchUrl")) jsonObj.get("searchUrl").asString else ""
                    } else {
                        if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                    }
                }
                "explore" -> {
                    val rule = jsonObj.getAsJsonObject("ruleExplore")
                    if (fieldKey == "exploreUrl") {
                        if (jsonObj.has("exploreUrl")) jsonObj.get("exploreUrl").asString else ""
                    } else {
                        if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                    }
                }
                "info" -> {
                    val rule = jsonObj.getAsJsonObject("ruleBookInfo")
                    if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                }
                "toc" -> {
                    val rule = jsonObj.getAsJsonObject("ruleToc")
                    if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                }
                "content" -> {
                    val rule = jsonObj.getAsJsonObject("ruleContent")
                    if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                }
                "article" -> {
                    val rule = jsonObj.getAsJsonObject("ruleArticle")
                    if (fieldKey == "ruleContent" || fieldKey == "ruleNextPage") {
                        if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                    } else {
                        if (rule != null && rule.has(fieldKey)) rule.get(fieldKey).asString else ""
                    }
                }
                else -> ""
            }
            editor.setText(value ?: "")
            viewModel.fieldKey = fieldKey
            viewModel.tabKey = tabKey
            viewModel.initialText = value ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    /**
     * 退出时保存
     * 检查是否有未保存的更改
     */
    override fun finish() {
        save(true)
    }

    /**
     * 提供帮助操作列表
     * @return 包含帮助操作的列表
     */
    override fun helpActions(): List<SelectItem<String>> {
        return arrayListOf(
            SelectItem("书源教程", "ruleHelp"),
            SelectItem("订阅源教程", "rssRuleHelp"),
            SelectItem("js教程", "jsHelp"),
            SelectItem("正则教程", "regexHelp")
        )
    }

    /**
     * 处理帮助操作的选择事件
     * @param action 操作标识符
     */
    override fun onHelpActionSelect(action: String) {
        when (action) {
            "ruleHelp" -> showHelp("ruleHelp")
            "rssRuleHelp" -> showHelp("rssRuleHelp")
            "jsHelp" -> showHelp("jsHelp")
            "regexHelp" -> showHelp("regexHelp")
        }
    }

    /**
     * 发送文本到当前焦点视图
     * 支持普通输入框和代码编辑器
     * @param text 要插入的文本
     */
    override fun sendText(text: String) {
        val view = window.decorView.findFocus()
        if (view is TextInputEditText) {
            var start = view.selectionStart
            var end = view.selectionEnd
            if (start > end) {
                val temp = start
                start = end
                end = temp
            }
            if (text.isNotEmpty()) {
                val edit = view.editableText//获取EditText的文字
                if (start < 0 || start >= edit.length) {
                    edit.append(text)
                } else {
                    edit.replace(start, end, text)//光标所在位置插入文字
                }
            }
        }
        else {
            editor.insertText(text, text.length)
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    /**
     * 撤销操作
     */
    override fun onUndoClicked() {
        editor.undo()
    }

    @RequiresApi(Build.VERSION_CODES.M)
    /**
     * 重做操作
     */
    override fun onRedoClicked() {
        editor.redo()
    }
}