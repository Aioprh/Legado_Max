package io.legado.app.ui.book.read.config

import android.graphics.Color
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogHighlightRuleEditBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class HighlightRuleEditDialog(
    private val sourceRule: HighlightRule? = null,
    private val onSave: (HighlightRule) -> Unit = {},
) : BaseDialogFragment(R.layout.dialog_highlight_rule_edit, true) {

    private val binding by viewBinding(DialogHighlightRuleEditBinding::bind)
    private lateinit var editingRule: HighlightRule
    private lateinit var groupItems: List<String>
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.92f)
        dialog?.window?.setGravity(Gravity.BOTTOM)
        dialog?.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        dialog?.window?.setBackgroundDrawableResource(R.drawable.shape_highlight_rule_sheet)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initTheme()
        editingRule = sourceRule?.copy() ?: HighlightRule()
        groupItems = HighlightRuleGroupStore.load(requireContext())
        attachBottomSheetDismiss(
            binding.dragHandle,
            binding.sheetContainer
        ) { dismissAllowingStateLoss() }

        binding.tvPageTitle.text =
            getString(if (sourceRule == null) R.string.highlight_rule_add else R.string.highlight_rule_edit)
        binding.tvPageSubtitle.text =
            if (sourceRule == null) "新增一条正文高亮规则" else "调整这条规则的匹配和显示方式"

        binding.spGroup.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_text_common,
            groupItems
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        binding.spUnderlineMode.adapter = ArrayAdapter(
            requireContext(),
            R.layout.item_text_common,
            listOf("无", "实线下划线", "虚线下划线", "波浪下划线", "标题强调条", "自定义SVG")
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }

        bindData()
        bindEvents()
        updatePreview()
    }

    override fun observeLiveBus() {
        observeEvent<ArrayList<Int>>(EventBus.UP_CONFIG) {
            if (it.contains(1) || it.contains(2)) {
                initTheme()
                updatePreview()
            }
        }
    }

    private fun initTheme() {
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        primaryTextColor = requireContext().getPrimaryTextColor(isLight)
        secondaryTextColor = requireContext().getSecondaryTextColor(isLight)
        accentColor = requireContext().accentColor

        val surfaceBg = if (isLight) {
            ColorUtils.blendColors(bg, 0xFF000000.toInt(), 0.08f)
        } else {
            ColorUtils.blendColors(bg, 0xFFFFFFFF.toInt(), 0.06f)
        }

        binding.sheetContainer.background?.mutate()?.setTint(bg)
        binding.ivBack.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.ivBack.background?.mutate()?.setTint(accentColor)
        binding.tvPageTitle.setTextColor(primaryTextColor)
        binding.tvPageSubtitle.setTextColor(secondaryTextColor)

        binding.tvSaveAction.background?.mutate()?.setTint(accentColor)
        binding.tvSaveAction.setTextColor(
            if (ColorUtils.isColorLight(accentColor)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        )

        binding.switchEnable.trackTintList = android.content.res.ColorStateList.valueOf(accentColor)
        binding.switchEnable.thumbTintList = android.content.res.ColorStateList.valueOf(accentColor)

        binding.tvSectionBasic.setTextColor(secondaryTextColor)
        binding.tvSectionStyle.setTextColor(secondaryTextColor)
        binding.tvSectionPreview.setTextColor(secondaryTextColor)

        binding.tvEnableLabel.setTextColor(primaryTextColor)
        binding.tvEnableDesc.setTextColor(secondaryTextColor)
        binding.tvNameLabel.setTextColor(primaryTextColor)
        binding.tvGroupLabel.setTextColor(primaryTextColor)
        binding.tvPatternLabel.setTextColor(primaryTextColor)
        binding.tvTextColorLabel.setTextColor(primaryTextColor)
        binding.tvUnderlineModeLabel.setTextColor(primaryTextColor)
        binding.tvUnderlineColorLabel.setTextColor(primaryTextColor)
        binding.tvSvgPathLabel.setTextColor(primaryTextColor)
        binding.tvSvgPathHint.setTextColor(secondaryTextColor)
        binding.tvSampleHint.setTextColor(secondaryTextColor)
        binding.tvPreview.setTextColor(primaryTextColor)

        binding.etName.setTextColor(primaryTextColor)
        binding.etName.setHintTextColor(secondaryTextColor)
        binding.etPattern.setTextColor(primaryTextColor)
        binding.etPattern.setHintTextColor(secondaryTextColor)
        binding.etTextColor.setTextColor(primaryTextColor)
        binding.etTextColor.setHintTextColor(secondaryTextColor)
        binding.etUnderlineColor.setTextColor(primaryTextColor)
        binding.etUnderlineColor.setHintTextColor(secondaryTextColor)
        binding.etSvgPath.setTextColor(primaryTextColor)
        binding.etSvgPath.setHintTextColor(secondaryTextColor)
        binding.etSampleText.setTextColor(primaryTextColor)
        binding.etSampleText.setHintTextColor(secondaryTextColor)

        binding.panelBasic.background?.mutate()?.setTint(surfaceBg)
        binding.panelStyle.background?.mutate()?.setTint(surfaceBg)
        binding.panelPreview.background?.mutate()?.setTint(surfaceBg)

        binding.etName.background?.mutate()?.setTint(bg)
        binding.etPattern.background?.mutate()?.setTint(bg)
        binding.spGroup.background?.mutate()?.setTint(bg)
        binding.etTextColor.background?.mutate()?.setTint(bg)
        binding.spUnderlineMode.background?.mutate()?.setTint(bg)
        binding.etUnderlineColor.background?.mutate()?.setTint(bg)
        binding.etSvgPath.background?.mutate()?.setTint(bg)
        binding.etSampleText.background?.mutate()?.setTint(bg)
        binding.tvPreview.background?.mutate()?.setTint(bg)
    }

    private fun bindData() {
        binding.switchEnable.isChecked = editingRule.enabled
        binding.etName.setText(editingRule.name)
        binding.etPattern.setText(editingRule.pattern)
        binding.etSampleText.setText(editingRule.sampleText)
        binding.etTextColor.setText(editingRule.textColor?.toHexColor().orEmpty())
        binding.etUnderlineColor.setText(editingRule.underlineColor?.toHexColor().orEmpty())
        binding.etSvgPath.setText(editingRule.underlineSvgPath.orEmpty())
        binding.spUnderlineMode.setSelection(editingRule.underlineMode.coerceIn(0, 5))
        val groupIndex = groupItems.indexOf(editingRule.group).takeIf { it >= 0 } ?: 0
        binding.spGroup.setSelection(groupIndex)
        updateSvgPathVisibility(editingRule.underlineMode)
    }

    private fun bindEvents() {
        binding.sheetContainer.setOnClickListener { }
        binding.ivBack.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.tvSaveAction.setOnClickListener {
            saveRule()
        }
        binding.switchEnable.setOnCheckedChangeListener { _, isChecked ->
            editingRule.enabled = isChecked
        }
        binding.etName.doAfterTextChanged {
            editingRule.name = it?.toString().orEmpty()
        }
        binding.etPattern.doAfterTextChanged {
            editingRule.pattern = it?.toString().orEmpty()
            updatePreview()
        }
        binding.etSampleText.doAfterTextChanged {
            editingRule.sampleText = it?.toString().orEmpty()
            updatePreview()
        }
        binding.etTextColor.doAfterTextChanged {
            editingRule.textColor = parseColorOrNull(it?.toString().orEmpty(), binding.tvTextColorError)
            updatePreview()
        }
        binding.etUnderlineColor.doAfterTextChanged {
            editingRule.underlineColor = parseColorOrNull(it?.toString().orEmpty(), binding.tvUnderlineColorError)
            updatePreview()
        }
        binding.spUnderlineMode.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    editingRule.underlineMode = position
                    updateSvgPathVisibility(position)
                    updatePreview()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
        binding.etSvgPath.doAfterTextChanged {
            editingRule.underlineSvgPath = it?.toString().orEmpty()
            updatePreview()
        }
        binding.spGroup.onItemSelectedListener =
            object : android.widget.AdapterView.OnItemSelectedListener {
                override fun onItemSelected(
                    parent: android.widget.AdapterView<*>?,
                    view: View?,
                    position: Int,
                    id: Long
                ) {
                    editingRule.group = groupItems.getOrElse(position) { HighlightRuleGroupStore.DEFAULT_GROUP }
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
            }
    }

    private fun saveRule() {
        val name = binding.etName.text?.toString()?.trim().orEmpty()
        val pattern = binding.etPattern.text?.toString()?.trim().orEmpty()
        if (name.isBlank()) {
            toastOnUi(R.string.highlight_rule_name_required)
            return
        }
        if (pattern.isBlank()) {
            toastOnUi(R.string.highlight_rule_pattern_required)
            return
        }
        val regexError = validatePattern(pattern)
        if (regexError != null) {
            binding.tvPatternError.visibility = View.VISIBLE
            binding.tvPatternError.text = regexError
            return
        }
        editingRule = editingRule.copy(
            id = editingRule.id.ifBlank { System.currentTimeMillis().toString() },
            name = name,
            pattern = pattern,
            sampleText = binding.etSampleText.text?.toString().orEmpty(),
            group = groupItems.getOrElse(binding.spGroup.selectedItemPosition) {
                HighlightRuleGroupStore.DEFAULT_GROUP
            },
            enabled = binding.switchEnable.isChecked,
            textColor = parseColorOrNull(binding.etTextColor.text?.toString().orEmpty(), binding.tvTextColorError),
            underlineMode = binding.spUnderlineMode.selectedItemPosition,
            underlineColor = parseColorOrNull(binding.etUnderlineColor.text?.toString().orEmpty(), binding.tvUnderlineColorError),
            underlineSvgPath = binding.etSvgPath.text?.toString().orEmpty(),
        )
        onSave(editingRule)
        dismissAllowingStateLoss()
    }

    private fun updatePreview() {
        val pattern = binding.etPattern.text?.toString().orEmpty()
        binding.tvPatternError.visibility = View.GONE
        val patternError = validatePattern(pattern)
        if (patternError != null && pattern.isNotBlank()) {
            binding.tvPatternError.visibility = View.VISIBLE
            binding.tvPatternError.text = patternError
        }
        binding.tvPreview.text = HighlightRulePreview.build(
            editingRule.copy(
                name = binding.etName.text?.toString().orEmpty(),
                pattern = pattern,
                sampleText = binding.etSampleText.text?.toString().orEmpty(),
                group = groupItems.getOrElse(binding.spGroup.selectedItemPosition) {
                    HighlightRuleGroupStore.DEFAULT_GROUP
                },
                textColor = parseColorOrNull(binding.etTextColor.text?.toString().orEmpty(), binding.tvTextColorError),
                underlineMode = binding.spUnderlineMode.selectedItemPosition,
                underlineColor = parseColorOrNull(binding.etUnderlineColor.text?.toString().orEmpty(), binding.tvUnderlineColorError),
                underlineSvgPath = binding.etSvgPath.text?.toString().orEmpty(),
            )
        )
    }

    private fun updateSvgPathVisibility(mode: Int) {
        val isVisible = mode == 5
        binding.tvSvgPathLabel.visibility = if (isVisible) View.VISIBLE else View.GONE
        binding.etSvgPath.visibility = if (isVisible) View.VISIBLE else View.GONE
    }

    private fun validatePattern(pattern: String): String? {
        if (pattern.isBlank()) return null
        return kotlin.runCatching { Regex(pattern) }.exceptionOrNull()?.localizedMessage
    }

    private fun parseColorOrNull(value: String, errorView: android.widget.TextView): Int? {
        val text = value.trim()
        if (text.isEmpty()) {
            errorView.visibility = View.GONE
            return null
        }
        return kotlin.runCatching {
            val normalized = if (text.startsWith("#")) text else "#$text"
            Color.parseColor(normalized)
        }.onFailure {
            errorView.visibility = View.VISIBLE
            errorView.text = getString(R.string.highlight_rule_color_invalid)
        }.getOrNull().also {
            if (it != null) errorView.visibility = View.GONE
        }
    }

    private fun Int.toHexColor(): String = String.format("#%08X", this)
}
