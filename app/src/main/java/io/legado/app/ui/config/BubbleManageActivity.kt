package io.legado.app.ui.config

import android.app.Activity.RESULT_OK
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.graphics.toColorInt
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.SimpleItemAnimator
import com.jaredrummler.android.colorpicker.ColorPickerDialog
import com.jaredrummler.android.colorpicker.ColorPickerDialogListener
import androidx.annotation.StringRes
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.constant.EventBus
import io.legado.app.databinding.ActivityThemeManageBinding
import io.legado.app.help.ExportResultHandler
import io.legado.app.help.config.BubblePackageManager
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.dialogs.selector
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.applyUiBodyTypefaceDeep
import io.legado.app.lib.theme.applyUiLabelStyle
import io.legado.app.lib.theme.applyUiSectionTitleStyle
import io.legado.app.lib.theme.uiTypeface
import io.legado.app.model.ImageProvider
import io.legado.app.ui.code.CodeEditActivity
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.widget.number.NumberPickerDialog
import io.legado.app.utils.SvgUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getFile
import io.legado.app.utils.postEvent
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.showHelp
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayInputStream
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 段评气泡管理 Activity。
 *
 * 管理段评气泡包的配置，包括 SVG 模板编辑、缩放比例、
 * 日夜间常规/强调色配置。内置气泡包只读，用户可创建自定义包。
 * 支持内置代码编辑器编辑 SVG 模板、zip 导入导出、实时预览。
 * 支持长按列表项进入多选模式，可批量导出、置顶、删除。
 * 列表项展示气泡预览图和来源信息。
 */
class BubbleManageActivity : BaseActivity<ActivityThemeManageBinding>(), ColorPickerDialogListener {

    override val binding by viewBinding(ActivityThemeManageBinding::inflate)

