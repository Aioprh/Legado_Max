package io.legado.app.ui.main.my.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.main.my.MyReadingStats

/**
 * 「我的」页顶部信息卡：应用标识 + 一句本地化声明 + 三项阅读统计。
 *
 * 背景使用主题主色到辅助色的渐变，文字颜色由主色亮度自动决定（浅主色用深字）。
 */
@Composable
internal fun MyHeaderCard(
    modifier: Modifier = Modifier,
    stats: MyReadingStats
) {
    val scheme = MaterialTheme.colorScheme
    val gradientStart = scheme.primary
    val gradientEnd = scheme.secondary
    val contentColor = if (gradientStart.luminance() > 0.55f) Color(0xFF12171B) else Color.White

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Brush.linearGradient(listOf(gradientStart, gradientEnd)))
    ) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawCircle(
                color = contentColor.copy(alpha = 0.08f),
                radius = size.minDimension * 0.62f,
                center = Offset(size.width * 1.02f, size.height * 0.05f)
            )
            drawCircle(
                color = contentColor.copy(alpha = 0.06f),
                radius = size.minDimension * 0.40f,
                center = Offset(size.width * 0.05f, size.height * 1.05f)
            )
        }
        Column(modifier = Modifier.padding(horizontal = 18.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(RoundedCornerShape(15.dp))
                        .background(contentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Image(
                        painter = painterResource(R.drawable.image_legado),
                        contentDescription = null,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(9.dp))
                    )
                }
                Spacer(Modifier.width(13.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.my_header_title),
                        color = contentColor,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = stringResource(R.string.my_header_subtitle),
                        color = contentColor.copy(alpha = 0.76f),
                        fontSize = 11.5.sp,
                        maxLines = 2
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            Row(modifier = Modifier.fillMaxWidth()) {
                MyStat(
                    modifier = Modifier.weight(1f),
                    value = stats.bookCount.toString(),
                    label = stringResource(R.string.my_stat_books),
                    contentColor = contentColor
                )
                MyStatDivider(contentColor)
                MyStat(
                    modifier = Modifier.weight(1f),
                    value = formatReadTime(stats.totalReadMillis),
                    label = stringResource(R.string.my_stat_read_time),
                    contentColor = contentColor
                )
                MyStatDivider(contentColor)
                MyStat(
                    modifier = Modifier.weight(1f),
                    value = stats.readDays.toString(),
                    label = stringResource(R.string.my_stat_read_days),
                    contentColor = contentColor
                )
            }
        }
    }
}

@Composable
private fun MyStat(
    modifier: Modifier = Modifier,
    value: String,
    label: String,
    contentColor: Color
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = value,
            color = contentColor,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = label,
            color = contentColor.copy(alpha = 0.7f),
            fontSize = 11.sp,
            maxLines = 1
        )
    }
}

@Composable
private fun MyStatDivider(contentColor: Color) {
    Box(
        modifier = Modifier
            .width(0.5.dp)
            .height(28.dp)
            .background(contentColor.copy(alpha = 0.18f))
    )
}

/**
 * 统计卡里的阅读时长：不足 1 小时显示分钟，其余显示整小时，保证三列等宽不换行。
 */
@Composable
private fun formatReadTime(millis: Long): String {
    val minutes = (millis / 60_000L).toInt().coerceAtLeast(0)
    return if (minutes < 60) {
        stringResource(R.string.duration_minute, minutes)
    } else {
        stringResource(R.string.duration_hour, minutes / 60)
    }
}
