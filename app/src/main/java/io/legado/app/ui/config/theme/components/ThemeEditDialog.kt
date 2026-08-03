package io.legado.app.ui.config.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.help.config.ThemeConfig

@Composable
fun ThemeEditDialog(
    draft: ThemeConfig.Config?,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSelectImage: () -> Unit,
    onUpdateDraft: ((ThemeConfig.Config) -> ThemeConfig.Config) -> Unit,
    onColorClick: (colorKey: String, currentColor: String) -> Unit = {},
    onBlurClick: (currentBlur: Int) -> Unit = {}
) {
    val config = draft ?: return

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isNew) stringResource(R.string.add_theme) else stringResource(R.string.edit_theme),
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // 主题名称
                OutlinedTextField(
                    value = config.themeName,
                    onValueChange = { name -> onUpdateDraft { cfg -> cfg.copy(themeName = name) } },
                    label = { Text(stringResource(R.string.theme_name)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )

                // 主色调
                ColorRow(
                    title = stringResource(R.string.primary),
                    hexColor = config.primaryColor,
                    isAccent = false,
                    onClick = { onColorClick("primaryColor", config.primaryColor) }
                )

                // 强调色
                ColorRow(
                    title = stringResource(R.string.accent_color),
                    hexColor = config.accentColor,
                    isAccent = true,
                    onClick = { onColorClick("accentColor", config.accentColor) }
                )

                // 背景色
                ColorRow(
                    title = stringResource(R.string.background_color),
                    hexColor = config.backgroundColor,
                    isAccent = false,
                    onClick = { onColorClick("backgroundColor", config.backgroundColor) }
                )

                // 底栏背景色
                ColorRow(
                    title = stringResource(R.string.bottom_background_color),
                    hexColor = config.bottomBackground,
                    isAccent = false,
                    onClick = { onColorClick("bottomBackground", config.bottomBackground) }
                )

                // 导航栏颜色透明
                SwitchRow(
                    title = stringResource(R.string.imm_navigation_bar_s),
                    checked = config.transparentNavBar,
                    onCheckedChange = { checked -> onUpdateDraft { cfg -> cfg.copy(transparentNavBar = checked) } }
                )

                // 背景图片
                OptionRow(
                    title = stringResource(R.string.background_image),
                    value = config.backgroundImgPath?.substringAfterLast('/')?.substringAfterLast('\\')?.ifBlank { config.backgroundImgPath }
                        ?: stringResource(R.string.select_image),
                    onClick = onSelectImage
                )

                // 背景图片虚化
                OptionRow(
                    title = stringResource(R.string.background_image_blurring),
                    value = "${config.backgroundImgBlur}",
                    onClick = { onBlurClick(config.backgroundImgBlur) }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

/**
 * 颜色属性行：左边标题，中间色号，右边颜色方块
 * isAccent=true 时颜色方块更大（还原旧版行为：强调色方块比其他色大）
 */
@Composable
private fun ColorRow(
    title: String,
    hexColor: String,
    isAccent: Boolean,
    onClick: () -> Unit
) {
    val currentColor = remember(hexColor) {
        runCatching { Color(hexColor.toColorInt()) }.getOrDefault(Color.Gray)
    }
    val displayHex = remember(hexColor) { hexColor.uppercase() }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = displayHex,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(100.dp)
            )

            Spacer(Modifier.width(10.dp))

            // 颜色方块：强调色比其他色大
            Box(
                modifier = Modifier
                    .size(
                        width = if (isAccent) 36.dp else 28.dp,
                        height = if (isAccent) 28.dp else 22.dp
                    )
                    .clip(RoundedCornerShape(5.dp))
                    .background(currentColor)
            )
        }
    }
}

/**
 * 通用属性行：左边标题，右边值文本，点击触发回调
 */
@Composable
private fun OptionRow(
    title: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )

            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis
            )
        }
    }
}

/**
 * 开关行：左边标题，右边Switch
 */
@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp)
            .clip(RoundedCornerShape(8.dp)),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontSize = 15.sp,
                modifier = Modifier.weight(1f)
            )

            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    }
}

}