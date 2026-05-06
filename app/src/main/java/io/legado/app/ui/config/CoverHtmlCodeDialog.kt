package io.legado.app.ui.config

import android.annotation.SuppressLint
import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.appcompat.app.AlertDialog
import androidx.core.os.bundleOf
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogCoverHtmlCodeBinding
import io.legado.app.help.DefaultData
import io.legado.app.help.config.CoverHtmlTemplateConfig
import io.legado.app.constant.EventBus
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.model.BookCover
import io.legado.app.ui.widget.code.addHtmlPattern
import io.legado.app.ui.widget.code.addJsPattern
import io.legado.app.utils.GSON
import io.legado.app.utils.postEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch
import splitties.views.onClick

/**
 * HTML封面代码配置对话框
 * 
 * 用于配置自定义HTML模板来生成封面图片，支持以下功能：
 * - 设置模板名称
 * - 输入书名和作者进行实时预览
 * - 编辑HTML代码模板（支持语法高亮）
 * - WebView实时渲染预览效果
 * 
 * 支持的变量：
 * - {{bookName}}: 书名
 * - {{author}}: 作者
 */
class CoverHtmlCodeDialog : BaseDialogFragment(R.layout.dialog_cover_html_code) {

    val binding by viewBinding(DialogCoverHtmlCodeBinding::bind)

    private var template: CoverHtmlTemplateConfig.Template? = null
    private var isNewTemplate: Boolean = false
    private var hasUnsavedChanges: Boolean = false
    private var originalHtmlCode: String = ""
    private var originalName: String = ""

