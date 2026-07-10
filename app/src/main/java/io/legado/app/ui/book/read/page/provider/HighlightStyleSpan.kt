package io.legado.app.ui.book.read.page.provider

import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.UpdateAppearance
import io.legado.app.ui.book.read.config.highlight.HighlightRuleStyle

/**
 * 阅读排版阶段传递高亮规则样式的轻量 Span。
 *
 * 只携带下划线、背景色和背景图等样式参数，不直接绘制；
 * 最终绘制由 TextLine 根据列对象上的样式字段完成。
 */
class HighlightStyleSpan(
    val underlineMode: Int,
    val underlineColor: Int,
    val underlineWidth: Float = 1f,
    val underlineOffset: Float = 2f,
    val underlineSvgPath: String = "",
    val bgColor: Int? = null,
    val bgImage: String = "",
    val bgImageFit: Int = 0,
    val bgImageScale: Float = 1f,
) : CharacterStyle(), UpdateAppearance {

    constructor(style: HighlightRuleStyle) : this(
        underlineMode = style.underlineMode,
        underlineColor = style.resolvedAccentColor,
        underlineWidth = style.underlineWidth,
        underlineOffset = style.underlineOffset,
        underlineSvgPath = style.underlineSvgPath,
        bgColor = style.bgColor,
        bgImage = style.bgImage,
        bgImageFit = style.bgImageFit,
        bgImageScale = style.bgImageScale,
    )

    override fun updateDrawState(tp: TextPaint) = Unit

}
