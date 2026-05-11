package io.legado.app.ui.book.read.config

import android.content.Context
import android.content.Intent
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.PopupMenu
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogHighlightRuleConfigBinding
import io.legado.app.databinding.ItemHighlightPresetRuleBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.dpToPx
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.getClipText
import io.legado.app.utils.postEvent
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class HighlightRuleConfigDialog : BaseDialogFragment(R.layout.dialog_highlight_rule_config),
    PopupMenu.OnMenuItemClickListener {

    private val binding by viewBinding(DialogHighlightRuleConfigBinding::bind)
    private val adapter by lazy { HighlightRuleAdapter(requireContext()) }
    private val rules = ArrayList<HighlightRule>()
    private var primaryTextColor = 0
    private var secondaryTextColor = 0

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.92f)
        dialog?.window?.setGravity(Gravity.BOTTOM)
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
        binding.recyclerView.setPadding(0, 10.dpToPx(), 0, 18.dpToPx())

        binding.ivClose.setOnClickListener { dismissAllowingStateLoss() }
        binding.ivMenu.setOnClickListener { showMenu(it) }
        binding.tvEmptyAdd.setOnClickListener { showPresetRules() }

        loadRules()
    }

    private fun initTheme() {
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        primaryTextColor = requireContext().getPrimaryTextColor(isLight)
        secondaryTextColor = requireContext().getSecondaryTextColor(isLight)

        binding.sheetContainer.setBackgroundColor(bg)
        binding.ivClose.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.tvPageTitle.setTextColor(primaryTextColor)
        binding.tvPageSubtitle.setTextColor(secondaryTextColor)
        binding.ivMenu.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.ivEmpty.setColorFilter(secondaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.tvEmptyMsg.setTextColor(secondaryTextColor)
    }

    private fun showMenu(anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.highlight_rule_config, menu)
            setOnMenuItemClickListener(this@HighlightRuleConfigDialog)
        }.show()
    }

    override fun onMenuItemClick(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_select -> {
                showRuleSelector()
                return true
            }
            R.id.menu_add -> {
                editRule(null)
                return true
            }
            R.id.menu_preset -> {
                showPresetRules()
                return true
            }
            R.id.menu_import -> {
                importRulesFromClipboard()
                return true
            }
            R.id.menu_group -> {
                showGroupManager()
                return true
            }
            R.id.menu_share -> {
                shareRules(rules)
                return true
            }
            R.id.menu_export -> {
                exportRulesToClipboard(rules)
                return true
            }
            R.id.menu_reset -> {
                resetRules()
                return true
            }
        }
        return false
    }

    private fun loadRules() {
        rules.clear()
        rules.addAll(HighlightRuleStore.load(requireContext()))
        adapter.setItems(rules.toList())
        updateEmptyState()
    }

    private fun updateEmptyState() {
        val empty = rules.isEmpty()
        binding.recyclerView.visibility = if (empty) View.GONE else View.VISIBLE
        binding.emptyPanel.visibility = if (empty) View.VISIBLE else View.GONE
    }

    private fun resetRules() {
        alert("恢复默认") {
            setMessage("恢复后会重新生成预置规则，自定义规则会被覆盖。")
            okButton {
                rules.clear()
                rules.addAll(HighlightRuleStore.reset(requireContext()))
                syncRules()
            }
            cancelButton()
        }
    }

    private fun editRule(rule: HighlightRule?) {
        HighlightRuleEditDialog(rule) { newRule ->
            val index = rules.indexOfFirst { it.id == newRule.id }
            if (index >= 0) {
                rules[index] = newRule
            } else {
                rules.add(newRule)
            }
            syncRules()
        }.show(childFragmentManager, "highlightRuleEdit")
    }

    private fun deleteRule(rule: HighlightRule) {
        alert("删除") {
            setMessage("确定删除“${rule.name}”吗？")
            okButton {
                rules.removeAll { it.id == rule.id }
                syncRules()
            }
            cancelButton()
        }
    }

    private fun showPresetRules() {
        HighlightPresetRuleDialog { rule ->
            if (rules.any { it.id == rule.id }) {
                requireContext().toastOnUi("该预置规则已存在")
                return@HighlightPresetRuleDialog
            }
            rules.add(rule)
            syncRules()
        }.show(childFragmentManager, "highlightPresetRule")
    }

    private fun showGroupManager() {
        HighlightRuleGroupManageDialog {
            loadRules()
        }.show(childFragmentManager, "highlightRuleGroupManage")
    }

    private fun showRuleSelector() {
        if (rules.isEmpty()) {
            context?.toastOnUi("暂无可选择规则")
            return
        }
        val selected = BooleanArray(rules.size)
        val names = rules.map { "${it.name.ifBlank { "未命名规则" }}  /  ${it.group}" }.toTypedArray()
        alert("选择规则") {
            multiChoiceItems(names, selected) { _, which, isChecked ->
                selected[which] = isChecked
            }
            positiveButton("分享选中") {
                val picked = rules.filterIndexed { index, _ -> selected[index] }
                if (picked.isEmpty()) {
                    requireContext().toastOnUi("请先选择规则")
                } else {
                    shareRules(picked)
                }
            }
            neutralButton("删除选中") {
                val ids = rules.filterIndexed { index, _ -> selected[index] }.map { it.id }
                if (ids.isEmpty()) {
                    requireContext().toastOnUi("请先选择规则")
                } else {
                    rules.removeAll { ids.contains(it.id) }
                    syncRules()
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
        imported.forEach { rule ->
            val normalized = rule.copy(
                id = if (rules.none { it.id == rule.id }) rule.id else rule.copyWithNewId().id,
                group = rule.group.ifBlank { HighlightRuleGroupStore.DEFAULT_GROUP }
            )
            rules.add(normalized)
        }
        syncRules()
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

    private fun syncRules() {
        HighlightRuleStore.save(requireContext(), rules)
        adapter.setItems(rules.toList())
        updateEmptyState()
        postEvent(EventBus.UP_CONFIG, arrayListOf(5))
    }

    private inner class HighlightRuleAdapter(context: Context) :
        RecyclerAdapter<HighlightRule, ItemHighlightPresetRuleBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemHighlightPresetRuleBinding {
            return ItemHighlightPresetRuleBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemHighlightPresetRuleBinding) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::editRule)
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
            binding.tvPattern.text = "${item.group} / ${item.displayPattern()}"
            binding.tvPreview.text = HighlightRulePreview.build(item)
            binding.switchEnable.setOnCheckedChangeListener(null)
            binding.switchEnable.isChecked = item.enabled
            binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
                if (item.enabled != isChecked) {
                    item.enabled = isChecked
                    syncRules()
                }
            }

            binding.tvTitle.setTextColor(primaryTextColor)
            binding.tvDesc.setTextColor(secondaryTextColor)
            binding.tvPattern.setTextColor(secondaryTextColor)
            binding.tvPreviewLabel.setTextColor(secondaryTextColor)
            binding.tvPreview.setTextColor(primaryTextColor)

            (binding.tvEdit.getChildAt(0) as? android.widget.ImageView)?.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
            (binding.tvEdit.getChildAt(1) as? android.widget.TextView)?.setTextColor(primaryTextColor)

            (binding.tvDelete.getChildAt(0) as? android.widget.ImageView)?.setColorFilter(context.getColor(R.color.error), PorterDuff.Mode.SRC_IN)
            (binding.tvDelete.getChildAt(1) as? android.widget.TextView)?.setTextColor(context.getColor(R.color.error))
        }
    }
}
