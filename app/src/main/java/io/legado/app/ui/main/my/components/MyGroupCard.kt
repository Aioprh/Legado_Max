package io.legado.app.ui.main.my.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.ui.theme.legadoCardColor
import io.legado.app.ui.theme.legadoHairlineColor

private val GroupShape = RoundedCornerShape(20.dp)

/**
 * 「我的」页分组卡片：外部小标题 + 圆角容器 + 内部行。
 */
@Composable
internal fun MyGroupCard(
    modifier: Modifier = Modifier,
    title: String,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            modifier = Modifier.padding(start = 6.dp, bottom = 8.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.4.sp
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(GroupShape)
                .background(legadoCardColor())
                .border(0.5.dp, legadoHairlineColor(), GroupShape),
            content = content
        )
    }
}
