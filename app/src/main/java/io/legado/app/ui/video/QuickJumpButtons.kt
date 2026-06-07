package io.legado.app.ui.video

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.legado.app.R
import io.legado.app.model.VideoPlay

/**
 * 快捷跳转按钮组件
 * 显示四个按钮：快退a分钟、快退b分钟、快进b分钟、快进a分钟
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

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // 快退a分钟
        Button(
            onClick = onBackA,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text(
                text = stringResource(R.string.quick_jump_back_a, minutesA),
                maxLines = 1
            )
        }

        // 快退b分钟
        Button(
            onClick = onBackB,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text(
                text = stringResource(R.string.quick_jump_back_b, minutesB),
                maxLines = 1
            )
        }

        // 快进b分钟
        Button(
            onClick = onForwardB,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text(
                text = stringResource(R.string.quick_jump_forward_b, minutesB),
                maxLines = 1
            )
        }

        // 快进a分钟
        Button(
            onClick = onForwardA,
            modifier = Modifier.weight(1f),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        ) {
            Text(
                text = stringResource(R.string.quick_jump_forward_a, minutesA),
                maxLines = 1
            )
        }
    }
}