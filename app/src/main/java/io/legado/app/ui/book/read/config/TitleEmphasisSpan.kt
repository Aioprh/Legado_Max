package io.legado.app.ui.book.read.config

import android.graphics.Canvas
import android.graphics.Paint
import android.text.style.ReplacementSpan
import io.legado.app.utils.dpToPx

class TitleEmphasisSpan(
    private val textColor: Int,
    private val accentColor: Int,
) : ReplacementSpan() {

    private val gap = 8.dpToPx()
    private val barWidth = 4.dpToPx()

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
        return (paint.measureText(text, start, end) + gap + barWidth).toInt()
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
        val originColor = paint.color
        val barLeft = x
        val barRight = x + barWidth
        val barPaint = Paint(paint).apply {
            color = accentColor
            style = Paint.Style.FILL
            isAntiAlias = true
        }
        val radius = 2.dpToPx().toFloat()
        canvas.drawRoundRect(
            barLeft,
            (top + 3.dpToPx()).toFloat(),
            barRight,
            (bottom - 3.dpToPx()).toFloat(),
            radius,
            radius,
            barPaint
        )
        paint.color = textColor
        val drawX = x + barWidth + gap
        canvas.drawText(text, start, end, drawX, y.toFloat(), paint)
        paint.color = originColor
    }
}
