package io.legado.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.util.AttributeSet
import android.view.View
import android.view.animation.DecelerateInterpolator
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.ui.widget.image.CoverImageView
import io.legado.app.utils.dpToPx

/**
 * 书架封面液态玻璃反射层。
 * 保留 CoverImageView 全部加载能力，仅增加轻量、一次性的玻璃折射高光。
 */
class GlassCoverImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : CoverImageView(context, attrs) {

    private val reflectionPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private var reflectionProgress = -0.4f
    private var reflectionAnimator: ValueAnimator? = null
    private var entryPlayed = false

    init {
        clipToOutline = true
        elevation = 1.dpToPx().toFloat()
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!entryPlayed) {
            entryPlayed = true
            postDelayed({
                if (isAttachedToWindow) animateReflection(560L)
            }, 110L)
        }
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (isPressed || isFocused) {
            animateReflection(340L)
            elevation = 3.dpToPx().toFloat()
        } else {
            elevation = 1.dpToPx().toFloat()
        }
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
        reflectionProgress = -0.4f
        reflectionAnimator = ValueAnimator.ofFloat(-0.4f, 1.4f).apply {
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
        val accent = ThemeStore.accentColor(context)
        val center = width * reflectionProgress
        val spread = width.coerceAtLeast(1) * 0.34f
        reflectionPaint.shader = LinearGradient(
            center - spread,
            0f,
            center + spread,
            height.toFloat(),
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(34, 255, 255, 255),
                Color.argb(18, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.43f, 0.57f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(
            1.dpToPx().toFloat(),
            1.dpToPx().toFloat(),
            width - 1.dpToPx().toFloat(),
            height - 1.dpToPx().toFloat(),
            11.dpToPx().toFloat(),
            11.dpToPx().toFloat(),
            reflectionPaint
        )
        canvas.restore()
        reflectionPaint.shader = null
    }
}
