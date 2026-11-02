package io.legado.app.ui.widget.text

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import io.legado.app.R
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor

/**
 * 强调色按钮。
 *
 * 默认是描边样式（透明底 + 强调色描边文字），设置 `app:isFilled="true"` 后
 * 变成实心强调色按钮，用于操作栏里的主操作，让主次操作有明确的视觉层级。
 */
class AccentStrokeTextView(context: Context, attrs: AttributeSet) :
    AppCompatTextView(context, attrs) {

    private var radius = 3.dpToPx()
    private val isBottomBackground: Boolean
    private val isFilled: Boolean

    init {
        val typedArray = context.obtainStyledAttributes(attrs, R.styleable.AccentStrokeTextView)
        radius = typedArray.getDimensionPixelOffset(R.styleable.AccentStrokeTextView_radius, radius)
        isBottomBackground =
            typedArray.getBoolean(R.styleable.AccentStrokeTextView_isBottomBackground, false)
        isFilled = typedArray.getBoolean(R.styleable.AccentStrokeTextView_isFilled, false)
        typedArray.recycle()
        upStyle()
    }

    private fun upStyle() {
        val isLight = ColorUtils.isColorLight(context.bottomBackground)
        val disableColor = if (isBottomBackground) {
            if (isLight) {
                context.getCompatColor(R.color.md_light_disabled)
            } else {
                context.getCompatColor(R.color.md_dark_disabled)
            }
        } else {
            context.getCompatColor(R.color.disabled)
        }
        val accentColor = if (isInEditMode) {
            context.getCompatColor(R.color.accent)
        } else {
            ThemeStore.accentColor(context)
        }
        if (isFilled) {
            background = Selector.shapeBuild()
                .setCornerRadius(radius)
                .setDefaultBgColor(accentColor)
                .setPressedBgColor(ColorUtils.withAlpha(accentColor, 0.82f))
                .setDisabledBgColor(disableColor)
                .create()
            val onAccentColor =
                if (ColorUtils.isColorLight(accentColor)) Color.BLACK else Color.WHITE
            setTextColor(
                Selector.colorBuild()
                    .setDefaultColor(onAccentColor)
                    .setDisabledColor(onAccentColor)
                    .create()
            )
            return
        }
        background = Selector.shapeBuild()
            .setCornerRadius(radius)
            .setStrokeWidth(1.dpToPx())
            .setDisabledStrokeColor(disableColor)
            .setDefaultStrokeColor(accentColor)
            .setPressedBgColor(context.getCompatColor(R.color.transparent30))
            .create()
        setTextColor(
            Selector.colorBuild()
                .setDefaultColor(accentColor)
                .setDisabledColor(disableColor)
                .create()
        )
    }

}
