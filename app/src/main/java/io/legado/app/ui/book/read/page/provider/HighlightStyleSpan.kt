package io.legado.app.ui.book.read.page.provider

import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance

/**
 * 用于在阅读排版阶段传递局部下划线样式
 */
class HighlightStyleSpan(
    val underlineMode: Int,
    val underlineColor: Int,
) : CharacterStyle(), UpdateAppearance {

    override fun updateDrawState(tp: TextPaint) = Unit

}
