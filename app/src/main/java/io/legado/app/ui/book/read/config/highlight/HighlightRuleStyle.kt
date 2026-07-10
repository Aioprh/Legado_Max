package io.legado.app.ui.book.read.config.highlight

/**
 * 高亮规则的统一样式快照。样式模型
 *
 * 从 `HighlightRule` 中抽取预览和阅读渲染共同需要的样式字段，
 * 避免配置页预览与阅读页实际绘制各自解析一套样式。
 */
data class HighlightRuleStyle(
    val textColor: Int?,
    val underlineMode: Int,
    val underlineColor: Int?,
    val underlineWidth: Float,
    val underlineOffset: Float,
    val underlineSvgPath: String,
    val bgColor: Int?,
    val bgImage: String,
    val bgImageFit: Int,
    val bgImageScale: Float,
) {

    val resolvedTextColor: Int
        get() = textColor ?: 0xFF111111.toInt()

    val resolvedAccentColor: Int
        get() = underlineColor ?: textColor ?: 0xFF63C37D.toInt()

    val hasDecoration: Boolean
        get() = underlineMode != 0 || bgImage.isNotBlank() || bgColor != null

    companion object {
        fun from(rule: HighlightRule): HighlightRuleStyle {
            return HighlightRuleStyle(
                textColor = rule.textColor,
                underlineMode = rule.underlineMode,
                underlineColor = rule.underlineColor,
                underlineWidth = rule.underlineWidth,
                underlineOffset = rule.underlineOffset,
                underlineSvgPath = rule.underlineSvgPath.orEmpty(),
                bgColor = rule.bgColor,
                bgImage = rule.bgImage.orEmpty(),
                bgImageFit = rule.bgImageFit,
                bgImageScale = rule.bgImageScale,
            )
        }
    }
}