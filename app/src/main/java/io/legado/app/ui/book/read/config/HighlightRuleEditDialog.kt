package io.legado.app.ui.book.read.config

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.widget.doAfterTextChanged
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.databinding.DialogHighlightRuleEditBinding
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding

class HighlightRuleEditDialog(
    private val sourceRule: HighlightRule? = null,
    private val onSave: (HighlightRule) -> Unit,
) : BaseDialogFragment(R.layout.dialog_highlight_rule_edit, true) {

    private val binding by viewBinding(DialogHighlightRuleEditBinding::bind)
    private lateinit var editingRule: HighlightRule
    private lateinit var groupItems: List<String>

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.92f)
        dialog?.window?.setGravity(Gravity.BOTTOM)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        editingRule = sourceRule?.copy() ?: HighlightRule()
        groupItems = HighlightRuleGroupStore.load(requireContext())
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
            listOf("无", "实线下划线", "虚线下划线", "波浪下划线", "标题强调条")
        ).apply {
            setDropDownViewResource(R.layout.item_spinner_dropdown)
        }
        bindData()
        bindEvents()
        updatePreview()
    }

    private fun bindData() {
        binding.switchEnable.isChecked = editingRule.enabled
        binding.etName.setText(editingRule.name)
        binding.etPattern.setText(editingRule.pattern)
        binding.etSampleText.setText(editingRule.sampleText)
        binding.etTextColor.setText(editingRule.textColor?.toHexColor().orEmpty())
        binding.etUnderlineColor.setText(editingRule.underlineColor?.toHexColor().orEmpty())
        binding.spUnderlineMode.setSelection(editingRule.underlineMode.coerceIn(0, 4))
        val groupIndex = groupItems.indexOf(editingRule.group).takeIf { it >= 0 } ?: 0
        binding.spGroup.setSelection(groupIndex)
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
                    updatePreview()
                }

                override fun onNothingSelected(parent: android.widget.AdapterView<*>?) = Unit
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
            )
        )
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