    companion object {
        private const val KEY_TEMPLATE = "template"
        private const val KEY_IS_NEW = "isNew"

        /**
         * 创建对话框实例
         * 
         * @param template 要编辑的模板
         *                 - 非null：编辑指定模板
         *                 - null且从模板列表调用：新建模板
         */
        fun newInstance(template: CoverHtmlTemplateConfig.Template?): CoverHtmlCodeDialog {
            return CoverHtmlCodeDialog().apply {
                if (template != null) {
                    arguments = bundleOf(KEY_TEMPLATE to GSON.toJson(template))
                } else {
                    arguments = bundleOf(KEY_IS_NEW to true)
                }
            }
        }

        /**
         * 创建编辑当前选中模板的对话框实例
         * 
         * 用于从封面配置页直接进入编辑当前选中模板
         */
        fun newEditInstance(): CoverHtmlCodeDialog {
            return CoverHtmlCodeDialog().apply {
                // 不设置任何参数，parseArguments() 会自动加载当前选中模板
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        parseArguments()
        initToolBar()
        initWebView()
        initCodeView()
        loadTemplateData()
        initClickListeners()
    }

    override fun onResume() {
        super.onResume()
        refreshIfTemplateChanged()
    }

    override fun onDestroyView() {
        binding.webViewPreview.stopLoading()
        binding.webViewPreview.destroy()
        super.onDestroyView()
    }

    /**
     * 解析传入参数，确定当前编辑的模板
     * 
     * 若从模板列表传入模板则编辑该模板；
     * 若无传入且 isNewTemplate=true 则为新建模板模式；
     * 若无传入且 isNewTemplate=false 则加载当前选中模板进行编辑
     */
    private fun parseArguments() {
        isNewTemplate = arguments?.getBoolean(KEY_IS_NEW) == true
        val templateJson = arguments?.getString(KEY_TEMPLATE)
        if (templateJson != null) {
            template = GSON.fromJson(templateJson, CoverHtmlTemplateConfig.Template::class.java)
        }
        // 只有非新建模式且 template 为 null 时，才加载当前选中模板
        if (!isNewTemplate && template == null) {
            template = CoverHtmlTemplateConfig.getSelectedTemplate()
        }
    }

    /**
     * 初始化工具栏菜单
     * 
     * 加载模板切换按钮，点击后弹出模板列表对话框，
     * 用户可在列表中切换当前使用的模板
     */
    private fun initToolBar() {
        binding.toolBar.inflateMenu(R.menu.cover_html_code)
        binding.toolBar.setOnMenuItemClickListener { menuItem ->
            when (menuItem.itemId) {
                R.id.menu_template -> {
                    showDialogFragment(CoverHtmlTemplateListDialog())
                    true
                }
                else -> false
            }
        }
    }

    /**
     * 初始化WebView配置
     * 
     * 配置WebView用于预览HTML封面效果：
     * - 启用JavaScript支持
     * - 禁用缩放功能
     * - 设置自适应布局
     */
    @SuppressLint("SetJavaScriptEnabled")
    private fun initWebView() {
        binding.webViewPreview.setBackgroundColor(Color.TRANSPARENT)
        binding.webViewPreview.settings.apply {
            javaScriptEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            setSupportZoom(false)
            builtInZoomControls = false
            displayZoomControls = false
            domStorageEnabled = true
            defaultTextEncodingName = "UTF-8"
        }
        binding.webViewPreview.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)
            }
        }
    }

    /**
     * 初始化代码编辑器
     * 
     * 为代码编辑器添加HTML和JavaScript语法高亮支持
     */
    private fun initCodeView() {
        binding.codeView.addHtmlPattern()
        binding.codeView.addJsPattern()
    }

    /**
     * 加载模板数据到界面
     * 
     * 将当前编辑模板的名称和HTML代码填充到输入框，
     * 设置默认预览参数并触发首次预览
     */
    private fun loadTemplateData() {
        lifecycleScope.launch {
            if (isNewTemplate) {
                binding.editTemplateName.setText("")
                binding.codeView.setText(DefaultData.coverHtmlTemplate)
                originalName = ""
                originalHtmlCode = DefaultData.coverHtmlTemplate
            } else {
                val currentTemplate = template ?: return@launch
                binding.editTemplateName.setText(currentTemplate.name)
                binding.codeView.setText(currentTemplate.htmlCode)
                originalName = currentTemplate.name
                originalHtmlCode = currentTemplate.htmlCode
            }
            hasUnsavedChanges = false
            binding.editBookName.setText("示例书名")
            binding.editAuthor.setText("示例作者")
            binding.webViewPreview.post {
                previewCover()
            }
        }
    }

    /**
     * 检测模板是否在模板列表中被切换
     * 
     * 从模板列表对话框返回后：
     * - 编辑模式：若当前选中模板与编辑中的模板ID不同，提示切换
     * - 新建模式：若用户在列表中选择了其他模板，提示是否保存当前编辑
     */
    private fun refreshIfTemplateChanged() {
        val selected = CoverHtmlTemplateConfig.getSelectedTemplate()
        val current = template
        
        if (isNewTemplate) {
            // 新建模式下，如果用户选择了其他模板（不是当前正在编辑的"虚拟"模板）
            // 检查是否有未保存的内容
            val currentName = binding.editTemplateName.text?.toString()?.trim() ?: ""
            val currentHtmlCode = binding.codeView.text?.toString()?.trim() ?: ""
            val nameChanged = currentName != originalName
            val htmlChanged = currentHtmlCode != originalHtmlCode
            
            if (nameChanged || htmlChanged) {
                // 有编辑内容，提示是否保存
                showSaveConfirmDialogForNewTemplate(selected)
            } else {
                // 无编辑内容，直接切换
                switchToTemplateFromNew(selected)
            }
        } else if (current != null && selected.id != current.id) {
            // 编辑模式，切换到其他模板
            checkUnsavedChangesAndSwitch(selected)
        }
    }

    private fun showSaveConfirmDialogForNewTemplate(selected: CoverHtmlTemplateConfig.Template) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle(R.string.cover_html_save_changes)
            .setMessage(R.string.cover_html_unsaved_hint)
            .setPositiveButton(R.string.action_save) { _, _ ->
                saveTemplate()
                switchToTemplateFromNew(selected)
            }
            .setNegativeButton(R.string.discard) { _, _ ->
                switchToTemplateFromNew(selected)
            }
            .setNeutralButton(R.string.cancel, null)
            .create()
            .show()
    }

    private fun switchToTemplateFromNew(newTemplate: CoverHtmlTemplateConfig.Template) {
        isNewTemplate = false
        template = newTemplate
        binding.editTemplateName.setText(newTemplate.name)
        binding.codeView.setText(newTemplate.htmlCode)
        originalName = newTemplate.name
        originalHtmlCode = newTemplate.htmlCode
        hasUnsavedChanges = false
        previewCover()
    }

    private fun checkUnsavedChangesAndSwitch(newTemplate: CoverHtmlTemplateConfig.Template) {
        val currentName = binding.editTemplateName.text?.toString()?.trim() ?: ""
        val currentHtmlCode = binding.codeView.text?.toString()?.trim() ?: ""
        
        val nameChanged = currentName != originalName
        val htmlChanged = currentHtmlCode != originalHtmlCode
        hasUnsavedChanges = nameChanged || htmlChanged
        
        if (hasUnsavedChanges) {
            showSaveConfirmDialog(newTemplate)
        } else {
            switchToTemplate(newTemplate)
        }
    }

    private fun showSaveConfirmDialog(newTemplate: CoverHtmlTemplateConfig.Template) {
        val context = context ?: return
        AlertDialog.Builder(context)
            .setTitle(R.string.cover_html_save_changes)
            .setMessage(R.string.cover_html_unsaved_hint)
            .setPositiveButton(R.string.action_save) { _, _ ->
                saveTemplate()
                switchToTemplate(newTemplate)
            }
            .setNegativeButton(R.string.discard) { _, _ ->
                switchToTemplate(newTemplate)
            }
            .setNeutralButton(R.string.cancel, null)
            .create()
            .show()
    }

    private fun switchToTemplate(newTemplate: CoverHtmlTemplateConfig.Template) {
        template = newTemplate
        isNewTemplate = false
        binding.editTemplateName.setText(newTemplate.name)
        binding.codeView.setText(newTemplate.htmlCode)
        originalName = newTemplate.name
        originalHtmlCode = newTemplate.htmlCode
        hasUnsavedChanges = false
        previewCover()
    }

    /**
     * 初始化点击事件监听
     */
    private fun initClickListeners() {
        binding.tvPreview.onClick {
            previewCover()
        }

        binding.tvCancel.onClick {
            dismissAllowingStateLoss()
        }

        binding.tvOk.onClick {
            saveTemplate()
            dismissAllowingStateLoss()
        }

        binding.tvFooterLeft.onClick {
            binding.editTemplateName.setText("")
            binding.codeView.setText(DefaultData.coverHtmlTemplate)
        }
    }

    /**
     * 预览封面
     * 
     * 获取用户输入的书名、作者和HTML模板，
     * 替换变量后在WebView中渲染预览
     */
    private fun previewCover() {
        val htmlTemplate = binding.codeView.text?.toString() ?: return
        val bookName = binding.editBookName.text?.toString() ?: "书名"
        val author = binding.editAuthor.text?.toString() ?: "作者"

        val renderedHtml = BookCover.renderHtmlTemplate(htmlTemplate, bookName, author)
        binding.webViewPreview.loadDataWithBaseURL(
            "about:blank",
            renderedHtml,
            "text/html",
            "UTF-8",
            null
        )
    }

    /**
     * 保存模板
     * 
     * 校验HTML代码不为空，将编辑内容更新到当前模板并持久化，
     * 同时清除封面缓存使书架上的封面重新生成
     */
    private fun saveTemplate() {
        val name = binding.editTemplateName.text?.toString()?.trim() ?: ""
        val htmlCode = binding.codeView.text?.toString()?.trim() ?: ""

        if (htmlCode.isBlank()) {
            context?.toastOnUi(R.string.cover_html_code_empty)
            return
        }

        if (isNewTemplate) {
            val newTemplate = CoverHtmlTemplateConfig.Template(
                id = CoverHtmlTemplateConfig.generateId(),
                name = name.ifEmpty { "未命名模板" },
                htmlCode = htmlCode,
                isSelected = true
            )
            CoverHtmlTemplateConfig.addTemplate(newTemplate)
            CoverHtmlTemplateConfig.setSelectedTemplate(newTemplate.id)
        } else {
            val existingTemplate = template?.copy(
                name = name.ifEmpty { "未命名模板" },
                htmlCode = htmlCode
            ) ?: return
            CoverHtmlTemplateConfig.updateTemplate(existingTemplate)
        }
        CoverImageView.clearHtmlCoverCache()
        postEvent(EventBus.BOOKSHELF_REFRESH, "")
    }

}
