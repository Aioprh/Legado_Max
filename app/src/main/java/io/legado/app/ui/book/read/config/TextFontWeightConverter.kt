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

/**
 * 字重切换控件
 * 
 * 支持两种模式：
 * - 粗略模式：三个固定选项（正常/粗体/细体）
 * - 精细模式：SeekBar 进度条，支持 100~900 的字重值
 * 
 * 精细模式需要 Android 9 (API 28) 以上版本支持
 */
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
        var currentMode = AppConfig.textBoldMode
        var currentBoldValue = ReadBookConfig.textBold
        var tempFineValue = if (currentMode == 1) currentBoldValue.coerceIn(100, 900) else 400
        
        // 视图引用，用于后续切换
        var coarseView: LinearLayout? = null
        var fineView: LinearLayout? = null
        var switchButton: TextView? = null
        
        context.alert(titleResource = null) {
            customTitle {
                createTitleBar(
                    context.getString(R.string.text_font_weight_converter),
                    if (currentMode == 0) context.getString(R.string.text_bold_fine_mode) 
                    else context.getString(R.string.text_bold_coarse_mode)
                ) { button ->
                    switchButton = button
                    // 切换模式逻辑
                    if (currentMode == 0) {
                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) {
                            showNotSupportedDialog()
                            return@createTitleBar
                        }
                        if (!AppConfig.textBoldFineTipShown) {
                            showFirstTimeTipDialog {
                                AppConfig.textBoldFineTipShown = true
                                currentMode = 1
                                AppConfig.textBoldMode = 1
                                button.text = context.getString(R.string.text_bold_coarse_mode)
                                coarseView?.visibility = View.GONE
                                fineView?.visibility = View.VISIBLE
                            }
                        } else {
                            currentMode = 1
                            AppConfig.textBoldMode = 1
                            button.text = context.getString(R.string.text_bold_coarse_mode)
                            coarseView?.visibility = View.GONE
                            fineView?.visibility = View.VISIBLE
                        }
                    } else {
                        currentMode = 0
                        AppConfig.textBoldMode = 0
                        button.text = context.getString(R.string.text_bold_fine_mode)
                        coarseView?.visibility = View.VISIBLE
                        fineView?.visibility = View.GONE
                    }
                }
            }
            
            customView {
                // 创建包含两种模式的容器
                LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    
                    // 粗略模式视图
                    coarseView = createCoarseModeView(currentBoldValue) { newValue ->
                        currentBoldValue = newValue
                    }.apply {
                        visibility = if (currentMode == 0) View.VISIBLE else View.GONE
                    }
                    
                    // 精细模式视图
                    fineView = createFineModeView(tempFineValue) { newValue ->
                        tempFineValue = newValue
                    }.apply {
                        visibility = if (currentMode == 1) View.VISIBLE else View.GONE
                    }
                    
                    addView(coarseView)
                    addView(fineView)
                }
            }
            
            positiveButton(android.R.string.ok) {
                if (currentMode == 1) {
                    ReadBookConfig.textBold = tempFineValue
                } else {
                    ReadBookConfig.textBold = currentBoldValue
                }
                upUi(ReadBookConfig.textBold)
                onChanged?.invoke()
            }
            
            negativeButton(android.R.string.cancel) {}
        }
    }

    private fun createTitleBar(
        title: String, 
        buttonText: String, 
        onButtonClick: (TextView) -> Unit
    ): LinearLayout {
        val bg = context.bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = context.getPrimaryTextColor(isLight)
        val accentColor = context.accentColor
        
        return LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(24.dpToPx(), 16.dpToPx(), 16.dpToPx(), 16.dpToPx())
            
            val titleTextView = TextView(context).apply {
                text = title
                textSize = 18f
                setTextColor(textColor)
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }
            
            val button = TextView(context).apply {
                text = buttonText
                textSize = 14f
                setTextColor(accentColor)
                setPadding(8.dpToPx(), 4.dpToPx(), 8.dpToPx(), 4.dpToPx())
                setOnClickListener {
                    onButtonClick(this)
                }
            }
            
            addView(titleTextView)
            addView(button)
        }
    }

    private fun createCoarseModeView(
        currentValue: Int,
        onValueChanged: (Int) -> Unit
    ): LinearLayout {
        val bg = context.bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = context.getPrimaryTextColor(isLight)
        val accentColor = context.accentColor
        
        return LinearLayout(context).apply linearLayout@{
            orientation = LinearLayout.VERTICAL
            setPadding(16.dpToPx(), 8.dpToPx(), 16.dpToPx(), 8.dpToPx())
            
            val items = context.resources.getStringArray(R.array.text_font_weight)
            items.forEachIndexed { index, text ->
                val itemView = TextView(context).apply {
                    this.text = text
                    textSize = 16f
                    setPadding(16.dpToPx(), 12.dpToPx(), 16.dpToPx(), 12.dpToPx())
                    setTextColor(if (index == currentValue) accentColor else textColor)
                    setOnClickListener {
                        onValueChanged(index)
                        updateSelection(this@linearLayout, index, accentColor, textColor)
                    }
                }
                addView(itemView)
            }
        }
    }

    private fun updateSelection(container: LinearLayout, selectedIndex: Int, accentColor: Int, normalColor: Int) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i) as? TextView ?: continue
            child.setTextColor(if (i == selectedIndex) accentColor else normalColor)
        }
    }

    private fun createFineModeView(
        currentValue: Int,
        onValueChanged: (Int) -> Unit
    ): LinearLayout {
        val bg = context.bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        val textColor = context.getPrimaryTextColor(isLight)
        
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24.dpToPx(), 16.dpToPx(), 24.dpToPx(), 8.dpToPx())
            
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
            
            addView(valueTextView)
            addView(fontWeightNameTextView)
            addView(seekBar)
            addView(labelsContainer)
        }
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

    fun onChanged(unit: () -> Unit) {
        onChanged = unit
    }
}
