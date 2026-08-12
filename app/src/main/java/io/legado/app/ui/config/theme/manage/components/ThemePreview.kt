package io.legado.app.ui.config.theme.manage.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/**
 * 主题色块预览卡片。
 *
 * 用主色、强调色、背景色和背景图缩略图组合渲染一个迷你主题预览，
 * 当前应用的主题会显示勾选标记。
 */
@Composable
fun ThemePreview(
    primaryColor: Color,
    accentColor: Color,
    backgroundColor: Color,
    backgroundImgPath: String?,
    isCurrent: Boolean,
    isMultiSelectMode: Boolean = false,
    isNightTheme: Boolean = false
) {
    Box(
        modifier = Modifier
            .size(width = 74.dp, height = 102.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor)
    ) {
        if (!backgroundImgPath.isNullOrBlank()) {
            ThemeBackgroundImage(
                path = backgroundImgPath,
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(8.dp))
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.08f))
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(primaryColor)
            )

            Box(
                modifier = Modifier
                    .padding(top = 36.dp)
                    .size(width = 56.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor)
            )

            Box(
                modifier = Modifier
                    .padding(top = 48.dp)
                    .size(width = 40.dp, height = 8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(accentColor.copy(alpha = 0.5f))
            )

            if (isCurrent && !isMultiSelectMode) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(20.dp),
                    tint = if (isNightTheme) Color.White else Color.Black
                )
            }
        }
    }
}