    private val adapter = Adapter()
    private val dateFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    private var editingConfig: BubblePackageManager.Config? = null
    private var editingRoot: LinearLayout? = null
    private var svgCursorPosition: Int = 0
    private val selectedPositions = mutableSetOf<Int>()
    private var isMultiSelectMode = false
    private val importPackage = registerForActivityResult(HandleFileContract()) {
        it.uri?.let(::importZip)
    }
    private val exportPackage = registerForActivityResult(HandleFileContract()) {
        ExportResultHandler.handleExportResult(this, it) { text ->
            sendToClip(text)
        }
    }
    private val svgEditLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            result.data?.getStringExtra("text")?.let { text ->
                editingConfig = editingConfig?.copy(svgTemplate = text)
                svgCursorPosition = result.data?.getIntExtra("cursorPosition", text.length) ?: text.length
                refreshEditDialog()
            }
        }
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initView()
        loadPackages()
    }

    override fun onResume() {
        super.onResume()
        loadPackages()
    }

    private fun initView() = binding.run {
        titleBar.title = getString(R.string.bubble_manage)
        tabContainer.visibility = View.GONE
        tvSummary.text = getString(R.string.bubble_manage_summary)
        tvSummary.setTextColor(themeSecondaryTextColor())
        // 强制软件气泡开关
        val switchRow = createForceSoftwareBubbleRow()
        (root as? LinearLayout)?.let { layout ->
            val index = layout.indexOfChild(tvSummary)
            layout.addView(switchRow, index + 1)
        }
        recyclerView.layoutManager = LinearLayoutManager(this@BubbleManageActivity)
        recyclerView.adapter = adapter
        (recyclerView.itemAnimator as? SimpleItemAnimator)?.supportsChangeAnimations = false
        tvAddTheme.text = getString(R.string.add)
        tvAddTheme.setTextColor(themePrimaryTextColor())
        tvAddTheme.background = UiCorner.actionSelector(
            ContextCompat.getColor(this@BubbleManageActivity, R.color.background_card),
            ContextCompat.getColor(this@BubbleManageActivity, R.color.background_menu),
            UiCorner.actionRadius(this@BubbleManageActivity)
        )
        tvAddTheme.setOnClickListener { showAddActions() }
        root.applyUiBodyTypefaceDeep(uiTypeface())
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        if (isMultiSelectMode) {
            menuInflater.inflate(R.menu.bubble_list_multi, menu)
            menu.applyTint(this)
        } else {
            menu.add(0, MENU_HELP, 0, R.string.help).apply {
                setIcon(R.drawable.ic_help)
                setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
            }
        }
        return true
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            MENU_HELP -> { showBubbleHelp(); true }
            R.id.menu_select_all -> { selectAllOrClear(); true }
            R.id.menu_to_top -> { toTopSelected(); true }
            R.id.menu_export -> { exportSelected(); true }
            R.id.menu_delete -> { deleteSelected(); true }
            else -> super.onCompatOptionsItemSelected(item)
        }
    }

    override fun onBackPressed() {
        if (isMultiSelectMode) {
            exitMultiSelectMode()
        } else {
            super.onBackPressed()
        }
    }

    private fun loadPackages() {
        adapter.items = BubblePackageManager.loadEntries()
    }

    private fun showAddActions() {
        selector(getString(R.string.add), listOf(getString(R.string.bubble_create_manual), getString(R.string.bubble_import_zip))) { _, index ->
            when (index) {
                0 -> showEditDialog(null)
                1 -> importPackage.launch {
                    mode = HandleFileContract.FILE
                    title = getString(R.string.bubble_import_zip)
                    allowExtensions = arrayOf("zip")
                }
            }
        }
    }

    private fun showActions(entry: BubblePackageManager.Entry) {
        val actions = if (entry.source == BubblePackageManager.Source.BUILTIN) {
            listOf(Action.APPLY)
        } else {
            buildList {
                add(Action.APPLY)
                add(Action.EDIT)
                add(Action.EXPORT)
                add(Action.SHARE)
                if (entry.dirName != BubblePackageManager.activeDirName()) {
                    add(Action.DELETE)
                }
            }
        }
        selector(entry.config.name, actions.map { getString(it.titleRes) }) { _, index ->
            when (actions[index]) {
                Action.APPLY -> applyEntry(entry)
                Action.EDIT -> showEditDialog(entry)
                Action.EXPORT -> exportPackage(entry)
                Action.SHARE -> sharePackage(entry)
                Action.DELETE -> confirmDelete(entry)
            }
        }
    }

    private fun showEditDialog(entry: BubblePackageManager.Entry?) {
        if (entry?.source == BubblePackageManager.Source.BUILTIN) return
        editingConfig = (entry?.config ?: BubblePackageManager.builtinConfig().copy(
            name = getString(R.string.bubble_custom_name),
            dirName = "",
            updatedAt = System.currentTimeMillis()
        )).copy(dirName = entry?.dirName.orEmpty())
        val root = buildEditView()
        editingRoot = root
        alert(if (entry == null) R.string.add else R.string.edit) {
            customView { root }
            okButton {
                captureEditFields()
                val config = editingConfig ?: return@okButton
                val next = config.copy(
                    name = root.findViewWithTag<EditText>(TAG_NAME)?.text?.toString().orEmpty(),
                    dirName = entry?.dirName.orEmpty()
                )
                runCatching {
                    requireValidSvg(next)
                    BubblePackageManager.addOrUpdate(next, entry)
                }.onSuccess {
                    if (entry?.dirName == BubblePackageManager.activeDirName()) {
                        notifyBubbleChanged()
                    }
                    loadPackages()
                }.onFailure {
                    toastOnUi(it.localizedMessage ?: getString(R.string.error))
                }
            }
            cancelButton()
        }
    }

    private fun buildEditView(): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(2.dp, 2.dp, 2.dp, 4.dp)
            populateEditView(this)
            applyUiBodyTypefaceDeep(this@BubbleManageActivity.uiTypeface())
        }
    }

    private fun populateEditView(root: LinearLayout) {
        val config = editingConfig ?: return
        root.addView(PackageManageUi.nameInput(this, config.name, getString(R.string.bubble_name)).apply { tag = TAG_NAME })
        root.addView(PackageManageUi.optionRow(this, getString(R.string.bubble_size_scale), "%.1f".format(Locale.ROOT, config.sizeScale)) {
            showSizeScalePicker()
        })
        root.addView(colorRow(getString(R.string.bubble_day_normal), colorOrDefault(config.dayNormalColor, false), COLOR_DAY_NORMAL))
        root.addView(colorRow(getString(R.string.bubble_day_emphasis), colorOrDefault(config.dayEmphasisColor, true), COLOR_DAY_EMPHASIS))
        root.addView(colorRow(getString(R.string.bubble_night_normal), colorOrDefault(config.nightNormalColor, false), COLOR_NIGHT_NORMAL))
        root.addView(colorRow(getString(R.string.bubble_night_emphasis), colorOrDefault(config.nightEmphasisColor, true), COLOR_NIGHT_EMPHASIS))
        root.addView(PackageManageUi.optionRow(this, getString(R.string.bubble_svg_edit_title), getString(R.string.bubble_svg_edit_hint)) {
            openSvgEditor()
        })
    }

    private fun captureEditFields() {
        val config = editingConfig ?: return
        val root = editingRoot ?: return
        editingConfig = config.copy(
            name = root.findViewWithTag<EditText>(TAG_NAME)?.text?.toString() ?: config.name
        )
    }

    private fun openSvgEditor() {
        captureEditFields()
        val svg = editingConfig?.svgTemplate.orEmpty()
        svgEditLauncher.launch(Intent(this, CodeEditActivity::class.java).apply {
            putExtra("text", svg)
            putExtra("title", getString(R.string.bubble_svg_template_title))
            putExtra("cursorPosition", svgCursorPosition.coerceIn(0, svg.length))
        })
    }

    private fun showSizeScalePicker() {
        captureEditFields()
        val config = editingConfig ?: return
        NumberPickerDialog(this, isDecimalMode = true)
            .setTitle(getString(R.string.bubble_size_scale))
            .setMinValue((BubblePackageManager.MIN_SIZE_SCALE * 10).toInt())
            .setMaxValue((BubblePackageManager.MAX_SIZE_SCALE * 10).toInt())
            .setValue((config.sizeScale.coerceIn(BubblePackageManager.MIN_SIZE_SCALE, BubblePackageManager.MAX_SIZE_SCALE) * 10).toInt())
            .show {
                captureEditFields()
                val latest = editingConfig ?: config
                editingConfig = latest.copy(
                    sizeScale = (it / 10f).coerceIn(BubblePackageManager.MIN_SIZE_SCALE, BubblePackageManager.MAX_SIZE_SCALE)
                )
                refreshEditDialog()
            }
    }

    private fun colorRow(title: String, value: String, target: Int): View {
        return PackageManageUi.optionRow(this, title, value.uppercase(Locale.ROOT), value.toColorInt()) {
            ColorPickerDialog.newBuilder()
                .setColor(value.toColorInt())
                .setShowAlphaSlider(false)
                .setDialogType(ColorPickerDialog.TYPE_CUSTOM)
                .setDialogId(target)
                .show(this)
        }
    }

    private fun refreshEditDialog() {
        val root = editingRoot ?: return
        root.removeAllViews()
        populateEditView(root)
    }

    private fun confirmDelete(entry: BubblePackageManager.Entry) {
        alert(R.string.delete) {
            setMessage(getString(R.string.sure_del))
            okButton {
                BubblePackageManager.deleteLocal(entry)
                notifyBubbleChanged()
                loadPackages()
            }
            cancelButton()
        }
    }

    private fun enterMultiSelectMode(position: Int) {
        isMultiSelectMode = true
        selectedPositions.clear()
        selectedPositions.add(position)
        binding.titleBar.title = getString(R.string.selected, selectedPositions.size)
        invalidateOptionsMenu()
        adapter.notifyDataSetChanged()
    }

    private fun exitMultiSelectMode() {
        if (!isMultiSelectMode) return
        isMultiSelectMode = false
        selectedPositions.clear()
        binding.titleBar.title = getString(R.string.bubble_manage)
        invalidateOptionsMenu()
        adapter.notifyDataSetChanged()
    }

    private fun toggleSelection(position: Int) {
        if (selectedPositions.contains(position)) {
            selectedPositions.remove(position)
            if (selectedPositions.isEmpty()) {
                exitMultiSelectMode()
                return
            }
        } else {
            selectedPositions.add(position)
        }
        adapter.notifyItemChanged(position)
        binding.titleBar.title = getString(R.string.selected, selectedPositions.size)
    }

    private fun selectAllOrClear() {
        if (selectedPositions.size == adapter.itemCount) {
            selectedPositions.clear()
        } else {
            selectedPositions.clear()
            for (i in 0 until adapter.itemCount) {
                selectedPositions.add(i)
            }
        }
        if (selectedPositions.isEmpty()) {
            exitMultiSelectMode()
        } else {
            binding.titleBar.title = getString(R.string.selected, selectedPositions.size)
            adapter.notifyDataSetChanged()
        }
    }

    private fun toTopSelected() {
        if (selectedPositions.isEmpty()) {
            toastOnUi(R.string.bubble_select_at_least_one)
            return
        }
        val entries = selectedPositions.sorted().mapNotNull { adapter.items.getOrNull(it) }
        BubblePackageManager.toTop(entries)
        exitMultiSelectMode()
        loadPackages()
    }

    private fun exportSelected() {
        if (selectedPositions.isEmpty()) {
            toastOnUi(R.string.bubble_select_at_least_one)
            return
        }
        val entries = selectedPositions.sorted().mapNotNull { adapter.items.getOrNull(it) }
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) { BubblePackageManager.exportZip(entries) }
            }.onSuccess { zip ->
                exportPackage.launch {
                    mode = HandleFileContract.EXPORT
                    title = getString(R.string.export_str)
                    fileData = HandleFileContract.FileData(zip.name, zip, "application/zip")
                }
                exitMultiSelectMode()
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }
        }
    }

    private fun deleteSelected() {
        if (selectedPositions.isEmpty()) {
            toastOnUi(R.string.bubble_select_at_least_one)
            return
        }
        alert(R.string.delete, R.string.sure_del) {
            yesButton {
                val entries = selectedPositions.sorted().mapNotNull { adapter.items.getOrNull(it) }
                entries.forEach { BubblePackageManager.deleteLocal(it) }
                exitMultiSelectMode()
                notifyBubbleChanged()
                loadPackages()
            }
            noButton()
        }
    }

    private fun exportPackage(entry: BubblePackageManager.Entry) {
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) { BubblePackageManager.exportZip(entry) }
            }.onSuccess { zip ->
                exportPackage.launch {
                    mode = HandleFileContract.EXPORT
                    title = getString(R.string.export_str)
                    fileData = HandleFileContract.FileData(zip.name, zip, "application/zip")
                }
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }
        }
    }

    private fun sharePackage(entry: BubblePackageManager.Entry) {
        lifecycleScope.launch {
            kotlin.runCatching {
                withContext(Dispatchers.IO) { BubblePackageManager.exportZip(entry) }
            }.onSuccess { zip ->
                share(zip, "application/zip")
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }
        }
    }

    private fun applyEntry(entry: BubblePackageManager.Entry) {
        BubblePackageManager.apply(entry)
        notifyBubbleChanged()
        loadPackages()
        toastOnUi(R.string.success)
    }

    private fun importZip(uri: Uri) {
        lifecycleScope.launch {
            kotlin.runCatching {
                val file = externalFiles.getFile("bubbleImports", "import_${System.currentTimeMillis()}.zip")
                file.parentFile?.mkdirs()
                contentResolver.openInputStream(uri)?.use { input ->
                    FileOutputStream(file).use { output -> input.copyTo(output) }
                } ?: throw IllegalArgumentException(getString(R.string.file_not_exist))
                withContext(Dispatchers.IO) { BubblePackageManager.importZip(file) }
            }.onSuccess { entries ->
                val msg = if (entries.size > 1) {
                    getString(R.string.bubble_import_count_success, entries.size)
                } else {
                    getString(R.string.import_success)
                }
                toastOnUi(msg)
                loadPackages()
            }.onFailure {
                toastOnUi(it.localizedMessage)
            }
        }
    }

    private fun notifyBubbleChanged() {
        ImageProvider.clear()
        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
    }

    private fun createForceSoftwareBubbleRow(): View {
        val density = resources.displayMetrics.density
        val dp = { value: Int -> (value * density).toInt() }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(12))
            background = UiCorner.panelRounded(
                this@BubbleManageActivity,
                ContextCompat.getColor(this@BubbleManageActivity, R.color.background_card),
                UiCorner.panelRadius(this@BubbleManageActivity)
            )
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(10)
                marginStart = dp(16)
                marginEnd = dp(16)
            }
        }
        val textContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val title = TextView(this).apply {
            text = getString(R.string.bubble_force_software)
            setTextColor(themePrimaryTextColor())
            textSize = 15f
            applyUiSectionTitleStyle(this@BubbleManageActivity)
        }
        val subtitle = TextView(this).apply {
            text = getString(R.string.bubble_force_software_summary)
            setTextColor(themeSecondaryTextColor())
            textSize = 12f
            setPadding(0, dp(4), 0, 0)
        }
        textContainer.addView(title)
        textContainer.addView(subtitle)
        val switch = SwitchCompat(this).apply {
            isChecked = AppConfig.forceSoftwareParagraphBubble
            setOnCheckedChangeListener { _, isChecked ->
                AppConfig.forceSoftwareParagraphBubble = isChecked
                notifyBubbleChanged()
            }
        }
        container.addView(textContainer)
        container.addView(switch)
        container.applyUiBodyTypefaceDeep(uiTypeface())
        return container
    }

    private fun requireValidSvg(config: BubblePackageManager.Config) {
        val color = config.dayNormalColor?.takeIf { it.isNotBlank() }
            ?: BubblePackageManager.DEFAULT_NORMAL_COLOR
        color.toColorInt()
        val svg = config.svgTemplate
            .replace("\${color}", color)
            .replace("\${num}", "1")
        require(SvgUtils.createBitmap(ByteArrayInputStream(svg.toByteArray()), 128, 128) != null) {
            getString(R.string.error_decode_bitmap)
        }
    }

    private fun previewBitmap(config: BubblePackageManager.Config) = runCatching {
        val color = config.dayEmphasisColor?.takeIf { it.isNotBlank() }
            ?: BubblePackageManager.DEFAULT_EMPHASIS_COLOR
        val svg = config.svgTemplate
            .replace("\${color}", color)
            .replace("\${num}", "12")
        SvgUtils.createBitmap(ByteArrayInputStream(svg.toByteArray()), 128, 128)
    }.getOrNull()

    override fun onColorSelected(dialogId: Int, color: Int) {
        captureEditFields()
        val config = editingConfig ?: return
        val hex = String.format(Locale.ROOT, "#%06X", color and 0x00FFFFFF)
        editingConfig = when (dialogId) {
            COLOR_DAY_NORMAL -> config.copy(dayNormalColor = hex)
            COLOR_DAY_EMPHASIS -> config.copy(dayEmphasisColor = hex)
            COLOR_NIGHT_NORMAL -> config.copy(nightNormalColor = hex)
            COLOR_NIGHT_EMPHASIS -> config.copy(nightEmphasisColor = hex)
            else -> config
        }
        refreshEditDialog()
    }

    override fun onDialogDismissed(dialogId: Int) = Unit

    private fun colorOrDefault(value: String?, emphasis: Boolean): String {
        val fallback = if (emphasis) {
            BubblePackageManager.DEFAULT_EMPHASIS_COLOR
        } else {
            BubblePackageManager.DEFAULT_NORMAL_COLOR
        }
        val normalized = value?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.startsWith("#")) it else "#$it" }
            ?: fallback
        return runCatching {
            normalized.toColorInt()
            normalized
        }.getOrDefault(fallback)
    }

    private fun showBubbleHelp() {
        showHelp("bubbleHelp")
    }

    private inner class Adapter : RecyclerView.Adapter<Adapter.Holder>() {
        private var activeDirName: String = BubblePackageManager.activeDirName()

        var items: List<BubblePackageManager.Entry> = emptyList()
            set(value) {
                field = value
                activeDirName = BubblePackageManager.activeDirName()
                notifyDataSetChanged()
            }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
            return Holder(createItemView(parent))
        }

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            holder.bind(items[position])
        }

        override fun onViewRecycled(holder: Holder) {
            holder.cancelPreview()
            super.onViewRecycled(holder)
        }

        private fun createItemView(parent: ViewGroup): LinearLayout {
            return LinearLayout(parent.context).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER_VERTICAL
                setPadding(12.dp, 10.dp, 12.dp, 10.dp)
                minimumHeight = BUBBLE_ITEM_MIN_HEIGHT_DP.dp
                background = UiCorner.panelRounded(
                    this@BubbleManageActivity,
                    ContextCompat.getColor(parent.context, R.color.background_card),
                    UiCorner.panelRadius(this@BubbleManageActivity)
                )
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    topMargin = 10.dp
                }
            }
        }

        inner class Holder(private val itemRoot: LinearLayout) : RecyclerView.ViewHolder(itemRoot) {
            private var previewJob: Job? = null
            private val preview = ImageView(itemRoot.context).apply {
                layoutParams = LinearLayout.LayoutParams(BUBBLE_PREVIEW_BOX_DP.dp, BUBBLE_PREVIEW_BOX_DP.dp)
                scaleType = ImageView.ScaleType.CENTER_INSIDE
                setPadding(6.dp, 6.dp, 6.dp, 6.dp)
                background = UiCorner.opaqueRounded(
                    ContextCompat.getColor(itemRoot.context, R.color.background_menu),
                    UiCorner.actionRadius(this@BubbleManageActivity)
                )
            }
            private val textBox = LinearLayout(itemRoot.context).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                    leftMargin = 12.dp
                    rightMargin = 12.dp
                }
            }
            private val title = TextView(itemRoot.context).apply {
                applyUiSectionTitleStyle(this@BubbleManageActivity)
                setTextColor(themePrimaryTextColor())
            }
            private val info = TextView(itemRoot.context).apply {
                applyUiLabelStyle(this@BubbleManageActivity)
                setTextColor(themeSecondaryTextColor())
            }
            private val action = TextView(itemRoot.context).apply {
                gravity = android.view.Gravity.CENTER
                minWidth = 62.dp
                minHeight = 36.dp
                background = UiCorner.actionSelector(
                    Color.TRANSPARENT,
                    ContextCompat.getColor(itemRoot.context, R.color.background_menu),
                    UiCorner.actionRadius(this@BubbleManageActivity)
                )
                setTextColor(themePrimaryTextColor())
                typeface = this@BubbleManageActivity.uiTypeface()
            }
            private val checkBox = CheckBox(itemRoot.context).apply {
                layoutParams = LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    marginStart = 4.dp
                }
                isClickable = false
                isFocusable = false
            }

            init {
                textBox.addView(title)
                textBox.addView(info)
                itemRoot.addView(preview)
                itemRoot.addView(textBox)
                itemRoot.addView(action)
                itemRoot.addView(checkBox)
            }

            fun bind(entry: BubblePackageManager.Entry) {
                val active = activeDirName == entry.dirName
                val previewKey = buildPreviewKey(entry)
                title.text = entry.config.name
                title.setTextColor(themePrimaryTextColor())
                info.text = buildString {
                    if (active) append("${getString(R.string.applied)} · ")
                    append(sourceLabel(entry.source))
                    append(" · ")
                    append(if (entry.config.updatedAt > 0L) dateFormat.format(Date(entry.config.updatedAt)) else getString(R.string.bubble_source_builtin))
                }
                info.setTextColor(themeSecondaryTextColor())
                preview.setTag(R.id.bubble_preview_key, previewKey)
                preview.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
                previewJob?.cancel()
                previewJob = lifecycleScope.launch {
                    val bitmap = withContext(Dispatchers.IO) {
                        previewBitmap(entry.config)
                    }
                    if (preview.getTag(R.id.bubble_preview_key) != previewKey) {
                        return@launch
                    }
                    if (bitmap != null) {
                        preview.setImageBitmap(bitmap)
                    } else {
                        preview.setImageDrawable(ColorDrawable(Color.TRANSPARENT))
                    }
                }
                if (isMultiSelectMode) {
                    action.visibility = View.GONE
                    checkBox.visibility = View.VISIBLE
                    checkBox.isChecked = selectedPositions.contains(layoutPosition)
                    itemRoot.setOnClickListener { toggleSelection(layoutPosition) }
                    itemRoot.setOnLongClickListener(null)
                } else {
                    action.visibility = View.VISIBLE
                    checkBox.visibility = View.GONE
                    action.text = if (active) getString(R.string.applied) else getString(R.string.apply)
                    action.setTextColor(if (active) accentColor else themePrimaryTextColor())
                    action.setOnClickListener { applyEntry(entry) }
                    itemRoot.setOnClickListener { showActions(entry) }
                    itemRoot.setOnLongClickListener {
                        if (entry.source != BubblePackageManager.Source.BUILTIN) {
                            enterMultiSelectMode(layoutPosition)
                        }
                        true
                    }
                }
            }

            fun cancelPreview() {
                previewJob?.cancel()
                previewJob = null
                preview.setTag(R.id.bubble_preview_key, null)
            }

            private fun buildPreviewKey(entry: BubblePackageManager.Entry): String {
                val config = entry.config
                return buildString {
                    append(entry.dirName)
                    append('#')
                    append(config.updatedAt)
                    append('#')
                    append(config.svgTemplate.hashCode())
                    append('#')
                    append(config.dayEmphasisColor)
                }
            }
        }
    }

    private fun sourceLabel(source: BubblePackageManager.Source): String {
        return when (source) {
            BubblePackageManager.Source.BUILTIN -> getString(R.string.bubble_source_builtin)
            BubblePackageManager.Source.LOCAL -> getString(R.string.bubble_source_local)
        }
    }

    private fun themePrimaryTextColor(): Int {
        return ContextCompat.getColor(this, R.color.primaryText)
    }

    private fun themeSecondaryTextColor(): Int {
        return ContextCompat.getColor(this, R.color.secondaryText)
    }

    private enum class Action(@StringRes val titleRes: Int) {
        APPLY(R.string.apply),
        EDIT(R.string.edit),
        EXPORT(R.string.export_str),
        SHARE(R.string.share),
        DELETE(R.string.delete)
    }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private companion object {
        private const val MENU_HELP = 0x6802
        private const val COLOR_DAY_NORMAL = 0x6811
        private const val COLOR_DAY_EMPHASIS = 0x6812
        private const val COLOR_NIGHT_NORMAL = 0x6813
        private const val COLOR_NIGHT_EMPHASIS = 0x6814
        private const val TAG_NAME = "name"
        private const val BUBBLE_PREVIEW_BOX_DP = 64
        private const val BUBBLE_ITEM_MIN_HEIGHT_DP = 86
    }
}
