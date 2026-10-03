package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.View
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.UiCorner
import io.legado.app.lib.theme.applyUiBodyTypeface
import io.legado.app.lib.theme.applyUiLabelStyle
import io.legado.app.lib.theme.applyUiSectionTitleStyle
import io.legado.app.lib.theme.surface.SurfaceStyles
import io.legado.app.utils.SurfaceBackdrop
import io.legado.app.utils.applyAdaptiveDim
import io.legado.app.utils.dpToPx

object SourceSelectDialog {

    fun <T> show(
        context: android.content.Context,
        title: CharSequence,
        items: List<T>,
        selectedKey: String?,
        displayName: (T) -> String,
        searchTexts: (T) -> List<String>,
        searchHint: String?,
        itemKey: (T) -> String,
        onSelect: (T) -> Unit,
        onDelete: ((T) -> Unit)? = null
    ) {
        if (items.isEmpty()) return
        var dialog: AlertDialog? = null
        // 已删除条目的 key 集合，过滤时需排除，
        // 否则用户再次输入搜索词时被删条目会从原始 items 中重新出现。
        val deletedKeys = hashSetOf<String>()
        var filteredItems = items.toList()
        val adapter = object : RecyclerView.Adapter<SourceViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SourceViewHolder {
                return SourceViewHolder(SourceOptionView(parent.context))
            }

            override fun getItemCount(): Int = filteredItems.size

            override fun onBindViewHolder(holder: SourceViewHolder, position: Int) {
                val item = filteredItems[position]
                val selectedPrefix = if (itemKey(item) == selectedKey) "✓ " else ""
                holder.bind(
                    title = selectedPrefix + displayName(item),
                    onClick = {
                        dialog?.dismiss()
                        onSelect(item)
                    },
                    onLongClick = if (onDelete == null) {
                        null
                    } else {
                        {
                            confirmDelete(context, displayName(item)) {
                                removeItem(item)
                                onDelete.invoke(item)
                            }
                        }
                    }
                )
            }

            // 删除后立即从当前弹窗列表中移除，避免出现"已删除但仍显示"的假象。
            // 后续列表数据由外部数据源（如 flowExplore()）驱动刷新。
            private fun removeItem(item: T) {
                deletedKeys.add(itemKey(item))
                val index = filteredItems.indexOfFirst { itemKey(it) == itemKey(item) }
                if (index < 0) return
                filteredItems = filteredItems.toMutableList().also { it.removeAt(index) }
                notifyDataSetChanged()
            }
        }
        val searchView = SearchView(context).apply {
            queryHint = searchHint ?: context.getString(R.string.screen)
            setIconifiedByDefault(false)
            isIconified = false
            isSubmitButtonEnabled = false
            background = GradientDrawable().apply {
                cornerRadius = UiCorner.searchRadius(10f)
                setColor(
                    UiCorner.dialogSurfaceColor(
                        UiCorner.themeSurfaceMutedColor(context)
                    )
                )
            }
            setPadding(4.dpToPx(), 0, 4.dpToPx(), 0)
            setOnQueryTextListener(object : SearchView.OnQueryTextListener {
                override fun onQueryTextSubmit(query: String?): Boolean = true

                override fun onQueryTextChange(newText: String?): Boolean {
                    val key = newText.orEmpty().trim()
                    filteredItems = if (key.isBlank()) {
                        items.filterNot { deletedKeys.contains(itemKey(it)) }
                    } else {
                        items.filter { item ->
                            !deletedKeys.contains(itemKey(item)) &&
                                searchTexts(item).any { text -> text.contains(key, true) }
                        }
                    }
                    adapter.notifyDataSetChanged()
                    return true
                }
            })
            setOnCloseListener {
                setQuery("", false)
                isIconified = false
                true
            }
        }
        searchView.applyUiBodyTypeface(context)
        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            this.adapter = adapter
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                360.dpToPx()
            ).apply {
                topMargin = 10.dpToPx()
            }
        }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            isFocusable = true
            isFocusableInTouchMode = true
            SurfaceBackdrop.installStatic(this, SurfaceStyles.dialog(context))
            setPadding(14.dpToPx(), 14.dpToPx(), 14.dpToPx(), 12.dpToPx())
            addView(
                TextView(context).apply {
                    text = title
                    applyUiSectionTitleStyle(context)
                    textSize = 18f
                    includeFontPadding = false
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(2.dpToPx(), 0, 2.dpToPx(), 12.dpToPx())
                },
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    32.dpToPx()
                )
            )
            addView(
                searchView,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    42.dpToPx()
                )
            )
            addView(recyclerView)
        }
        dialog = AlertDialog.Builder(context)
            .setView(container)
            .create()
        dialog.setOnShowListener {
            container.requestFocus()
            searchView.clearFocus()
            dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN)
        }
        dialog.show()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.applyAdaptiveDim(container)
    }

    /**
     * 长按书源后的删除确认弹窗。
     * 文案与换源页 / 发现页删除保持一致：标题"提醒"，正文"是否确认删除？"+ 换行 + 书源名。
     */
    private fun confirmDelete(context: Context, name: String, onConfirm: () -> Unit) {
        context.alert(R.string.draw) {
            setMessage(context.getString(R.string.sure_del) + "\n" + name)
            noButton()
            yesButton { onConfirm() }
        }
    }

    private class SourceOptionView(context: android.content.Context) : TextView(context) {
        init {
            layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            gravity = Gravity.CENTER_VERTICAL
            includeFontPadding = false
            minHeight = 48.dpToPx()
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            applyUiLabelStyle(context)
            textSize = 15f
            setPadding(18.dpToPx(), 0, 18.dpToPx(), 0)
            background = UiCorner.dialogActionSelector(
                Color.TRANSPARENT,
                UiCorner.themeSurfaceMutedColor(context),
                UiCorner.actionRadius(context)
            )
            isClickable = true
            isFocusable = true
        }
    }

    private class SourceViewHolder(private val rowView: SourceOptionView) : RecyclerView.ViewHolder(rowView) {
        fun bind(title: CharSequence, onClick: () -> Unit, onLongClick: (() -> Boolean)? = null) {
            rowView.text = title
            rowView.setOnClickListener { onClick() }
            rowView.setOnLongClickListener(
                if (onLongClick == null) {
                    null
                } else {
                    View.OnLongClickListener {
                        // 消费长按事件，避免同时触发单击切换书源
                        onLongClick.invoke()
                        true
                    }
                }
            )
        }
    }
}