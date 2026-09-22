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
        labels.forEach {
            addLabel(it, onClick, onLongClick)
        }
    }

    fun clear() {
        unUsedViews.addAll(usedViews)
        usedViews.clear()
        removeAllViews()
    }

    fun addLabel(label: String, onClick: ((String) -> Unit)?, onLongClick: ((String) -> Boolean)?) {
        val tv = if (unUsedViews.isEmpty()) {
            AccentBgTextView(context, null).apply {
                setPadding(6.dpToPx(), 3.dpToPx(), 6.dpToPx(), 3.dpToPx())
                setRadius(10)
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
        // 单行省略，避免标签文本过长时被硬裁剪而"缺字"。
        tv.isSingleLine = true
        tv.ellipsize = TextUtils.TruncateAt.END
        tv.maxWidth = Int.MAX_VALUE
        // 所有标签均 wrap_content，按内容自适应宽度。
        val lp = (tv.layoutParams as? LayoutParams) ?: LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
        if (lp.width != LayoutParams.WRAP_CONTENT || lp.weight != 0f) {
            lp.width = LayoutParams.WRAP_CONTENT
            lp.weight = 0f
        }
        lp.setMargins(0, 0, 4.dpToPx(), 0)
        tv.layoutParams = lp
        if (onClick != null) {
            tv.setOnClickListener { onClick.invoke(label) }
        }
        if (onLongClick != null) {
            tv.setOnLongClickListener { onLongClick.invoke(label) }
        }
        addView(tv)
    }
}