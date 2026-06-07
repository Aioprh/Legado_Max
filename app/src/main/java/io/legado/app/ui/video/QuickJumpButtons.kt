package io.legado.app.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.model.VideoPlay

/**
 * 快捷跳转按钮组件
 * 显示四个图标按钮：快退a分钟、快退b分钟、快进b分钟、快进a分钟
 */
@Composable
fun QuickJumpButtons(
    enabled: Boolean = VideoPlay.quickJumpButtonsEnabled,
    minutesA: Int = VideoPlay.quickJumpMinutesA,
    minutesB: Int = VideoPlay.quickJumpMinutesB,
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
            // 快退a分钟（大步后退）
            QuickJumpButton(
                icon = Icons.Filled.Replay,
                label = "-${minutesA}分",
                contentDescription = stringResource(R.string.quick_jump_back_a, minutesA),
                onClick = onBackA
            )

            // 快退b分钟（小步后退）
            QuickJumpButton(
                icon = Icons.Filled.FastRewind,
                label = "-${minutesB}分",
                contentDescription = stringResource(R.string.quick_jump_back_b, minutesB),
                onClick = onBackB
            )

            // 快进b分钟（小步前进）
            QuickJumpButton(
                icon = Icons.Filled.FastForward,
                label = "+${minutesB}分",
                contentDescription = stringResource(R.string.quick_jump_forward_b, minutesB),
                onClick = onForwardB
            )

            // 快进a分钟（大步前进）
            QuickJumpButton(
                icon = Icons.Filled.Forward30,
                label = "+${minutesA}分",
                contentDescription = stringResource(R.string.quick_jump_forward_a, minutesA),
                onClick = onForwardA
            )
        }
    }
}

@Composable
private fun QuickJumpButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    contentDescription: String,
    onClick: () -> Unit
) {
    IconButton(
        onClick = onClick,
        modifier = Modifier.padding(2.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = MaterialTheme.colorScheme.primary
            )
            Text(
                text = label,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}