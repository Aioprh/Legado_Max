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

    // ── 新版发现模式适配（legadoC 移植） ──
    // 以下方法映射到本项目现有主题体系，不引入 legadoC 的透明度/模糊系统。

    /** 常规表面色（本项目无全局透明度，原样返回） */
    fun surfaceColor(color: Int, pressed: Boolean = false): Int = color

    /** 弹窗表面色（本项目无弹窗透明度，原样返回） */
    fun dialogSurfaceColor(color: Int): Int = color

    /** 弹窗/菜单使用的小圆角 */
    fun compactSurfaceRadius(context: Context): Float {
        return panelRadius(context)
    }

    /** 搜索框圆角 */
    fun searchRadius(value: Float): Float = value.dpToPx()

    /** 主题标签条底色 */
    fun themeSurfaceTagBarColor(context: Context): Int {
        return androidx.core.content.ContextCompat.getColor(context, R.color.background_card)
    }

    /** 主题标签选中色 */
    fun themeSurfaceTagSelectedColor(context: Context): Int {
        return androidx.core.content.ContextCompat.getColor(context, R.color.background_card)
    }

    /** 主题标签底色 */
    fun themeSurfaceTabColor(context: Context): Int {
        return androidx.core.content.ContextCompat.getColor(context, R.color.background_card)
    }

    /** 主题弱化表面色（菜单/浮层） */
    fun themeSurfaceMutedColor(context: Context): Int {
        return androidx.core.content.ContextCompat.getColor(context, R.color.background_menu)
    }

    /** 主题弹窗表面色 */
    fun themeSurfaceDialogColor(context: Context): Int {
        return androidx.core.content.ContextCompat.getColor(context, R.color.dialog_surface)
    }

    /** 主题面板描边色（本项目无主题描边体系，返回 null） */
    fun themePanelBorderColor(context: Context): Int? = null

    /** 弹窗内按钮按压/选中状态选择器 */
    fun dialogActionSelector(defaultColor: Int, pressedColor: Int, radius: Float): StateListDrawable {
        return actionSelector(defaultColor, pressedColor, radius)
    }

    /** 轻量按压/选中状态选择器（默认态使用半透明圆角） */
    fun softActionSelector(defaultColor: Int, pressedColor: Int, radius: Float): StateListDrawable {
        return actionSelector(defaultColor, pressedColor, radius)
    }

    private fun panelStrokeColor(color: Int): Int {
        val base = if (ColorUtils.calculateLuminance(color) > 0.5) Color.BLACK else Color.WHITE
        return ColorUtils.setAlphaComponent(base, (0.10f * 255).toInt())
    }
}
