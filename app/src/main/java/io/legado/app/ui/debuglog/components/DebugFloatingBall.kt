package io.legado.app.ui.debuglog.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Badge
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 调试悬浮球组件
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DebugFloatingBall(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    isVisible: Boolean = true,
    unreadCount: Int = 0
) {
    if (!isVisible) return

    val ballSize = 56.dp
    var offset by remember { mutableStateOf(Offset(x = 0f, y = 0f)) }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current

    val screenWidthPx = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeightPx = with(density) { configuration.screenHeightDp.dp.toPx() }
    val ballSizePx = with(density) { ballSize.toPx() }

    var currentUnread by remember { mutableIntStateOf(unreadCount) }

    LaunchedEffect(unreadCount) {
        currentUnread = unreadCount
    }

    val startColor = lerp(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.secondary, 0.28f)
    val endColor = lerp(MaterialTheme.colorScheme.secondary, MaterialTheme.colorScheme.tertiary, 0.22f)
    val ringColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.28f)
    val glowColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.18f)

    Box(
        modifier = modifier
            .offset { IntOffset(offset.x.roundToInt(), offset.y.roundToInt()) }
            .size(ballSize)
            .shadow(elevation = 12.dp, shape = CircleShape, ambientColor = startColor, spotColor = endColor)
            .clip(CircleShape)
            .background(Brush.linearGradient(colors = listOf(startColor, endColor)))
            .border(1.5.dp, ringColor, CircleShape)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { },
                    onDragEnd = {
                        val centerX = offset.x + ballSizePx / 2
                        if (centerX > screenWidthPx / 2) {
                            offset = Offset(screenWidthPx - ballSizePx, offset.y)
                        } else {
                            offset = Offset(0f, offset.y)
                        }
                    },
                    onDragCancel = { },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        offset += dragAmount
                        offset = Offset(
                            x = offset.x.coerceIn(0f, screenWidthPx - ballSizePx),
                            y = offset.y.coerceIn(0f, screenHeightPx - ballSizePx)
                        )
                    }
                )
            }
            .clickable(
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            modifier = Modifier.size(42.dp),
            shape = CircleShape,
            color = glowColor
        ) {}

        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = "调试日志",
            tint = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.size(28.dp)
        )

        if (currentUnread > 0) {
            Badge(
                containerColor = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-4).dp, y = 4.dp)
            ) {
                Text(
                    text = currentUnread.coerceAtMost(99).toString(),
                    color = MaterialTheme.colorScheme.onError,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

/**
 * 带过滤功能的智能悬浮球
 */
@Composable
fun SmartDebugFloatingBall(
    currentRoute: String?,
    blackListRoutes: Set<String> = setOf("/splash", "/webview", "/reader"),
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shouldShow = currentRoute !in blackListRoutes

    if (shouldShow) {
        DebugFloatingBall(
            onClick = onClick,
            modifier = modifier
        )
    }
}
