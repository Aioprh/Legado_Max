package io.legado.app.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.widget.LinearLayout
import androidx.core.view.ViewCompat

/** 书籍详情页底部操作栏：保持导航栏兼容，同时避免被全局底部背景色覆盖。 */
class GlassBookActionBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        orientation = HORIZONTAL
        gravity = android.view.Gravity.CENTER_VERTICAL
        clipToOutline = true
        outlineProvider = android.view.ViewOutlineProvider.BACKGROUND
        elevation = 8.dp.toFloat()
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 20.dp.toFloat()
            setColor(resolveSurfaceColor())
            setStroke(1.dp, resolveStrokeColor())
        }
        ViewCompat.setBackgroundTintList(this, null)
    }

    override fun setBackgroundColor(color: Int) {
        // BookInfoActivity historically applies bottomBackground here. Keep the
        // glass drawable instead so the rounded action surface is not replaced.
        if (background == null) {
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 20.dp.toFloat()
                setColor(resolveSurfaceColor())
                setStroke(1.dp, resolveStrokeColor())
            }
        }
    }

    private fun resolveSurfaceColor(): Int {
        val night = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        return if (night) 0xE61B1B1D.toInt() else 0xF2FFFFFF.toInt()
    }

    private fun resolveStrokeColor(): Int =
        if ((resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES) {
            0x55FFFFFF
        } else {
            0xB3FFFFFF.toInt()
        }

    private val Int.dp: Int get() = (this * resources.displayMetrics.density).toInt()
}
