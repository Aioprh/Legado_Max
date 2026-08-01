package io.legado.app.lib.theme

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import androidx.core.graphics.ColorUtils
import io.legado.app.R
import io.legado.app.utils.dpToPx

/**
 * UI 圆角工具类。
 *
 * 提供面板圆角、按钮圆角等常用圆角值，以及生成圆角 Drawable 的便捷方法。
 * 面板圆角带描边，描边颜色根据背景亮度自动取黑/白。
 * 按钮圆角支持按下/选中状态切换的选择器 Drawable。
 */
object UiCorner {

    /** 获取面板圆角值 */
    fun panelRadius(context: Context): Float {
        return context.resources.getDimension(R.dimen.ui_panel_radius)
    }

    /** 获取按钮圆角值 */
    fun actionRadius(context: Context): Float {
        return context.resources.getDimension(R.dimen.ui_action_radius)
    }

    /** 生成纯色圆角矩形 Drawable */
    fun rounded(color: Int, radius: Float): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(color)
        }
    }

    fun opaqueRounded(color: Int, radius: Float): GradientDrawable {
        return rounded(color, radius)
    }

    /** 生成带描边的面板圆角 Drawable */
    fun panelRounded(context: Context, color: Int, radius: Float): Drawable {
        return rounded(color, radius).apply {
            setStroke(1.dpToPx(), panelStrokeColor(color))
        }
    }

    /**
     * 生成半透明面板圆角 Drawable，描边使用 [R.color.border_card_surface]。
     *
     * 适用于管理界面中随背景自然融合的半透明层级（列表卡片、编辑区行等），
     * 白天用黑叠加描边、夜间用白叠加描边，避免 [panelRounded] 对半透明色
     * 计算亮度时取反描边方向的问题。
     */
    fun surfaceRounded(context: Context, color: Int, radius: Float): Drawable {
        val stroke = androidx.core.content.ContextCompat
            .getColor(context, R.color.border_card_surface)
        return rounded(color, radius).apply {
            setStroke(1.dpToPx(), stroke)
        }
    }

    /** 生成按钮按压/选中状态选择器 Drawable */
    fun actionSelector(defaultColor: Int, pressedColor: Int, radius: Float): StateListDrawable {
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), rounded(pressedColor, radius))
            addState(intArrayOf(android.R.attr.state_selected), rounded(pressedColor, radius))
            addState(intArrayOf(), opaqueRounded(defaultColor, radius))
        }
    }

    private fun panelStrokeColor(color: Int): Int {
        val base = if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
        return ColorUtils.setAlphaComponent(base, (0.10f * 255).toInt())
    }
}
