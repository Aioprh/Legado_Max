package io.legado.app.ui.book.read.config.highlight

import android.graphics.PorterDuff
import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogHighlightRuleGroupManageBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.observeEvent
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 高亮规则分组管理弹窗。
 *
 * 负责分组列表展示、输入弹窗、分组菜单和选择回调；
 * 分组与规则数据变更由 `HighlightRuleGroupManageViewModel` 执行。
 */
class HighlightRuleGroupManageDialog @JvmOverloads constructor(
    private val onChanged: (oldGroup: String?, newGroup: String?) -> Unit = { _, _ -> },
    private val onSelectGroup: (String?) -> Unit = {},
) : BaseDialogFragment(R.layout.dialog_highlight_rule_group_manage) {

    private val binding by viewBinding(DialogHighlightRuleGroupManageBinding::bind)
    private val viewModel by viewModels<HighlightRuleGroupManageViewModel>()
    private val adapter by lazy { GroupAdapter(requireContext()) }
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0
    private var cardBgColor = 0
    private var actionBgColor = 0

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
        binding.ivBack.setOnClickListener { dismissAllowingStateLoss() }
        binding.tvAddGroup.setOnClickListener { showGroupInputDialog(null) }
        binding.llViewAll.setOnClickListener {
            onSelectGroup(null)
            dismissAllowingStateLoss()
        }
        loadData()
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
        actionBgColor = if (isLight) {
            ColorUtils.blendColors(bg, 0xFF000000.toInt(), 0.12f)
        } else {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.10f)
        }

        binding.sheetContainer.background?.mutate()?.setTint(bg)
        binding.ivBack.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.ivBack.background?.mutate()?.setTint(cardBgColor)
        binding.tvPageTitle.setTextColor(primaryTextColor)
        binding.tvPageSubtitle.setTextColor(secondaryTextColor)

        binding.tvAddGroup.background?.mutate()?.setTint(accentColor)
        binding.tvAddGroup.setTextColor(
            if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        )

        binding.tvEmptyMsg.setTextColor(secondaryTextColor)
        
        binding.llViewAll.background?.mutate()?.setTint(cardBgColor)
        binding.tvAllCount.setTextColor(secondaryTextColor)
    }

    private fun loadData() {
        viewModel.loadData()
        adapter.setItems(viewModel.groups.toList())
        binding.tvEmptyMsg.visibility = if (viewModel.groups.isEmpty()) View.VISIBLE else View.GONE
        binding.tvAllCount.text = "${viewModel.rules.size} 条规则"
    }

    private fun showGroupInputDialog(source: String?) {
        val editText = EditText(requireContext()).apply {
            setText(source.orEmpty())
            setSelection(text.length)
            hint = "输入分组名称"
            inputType = InputType.TYPE_CLASS_TEXT
        }
        val container = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20.dpToPx(), 8.dpToPx(), 20.dpToPx(), 0)
            addView(
                editText,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
            )
        }
        alert(if (source == null) "新增分组" else "重命名分组") {
            customView { container }
            okButton {
                val newName = editText.text?.toString()?.trim().orEmpty()
                if (newName.isBlank()) {
                    requireContext().toastOnUi("分组名称不能为空")
                    return@okButton
                }
                if (viewModel.groups.contains(newName) && newName != source) {
                    requireContext().toastOnUi("分组名称已存在")
                    return@okButton
                }
                if (source == null) {
                    viewModel.addGroup(newName)
                    refreshGroups()
                    onChanged(null, null)
                } else {
                    viewModel.renameGroup(source, newName)
                    refreshGroups()
                    onChanged(source, newName)
                }
            }
            cancelButton()
        }
    }

    private fun deleteGroup(group: String) {
        if (group == HighlightRuleGroupStore.DEFAULT_GROUP) {
            context?.toastOnUi("默认分组不能删除")
            return
        }
        alert("删除分组") {
            setMessage("删除后，该分组下的规则会移动到默认分组。")
            okButton {
                viewModel.deleteGroup(group)
                refreshGroups()
                onChanged(group, null)
            }
            cancelButton()
        }
    }

    private fun exportGroup(group: String) {
        val targetRules = viewModel.rulesInGroup(group)
        if (targetRules.isEmpty()) {
            context?.toastOnUi("该分组暂无规则可导出")
            return
        }
        requireContext().sendToClip(HighlightRuleRepository.encodeRules(targetRules))
        context?.toastOnUi("已复制 ${targetRules.size} 条规则")
    }

    private fun showItemMenu(group: String, anchor: View) {
        PopupMenu(requireContext(), anchor).apply {
            menuInflater.inflate(R.menu.highlight_rule_group_item, menu)
            if (group == HighlightRuleGroupStore.DEFAULT_GROUP) {
                menu.findItem(R.id.menu_delete)?.isVisible = false
            }
            setOnMenuItemClickListener { item: MenuItem ->
                when (item.itemId) {
                    R.id.menu_rename_group -> showGroupInputDialog(group)
                    R.id.menu_export_group -> exportGroup(group)
                    R.id.menu_delete -> deleteGroup(group)
                    else -> return@setOnMenuItemClickListener false
                }
                true
            }
        }.show()
    }

    private fun groupCount(group: String): Int {
        return viewModel.groupCount(group)
    }

    private fun refreshGroups() {
        adapter.setItems(viewModel.groups.toList())
        binding.tvEmptyMsg.visibility = if (viewModel.groups.isEmpty()) View.VISIBLE else View.GONE
        binding.tvAllCount.text = "${viewModel.rules.size} 条规则"
    }

    /**
     * 高亮规则分组列表适配器。
     */
    private inner class GroupAdapter(context: android.content.Context) :
        RecyclerAdapter<String, ViewBindingHolder>(context) {

        override fun getViewBinding(parent: ViewGroup): ViewBindingHolder {
            val view = inflater.inflate(R.layout.item_highlight_rule_group, parent, false)
            return ViewBindingHolder(view)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ViewBindingHolder) {
            binding.itemRoot.setOnClickListener {
                getItem(holder.layoutPosition)?.let { group ->
                    onSelectGroup(group)
                    dismissAllowingStateLoss()
                }
            }
            binding.itemRoot.setOnLongClickListener {
                getItem(holder.layoutPosition)?.let { group ->
                    showItemMenu(group, binding.itemRoot)
                }
                true
            }
            binding.tvEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::showGroupInputDialog)
            }
            binding.tvDelete.setOnClickListener {
                getItem(holder.layoutPosition)?.let { group ->
                    showItemMenu(group, binding.tvDelete)
                }
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ViewBindingHolder,
            item: String,
            payloads: MutableList<Any>
        ) {
            binding.itemRoot.background?.mutate()?.setTint(cardBgColor)
            binding.tvEdit.background?.mutate()?.setTint(accentColor)
            binding.tvDelete.background?.mutate()?.setTint(actionBgColor)

            binding.tvTitle.text = item
            binding.tvTitle.setTextColor(primaryTextColor)
            binding.tvCount.text = "${groupCount(item)} 条规则"
            binding.tvCount.setTextColor(secondaryTextColor)
            binding.tvEdit.setTextColor(
                if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
            )
            binding.tvDelete.setTextColor(primaryTextColor)
            binding.tvDelete.visibility = View.VISIBLE
        }
    }

    /**
     * 分组列表项的手写 ViewBinding 包装。
     */
    class ViewBindingHolder(view: View) : androidx.viewbinding.ViewBinding {
        override fun getRoot(): View = itemRoot
        val itemRoot: View = view
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvCount: TextView = view.findViewById(R.id.tv_count)
        val tvEdit: TextView = view.findViewById(R.id.tv_edit)
        val tvDelete: TextView = view.findViewById(R.id.tv_delete)
    }
}
