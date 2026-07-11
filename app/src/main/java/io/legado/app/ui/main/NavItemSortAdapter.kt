package io.legado.app.ui.main

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.databinding.ItemNavSortBinding
import io.legado.app.lib.theme.accentColor

/**
 * 导航栏排序对话框的适配器
 *
 * 支持长按拖拽排序，同时可通过 RadioButton 选择默认主页。
 * 排序顺序与默认主页选择相互独立。
 */
class NavItemSortAdapter(
    private val context: Context,
    private val items: MutableList<NavItemConfig>,
    private var defaultHomeKey: String
) : RecyclerView.Adapter<NavItemSortAdapter.ViewHolder>() {

    /** 导航项配置数据 */
    data class NavItemConfig(
        val key: String,       // "bookshelf" | "homepage" | "explore" | "rss" | "my"
        val title: String,     // 显示名称
        val iconRes: Int,      // 图标资源
        val visible: Boolean   // 是否在底部导航栏中显示（受开关控制）
    )

    inner class ViewHolder(val binding: ItemNavSortBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = ItemNavSortBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val isDefault = item.key == defaultHomeKey
        holder.binding.apply {
            ivIcon.setImageResource(item.iconRes)
            ivIcon.alpha = if (item.visible) 1f else 0.4f
            tvTitle.text = item.title
            tvTitle.alpha = if (item.visible) 1f else 0.4f

            // 默认主页徽标
            if (isDefault) {
                tvBadge.text = context.getString(R.string.nav_item_sort_default_home)
                tvBadge.visibility = View.VISIBLE
            } else {
                tvBadge.visibility = View.GONE
            }

            // RadioButton 单选默认主页
            radioButton.isChecked = isDefault
            radioButton.setOnClickListener {
                if (!isDefault) {
                    val previousKey = defaultHomeKey
                    defaultHomeKey = item.key
                    // 刷新新旧两项
                    val prevPos = items.indexOfFirst { it.key == previousKey }
                    if (prevPos >= 0) notifyItemChanged(prevPos)
                    notifyItemChanged(position)
                }
            }

            // 拖拽手柄着色
            ivDrag.setColorFilter(context.accentColor)
        }
    }

    override fun getItemCount(): Int = items.size

    /**
     * 交换两个 item 的位置（供 ItemTouchHelper 调用）
     */
    fun moveItem(fromPosition: Int, toPosition: Int) {
        if (fromPosition < 0 || fromPosition >= items.size) return
        if (toPosition < 0 || toPosition >= items.size) return
        val item = items.removeAt(fromPosition)
        items.add(toPosition, item)
        notifyItemMoved(fromPosition, toPosition)
    }

    /** 获取当前排序的 key 列表 */
    fun getOrderKeys(): List<String> = items.map { it.key }

    /** 获取当前选中的默认主页 key */
    fun getDefaultHomeKey(): String = defaultHomeKey

    /**
     * ItemTouchHelper Callback —— 仅允许上下拖拽，禁止滑动删除
     */
    val itemTouchCallback = object : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled(): Boolean = true
        override fun isItemViewSwipeEnabled(): Boolean = false

        override fun getMovementFlags(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder
        ): Int {
            return makeMovementFlags(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
            )
        }

        override fun onMove(
            recyclerView: RecyclerView,
            viewHolder: RecyclerView.ViewHolder,
            target: RecyclerView.ViewHolder
        ): Boolean {
            moveItem(
                viewHolder.bindingAdapterPosition,
                target.bindingAdapterPosition
            )
            return true
        }

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
            // 不支持滑动删除
        }
    }
}
