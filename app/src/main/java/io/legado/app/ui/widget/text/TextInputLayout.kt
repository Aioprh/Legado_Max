package io.legado.app.ui.widget.text

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.ContextCompat
import com.google.android.material.textfield.TextInputLayout
import io.legado.app.R
import io.legado.app.lib.theme.Selector
import io.legado.app.lib.theme.ThemeStore

class TextInputLayout(context: Context, attrs: AttributeSet?) : TextInputLayout(context, attrs) {

    init {
        if (!isInEditMode) {
            defaultHintTextColor =
                Selector.colorBuild().setDefaultColor(ThemeStore.accentColor(context)).create()
            applyRoundedSurface()
        }
    }

    /**
     * 输入框统一成圆角填充块，底色与卡片区分。
     * 显式声明 boxBackgroundMode="none" 的输入框（如全屏代码编辑）保持原样。
     */
    private fun applyRoundedSurface() {
        if (boxBackgroundMode == BOX_BACKGROUND_NONE) return
        val radius = 12f * resources.displayMetrics.density
        setBoxBackgroundColor(ContextCompat.getColor(context, R.color.input_background))
        setBoxCornerRadii(radius, radius, radius, radius)
    }

}
