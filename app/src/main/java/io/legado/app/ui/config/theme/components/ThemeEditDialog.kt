package io.legado.app.ui.config.theme.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.toColorInt
import io.legado.app.R
import io.legado.app.help.config.ThemeConfig

private val presetColors = listOf(
    "#F44336", "#E91E63", "#9C27B0", "#673AB7",
    "#3F51B5", "#2196F3", "#03A9F4", "#00BCD4",
    "#009688", "#4CAF50", "#8BC34A", "#CDDC39",
    "#FFEB3B", "#FFC107", "#FF9800", "#FF5722",
    "#795548", "#607D8B", "#9E9E9E", "#424242",
    "#FFFFFF", "#000000"
)

@Composable
fun ThemeEditDialog(
    draft: ThemeConfig.Config?,
    isNew: Boolean,
    onDismiss: () -> Unit,
    onSave: () -> Unit,
    onSelectImage: () -> Unit,
    onUpdateDraft: ((ThemeConfig.Config) -> ThemeConfig.Config) -> Unit
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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 主题名称
                OutlinedTextField(
                    value = config.themeName,
                    onValueChange = { onUpdateDraft { it.copy(themeName = it) } },
                    label = { Text(stringResource(R.string.theme_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // 主色调
                ColorPickerSection(
                    label = stringResource(R.string.top_bar_primary_color),
                    hexColor = config.primaryColor,
                    onColorChanged = { onUpdateDraft { it.copy(primaryColor = it) } }
                )

                // 强调色
                ColorPickerSection(
                    label = stringResource(R.string.accent_color),
                    hexColor = config.accentColor,
                    onColorChanged = { onUpdateDraft { it.copy(accentColor = it) } }
                )

                // 背景色
                ColorPickerSection(
                    label = stringResource(R.string.background_color),
                    hexColor = config.backgroundColor,
                    onColorChanged = { onUpdateDraft { it.copy(backgroundColor = it) } }
                )

                // 底栏背景色
                ColorPickerSection(
                    label = stringResource(R.string.bottom_background_color),
                    hexColor = config.bottomBackground,
                    onColorChanged = { onUpdateDraft { it.copy(bottomBackground = it) } }
                )

                // 背景图
                BackgroundImageSection(
                    path = config.backgroundImgPath,
                    onSelectImage = onSelectImage,
                    onClear = { onUpdateDraft { it.copy(backgroundImgPath = null) } }
                )

                // 模糊度
                BlurSlider(
                    blur = config.backgroundImgBlur,
                    onBlurChanged = { onUpdateDraft { it.copy(backgroundImgBlur = it) } }
                )

                // 透明导航栏
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.transparent),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(
                        checked = config.transparentNavBar,
                        onCheckedChange = { onUpdateDraft { it.copy(transparentNavBar = it) } }
                    )
                }
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

@Composable
private fun ColorPickerSection(
    label: String,
    hexColor: String,
    onColorChanged: (String) -> Unit
) {
    val currentColor = remember(hexColor) {
        runCatching { Color(hexColor.toColorInt()) }.getOrDefault(Color.Gray)
    }
    var hexText by remember(hexColor) { mutableStateOf(hexColor) }

    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(8.dp))

        // 预设颜色
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presetColors) { hex ->
                val color = runCatching { Color(hex.toColorInt()) }.getOrDefault(Color.Gray)
                val isSelected = hex.equals(hexColor, ignoreCase = true)
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (isSelected) Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                            else Modifier.border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                        .clickable {
                            hexText = hex
                            onColorChanged(hex)
                        }
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        // 十六进制输入
        OutlinedTextField(
            value = hexText,
            onValueChange = { text ->
                hexText = text
                val cleaned = text.trim()
                if (cleaned.matches(Regex("^#?[0-9A-Fa-f]{6,8}$"))) {
                    val normalized = if (cleaned.startsWith("#")) cleaned else "#$cleaned"
                    runCatching { normalized.toColorInt() }.onSuccess { onColorChanged(normalized) }
                }
            },
            label = { Text("Hex") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text)
        )
    }
}

@Composable
private fun BackgroundImageSection(
    path: String?,
    onSelectImage: () -> Unit,
    onClear: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.background_image),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = path ?: stringResource(R.string.empty),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )

        Spacer(Modifier.height(4.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onSelectImage) {
                Text(stringResource(R.string.select_image))
            }
            if (path != null) {
                Button(
                    onClick = onClear,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text(stringResource(R.string.clear))
                }
            }
        }
    }
}

@Composable
private fun BlurSlider(
    blur: Int,
    onBlurChanged: (Int) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.background_image_blurring_radius),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = blur.toString(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = blur.toFloat(),
            onValueChange = { onBlurChanged(it.toInt()) },
            valueRange = 0f..25f,
            steps = 24,
            modifier = Modifier.fillMaxWidth()
        )

        Text(
            text = stringResource(R.string.background_image_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 11.sp
        )
    }
}