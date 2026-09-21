package io.legado.app.lib.theme.surface

import android.content.Context
import androidx.annotation.ColorInt
import io.legado.app.lib.theme.UiCorner

/**
 * 可调表面的视觉描述（legadoC 移植的简化版）。
 *
 * 本项目没有全局透明度/模糊体系，这里只保留圆角、颜色、圆角方向与描边，
 * 由 [SurfaceBackdrop] 直接绘制为普通圆角背景，与现有主题保持一致。
 */
data class SurfaceStyle(
    @param:ColorInt val tintColor: Int,
    val cornerRadiusPx: Float,
    val corners: SurfaceCorners = SurfaceCorners.ALL,
    @param:ColorInt val strokeColor: Int = android.graphics.Color.TRANSPARENT,
    val strokeWidthPx: Float = 0f,
    val blurRadiusPx: Int = 0
)

enum class SurfaceCorners {
    NONE,
    ALL,
    TOP
}

/**
 * 弹窗、阅读浮层和普通 UI 块从这里取得样式。
 */
object SurfaceStyles {

    private const val PANEL_STROKE_WIDTH_DP = 1f

    fun dialog(context: Context, corners: SurfaceCorners = SurfaceCorners.ALL): SurfaceStyle {
        val themeStroke = UiCorner.themePanelBorderColor(context)
        return SurfaceStyle(
            tintColor = UiCorner.dialogSurfaceColor(
                UiCorner.themeSurfaceDialogColor(context)
            ),
            cornerRadiusPx = UiCorner.compactSurfaceRadius(context),
            corners = corners,
            strokeColor = themeStroke ?: android.graphics.Color.TRANSPARENT,
            strokeWidthPx = if (themeStroke != null) {
                PANEL_STROKE_WIDTH_DP.dpToPx()
            } else {
                0f
            },
            blurRadiusPx = 0
        )
    }

    fun popup(context: Context): SurfaceStyle = dialog(context)

    fun ui(
        context: Context,
        @ColorInt color: Int,
        cornerRadiusPx: Float = UiCorner.panelRadius(context),
        corners: SurfaceCorners = SurfaceCorners.ALL,
        @ColorInt strokeColor: Int = android.graphics.Color.TRANSPARENT,
        strokeWidthPx: Float = 0f
    ): SurfaceStyle {
        return SurfaceStyle(
            tintColor = UiCorner.surfaceColor(color),
            cornerRadiusPx = cornerRadiusPx,
            corners = corners,
            strokeColor = strokeColor,
            strokeWidthPx = strokeWidthPx
        )
    }
}

private fun Float.dpToPx(): Float {
    return this * android.content.res.Resources.getSystem().displayMetrics.density
}
