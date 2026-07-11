package io.legado.app.ui.book.read.config.highlight

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import io.legado.app.utils.dpToPx

/**
 * 方框 Span，用矩形框住匹配的文字。
 * 如果匹配到一整行，由于段合并机制，会绘制为一个长方形。
 *
 * @param textColor 文字颜色
 * @param boxColor 方框颜色
 * @param underlineWidth 方框线条粗细(dp)
 */
class BoxTextSpan(
    private val textColor: Int,
    private val boxColor: Int,
    private val underlineWidth: Float = 1f,
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        if (fm != null) {
            val metrics = paint.fontMetricsInt
            fm.top = metrics.top
            fm.ascent = metrics.ascent
            fm.descent = metrics.descent
            fm.bottom = metrics.bottom
        }
        return paint.measureText(text, start, end).toInt()
    }

    override fun draw(
        canvas: Canvas,
        text: CharSequence,
        start: Int,
        end: Int,
        x: Float,
        top: Int,
        y: Int,
        bottom: Int,
        paint: Paint
    ) {
        val textStr = text.subSequence(start, end).toString()
        paint.color = textColor
        canvas.drawText(textStr, x, y.toFloat(), paint)

        val width = paint.measureText(text, start, end)
        val fm = paint.fontMetrics
        val pad = 1.dpToPx().toFloat()
        val boxTop = y + fm.ascent - pad
        val boxBottom = y + fm.descent + pad
        val boxPaint = Paint(paint).apply {
            color = boxColor
            style = Paint.Style.STROKE
            strokeWidth = underlineWidth.dpToPx()
            isAntiAlias = true
        }
        canvas.drawRect(x, boxTop, x + width, boxBottom, boxPaint)
    }
}
