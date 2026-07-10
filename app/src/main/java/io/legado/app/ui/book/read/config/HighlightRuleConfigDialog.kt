package io.legado.app.ui.book.read.config

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogHighlightRuleConfigBinding
import io.legado.app.databinding.ItemHighlightPresetRuleBinding
import io.legado.app.help.book.isLocal
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.model.ReadBook
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getClipText
import io.legado.app.utils.observeEvent
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.ui.book.read.config.highlight.HighlightRule
import io.legado.app.ui.book.read.config.highlight.HighlightRuleConfigViewModel

/**
 * 阅读高亮规则的配置弹窗。
 *
 * 负责展示规则列表、分组筛选、菜单入口、导入导出和编辑弹窗调度；
 * 规则状态和保存动作由 `HighlightRuleConfigViewModel` 承担。
 */
class HighlightRuleConfigDialog : BaseDialogFragment(R.layout.dialog_highlight_rule_config),
    PopupMenu.OnMenuItemClickListener {

    private val binding by viewBinding(DialogHighlightRuleConfigBinding::bind)
    private val viewModel: HighlightRuleConfigViewModel by viewModels()
    private val adapter by lazy { HighlightRuleAdapter(requireContext()) }
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0
    private var cardBgColor = 0
    private var cardStrokeColor = 0
    private var previewBgColor = 0
    private var previewStrokeColor = 0

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.85f)
        dialog?.window?.setGravity(Gravity.BOTTOM)
        dialog?.window?.setBackgroundDrawableResource(R.drawable.shape_highlight_rule_sheet)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initTheme()
        attachBottomSheetDismiss(
            binding.dragHandle,
            binding.sheetContainer
        ) { dismissAllowingStateLoss() }

        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.recyclerView.clipToPadding = false
        binding.recyclerView.setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 28.dpToPx())

        binding.ivClose.setOnClickListener { dismissAllowingStateLoss() }
        binding.ivMenu.setOnClickListener { showMenu(it) }
        binding.tvEmptyAdd.setOnClickListener { showPresetRules() }

        loadRules()
    }

    override fun observeLiveBus() {
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            if (it.contains(1) || it.contains(2)) {
                initTheme()
                adapter.notifyDataSetChanged()
            }
        }
    }

    private fun initTheme() {
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        primaryTextColor = requireContext().getPrimaryTextColor(isLight)
        secondaryTextColor = requireContext().getSecondaryTextColor(isLight)
        accentColor = requireContext().accentColor

        cardBgColor = if (isLight) {
            ColorUtils.blendColors(bg, 0xFF000000.toInt(), 0.08f)
        } else {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.06f)
        }
        cardStrokeColor = if (isLight) {
            ColorUtils.blendColors(0xFF000000.toInt(), bg, 0.88f)
        } else {
            ColorUtils.blendColors(0xFFFFFFFF.toInt(), bg, 0.85f)
        }
        previewBgColor = cardBgColor
        previewStrokeColor = cardStrokeColor
        val accentForegroundColor =
            if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()

        binding.sheetContainer.background?.mutate()?.setTint(bg)
        binding.ivClose.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.ivClose.background?.mutate()?.setTint(cardBgColor)
        binding.tvPageTitle.setTextColor(primaryTextColor)
        binding.tvPageSubtitle.setTextColor(secondaryTextColor)
        binding.ivMenu.setColorFilter(accentForegroundColor, PorterDuff.Mode.SRC_IN)
        binding.ivMenu.background?.mutate()?.setTint(accentColor)
        binding.ivEmpty.setColorFilter(secondaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.tvEmptyMsg.setTextColor(secondaryTextColor)
        binding.tvEmptyHint.setTextColor(secondaryTextColor)

        binding.tvEmptyAdd.background?.mutate()?.setTint(accentColor)
        binding.tvEmptyAdd.setTextColor(accentForegroundColor)
    }

    private fun showMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.highlight_rule_config, menu)
            setOnMenuItemClickListener(this@HighlightRuleConfigDialog)
        }.show()
    }

    private fun showItemMenu(rule: HighlightRule, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.highlight_rule_item, menu)
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    R.id.menu_edit -> editRule(rule)
                    R.id.menu_delete -> deleteRule(rule)
                    R.id.menu_export_single -> exportRulesToClipboard(listOf(rule))
                    R.id.menu_share_single -> shareRules(listOf(rule))
                }
                true
            }
        }.show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_select -> showRuleSelector()
            R.id.menu_add -> editRule(null)
            R.id.menu_preset -> showPresetRules()
            R.id.menu_import -> importRulesFromClipboard()
            R.id.menu_group -> showGroupManager()
            R.id.menu_share -> shareRules(getFilteredRules())
            R.id.menu_export -> exportRulesToClipboard(getFilteredRules())
            R.id.menu_reset -> resetRules()
            else -> return false
        }
        return true
    }

    private fun loadRules() {
        viewModel.loadRules()
        applyGroupFilter()
    }

    private fun applyGroupFilter() {
        val filtered = getFilteredRules()
        adapter.setItems(filtered)
        updateEmptyState()
        updateSubtitle()
    }

    private fun getFilteredRules(): List<HighlightRule> {
        return viewModel.filteredRules()
    }

    private fun updateSubtitle() {
        val groupText = viewModel.currentGroup ?: "全部分组"
        val count = getFilteredRules().size
        binding.tvPageSubtitle.text = "$groupText · $count 条规则"
    }

    fun switchToGroup(group: String?) {
        viewModel.switchToGroup(group)
        applyGroupFilter()
    }

    private fun updateEmptyState() {
        val filteredCount = getFilteredRules().size
        val empty = filteredCount == 0
        binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptyPanel.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun resetRules() {
        alert("恢复默认") {
            setMessage("恢复后会重新生成预置规则，自定义规则会被覆盖。")
            okButton {
                viewModel.resetRules()
                applyGroupFilter()
            }
            cancelButton()
        }
    }

    private fun editRule(rule: HighlightRule?) {
        val defaultGroup = rule?.group ?: viewModel.currentGroup
        // 新增规则时，预填当前书籍的书名和书源URL作为作用范围
        val defaultScope = if (rule == null) {
            ReadBook.book?.let { book ->
                val parts = mutableListOf<String>()
                parts.add(book.name)
                if (book.origin.isNotBlank() && !book.isLocal) {
                    parts.add(book.origin)
                }
                parts.joinToString(";")
            }
        } else null
        HighlightRuleEditDialog(rule, defaultGroup, defaultScope) { newRule ->
            viewModel.upsertRule(newRule)
            applyGroupFilter()
        }.show(childFragmentManager, "highlightRuleEdit")
    }

    private fun deleteRule(rule: HighlightRule) {
        alert("删除") {
            setMessage("确定删除“${rule.name}”吗？")
            okButton {
                viewModel.deleteRule(rule)
                applyGroupFilter()
            }
            cancelButton()
        }
    }

    private fun showPresetRules() {
        HighlightPresetRuleDialog(viewModel.currentGroup) { rule ->
            viewModel.addRule(rule)
            applyGroupFilter()
        }.show(childFragmentManager, "highlightPresetRule")
    }

    private fun showGroupManager() {
        HighlightRuleGroupManageDialog(
            onChanged = { oldGroup, newGroup ->
                if (oldGroup != null && viewModel.currentGroup == oldGroup) {
                    viewModel.switchToGroup(newGroup)
                }
                loadRules()
            },
            onSelectGroup = { group ->
                viewModel.switchToGroup(group)
                applyGroupFilter()
            }
        ).show(childFragmentManager, "highlightRuleGroupManage")
    }

    private fun showRuleSelector() {
        if (viewModel.rules.isEmpty()) {
            context?.toastOnUi("暂无可选择规则")
            return
        }
        val selected = BooleanArray(viewModel.rules.size)
        val names = viewModel.rules.map {
            "${it.name.ifBlank { "未命名规则" }} / ${it.group}"
        }.toTypedArray()
        alert("选择规则") {
            multiChoiceItems(names, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            positiveButton("分享选中") {
                val picked = viewModel.rules.filterIndexed { index, _ -> selected[index] }
                if (picked.isEmpty()) {
                    requireContext().toastOnUi("请先选择规则")
                } else {
                    shareRules(picked)
                }
            }
            neutralButton("删除选中") {
                val ids = viewModel.rules.filterIndexed { index, _ -> selected[index] }.map { it.id }.toSet()
                if (ids.isEmpty()) {
                    requireContext().toastOnUi("请先选择规则")
                } else {
                    viewModel.deleteRules(ids)
                    applyGroupFilter()
                }
            }
            negativeButton("取消")
        }
    }

    private fun importRulesFromClipboard() {
        val clip = requireContext().getClipText()
        if (clip.isNullOrBlank()) {
            context?.toastOnUi(R.string.highlight_rule_clipboard_empty)
            return
        }
        val imported = GSON.fromJsonArray<HighlightRule>(clip).getOrNull()
        if (imported.isNullOrEmpty()) {
            context?.toastOnUi(R.string.highlight_rule_import_invalid)
            return
        }
        viewModel.importRules(imported)
        applyGroupFilter()
        context?.toastOnUi(R.string.highlight_rule_import_success)
    }

    private fun exportRulesToClipboard(targetRules: List<HighlightRule>) {
        if (targetRules.isEmpty()) {
            context?.toastOnUi("暂无规则可导出")
            return
        }
        requireContext().sendToClip(GSON.toJson(targetRules))
        context?.toastOnUi("已复制 ${targetRules.size} 条规则")
    }

    private fun shareRules(targetRules: List<HighlightRule>) {
        if (targetRules.isEmpty()) {
            context?.toastOnUi("没有可分享的规则")
            return
        }
        val json = GSON.toJson(targetRules)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, json)
        }
        startActivity(Intent.createChooser(intent, "分享规则"))
    }

    /**
     * 高亮规则列表适配器。
     */
    private inner class HighlightRuleAdapter(context: Context) :
        RecyclerAdapter<HighlightRule, ItemHighlightPresetRuleBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemHighlightPresetRuleBinding {
            return ItemHighlightPresetRuleBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemHighlightPresetRuleBinding) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::editRule)
            }
            binding.root.setOnLongClickListener {
                getItem(holder.layoutPosition)?.let { showItemMenu(it, binding.root) }
                true
            }
            binding.tvPreview.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::editRule)
            }
            binding.tvEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::editRule)
            }
            binding.tvDelete.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::deleteRule)
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemHighlightPresetRuleBinding,
            item: HighlightRule,
            payloads: MutableList<Any>
        ) {
            binding.tvTitle.text = item.name.ifBlank { getString(R.string.highlight_rule_unnamed) }
            binding.tvDesc.text = item.styleSummary()
            binding.tvPattern.text = buildString {
                append(item.group)
                append(" / ")
                append(item.targetScopeLabel())
                append(" / ")
                append(item.displayPattern())
                // 显示书籍作用域信息
                if (!item.scope.isNullOrBlank()) {
                    append(" / 仅: ")
                    append(item.scope!!.replace(";", "; ").trim())
                }
                if (!item.excludeScope.isNullOrBlank()) {
                    append(" / 排除: ")
                    append(item.excludeScope!!.replace(";", "; ").trim())
                }
            }
            binding.tvPreview.text = HighlightRulePreview.build(item)

            val density = binding.root.context.resources.displayMetrics.density
            binding.root.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 12f * density
                setColor(cardBgColor)
                setStroke((1f * density).toInt().coerceAtLeast(1), cardStrokeColor)
            }
            binding.tvPattern.background?.mutate()?.setTint(accentColor)
            binding.tvPreview.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(previewBgColor)
                setStroke((1f * density).toInt().coerceAtLeast(1), previewStrokeColor)
            }
            binding.tvEdit.background?.mutate()?.setTint(accentColor)

            binding.switchEnable.setOnCheckedChangeListener(null)
            binding.switchEnable.isChecked = item.enabled
            binding.switchEnable.trackTintList = android.content.res.ColorStateList.valueOf(
                if (item.enabled) accentColor else 0xFF666666.toInt()
            )
            binding.switchEnable.thumbTintList = android.content.res.ColorStateList.valueOf(
                if (item.enabled) accentColor else 0xFF999999.toInt()
            )
            binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
                if (item.enabled != isChecked) {
                    viewModel.setRuleEnabled(item, isChecked)
                    applyGroupFilter()
                }
            }

            binding.tvTitle.setTextColor(primaryTextColor)
            binding.tvDesc.setTextColor(secondaryTextColor)
            binding.tvPattern.setTextColor(
                if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            )
            binding.tvPreviewLabel.setTextColor(secondaryTextColor)
            binding.tvPreview.setTextColor(primaryTextColor)

            (binding.tvEdit.getChildAt(0) as? android.widget.ImageView)
                ?.setColorFilter(
                    if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt(),
                    PorterDuff.Mode.SRC_IN
                )
            (binding.tvEdit.getChildAt(1) as? android.widget.TextView)
                ?.setTextColor(
                    if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                )

            (binding.tvDelete.getChildAt(0) as? android.widget.ImageView)
                ?.setColorFilter(context.getColor(R.color.error), PorterDuff.Mode.SRC_IN)
            (binding.tvDelete.getChildAt(1) as? android.widget.TextView)
                ?.setTextColor(context.getColor(R.color.error))
        }
    }
}
