package io.legado.app.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.ViewGroup
import android.widget.FrameLayout
import com.qmdeve.liquidglass.Config
import com.qmdeve.liquidglass.LiquidGlass
import io.legado.app.utils.dpToPx

/**
 * 稳定的液态玻璃视图，封装 [LiquidGlass] 库以提供实时模糊效果。
 *
 * 用于底栏导航栏的玻璃/磨砂材质渲染。通过 [bind] 绑定内容容器作为模糊采样源，
 * 然后通过各项 setter 配置模糊半径、色散、折射高度等参数。
 *
 * 玻璃效果（EFFECT_GLASS）：较小的模糊半径 + 较高的色散，产生清晰通透的折射感。
 * 磨砂效果（EFFECT_FROSTED）：较大的模糊半径 + 较低的色散，产生朦胧磨砂感。
 *
 * 性能优化：
 * - [beginBatchUpdate] / [endBatchUpdate] 合并多个参数设置为单次刷新，避免逐个 setter
 *   各自触发 [LiquidGlass.updateParameters] 导致的重复 GPU 上传。
 * - [release] 主动解绑采样源并移除 LiquidGlass 子视图，在底栏隐藏或切换为实色模式时
 *   及时释放资源，避免持续采样整页内容造成的电量与 CPU 开销。
 */
class StableLiquidGlassView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private var sampleSource: ViewGroup? = null
    private var boundSource: ViewGroup? = null
    private var glass: LiquidGlass? = null
    private var config: Config? = null

    private var cornerRadius = 40f.dpToPx()
    private var refractionHeight = 20f.dpToPx()
    private var refractionOffset = 70f.dpToPx()
    private var tintAlpha = 0f
    private var tintColorRed = 1f
    private var tintColorGreen = 1f
    private var tintColorBlue = 1f
    private var blurRadius = 0.01f
    private var dispersion = 0.5f

    /** 批量更新计数器，> 0 时延迟 applyConfig */
    private var batchDepth = 0

    /** 标记批量更新期间有参数变化，待 endBatchUpdate 时统一刷新 */
    private var batchDirty = false

    /** 是否已主动释放采样源 */
    private var released = false

    init {
        clipChildren = false
        clipToPadding = false
        setWillNotDraw(false)
    }

    /**
     * 开启批量更新模式。在 [endBatchUpdate] 之前，所有 setter 方法
     * 只更新内部字段值，不会触发 [LiquidGlass.updateParameters]。
     *
     * 可嵌套调用，只有最外层的 [endBatchUpdate] 才会执行实际刷新。
     */
    fun beginBatchUpdate() {
        batchDepth++
    }

    /**
     * 结束批量更新模式。如果是最外层调用且有参数变化，执行一次 [applyConfig]。
     */
    fun endBatchUpdate() {
        if (batchDepth > 0) {
            batchDepth--
            if (batchDepth == 0 && batchDirty) {
                batchDirty = false
                applyConfig()
            }
        }
    }

    /** 绑定内容容器作为模糊采样源 */
    fun bind(source: ViewGroup?) {
        if (source == null) return
        sampleSource = source
        released = false
        ensureGlass()
    }

    /**
     * 释放采样源并移除 LiquidGlass 子视图。
     *
     * 在底栏切换为实色模式、隐藏或 Activity 不可见时调用，
     * 避免持续采样整页内容造成的 CPU/GPU 开销。
     * 可通过再次调用 [bind] 恢复。
     */
    fun release() {
        released = true
        removeGlass()
        sampleSource = null
    }

    /** 当前是否已释放采样源 */
    fun isReleased(): Boolean = released

    fun setCornerRadius(value: Float) {
        cornerRadius = value.coerceIn(0f, (height.takeIf { it > 0 } ?: Int.MAX_VALUE).toFloat() / 2f)
        deferOrApply()
    }

    fun setRefractionHeight(value: Float) {
        refractionHeight = value.coerceIn(12f.dpToPx(), 50f.dpToPx())
        deferOrApply()
    }

    fun setRefractionOffset(value: Float) {
        refractionOffset = value.coerceIn(20f.dpToPx(), 120f.dpToPx())
        deferOrApply()
    }

    fun setTintAlpha(value: Float) {
        tintAlpha = value
        deferOrApply()
    }

    fun setTintColorRed(value: Float) {
        tintColorRed = value
        deferOrApply()
    }

    fun setTintColorGreen(value: Float) {
        tintColorGreen = value
        deferOrApply()
    }

    fun setTintColorBlue(value: Float) {
        tintColorBlue = value
        deferOrApply()
    }

    fun setDispersion(value: Float) {
        dispersion = value.coerceIn(0f, 1f)
        deferOrApply()
    }

    fun setBlurRadius(value: Float) {
        blurRadius = value.coerceIn(0.01f, 50f)
        deferOrApply()
    }

    fun setDraggableEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = Unit

    fun setElasticEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = Unit

    fun setTouchEffectEnabled(@Suppress("UNUSED_PARAMETER") enabled: Boolean) = Unit

    /**
     * 如果处于批量更新模式，仅标记 dirty；否则立即执行 [applyConfig]。
     */
    private fun deferOrApply() {
        if (batchDepth > 0) {
            batchDirty = true
        } else {
            applyConfig()
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (!released) {
            ensureGlass()
        }
    }

    override fun onDetachedFromWindow() {
        removeGlass()
        super.onDetachedFromWindow()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w != oldw || h != oldh) {
            applyConfig()
        }
    }

    private fun ensureGlass() {
        val source = sampleSource ?: return
        if (released) return
        if (!isAttachedToWindow) return
        if (glass == null) {
            val nextConfig = createConfig()
            val nextGlass = LiquidGlass(context, nextConfig)
            config = nextConfig
            glass = nextGlass
            addView(nextGlass, 0, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        }
        if (boundSource !== source) {
            runCatching {
                glass?.init(source)
                boundSource = source
            }.onFailure {
                rebuildGlass(source)
            }
        }
        applyConfig()
    }

    private fun applyConfig() {
        val source = sampleSource
        if (!isAttachedToWindow || source == null || released) return
        if (glass == null) {
            ensureGlass()
            return
        }
        runCatching {
            config?.configure(overrides())
            glass?.updateParameters()
            invalidate()
        }.onFailure {
            rebuildGlass(source)
        }
    }

    private fun rebuildGlass(source: ViewGroup) {
        removeGlass()
        sampleSource = source
        post { if (!released) ensureGlass() }
    }

    private fun removeGlass() {
        glass?.let { runCatching { removeView(it) } }
        glass = null
        config = null
        boundSource = null
    }

    private fun createConfig(): Config {
        return Config().apply {
            configure(overrides())
        }
    }

    private fun overrides(): Config.Overrides {
        val width = width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels
        val height = height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels
        return Config.Overrides()
            .noFilter()
            .contrast(0f)
            .whitePoint(0f)
            .chromaMultiplier(1f)
            .blurRadius(blurRadius)
            .cornerRadius(cornerRadius)
            .refractionHeight(refractionHeight)
            .refractionOffset(-refractionOffset)
            .tintAlpha(tintAlpha)
            .tintColorRed(tintColorRed)
            .tintColorGreen(tintColorGreen)
            .tintColorBlue(tintColorBlue)
            .dispersion(dispersion)
            .size(width, height)
    }
}
