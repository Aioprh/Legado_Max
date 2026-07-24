package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View
import io.legado.app.utils.dpToPx
import kotlin.math.min

/**
 * 图片裁剪遮罩 View。
 *
 * 在 [ImageCropActivity] 中叠加在 PhotoView 上方，显示裁剪框和九宫格辅助线。
 * 裁剪框区域为高亮，外部区域为半透明遮罩。
 * 支持设置宽高比，自动计算最佳裁剪框位置和尺寸。
 */
class ImageCropOverlayView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    private val cropRect = RectF()
    private val dimPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x99000000.toInt()
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f.dpToPx()
    }
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66FFFFFF
        style = Paint.Style.STROKE
        strokeWidth = 0.75f.dpToPx()
    }

    private var aspectWidth: Int = 1
    private var aspectHeight: Int = 1

    /** 设置裁剪框的宽高比，会重新计算裁剪框位置 */
    fun setAspect(width: Int, height: Int) {
        aspectWidth = width.coerceAtLeast(1)
        aspectHeight = height.coerceAtLeast(1)
        updateCropRect()
        invalidate()
    }

    /** 返回当前裁剪框的矩形副本 */
    fun getCropRect(): RectF = RectF(cropRect)

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        updateCropRect()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (cropRect.isEmpty) return
        canvas.drawRect(0f, 0f, width.toFloat(), cropRect.top, dimPaint)
        canvas.drawRect(0f, cropRect.bottom, width.toFloat(), height.toFloat(), dimPaint)
        canvas.drawRect(0f, cropRect.top, cropRect.left, cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect.right, cropRect.top, width.toFloat(), cropRect.bottom, dimPaint)
        canvas.drawRect(cropRect, borderPaint)

        val thirdWidth = cropRect.width() / 3f
        val thirdHeight = cropRect.height() / 3f
        canvas.drawLine(cropRect.left + thirdWidth, cropRect.top, cropRect.left + thirdWidth, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left + thirdWidth * 2f, cropRect.top, cropRect.left + thirdWidth * 2f, cropRect.bottom, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + thirdHeight, cropRect.right, cropRect.top + thirdHeight, gridPaint)
        canvas.drawLine(cropRect.left, cropRect.top + thirdHeight * 2f, cropRect.right, cropRect.top + thirdHeight * 2f, gridPaint)
    }

    private fun updateCropRect() {
        if (width <= 0 || height <= 0) return
        val horizontalPadding = 24.dpToPx().toFloat()
        val topPadding = 36.dpToPx().toFloat()
        val bottomPadding = 88.dpToPx().toFloat()
        val availableWidth = (width - horizontalPadding * 2f).coerceAtLeast(1f)
        val availableHeight = (height - topPadding - bottomPadding).coerceAtLeast(1f)
        val targetRatio = aspectWidth.toFloat() / aspectHeight
        var cropWidth = availableWidth
        var cropHeight = cropWidth / targetRatio
        if (cropHeight > availableHeight) {
            cropHeight = availableHeight
            cropWidth = cropHeight * targetRatio
        }
        val maxSize = min(availableWidth, availableHeight)
        if (aspectWidth == aspectHeight) {
            cropWidth = maxSize
            cropHeight = maxSize
        }
        val left = (width - cropWidth) / 2f
        val top = topPadding + (availableHeight - cropHeight) / 2f
        cropRect.set(left, top, left + cropWidth, top + cropHeight)
    }
}
