package io.legado.app.ui.widget

import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.TopBarConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.lib.theme.uiTypeface

/**
 * 书架分组标签导航条，在分组样式为标签时显示于分组栏下方。
 *
 * 展示当前分组下的二级标签列表，支持选中高亮、点击切换、长按操作。
 * 视觉采用轻量液态玻璃材质：半透明底、细高光描边、胶囊标签和轻微悬浮阴影。
 */
class RoundedTagBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    enum class DisplayMode { CHIP, LIGHT, TEXT }

    data class Item(
        val text: CharSequence,
        val alpha: Float = 1f
    )

    private val layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
    private val adapter = TagAdapter()
    private val recyclerView = RecyclerView(context).apply {
        layoutManager = this@RoundedTagBarView.layoutManager
        adapter = this@RoundedTagBarView.adapter
        overScrollMode = OVER_SCROLL_NEVER
        itemAnimator = null
        // 标签只能在“大胶囊”的内容区域内移动，禁止滑动内容从左右边缘穿出去。
        clipChildren = true
        clipToPadding = true
        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = false
        isVerticalFadingEdgeEnabled = false
        setFadingEdgeLength(0)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.bookshelf_tag_recycler_padding_vertical)
        setPadding(2.dp, verticalPadding, 2.dp, verticalPadding)
        setBackgroundColor(Color.TRANSPARENT)
        clipToOutline = true
        outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(view: View, outline: Outline) {
                outline.setRoundRect(
                    0,
                    0,
                    view.width,
                    view.height,
                    16.dp.toFloat()
                )
            }
        }
    }
    private var items = emptyList<Item>()
    private var selectedIndex = RecyclerView.NO_POSITION
    private var onTagClick: ((Int) -> Unit)? = null
    private var onTagLongClick: ((Int) -> Boolean)? = null
    private var styleSignature: String? = null
    private var selectedBackgroundVisible = true
    private var displayMode = DisplayMode.CHIP
    private var backgroundOverrideColor: Int? = null

    init {
        // 同时约束 FrameLayout 自身及内部 RecyclerView，确保快速左右滑动时胶囊不会越过外层玻璃边界。
        clipChildren = true
        clipToPadding = true
        clipToOutline = true
        applyTopBarStyle(force = true)
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.bookshelf_tag_bar_padding_horizontal)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.bookshelf_tag_bar_padding_vertical)
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        addView(
            recyclerView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
        )
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyTopBarStyle()
    }

    /**
     * 应用二级标签栏液态玻璃样式。
     * 采用静态磨砂模拟，避免额外的实时模糊采样影响书架滚动性能。
     */
    fun applyTopBarStyle(force: Boolean = false) {
        val signature = "${TopBarConfig.currentSignature(AppConfig.isNightTheme)}|$displayMode|$backgroundOverrideColor"
        if (!force && styleSignature == signature) return
        styleSignature = signature

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val baseSurface = backgroundOverrideColor ?: if (isNight) 0x661B1B1D else 0xB8FFFFFF.toInt()
        val glassSurface = ColorUtilsCompat.withAlpha(baseSurface, if (isNight) 0.92f else 0.86f)
        val glassStroke = if (isNight) 0x55FFFFFF else 0x99FFFFFF.toInt()

        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20.dp.toFloat()
            setColor(glassSurface)
            setStroke(1.dp, glassStroke)
        }
        elevation = 3.dp.toFloat()
        translationZ = 1.dp.toFloat()

        // 玻璃栏内部留出空间，让胶囊标签看起来像漂浮在材质表面。
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.bookshelf_tag_bar_padding_horizontal)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.bookshelf_tag_bar_padding_vertical)
        setPadding(horizontalPadding, maxOf(verticalPadding, 4.dp), horizontalPadding, maxOf(verticalPadding, 4.dp))

        adapter.normalTextColor = if (isNight) Color.argb(225, 255, 255, 255) else context.primaryTextColor
        adapter.selectedTextColor = Color.WHITE
        adapter.selectedBackgroundColor = context.accentColor
        adapter.glassNormalFill = if (isNight) 0x331F1F22 else 0x70FFFFFF
        adapter.glassNormalStroke = if (isNight) 0x55FFFFFF else 0x8CFFFFFF.toInt()
        adapter.glassSelectedFill = ColorUtilsCompat.withAlpha(context.accentColor, 0.82f)
        adapter.glassSelectedStroke = 0x99FFFFFF.toInt()
        adapter.notifyDataSetChanged()
    }

    fun setDisplayMode(mode: DisplayMode) {
        if (displayMode == mode) return
        displayMode = mode
        styleSignature = null
        applyTopBarStyle(force = true)
    }

    fun setBackgroundOverrideColor(color: Int?) {
        if (backgroundOverrideColor == color) return
        backgroundOverrideColor = color
        styleSignature = null
        applyTopBarStyle(force = true)
    }

    fun setSelectedBackgroundVisible(visible: Boolean) {
        if (selectedBackgroundVisible == visible) return
        selectedBackgroundVisible = visible
        adapter.notifyDataSetChanged()
    }

    fun submitItems(items: List<Item>, selectedIndex: Int = this.selectedIndex) {
        val sameItems = this.items == items
        if (sameItems) {
            setSelectedIndex(selectedIndex, smooth = false)
            return
        }
        this.items = items.toList()
        this.selectedIndex = normalizeIndex(selectedIndex)
        adapter.notifyDataSetChanged()
        if (this.selectedIndex != RecyclerView.NO_POSITION) {
            scrollToIndex(this.selectedIndex, smooth = false)
        }
    }

    fun setSelectedIndex(index: Int, smooth: Boolean = true) {
        val newIndex = normalizeIndex(index)
        if (selectedIndex == newIndex) {
            if (newIndex != RecyclerView.NO_POSITION) {
                scrollToIndex(newIndex, smooth)
            }
            return
        }
        val oldIndex = selectedIndex
        selectedIndex = newIndex
        if (oldIndex in items.indices) adapter.notifyItemChanged(oldIndex)
        if (newIndex != RecyclerView.NO_POSITION) {
            adapter.notifyItemChanged(newIndex)
            scrollToIndex(newIndex, smooth)
        }
    }

    fun getSelectedIndex(): Int = selectedIndex

    fun setOnTagClickListener(listener: ((Int) -> Unit)?) {
        onTagClick = listener
    }

    fun setOnTagLongClickListener(listener: ((Int) -> Boolean)?) {
        onTagLongClick = listener
    }

    private fun normalizeIndex(index: Int): Int =
        if (index in items.indices) index else RecyclerView.NO_POSITION

    private fun scrollToIndex(index: Int, smooth: Boolean) {
        recyclerView.post {
            if (index !in items.indices) return@post
            val child = layoutManager.findViewByPosition(index)
            if (child == null) {
                if (smooth) recyclerView.smoothScrollToPosition(index) else recyclerView.scrollToPosition(index)
                recyclerView.post { centerVisibleChild(index, false) }
                return@post
            }
            centerChild(child.left, child.width, smooth)
        }
    }

    private fun centerVisibleChild(index: Int, smooth: Boolean) {
        val child = layoutManager.findViewByPosition(index) ?: return
        centerChild(child.left, child.width, smooth)
    }

    private fun centerChild(childLeft: Int, childWidth: Int, smooth: Boolean) {
        val dx = childLeft - (recyclerView.width - childWidth) / 2
        if (dx == 0) return
        if (smooth) recyclerView.smoothScrollBy(dx, 0) else recyclerView.scrollBy(dx, 0)
    }

    private inner class TagAdapter : RecyclerView.Adapter<TagViewHolder>() {
        var selectedBackgroundColor: Int = context.primaryColor
        var selectedTextColor: Int = context.accentColor
        var normalTextColor: Int = context.primaryTextColor
        var glassNormalFill: Int = 0x55FFFFFF
        var glassNormalStroke: Int = 0x88FFFFFF.toInt()
        var glassSelectedFill: Int = context.accentColor
        var glassSelectedStroke: Int = 0xAAFFFFFF.toInt()

        override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): TagViewHolder {
            val textView = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_bookshelf_group_tag, parent, false) as TextView
            textView.gravity = Gravity.CENTER
            textView.includeFontPadding = false
            textView.minHeight = 32.dp
            return TagViewHolder(textView)
        }

        override fun onBindViewHolder(holder: TagViewHolder, position: Int) {
            val item = items[position]
            val textView = holder.textView
            val horizontalPadding = resources.getDimensionPixelSize(R.dimen.bookshelf_tag_item_padding_horizontal)
            textView.setPadding(horizontalPadding + 2.dp, 0, horizontalPadding + 2.dp, 0)
            textView.setTextColor(
                ColorStateList.valueOf(if (position == selectedIndex) selectedTextColor else normalTextColor)
            )
            textView.text = item.text
            textView.typeface = textView.context.uiTypeface()
            textView.alpha = item.alpha
            textView.isSelected = position == selectedIndex

            val selected = position == selectedIndex && selectedBackgroundVisible
            textView.background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16.dp.toFloat()
                setColor(if (selected) glassSelectedFill else glassNormalFill)
                setStroke(1.dp, if (selected) glassSelectedStroke else glassNormalStroke)
            }
            textView.elevation = if (selected) 2.dp.toFloat() else 0f

            textView.setOnClickListener {
                val bindingPosition = holder.bindingAdapterPosition
                if (bindingPosition != RecyclerView.NO_POSITION) onTagClick?.invoke(bindingPosition)
            }
            textView.setOnLongClickListener {
                val bindingPosition = holder.bindingAdapterPosition
                if (bindingPosition == RecyclerView.NO_POSITION) false
                else onTagLongClick?.invoke(bindingPosition) ?: false
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private class TagViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private object ColorUtilsCompat {
        fun withAlpha(color: Int, alpha: Float): Int =
            Color.argb((Color.alpha(color) * alpha).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}
