package io.legado.app.ui.widget.number

import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.Window
import android.view.WindowManager
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.legado.app.R
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.utils.dpToPx
import io.legado.app.utils.windowSize
import splitties.systemservices.windowManager
import kotlin.math.roundToInt

/**
 * 可输入数值的 Compose 对话框。
 *
 * 中间为 [OutlinedTextField] 可直接输入数字，左右各一个 ± 步进按钮，底部显示取值范围。
 * 适用于数值范围较大的场景（如行数、端口号等）。
 *
 * 用法与 [NumberPickerDialog] 一致：
 * ```
 * InputNumberDialog(context, maxValue = Int.MAX_VALUE, minValue = 10)
 *     .setTitle("标题")
 *     .setValue(currentValue)
 *     .show { newValue -> ... }
 * ```
 *
 * @param context 上下文
 * @param maxValue 最大值
 * @param minValue 最小值（默认 0）
 * @param step ± 按钮的步进值（默认 1）
 */
class InputNumberDialog(
    private val context: Context,
    private val maxValue: Int = Int.MAX_VALUE,
    private val minValue: Int = 0,
    private val step: Int = 1
) {
    private var title: String = ""
    private var value: Int = minValue

    init {
        require(maxValue > minValue) { "maxValue ($maxValue) must be greater than minValue ($minValue)" }
        require(step > 0) { "step must be positive" }
    }

    fun setTitle(title: String): InputNumberDialog {
        this.title = title
        return this
    }

    fun setValue(value: Int): InputNumberDialog {
        this.value = value.coerceIn(minValue, maxValue)
        return this
    }

    /**
     * 弹出对话框，确认时回调当前值。
     */
    fun show(callBack: ((value: Int) -> Unit)?) {
        val dialog = ComponentDialog(context).apply {
            requestWindowFeature(Window.FEATURE_NO_TITLE)
            window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
            window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        dialog.setContentView(
            ComposeView(context).apply {
                setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
                setContent {
                    LegadoTheme {
                        InputNumberDialogContent(
                            title = title,
                            minValue = minValue,
                            maxValue = maxValue,
                            initialValue = value,
                            step = step,
                            onCancel = { dialog.dismiss() },
                            onConfirm = {
                                callBack?.invoke(it)
                                dialog.dismiss()
                            }
                        )
                    }
                }
            }
        )
        dialog.setOnShowListener {
            val width = minOf(
                (context.windowManager.windowSize.widthPixels * 0.92f).toInt(),
                420.dpToPx()
            )
            dialog.window?.setLayout(width, WindowManager.LayoutParams.WRAP_CONTENT)
        }
        dialog.show()
    }
}

@Composable
private fun InputNumberDialogContent(
    title: String,
    minValue: Int,
    maxValue: Int,
    initialValue: Int,
    step: Int,
    onCancel: () -> Unit,
    onConfirm: (Int) -> Unit
) {
    var currentValue by remember {
        mutableIntStateOf(initialValue.coerceIn(minValue, maxValue))
    }
    var inputText by remember(currentValue) {
        mutableStateOf(currentValue.toString())
    }

    androidx.compose.material3.AlertDialog(
        onDismissRequest = onCancel,
        title = {
            Text(
                text = title,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            androidx.compose.foundation.layout.Column(
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 减号按钮
                    StepButton(
                        text = "−",
                        enabled = currentValue > minValue,
                        onClick = {
                            currentValue = (currentValue - step).coerceAtLeast(minValue)
                            inputText = currentValue.toString()
                        }
                    )
                    // 输入框
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { input ->
                            inputText = input
                            input.toIntOrNull()?.let { parsed ->
                                currentValue = parsed.coerceIn(minValue, maxValue)
                            }
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = if (minValue < 0) {
                                KeyboardType.Number
                            } else {
                                KeyboardType.Number
                            }
                        ),
                        textStyle = androidx.compose.material3.LocalTextStyle.current.copy(
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    )
                    // 加号按钮
                    StepButton(
                        text = "+",
                        enabled = currentValue < maxValue,
                        onClick = {
                            currentValue = (currentValue + step).coerceAtMost(maxValue)
                            inputText = currentValue.toString()
                        }
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$minValue - $maxValue",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 12.sp,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        },
        confirmButton = {
            TextButton(onClick = {
                onConfirm(currentValue)
            }) {
                Text(stringResource(R.string.ok))
            }
        },
        dismissButton = {
            TextButton(onClick = onCancel) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun StepButton(
    text: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = Modifier.width(48.dp)
    ) {
        Text(
            text = text,
            fontSize = 24.sp,
            fontWeight = FontWeight.SemiBold
        )
    }
}
