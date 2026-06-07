package io.legado.app.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R

/**
 * 快捷跳转按钮组件
 * 显示四个图标按钮：快退a分钟、快退b分钟、快进b分钟、快进a分钟
 */
@Composable
fun QuickJumpButtons(
    enabled: Boolean,
    minutesA: Int,
    minutesB: Int,
    onBackA: () -> Unit,
    onBackB: () -> Unit,
    onForwardB: () -> Unit,
    onForwardA: () -> Unit
) {
    if (!enabled) return

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 快退a分钟（大步后退）- 使用两个左箭头叠加
            QuickJumpButton(
                icon = { DoubleArrowLeft() },
                label = "-${minutesA}分",
                contentDescription = stringResource(R.string.quick_jump_back_a, minutesA),
                onClick = onBackA
            )

            // 快退b分钟（小步后退）
            QuickJumpButton(
                icon = { Icon(Icons.Default.KeyboardArrowLeft, contentDescription = null) },
                label = "-${minutesB}分",
                contentDescription = stringResource(R.string.quick_jump_back_b, minutesB),
                onClick = onBackB
            )

            // 快进b分钟（小步前进）
            QuickJumpButton(
                icon = { Icon(Icons.Default.KeyboardArrowRight, contentDescription = null) },
                label = "+${minutesB}分",
                contentDescription = stringResource(R.string.quick_jump_forward_b, minutesB),
                onClick = onForwardB
            )

            // 快进a分钟（大步前进）- 使用两个右箭头叠加
            QuickJumpButton(
                icon = { DoubleArrowRight() },
                label = "+${minutesA}分",
                contentDescription = stringResource(R.string.quick_jump_forward_a, minutesA),
                onClick = onForwardA
            )
        }
    }
}

@Composable
private fun QuickJumpButton(
    icon: @Composable () -> Unit,
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(horizontal = 4.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            icon()
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                fontSize = 8.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

/**
 * 双左箭头图标 - 用于大步后退
 */
@Composable
private fun DoubleArrowLeft() {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowLeft,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.offset(x = (-4).dp)
        )
    }
}

/**
 * 双右箭头图标 - 用于大步前进
 */
@Composable
private fun DoubleArrowRight() {
    Box(contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.offset(x = 4.dp)
        )
    }
}