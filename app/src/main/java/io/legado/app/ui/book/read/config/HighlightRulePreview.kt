package io.legado.app.ui.book.read.config

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import io.legado.app.ui.book.read.config.BgColorSpan
import io.legado.app.ui.book.read.config.BgImageSpan
import io.legado.app.ui.book.read.config.DoubleUnderlineSpan
import io.legado.app.ui.book.read.config.SvgUnderlineSpan
import io.legado.app.ui.book.read.config.SolidUnderlineSpan
import io.legado.app.ui.book.read.config.DashUnderlineSpan
import io.legado.app.ui.book.read.config.WaveUnderlineSpan
import io.legado.app.ui.book.read.config.highlight.HighlightRule
import io.legado.app.ui.book.read.config.highlight.HighlightRuleStyle

/**
 * 高亮规则配置页的预览文本构建器。
 *
 * 根据规则正则和统一样式模型生成可直接显示在 TextView 中的预览内容，
 * 用于编辑页和规则列表卡片，不参与阅读页最终绘制。
 */
object HighlightRulePreview {

    fun build(rule: HighlightRule): CharSequence {
        val text = rule.normalizedSampleText()
        val spannable = SpannableStringBuilder(text)
        val regex = kotlin.runCatching { Regex(rule.pattern) }.getOrNull() ?: return spannable
        val style = HighlightRuleStyle.from(rule)
        regex.findAll(text).forEachIndexed { index, match ->
            val start = match.range.first
            val end = match.range.last + 1
            val textColor = style.resolvedTextColor
            val accentColor = style.resolvedAccentColor
            val underlineWidth = style.underlineWidth
            val underlineOffset = style.underlineOffset
            val hasBgImage = style.bgImage.isNotBlank()
            val bgColor = style.bgColor

            if (hasBgImage) {
                spannable.setSpan(
                    BgImageSpan(
                        textColor,
                        style.bgImage,
                        style.bgImageFit,
                        style.bgImageScale,
                        style.underlineMode,
                        accentColor,
                        underlineWidth,
                        style.underlineSvgPath,
                        underlineOffset
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else if (bgColor != null) {
                spannable.setSpan(
                    BgColorSpan(
                        textColor,
                        bgColor,
                        style.underlineMode,
                        accentColor,
                        underlineWidth,
                        style.underlineSvgPath,
                        underlineOffset
                    ),
                    start,
                    end,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            } else {
                when (style.underlineMode) {
                    4 -> {
                        spannable.setSpan(
                            DoubleUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                            start,
                            end,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                        )
                    }
                    5 -> {
                        val svgPath = style.underlineSvgPath
                        if (!svgPath.isNullOrBlank()) {
                            spannable.setSpan(
                                SvgUnderlineSpan(textColor, accentColor, underlineWidth, svgPath),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        } else {
                            spannable.setSpan(
                                ForegroundColorSpan(textColor),
                                start,
                                end,
                                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                            )
                        }
                    }
                    else -> {
                        when (style.underlineMode) {
                            1 -> {
                                spannable.setSpan(
                                    SolidUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            2 -> {
                                spannable.setSpan(
                                    DashUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            3 -> {
                                spannable.setSpan(
                                    WaveUnderlineSpan(textColor, accentColor, underlineWidth, underlineOffset),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                            else -> {
                                spannable.setSpan(
                                    ForegroundColorSpan(textColor),
                                    start,
                                    end,
                                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                                )
                            }
                        }
                    }
                }
                if (index == 0 && style.underlineMode != 4 && style.underlineMode != 5) {
                    val baseColor = rule.textColor ?: style.underlineColor ?: 0xFF63C37D.toInt()
                    spannable.setSpan(
                        BackgroundColorSpan((0x33 shl 24) or (baseColor and 0x00FFFFFF)),
                        start,
                        end,
                        Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
        }
        return spannable
    }
}
