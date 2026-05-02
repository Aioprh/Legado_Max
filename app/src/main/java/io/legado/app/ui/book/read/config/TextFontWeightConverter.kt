package io.legado.app.ui.book.read.config

import android.content.Context
import android.os.Build
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ReadBookConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.ui.widget.text.StrokeTextView
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx

class TextFontWeightConverter(context: Context, attrs: AttributeSet?) :
    StrokeTextView(context, attrs) {

    private val spannableString = SpannableString(context.getString(R.string.font_weight_text))
    private var enabledSpan: ForegroundColorSpan = ForegroundColorSpan(context.accentColor)
    private var onChanged: (() -> Unit)? = null

    init {
        text = spannableString
        if (!isInEditMode) {
            upUi(ReadBookConfig.textBold)
        }
        setOnClickListener {
            showFontWeightDialog()
        }
    }

    fun upUi(type: Int) {
        spannableString.removeSpan(enabledSpan)
        if (AppConfig.textBoldMode == 0) {
            when (type) {
                0 -> spannableString.setSpan(enabledSpan, 0, 1, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                1 -> spannableString.setSpan(enabledSpan, 2, 3, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
                2 -> spannableString.setSpan(enabledSpan, 4, 5, Spannable.SPAN_INCLUSIVE_EXCLUSIVE)
            }
        }
        text = spannableString
    }

    private fun showFontWeightDialog() {
        if (AppConfig.textBoldMode == 0) {
            showCoarseModeDialog()
        } else {
            showFineModeDialog()
        }
    }

    private fun showCoarseModeDialog() {
        val items = context.resources.getStringArray(R.array.text_font_weight).toList()
        context.alert(titleResource = R.string.text_font_weight_converter) {
            neutralButton(R.string.text_bold_fine_mode) {
                switchToFineMode()
            }
            items(items) { _, i ->
                ReadBookConfig.textBold = i
                upUi(i)
                onChanged?.invoke()
            }
        }
    }

    private fun showFineModeDialog() {
        val currentValue = ReadBookConfig.textBold.coerceIn(100, 900)
        var tempValue = currentValue
        
        context.alert(titleResource = R.string.text_font_weight_converter) {
            customView {
                createFineModeView(tempValue) { newValue ->
                    tempValue = newValue
                }
            }
            
            neutralButton(R.string.text_bold_coarse_mode) {
                switchToCoarseMode()
            }
            
            positiveButton(android.R.string.ok) {
                ReadBookConfig.textBold = tempValue
                upUi(tempValue)
                onChanged?.invoke()
            }
            
            negativeButton(android.R.string.cancel) {}
        }
    }

    private fun createFineModeView(currentValue: Int, onValueChanged: (Int) -> Unit): View {
        val bg = context.bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = context.getPrimaryTextColor(isLight)
        
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 8.dpToPx())
        }
        
        val valueTextView = TextView(context).apply {
            text = currentValue.toString()
            textSize = 18f
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }
        
        val fontWeightNames = context.resources.getStringArray(R.array.text_font_weight_fine)
        val fontWeightNameTextView = TextView(context).apply {
            text = getFontWeightName(currentValue, fontWeightNames)
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(textColor)
        }
        
        val seekBar = SeekBar(context).apply {
            max = 800
            progress = currentValue - 100
            setPadding(0, 16.dpToPx(), 0, 16.dpToPx())
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val value = progress + 100
                    valueTextView.text = value.toString()
                    fontWeightNameTextView.text = getFontWeightName(value, fontWeightNames)
                    onValueChanged(value)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        
        val labelsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        
        val thinLabel = TextView(context).apply {
            text = context.getString(R.string.text_bold_thin)
            textSize = 12f
            setTextColor(textColor)
        }
        
        val boldLabel = TextView(context).apply {
            text = context.getString(R.string.text_bold_bold)
            textSize = 12f
            setTextColor(textColor)
        }
        
        labelsContainer.apply {
            addView(thinLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f))
            addView(boldLabel, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                gravity = Gravity.END
            })
        }
        
        container.apply {
            addView(valueTextView)
            addView(fontWeightNameTextView)
            addView(seekBar)
            addView(labelsContainer)
        }
        
        return container
    }

    private fun getFontWeightName(value: Int, names: Array<String>): String {
        return when {
            value <= 150 -> names.getOrElse(0) { "" }
            value <= 250 -> names.getOrElse(1) { "" }
            value <= 350 -> names.getOrElse(2) { "" }
            value <= 450 -> names.getOrElse(3) { "" }
            value <= 550 -> names.getOrElse(4) { "" }
            value <= 650 -> names.getOrElse(5) { "" }
            value <= 750 -> names.getOrElse(6) { "" }
            else -> names.getOrElse(7) { "" }
        }
    }

    private fun switchToFineMode() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
            showNotSupportedDialog()
            return
        }
        
        if (!AppConfig.textBoldFineTipShown) {
            showFirstTimeTipDialog {
                AppConfig.textBoldFineTipShown = true
                doSwitchToFineMode()
            }
        } else {
            doSwitchToFineMode()
        }
    }

    private fun showNotSupportedDialog() {
        context.alert(
            titleResource = R.string.text_bold_not_supported_title,
            messageResource = R.string.text_bold_not_supported_message
        ) {
            okButton()
        }
    }

    private fun showFirstTimeTipDialog(onConfirmed: () -> Unit) {
        context.alert(
            titleResource = R.string.text_bold_fine_tip_title,
            messageResource = R.string.text_bold_fine_tip_message
        ) {
            okButton {
                onConfirmed()
            }
        }
    }

    private fun doSwitchToFineMode() {
        AppConfig.textBoldMode = 1
        ReadBookConfig.textBold = 400
        upUi(400)
        onChanged?.invoke()
        showFineModeDialog()
    }

    private fun switchToCoarseMode() {
        AppConfig.textBoldMode = 0
        ReadBookConfig.textBold = 0
        upUi(0)
        onChanged?.invoke()
        showCoarseModeDialog()
    }

    fun onChanged(unit: () -> Unit) {
        onChanged = unit
    }
}
