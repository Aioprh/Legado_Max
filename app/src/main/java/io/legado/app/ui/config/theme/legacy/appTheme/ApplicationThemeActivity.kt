package io.legado.app.ui.config.theme.legacy.appTheme

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.net.Uri
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityThemeManageBinding
import io.legado.app.databinding.ItemThemeConfigBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ApplicationThemeManager
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.selector
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.GSON
import io.legado.app.utils.applyNavigationBarMargin
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.observeEvent
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.ui.config.theme.legacy.appTheme.ApplicationThemeEditActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import java.io.File
import java.io.FileOutputStream

private const val MENU_CREATE = 6101
private const val MENU_IMPORT = 6102
private const val MENU_EXPORT = 6103

private data class ApplicationThemeListItem(
    val config: ApplicationThemeManager.Config,
    val summary: String,
    val isCurrent: Boolean
)

/**
 * 应用主题管理 Activity。
 *
 * 展示所有应用主题方案列表，支持创建、导入、导出、应用、编辑、删除。
 * 每个主题方案打包了日间/夜间主题、顶栏、底栏、封面图集的完整配置。
 * 列表项展示当前模式的预览效果（背景色 + 主色条）。
 */
class ApplicationThemeActivity : BaseActivity<ActivityThemeManageBinding>() {

    override val binding by viewBinding(ActivityThemeManageBinding::inflate)
    private val adapter by lazy { Adapter(this) }
    private val importTheme = registerForActivityResult(HandleFileContract()) {
        it.uri?.let(::importTheme)
    }
    private val exportTheme = registerForActivityResult(HandleFileContract()) {
        if (it.uri != null) toastOnUi(R.string.export_success)
    }

