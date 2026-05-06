package io.legado.app.ui.book.read

import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogTextMenuConfigBinding
import io.legado.app.databinding.ItemTextMenuConfigBinding
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 文本菜单项配置对话框
 * 
 * 功能说明：
 * 提供一个界面让用户选择要显示/隐藏的文本菜单项
 */
class TextMenuConfigDialog : BaseDialogFragment(R.layout.dialog_text_menu_config) {

    private val binding by viewBinding(DialogTextMenuConfigBinding::bind)
    private val adapter by lazy { MenuItemsAdapter() }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerView.adapter = adapter
        
        // 重置按钮
        binding.btnReset.setOnClickListener {
            TextMenuConfig.resetToDefault(requireContext())
            adapter.updateItems()
        }
        
        // 关闭按钮
        binding.btnClose.setOnClickListener {
            dismiss()
        }
        
        // 加载菜单项
        adapter.updateItems()
    }

    /**
     * 菜单项配置适配器
     */
    private inner class MenuItemsAdapter : RecyclerAdapter<TextMenuConfig.MenuItemInfo, ItemTextMenuConfigBinding>(requireContext()) {

        private val hiddenIds: MutableSet<Int> = mutableSetOf()

        fun updateItems() {
            hiddenIds.clear()
            hiddenIds.addAll(TextMenuConfig.getHiddenMenuItemIds(requireContext()))
            setItems(TextMenuConfig.getAllMenuItems())
        }

        override fun getViewBinding(parent: ViewGroup): ItemTextMenuConfigBinding {
            return ItemTextMenuConfigBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextMenuConfigBinding,
            item: TextMenuConfig.MenuItemInfo,
            payloads: MutableList<Any>
        ) {
            binding.apply {
                // 设置菜单项名称
                tvMenuName.text = getString(item.nameResId)
                
                // 设置复选框状态
                checkBox.isChecked = item.id !in hiddenIds
                
                // 设置菜单项ID（用于调试）
                tvMenuId.text = "ID: ${item.id}"
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextMenuConfigBinding) {
            // 点击整个item切换状态
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let { item ->
                    val isVisible = TextMenuConfig.toggleMenuItem(requireContext(), item.id)
                    binding.checkBox.isChecked = isVisible
                    
                    // 更新本地缓存
                    if (isVisible) {
                        hiddenIds.remove(item.id)
                    } else {
                        hiddenIds.add(item.id)
                    }
                }
            }
        }
    }
}
