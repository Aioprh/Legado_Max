package io.legado.app.ui.main.my.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R

/**
 * 主题模式选择弹窗（跟随系统 / 日间 / 夜间 / 墨水屏）。
 *
 * 选项文案直接复用 `R.array.theme_mode`，与设置页的取值顺序保持一致。
 */
@Composable
internal fun MyThemeModeDialog(
    modifier: Modifier = Modifier,
    selectedMode: Int,
    onDismiss: () -> Unit,
    onSelect: (Int) -> Unit
) {
    val modes = LocalContext.current.resources.getStringArray(R.array.theme_mode)
    AlertDialog(
        modifier = modifier,
        onDismissRequest = onDismiss,
        title = { Text(text = stringResource(R.string.theme_mode)) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, label ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(index) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = index == selectedMode,
                            onClick = { onSelect(index) }
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = label,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(text = stringResource(android.R.string.cancel))
            }
        }
    )
}