    /**
     * Activity 创建时的初始化。
     * 设置标题、摘要、列表适配器和点击监听。
     */
    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.title = getString(R.string.application_theme_manage)
        binding.tabContainer.visibility = View.GONE
        binding.tvSummary.text = getString(R.string.application_theme_summary)
        binding.tvAddTheme.text = getString(R.string.application_theme_create)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
        binding.tvAddTheme.setOnClickListener { showNameDialog() }
        binding.tvAddTheme.applyNavigationBarMargin(withInitialMargin = true)
        refresh()
    }

    /**
     * Activity 恢复时刷新列表数据。
     */
    override fun onResume() {
        super.onResume()
        refresh()
    }

    /**
     * 监听 LiveBus 事件。
     * 当收到 RECREATE 事件时通知适配器刷新。
     */
    override fun observeLiveBus() {
        observeEvent<String>(EventBus.RECREATE) {
            adapter.notifyDataSetChanged()
        }
    }

    /**
     * 创建选项菜单。
     * 添加创建、导入、导出三个菜单项。
     */
    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, MENU_CREATE, 0, R.string.application_theme_create)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_IMPORT, 1, R.string.application_theme_import)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, MENU_EXPORT, 2, R.string.application_theme_export)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }

    /**
     * 处理选项菜单项点击事件。
     * 根据菜单项 ID 执行对应操作。
     */
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_CREATE -> { showNameDialog(); true }
            MENU_IMPORT -> { showImportOptionsDialog(); true }
            MENU_EXPORT -> { exportCurrent(); true }
            else -> super.onCompatOptionsItemSelected(item)
        }
    }

    /**
     * 启动文件选择器以选择要导入的主题文件。
     * 支持 zip 和 json 格式。
     */
    private fun selectImport() {
        importTheme.launch {
            mode = HandleFileContract.FILE
            title = getString(R.string.application_theme_import)
            allowExtensions = arrayOf("zip", "json")
        }
    }

    /**
     * 显示导入选项对话框。
     * 允许用户选择导入时包含哪些组件（主题、顶栏、底栏、封面）以及对应的模式（日间/夜间）。
     * 确认后保存选项并启动文件选择器。
     */
    private fun showImportOptionsDialog() {
        val saved = ApplicationThemeManager.getImportOptions(this)
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 32, 48, 16)
        }
        val tvHint = TextView(this).apply {
            text = getString(R.string.application_theme_import_options_hint)
            setPadding(0, 0, 0, 24)
        }
        container.addView(tvHint)

        fun addRow(labelRes: Int, dayChecked: Boolean, nightChecked: Boolean): Pair<CheckBox, CheckBox> {
            val tvLabel = TextView(this).apply {
                text = getString(labelRes)
                setPadding(0, 12, 0, 4)
                paintFlags = paintFlags or android.graphics.Paint.FAKE_BOLD_TEXT_FLAG
            }
            container.addView(tvLabel)
            val cbDay = CheckBox(this).apply {
                text = getString(R.string.day)
                isChecked = dayChecked
            }
            val cbNight = CheckBox(this).apply {
                text = getString(R.string.night)
                isChecked = nightChecked
            }
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(48, 0, 0, 0)
                addView(cbDay)
                addView(cbNight)
            }
            container.addView(row)
            return cbDay to cbNight
        }

        val (cbDayTheme, cbNightTheme) = addRow(R.string.application_theme_component_theme, saved.importDayTheme, saved.importNightTheme)
        val (cbDayTopBar, cbNightTopBar) = addRow(R.string.application_theme_component_top_bar, saved.importDayTopBar, saved.importNightTopBar)
        val (cbDayBottomBar, cbNightBottomBar) = addRow(R.string.application_theme_component_bottom_bar, saved.importDayBottomBar, saved.importNightBottomBar)
        val (cbDayCover, cbNightCover) = addRow(R.string.application_theme_component_cover, saved.importDayCover, saved.importNightCover)

        alert(R.string.application_theme_import_with_options) {
            customView { container }
            okButton {
                ApplicationThemeManager.saveImportOptions(
                    this@ApplicationThemeActivity,
                    ApplicationThemeManager.ImportOptions(
                        importDayTheme = cbDayTheme.isChecked,
                        importNightTheme = cbNightTheme.isChecked,
                        importDayTopBar = cbDayTopBar.isChecked,
                        importNightTopBar = cbNightTopBar.isChecked,
                        importDayBottomBar = cbDayBottomBar.isChecked,
                        importNightBottomBar = cbNightBottomBar.isChecked,
                        importDayCover = cbDayCover.isChecked,
                        importNightCover = cbNightCover.isChecked
                    )
                )
                selectImport()
            }
            cancelButton()
        }
    }

    /**
     * 从指定 URI 导入主题配置。
     * 将文件复制到临时目录后调用 [ApplicationThemeManager.importFile] 解析。
     *
     * @param uri 要导入的文件 URI
     */
    private fun importTheme(uri: Uri) {
        val options = ApplicationThemeManager.getImportOptions(this)
        lifecycleScope.launch {
            runCatching {
                val file = externalFiles.getFile("applicationThemeImports", "import_${System.currentTimeMillis()}.json")
                file.parentFile?.mkdirs()
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                } ?: error(getString(R.string.file_not_exist))
                withContext(Dispatchers.IO) {
                    try {
                        ApplicationThemeManager.importFile(file, options)
                    } finally {
                        file.delete()
                    }
                }
            }.onSuccess {
                toastOnUi(R.string.import_success)
                refresh()
            }.onFailure {
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
            }
        }
    }

    /**
     * 导出当前应用的主题配置。
     * 将当前配置打包后通过系统分享/保存对话框导出。
     */
    private fun exportCurrent() {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ApplicationThemeManager.exportCurrent(this@ApplicationThemeActivity) }
            }.onSuccess { file ->
                exportTheme.launch {
                    mode = HandleFileContract.EXPORT
                    title = getString(R.string.application_theme_export)
                    fileData = HandleFileContract.FileData(file.name, file, "application/zip")
                    onlyOtherActions = true
                    otherActions = arrayListOf(
                        SelectItem(getString(R.string.sys_folder_picker), HandleFileContract.DIR),
                        SelectItem(getString(R.string.app_folder_picker), 10),
                        SelectItem(getString(R.string.manual_input), 112)
                    )
                }
            }.onFailure {
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
            }
        }
    }

    /**
     * 刷新主题列表。
     * 从 [ApplicationThemeManager] 加载所有配置并更新适配器。
     */
    private fun refresh() {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    ApplicationThemeManager.load().map { config ->
                        ApplicationThemeListItem(
                            config = config,
                            summary = ApplicationThemeManager.summary(this@ApplicationThemeActivity, config),
                            isCurrent = ApplicationThemeManager.isCurrent(this@ApplicationThemeActivity, config)
                        )
                    }
                }
            }.onSuccess { items ->
                    adapter.setItems(items)
                    binding.tvMsg.visibility = if (items.isEmpty()) View.VISIBLE else View.GONE
                    binding.tvMsg.text = getString(R.string.application_theme_empty)
                }
                .onFailure {
                    adapter.setItems(emptyList())
                    binding.tvMsg.visibility = View.VISIBLE
                    binding.tvMsg.text = it.localizedMessage ?: getString(R.string.error)
                }
            }
    }

    /**
     * 显示名称输入对话框。
     * 用于创建新主题或重命名现有主题。
     *
     * @param config 要重命名的配置，为 null 时表示创建新主题
     */
    private fun showNameDialog(config: ApplicationThemeManager.Config? = null) {
        val input = EditText(this).apply {
            hint = getString(R.string.application_theme_name)
            setText(config?.name.orEmpty())
            setSelection(text.length)
        }
        alert(if (config == null) R.string.application_theme_create else R.string.application_theme_rename) {
            customView { input }
            okButton {
                val name = input.text.toString().trim()
                if (name.isBlank()) {
                    toastOnUi(R.string.input_is_empty)
                    return@okButton
                }
                runCatching {
                    if (config == null) {
                        val created = ApplicationThemeManager.captureCurrent(this@ApplicationThemeActivity, name)
                        ApplicationThemeManager.add(created)
                        openEditor(created)
                    } else {
                        ApplicationThemeManager.rename(config.id, name)
                    }
                }.onSuccess { refresh() }
                    .onFailure { toastOnUi(R.string.application_theme_name_exists) }
            }
            cancelButton()
        }
    }

    /**
     * 应用指定的主题配置。
     * 应用成功后显示提示并重建 Activity 以刷新界面。
     *
     * @param config 要应用的主题配置
     */
    private fun apply(config: ApplicationThemeManager.Config) {
        runCatching { ApplicationThemeManager.apply(this, config) }
            .onSuccess {
                toastOnUi(R.string.application_theme_applied)
                recreate()
            }
            .onFailure { toastOnUi(it.localizedMessage ?: getString(R.string.error)) }
    }

    /**
     * 显示主题操作选择器。
     * 提供编辑、更新当前、重命名、导出、删除等操作选项。
     *
     * @param config 要操作的主题配置
     */
    private fun showActions(config: ApplicationThemeManager.Config) {
        val items = listOf(
            getString(R.string.edit),
            getString(R.string.application_theme_update_current),
            getString(R.string.application_theme_rename),
            getString(R.string.export),
            getString(R.string.delete)
        )
        selector(config.name, items) { _, index ->
            when (index) {
                0 -> openEditor(config)
                1 -> {
                    ApplicationThemeManager.replace(
                        ApplicationThemeManager.captureCurrent(this, config.name, config.id)
                    )
                    toastOnUi(R.string.success)
                    refresh()
                }
                2 -> showNameDialog(config)
                3 -> exportConfig(config)
                4 -> confirmDelete(config)
            }
        }
    }

    /**
     * 导出指定的主题配置。
     * 将配置打包后通过系统分享/保存对话框导出。
     *
     * @param config 要导出的主题配置
     */
    private fun exportConfig(config: ApplicationThemeManager.Config) {
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { ApplicationThemeManager.exportConfig(this@ApplicationThemeActivity, config) }
            }.onSuccess { file ->
                exportTheme.launch {
                    mode = HandleFileContract.EXPORT
                    title = getString(R.string.application_theme_export)
                    fileData = HandleFileContract.FileData(file.name, file, "application/zip")
                    onlyOtherActions = true
                    otherActions = arrayListOf(
                        SelectItem(getString(R.string.sys_folder_picker), HandleFileContract.DIR),
                        SelectItem(getString(R.string.app_folder_picker), 10),
                        SelectItem(getString(R.string.manual_input), 112)
                    )
                }
            }.onFailure {
                toastOnUi(it.localizedMessage ?: getString(R.string.error))
            }
        }
    }

    /**
     * 打开主题编辑界面。
     *
     * @param config 要编辑的主题配置
     */
    private fun openEditor(config: ApplicationThemeManager.Config) {
        startActivity<ApplicationThemeEditActivity> {
            putExtra(ApplicationThemeEditActivity.EXTRA_ID, config.id)
        }
    }

    /**
     * 将主题配置的 JSON 复制到剪贴板。
     *
     * @param config 要复制的主题配置
     */
    private fun copyThemeJson(config: ApplicationThemeManager.Config) {
        runCatching {
            val json = GSON.toJson(config)
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("application_theme", json))
        }.onSuccess {
            toastOnUi(R.string.copy_complete)
        }.onFailure {
            toastOnUi(it.localizedMessage ?: getString(R.string.error))
        }
    }

    /**
     * 显示删除确认对话框。
     * 用户确认后删除指定的主题配置。
     *
     * @param config 要删除的主题配置
     */
    private fun confirmDelete(config: ApplicationThemeManager.Config) {
        alert(R.string.delete, R.string.sure_del) {
            okButton {
                ApplicationThemeManager.delete(this@ApplicationThemeActivity, config.id)
                refresh()
            }
            cancelButton()
        }
    }

    private inner class Adapter(context: Context) :
        RecyclerAdapter<ApplicationThemeListItem, ItemThemeConfigBinding>(context) {

        /**
         * 创建列表项的视图绑定。
         */
        override fun getViewBinding(parent: ViewGroup): ItemThemeConfigBinding {
            return ItemThemeConfigBinding.inflate(inflater, parent, false)
        }

        /**
         * 绑定数据到列表项视图。
         * 设置名称、摘要、预览效果和各按钮状态。
         */
        override fun convert(
            holder: ItemViewHolder,
            binding: ItemThemeConfigBinding,
            item: ApplicationThemeListItem,
            payloads: MutableList<Any>
        ) = binding.run {
            val config = item.config
            tvName.text = config.name
            tvInfo.text = item.summary
            tvInfo.maxLines = 2
            tvBuiltin.visibility = View.GONE
            tvEdit.visibility = View.VISIBLE
            tvCopy.visibility = View.VISIBLE
            cbSelect.visibility = View.GONE
            ivShare.visibility = View.GONE
            ivDelete.visibility = View.GONE
            ivCurrent.visibility = if (item.isCurrent) View.VISIBLE else View.GONE
            tvApply.text = getString(if (item.isCurrent) R.string.applied else R.string.apply)
            tvApply.setTextColor(
                if (item.isCurrent) accentColor else ContextCompat.getColor(context, R.color.primaryText)
            )
            val isNight = AppConfig.isNightTheme
            val previewTheme = if (isNight) config.nightTheme else config.dayTheme
            val background = parseThemeColor(
                previewTheme?.backgroundColor,
                if (isNight) R.color.default_night_background else R.color.default_background
            )
            val primary = parseThemeColor(
                previewTheme?.primaryColor,
                if (isNight) R.color.default_night_primary else R.color.default_primary
            )
            previewContainer.elevation = 8.dp.toFloat()
            previewContainer.translationZ = 2.dp.toFloat()
            previewContainer.background = rounded(background, 10f)
            val backgroundPath = previewTheme?.backgroundImgPath
                ?.takeIf { !it.startsWith("http", ignoreCase = true) && File(it).isFile }
            Glide.with(previewBackground).clear(previewBackground)
            if (backgroundPath == null) {
                previewBackground.visibility = View.GONE
                previewBackground.setImageDrawable(null)
            } else {
                previewBackground.visibility = View.VISIBLE
                Glide.with(previewBackground)
                    .load(File(backgroundPath))
                    .override(74.dp, 102.dp)
                    .centerCrop()
                    .into(previewBackground)
            }
            previewPrimary.background = rounded(primary, 4f)
            previewBar1.background = rounded(primary, 2f, 77)
            previewBar2.background = rounded(primary, 2f, 51)
        }

        /**
         * 注册列表项的点击监听器。
         * 包括应用、更多操作、编辑、复制和根视图点击。
         */
        override fun registerListener(holder: ItemViewHolder, binding: ItemThemeConfigBinding) {
            binding.tvApply.setOnClickListener {
                getItem(holder.layoutPosition)?.config?.let(::apply)
            }
            binding.tvMore.setOnClickListener {
                getItem(holder.layoutPosition)?.config?.let(::showActions)
            }
            binding.tvEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.config?.let(::openEditor)
            }
            binding.tvCopy.setOnClickListener {
                getItem(holder.layoutPosition)?.config?.let(::copyThemeJson)
            }
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.config?.let(::apply)
            }
        }

        /**
         * 创建圆角矩形 Drawable。
         *
         * @param color   填充颜色
         * @param radius  圆角半径（px）
         * @param opacity 透明度（0-255）
         */
        private fun rounded(color: Int, radius: Float, opacity: Int = 255) = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
            alpha = opacity
        }
    }

    /**
     * 解析主题颜色字符串。
     * 解析失败时返回备用颜色。
     *
     * @param value    颜色字符串（如 "#RRGGBB"）
     * @param fallback 备用颜色资源 ID
     * @return 解析后的颜色值
     */
    private fun parseThemeColor(value: String?, fallback: Int): Int {
        return runCatching { Color.parseColor(value) }
            .getOrDefault(ContextCompat.getColor(this, fallback))
    }

    /**
     * dp 转 px 的扩展属性。
     */
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
