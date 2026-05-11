package io.legado.app.ui.book.read.config

import android.os.Bundle
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogHighlightRuleGroupManageBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.utils.dpToPx
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class HighlightRuleGroupManageDialog(
    private val onChanged: () -> Unit,
) : BaseDialogFragment(R.layout.dialog_highlight_rule_group_manage) {

    private val binding by viewBinding(DialogHighlightRuleGroupManageBinding::bind)
    private val adapter by lazy { GroupAdapter(requireContext()) }
    private val groups = ArrayList<String>()
    private val rules = ArrayList<HighlightRule>()

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.92f)
        dialog?.window?.setGravity(Gravity.BOTTOM)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        attachBottomSheetDismiss(
            binding.dragHandle,
            binding.sheetContainer
        ) { dismissAllowingStateLoss() }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.ivBack.setOnClickListener { dismissAllowingStateLoss() }
        binding.tvAddGroup.setOnClickListener { showGroupInputDialog(null) }
        loadData()
    }

    private fun loadData() {
        groups.clear()
        groups.addAll(HighlightRuleGroupStore.load(requireContext()))
        rules.clear()
        rules.addAll(HighlightRuleStore.load(requireContext()))
        adapter.setItems(groups.toList())
        binding.tvEmptyMsg.visibility = if (groups.isEmpty()) View.VISIBLE else View.GONE
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
                if (groups.contains(newName) && newName != source) {
                    requireContext().toastOnUi("分组名称已存在")
                    return@okButton
                }
                if (source == null) {
                    groups.add(newName)
                } else {
                    val index = groups.indexOf(source)
                    if (index >= 0) groups[index] = newName
                    rules.replaceAll { rule ->
                        if (rule.group == source) rule.copy(group = newName) else rule
                    }
                    HighlightRuleStore.save(requireContext(), rules)
                }
                HighlightRuleGroupStore.save(requireContext(), groups)
                loadData()
                onChanged()
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
                groups.remove(group)
                rules.replaceAll { rule ->
                    if (rule.group == group) {
                        rule.copy(group = HighlightRuleGroupStore.DEFAULT_GROUP)
                    } else {
                        rule
                    }
                }
                HighlightRuleGroupStore.save(requireContext(), groups)
                HighlightRuleStore.save(requireContext(), rules)
                loadData()
                onChanged()
            }
            cancelButton()
        }
    }

    private fun groupCount(group: String): Int {
        return rules.count { it.group == group }
    }

    private inner class GroupAdapter(context: android.content.Context) :
        RecyclerAdapter<String, ViewBindingHolder>(context) {

        override fun getViewBinding(parent: ViewGroup): ViewBindingHolder {
            val view = inflater.inflate(R.layout.item_highlight_rule_group, parent, false)
            return ViewBindingHolder(view)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ViewBindingHolder) {
            binding.tvEdit.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::showGroupInputDialog)
            }
            binding.tvDelete.setOnClickListener {
                getItem(holder.layoutPosition)?.let(::deleteGroup)
            }
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ViewBindingHolder,
            item: String,
            payloads: MutableList<Any>
        ) {
            binding.tvTitle.text = item
            binding.tvCount.text = "${groupCount(item)} 条规则"
            binding.tvDelete.visibility =
                if (item == HighlightRuleGroupStore.DEFAULT_GROUP) View.GONE else View.VISIBLE
        }
    }

    class ViewBindingHolder(view: View) : androidx.viewbinding.ViewBinding {
        override fun getRoot(): View = itemRoot
        val itemRoot: View = view
        val tvTitle: TextView = view.findViewById(R.id.tv_title)
        val tvCount: TextView = view.findViewById(R.id.tv_count)
        val tvEdit: TextView = view.findViewById(R.id.tv_edit)
        val tvDelete: TextView = view.findViewById(R.id.tv_delete)
    }
}
