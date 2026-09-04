package io.legado.app.ui.widget

import android.animation.ValueAnimator
import android.content.Context
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.animation.DecelerateInterpolator
import androidx.constraintlayout.widget.ConstraintLayout
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.utils.dpToPx

/**
 * 书架书籍卡片液态玻璃容器。
 * 不改变卡片内部布局，仅负责材质、边缘高光以及点击/聚焦时的折射光带。
 */
class GlassBookCardView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ConstraintLayout(context, attrs) {

    private var highlightProgress = -0.35f
    private var highlightAnimator: ValueAnimator? = null
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    init {
        isClickable = true
        isFocusable = true
        background = createGlassBackground()
        elevation = 2.dpToPx().toFloat()
    }

    override fun drawableStateChanged() {
        super.drawableStateChanged()
        if (isPressed || isFocused) {
            animateHighlight()
            elevation = 4.dpToPx().toFloat()
        } else {
            elevation = 2.dpToPx().toFloat()
        }
    }

    override fun dispatchDraw(canvas: Canvas) {
        super.dispatchDraw(canvas)
        if (isPressed || isFocused || highlightAnimator?.isRunning == true) {
            drawHighlight(canvas)
        }
    }

    override fun onDetachedFromWindow() {
        highlightAnimator?.cancel()
        highlightAnimator = null
        super.onDetachedFromWindow()
    }

    private fun createGlassBackground(): GradientDrawable {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
        val stroke = if (night) Color.argb(55, 255, 255, 255) else Color.argb(92, 255, 255, 255)
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            if (night) {
                intArrayOf(Color.argb(58, 255, 255, 255), Color.argb(30, 255, 255, 255))
            } else {
                intArrayOf(Color.argb(118, 255, 255, 255), Color.argb(62, 255, 255, 255))
            }
        ).apply {
            cornerRadius = 16.dpToPx().toFloat()
            setStroke(1.dpToPx(), stroke)
        }
    }

    private fun animateHighlight() {
        highlightAnimator?.cancel()
        highlightProgress = -0.35f
        highlightAnimator = ValueAnimator.ofFloat(-0.35f, 1.35f).apply {
            duration = 420L
            interpolator = DecelerateInterpolator(1.5f)
            addUpdateListener {
                highlightProgress = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    private fun drawHighlight(canvas: Canvas) {
        val accent = ThemeStore.accentColor(context)
        val center = width * highlightProgress
        val spread = width.coerceAtLeast(1) * 0.30f
        highlightPaint.shader = LinearGradient(
            center - spread, 0f, center + spread, 0f,
            intArrayOf(
                Color.TRANSPARENT,
                Color.argb(42, 255, 255, 255),
                Color.argb(22, Color.red(accent), Color.green(accent), Color.blue(accent)),
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.44f, 0.58f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.save()
        canvas.clipRect(0f, 0f, width.toFloat(), height.toFloat())
        canvas.drawRoundRect(
            1.dpToPx().toFloat(), 1.dpToPx().toFloat(),
            width - 1.dpToPx().toFloat(), height - 1.dpToPx().toFloat(),
            15.dpToPx().toFloat(), 15.dpToPx().toFloat(), highlightPaint
        )
        canvas.restore()
        highlightPaint.shader = null
    }
}
