package io.legado.app.ui.widget

import android.content.Context
import android.text.TextUtils
import android.util.AttributeSet
import android.widget.LinearLayout
import android.widget.TextView
import io.legado.app.ui.widget.text.AccentBgTextView
import io.legado.app.utils.dpToPx

@Suppress("unused", "MemberVisibilityCanBePrivate")
class LabelsBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val unUsedViews = arrayListOf<TextView>()
    private val usedViews = arrayListOf<TextView>()
    var textSize = 12f

    fun setLabels(labels: List<String>, onClick: ((String) -> Unit)? = null, onLongClick: ((String) -> Boolean)? = null) {
        clear()
        labels.forEachIndexed { index, it ->
            addLabel(it, onClick, onLongClick, leading = index == 0)
        }
    }

    fun clear() {
        unUsedViews.addAll(usedViews)
        usedViews.clear()
        removeAllViews()
    }

    fun addLabel(label: String, onClick: ((String) -> Unit)?, onLongClick: ((String) -> Boolean)?, leading: Boolean = false) {
        val tv = if (unUsedViews.isEmpty()) {
            AccentBgTextView(context, null).apply {
                setPadding(6.dpToPx(), 3.dpToPx(), 6.dpToPx(), 3.dpToPx())
                setRadius(10)
                val lp = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
                lp.setMargins(0, 0, 4.dpToPx(), 0)
                layoutParams = lp
                text = label
                maxLines = 1
                usedViews.add(this)
            }
        } else {
            unUsedViews.last().apply {
                usedViews.add(this)
                unUsedViews.removeAt(unUsedViews.lastIndex)
            }
        }
        tv.textSize = textSize
        tv.text = label
        // 单行省略，避免标签文本过长时被硬裁剪而“缺字”。
        tv.isSingleLine = true
        tv.ellipsize = TextUtils.TruncateAt.END
        // 首个标签通常是字数等元数据，限制其宽度以保证玄幻/仙侠等分类标签完整展示。
        tv.maxWidth = if (leading) LEADING_LABEL_MAX_WIDTH else Int.MAX_VALUE
        if (onClick != null) {
            tv.setOnClickListener { onClick.invoke(label) }
        }
        if (onLongClick != null) {
            tv.setOnLongClickListener { onLongClick.invoke(label) }
        }
        addView(tv)
    }

    private companion object {
        val LEADING_LABEL_MAX_WIDTH = 88.dpToPx()
    }
}