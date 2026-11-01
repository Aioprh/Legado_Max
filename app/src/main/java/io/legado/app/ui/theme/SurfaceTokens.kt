package io.legado.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance

/**
 * 卡片容器色。
 *
 * 由主题背景色推导：浅色主题向白色提亮，深色主题也向白色微提亮，
 * 保证使用自定义背景色（含背景图）时，卡片与页面底色之间仍有稳定、可辨识的层次。
 *
 * 注意：不要直接用 `MaterialTheme.colorScheme.surface`，Legado 的 surface 在夜间
 * 是"比背景更暗"的方向，用作卡片会失去层级感。
 */
@Composable
@ReadOnlyComposable
fun legadoCardColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val isLight = background.luminance() > 0.5f
    return lerp(background, Color.White, if (isLight) 0.62f else 0.08f)
}

/**
 * 卡片描边/分割线色，与 [legadoCardColor] 配套使用，细描边代替重阴影。
 */
@Composable
@ReadOnlyComposable
fun legadoHairlineColor(): Color {
    val background = MaterialTheme.colorScheme.background
    val onBackground = MaterialTheme.colorScheme.onBackground
    val isLight = background.luminance() > 0.5f
    return lerp(background, onBackground, if (isLight) 0.10f else 0.16f)
}
