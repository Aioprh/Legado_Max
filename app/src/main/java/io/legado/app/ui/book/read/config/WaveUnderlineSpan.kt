package io.legado.app.ui.book.read.config

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.text.style.ReplacementSpan
import io.legado.app.utils.dpToPx

class WaveUnderlineSpan(
    private val textColor: Int,
    private val underlineColor: Int,
) : ReplacementSpan() {

    override fun getSize(
        paint: Paint,
        text: CharSequence,
        start: Int,
        end: Int,
        fm: Paint.FontMetricsInt?
    ): Int {
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
        val originColor = paint.color
        val textStr = text.subSequence(start, end).toString()
        paint.color = textColor
        canvas.drawText(textStr, x, y.toFloat(), paint)

        val width = paint.measureText(text, start, end)
        val lineY = y + 6.dpToPx()
        val amplitude = 2.dpToPx().toFloat()
        val waveLength = 10.dpToPx().toFloat()
        val wavePaint = Paint(paint).apply {
            color = underlineColor
            style = Paint.Style.STROKE
            strokeWidth = 1.5f
            isAntiAlias = true
        }
        val path = Path().apply { moveTo(x, lineY.toFloat()) }
        var currentX = x
        val endX = x + width
        while (currentX < endX) {
            val nextX = (currentX + waveLength).coerceAtMost(endX)
            val midX = (currentX + nextX) / 2
            path.quadTo(midX, lineY - amplitude, nextX, lineY.toFloat())
            currentX = nextX
            if (currentX < endX) {
                val nextX2 = (currentX + waveLength).coerceAtMost(endX)
                val midX2 = (currentX + nextX2) / 2
                path.quadTo(midX2, lineY + amplitude, nextX2, lineY.toFloat())
                currentX = nextX2
            }
        }
        canvas.drawPath(path, wavePaint)
        paint.color = originColor
    }
}
