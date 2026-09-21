package io.legado.app.utils

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.drawable.Drawable
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.View
import android.view.Window
import io.legado.app.lib.theme.surface.SurfaceCorners
import io.legado.app.lib.theme.surface.SurfaceStyle

/** Resolves the host Activity window without inspecting popup internals. */
fun Context.findHostWindow(): Window? {
    var current: Context = this
    while (current is ContextWrapper) {
        if (current is Activity) return current.window
        current = current.baseContext
    }
    return null
}

/**
 * 玻璃表面生命周期（legadoC 移植的简化版）。
 *
 * 本项目采用普通圆角背景，不引入模糊/透明度体系。这里把 [SurfaceStyle] 直接绘制为
 * 单个圆角 [GradientSurfaceDrawable]，保证组件调用侧 API 与 legadoC 一致，但视觉保持
 * 与现有主题统一。模糊相关的 apply/refresh 保留签名并直接回调用户 onReady。
 */
object SurfaceBackdrop {

    /** 安装静态表面：立即把 [style] 画为目标背景。 */
    fun installStatic(target: View, style: SurfaceStyle) {
        target.background = GradientSurfaceDrawable(style)
        target.clipToOutline = style.cornerRadiusPx > 0f
        target.invalidateOutline()
        target.invalidate()
    }

    /** 更新表面视觉（保留静态效果）。 */
    fun updateStyle(target: View, style: SurfaceStyle) {
        installStatic(target, style)
    }

    /** 模糊采集入口：本项目无模糊，直接回调用户 ready。 */
    fun apply(
        hostWindow: Window,
        target: View,
        style: SurfaceStyle,
        layerOwner: View,
        onReady: () -> Unit = {}
    ) {
        installStatic(target, style)
        onReady()
    }

    /** 重新采集并保持样式（见 [apply]）。 */
    fun refresh(
        hostWindow: Window,
        target: View,
        layerOwner: View,
        onReady: () -> Unit = {}
    ) {
        onReady()
    }

    /** 多个表面的批量刷新。 */
    fun refresh(
        hostWindow: Window,
        targets: Iterable<View>,
        layerOwner: View,
        onReady: () -> Unit = {}
    ) {
        targets.forEach { refresh(hostWindow, it, layerOwner) }
        onReady()
    }

    /** 取消采集，保留静态样式（本项目此时仅保留静态背景）。 */
    fun cancel(target: View, keepStaticStyle: Boolean = true) {
        if (!keepStaticStyle) clear(target)
    }

    fun cancel(targets: Iterable<View>, keepStaticStyle: Boolean = true) {
        targets.forEach { cancel(it, keepStaticStyle) }
    }

    /** 清除表面背景，恢复为透明背景。 */
    fun clear(target: View) {
        target.background = null
        target.clipToOutline = false
        target.invalidateOutline()
        target.invalidate()
    }

    fun clear(targets: Iterable<View>) {
        targets.forEach(::clear)
    }
}

/**
 * 圆角矩形表面绘制器：把 [SurfaceStyle] 的圆角、方向与描边绘制为一个普通背景。
 * 不依赖 RenderEffect，兼容所有支持的 Android 版本。
 */
class GradientSurfaceDrawable(private val style: SurfaceStyle) : Drawable() {

    private val path = Path()
    private val rectF = RectF()
    private val strokeRectF = RectF()
    private val fillPaint = Paint().apply {
        isAntiAlias = true
        color = style.tintColor
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint().apply {
        isAntiAlias = true
        color = style.strokeColor
        strokeWidth = style.strokeWidthPx
        style = Paint.Style.STROKE
    }

    override fun draw(canvas: Canvas) {
        val bounds = bounds
        if (bounds.isEmpty) return
        rectF.set(bounds.left.toFloat(), bounds.top.toFloat(), bounds.right.toFloat(), bounds.bottom.toFloat())
        path.reset()
        buildRoundedPath(rectF, offsetRadius = 0f, out = path)
        val save = canvas.save()
        canvas.clipPath(path)
        canvas.drawRect(rectF, fillPaint)
        canvas.restoreToCount(save)
        if (style.strokeColor != android.graphics.Color.TRANSPARENT && style.strokeWidthPx > 0f) {
            val half = style.strokeWidthPx / 2f
            strokeRectF.set(
                bounds.left + half,
                bounds.top + half,
                bounds.right - half,
                bounds.bottom - half
            )
            val strokePath = Path()
            buildRoundedPath(strokeRectF, offsetRadius = -half, out = strokePath)
            canvas.drawPath(strokePath, strokePaint)
        }
    }

    private fun buildRoundedPath(rect: RectF, offsetRadius: Float, out: Path) {
        val radius = (style.cornerRadiusPx + offsetRadius).coerceAtLeast(0f)
        when (style.corners) {
            SurfaceCorners.NONE -> out.addRect(rect, Path.Direction.CW)
            SurfaceCorners.ALL -> out.addRoundRect(rect, radius, radius, Path.Direction.CW)
            SurfaceCorners.TOP -> {
                val clamp = radius.coerceAtMost(rect.width() / 2f)
                out.addRoundRect(
                    rect,
                    floatArrayOf(clamp, clamp, clamp, clamp, 0f, 0f, 0f, 0f),
                    Path.Direction.CW
                )
            }
        }
    }

    override fun setAlpha(alpha: Int) {
        fillPaint.alpha = alpha
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        fillPaint.colorFilter = colorFilter
    }

    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = android.graphics.PixelFormat.TRANSLUCENT
}