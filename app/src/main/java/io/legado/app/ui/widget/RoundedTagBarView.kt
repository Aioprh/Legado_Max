package io.legado.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
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
 * 视觉与顶部 Tab 共用液态玻璃体系，并在选中标签切换时显示流动高光折射。
 */
class RoundedTagBarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : FrameLayout(context, attrs) {

    enum class DisplayMode { CHIP, LIGHT, TEXT }
    data class Item(val text: CharSequence, val alpha: Float = 1f)

    private val layoutManager = LinearLayoutManager(context, RecyclerView.HORIZONTAL, false)
    private val adapter = TagAdapter()
    private val recyclerView = RecyclerView(context).apply {
        layoutManager = this@RoundedTagBarView.layoutManager
        adapter = this@RoundedTagBarView.adapter
        overScrollMode = OVER_SCROLL_NEVER
        itemAnimator = null
        clipToPadding = false
        isHorizontalScrollBarEnabled = false
        isHorizontalFadingEdgeEnabled = false
        isVerticalFadingEdgeEnabled = false
        setFadingEdgeLength(0)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.bookshelf_tag_recycler_padding_vertical)
        setPadding(0, verticalPadding, 0, verticalPadding)
        setBackgroundColor(Color.TRANSPARENT)
    }
    private var items = emptyList<Item>()
    private var selectedIndex = RecyclerView.NO_POSITION
    private var onTagClick: ((Int) -> Unit)? = null
    private var onTagLongClick: ((Int) -> Boolean)? = null
    private var styleSignature: String? = null
    private var selectedBackgroundVisible = true
    private var displayMode = DisplayMode.CHIP
    private var backgroundOverrideColor: Int? = null
    private var highlightProgress = -0.35f
    private var highlightAnimator: ValueAnimator? = null
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        clipToOutline = true
        applyTopBarStyle(force = true)
        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.bookshelf_tag_bar_padding_horizontal)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.bookshelf_tag_bar_padding_vertical)
        setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)
        addView(recyclerView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        applyTopBarStyle()
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        drawGlassHighlight(canvas)
    }

    override fun onDetachedFromWindow() {
        highlightAnimator?.cancel()
        highlightAnimator = null
        super.onDetachedFromWindow()
    }

    fun applyTopBarStyle(force: Boolean = false) {
        val signature = "${TopBarConfig.currentSignature(AppConfig.isNightTheme)}|$displayMode|$backgroundOverrideColor"
        if (!force && styleSignature == signature) return
        styleSignature = signature

        val isNight = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val glassStroke = if (isNight) 0x4DFFFFFF else 0x80FFFFFF.toInt()
        background = GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (isNight) intArrayOf(Color.argb(48, 255, 255, 255), Color.argb(28, 255, 255, 255))
            else intArrayOf(Color.argb(108, 255, 255, 255), Color.argb(66, 255, 255, 255))
        ).apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20.dp.toFloat()
            setStroke(1.dp, glassStroke)
        }
        elevation = 3.dp.toFloat()
        translationZ = 1.dp.toFloat()

        val horizontalPadding = resources.getDimensionPixelSize(R.dimen.bookshelf_tag_bar_padding_horizontal)
        val verticalPadding = resources.getDimensionPixelSize(R.dimen.bookshelf_tag_bar_padding_vertical)
        setPadding(horizontalPadding, maxOf(verticalPadding, 4.dp), horizontalPadding, maxOf(verticalPadding, 4.dp))

        adapter.normalTextColor = if (isNight) Color.argb(225, 255, 255, 255) else context.primaryTextColor
        adapter.selectedTextColor = Color.WHITE
        adapter.selectedBackgroundColor = context.accentColor
        adapter.glassNormalFill = if (isNight) 0x2EFFFFFF else 0x58FFFFFF
        adapter.glassNormalStroke = if (isNight) 0x4DFFFFFF else 0x75FFFFFF
        adapter.glassSelectedFill = ColorUtilsCompat.withAlpha(context.accentColor, 0.80f)
        adapter.glassSelectedStroke = 0xA6FFFFFF.toInt()
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
        if (this.selectedIndex != RecyclerView.NO_POSITION) scrollToIndex(this.selectedIndex, smooth = false)
    }

    fun setSelectedIndex(index: Int, smooth: Boolean = true) {
        val newIndex = normalizeIndex(index)
        if (selectedIndex == newIndex) {
            if (newIndex != RecyclerView.NO_POSITION) scrollToIndex(newIndex, smooth)
            return
        }
        val oldIndex = selectedIndex
        selectedIndex = newIndex
        if (oldIndex in items.indices) adapter.notifyItemChanged(oldIndex)
        if (newIndex != RecyclerView.NO_POSITION) {
            adapter.notifyItemChanged(newIndex)
            scrollToIndex(newIndex, smooth)
            animateGlassHighlight()
        }
    }

    fun getSelectedIndex(): Int = selectedIndex
    fun setOnTagClickListener(listener: ((Int) -> Unit)?) { onTagClick = listener }
    fun setOnTagLongClickListener(listener: ((Int) -> Boolean)?) { onTagLongClick = listener }

    private fun normalizeIndex(index: Int): Int = if (index in items.indices) index else RecyclerView.NO_POSITION

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

    private fun animateGlassHighlight() {
        highlightAnimator?.cancel()
        highlightProgress = -0.35f
        highlightAnimator = ValueAnimator.ofFloat(-0.35f, 1.35f).apply {
            duration = 520L
            interpolator = DecelerateInterpolator(1.6f)
            addUpdateListener {
                highlightProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun drawGlassHighlight(canvas: Canvas) {
        val selectedView = recyclerView.findViewHolderForAdapterPosition(selectedIndex)?.itemView ?: return
        if (selectedView.width <= 0 || selectedView.height <= 0) return
        val location = IntArray(2)
        selectedView.getLocationInWindow(location)
        val own = IntArray(2)
        getLocationInWindow(own)
        val left = (location[0] - own[0]).toFloat()
        val top = (location[1] - own[1]).toFloat()
        val right = left + selectedView.width
        val bottom = top + selectedView.height
        val center = left + (right - left) * highlightProgress
        val spread = (right - left).coerceAtLeast(1f) * 0.38f
        highlightPaint.shader = LinearGradient(
            center - spread, 0f, center + spread, 0f,
            intArrayOf(Color.TRANSPARENT, Color.argb(78, 255, 255, 255), Color.TRANSPARENT),
            floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.clipRect(left, top, right, bottom)
        canvas.drawRoundRect(left + 1.dp, top + 1.dp, right - 1.dp, bottom - 1.dp, 15.dp.toFloat(), 15.dp.toFloat(), highlightPaint)
        canvas.restore()
        highlightPaint.shader = null
    }

    private inner class TagAdapter : RecyclerView.Adapter<TagViewHolder>() {
        var selectedBackgroundColor: Int = context.primaryColor
        var selectedTextColor: Int = context.accentColor
        var normalTextColor: Int = context.primaryTextColor
        var glassNormalFill: Int = 0x55FFFFFF
        var glassNormalStroke: Int = 0x88FFFFFF.toInt()
        var glassSelectedFill: Int = context.accentColor
        var glassSelectedStroke: Int = 0xAAFFFFFF.toInt()

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TagViewHolder {
            val textView = LayoutInflater.from(parent.context).inflate(R.layout.item_bookshelf_group_tag, parent, false) as TextView
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
            textView.setTextColor(ColorStateList.valueOf(if (position == selectedIndex) selectedTextColor else normalTextColor))
            textView.text = item.text
            textView.typeface = textView.context.uiTypeface()
            textView.alpha = item.alpha
            textView.isSelected = position == selectedIndex
            val selected = position == selectedIndex && selectedBackgroundVisible
            textView.background = GradientDrawable(
                GradientDrawable.Orientation.TOP_BOTTOM,
                if (selected) intArrayOf(ColorUtilsCompat.withAlpha(glassSelectedFill, 0.96f), ColorUtilsCompat.withAlpha(glassSelectedFill, 0.72f))
                else intArrayOf(glassNormalFill, ColorUtilsCompat.withAlpha(glassNormalFill, 0.62f))
            ).apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 16.dp.toFloat()
                setStroke(1.dp, if (selected) glassSelectedStroke else glassNormalStroke)
            }
            textView.elevation = if (selected) 2.dp.toFloat() else 0f
            textView.setOnClickListener {
                val bindingPosition = holder.bindingAdapterPosition
                if (bindingPosition != RecyclerView.NO_POSITION) {
                    onTagClick?.invoke(bindingPosition)
                    setSelectedIndex(bindingPosition, smooth = true)
                }
            }
            textView.setOnLongClickListener {
                val bindingPosition = holder.bindingAdapterPosition
                if (bindingPosition == RecyclerView.NO_POSITION) false else onTagLongClick?.invoke(bindingPosition) ?: false
            }
        }

        override fun getItemCount(): Int = items.size
    }

    private class TagViewHolder(val textView: TextView) : RecyclerView.ViewHolder(textView)
    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()

    private object ColorUtilsCompat {
        fun withAlpha(color: Int, alpha: Float): Int = Color.argb((Color.alpha(color) * alpha).toInt().coerceIn(0, 255), Color.red(color), Color.green(color), Color.blue(color))
    }
}
