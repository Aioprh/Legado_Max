package io.legado.app.ui.widget.image

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator

/**
 * 书架专用玻璃封面。
 * 继承 CoverImageView 保持全部原有加载能力，只增加轻量边缘高光和一次性入场反射。
 */
class GlassCoverImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoverImageView(context, attrs) {

    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var reflectionProgress = -0.45f
    private var reflectionAnimator: ValueAnimator? = null
    private var entryPlayed = false

    init {
        clipToOutline = true
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!entryPlayed) {
            entryPlayed = true
            postDelayed({
                if (isAttachedToWindow) animateReflection(560L)
            }, 90L)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (isPressed || isFocused) animateReflection(380L)
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (reflectionAnimator?.isRunning == true || isPressed || isFocused) {
            drawReflection(canvas)
        }
    }

    override fun onDetachedFromWindow() {
        reflectionAnimator?.cancel()
        reflectionAnimator = null
        super.onDetachedFromWindow()
    }

    private fun animateReflection(duration: Long) {
        reflectionAnimator?.cancel()
        reflectionProgress = -0.45f
        reflectionAnimator = ValueAnimator.ofFloat(-0.45f, 1.45f).apply {
            this.duration = duration
            interpolator = DecelerateInterpolator(1.4f)
            addUpdateListener {
                reflectionProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun drawReflection(canvas: Canvas) {
        val center = width * reflectionProgress
        val spread = width.coerceAtLeast(1) * 0.24f
        reflectionPaint.shader = LinearGradient(
            center - spread, 0f, center + spread, 0f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(26, 255, 255, 255),
                Color.argb(54, 255, 255, 255),
                Color.argb(16, 255, 255, 255),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.35f, 0.50f, 0.65f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(
            0f, 0f, width.toFloat(), height.toFloat(),
            12f, 12f, reflectionPaint
        )
        reflectionPaint.shader = null
        canvas.restore()
    }
}
