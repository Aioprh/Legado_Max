package io.legado.app.ui.book.read.config

import android.content.Context
import android.graphics.PorterDuff
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogHighlightPresetRuleBinding
import io.legado.app.databinding.ItemHighlightPresetAddBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.getPrimaryTextColor
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class HighlightPresetRuleDialog(
    private val onAddRule: (HighlightRule) -> Unit,
) : BaseDialogFragment(R.layout.dialog_highlight_preset_rule) {

    private val binding by viewBinding(DialogHighlightPresetRuleBinding::bind)
    private val adapter by lazy { PresetRuleAdapter(requireContext()) }
    private val presetRules by lazy { HighlightRuleStore.defaultPresetRules(requireContext()) }
    private var primaryTextColor = 0
    private var secondaryTextColor = 0
    private var accentColor = 0

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.92f)
        dialog?.window?.setGravity(Gravity.BOTTOM)
        dialog?.window?.setBackgroundDrawableResource(R.drawable.shape_highlight_rule_sheet)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        initTheme()
        attachBottomSheetDismiss(
            binding.dragHandle,
            binding.sheetContainer
        ) { dismissAllowingStateLoss() }
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        adapter.setItems(presetRules)

        binding.ivBack.setOnClickListener { dismissAllowingStateLoss() }
    }

    private fun initTheme() {
        val bg = requireContext().bottomBackground
        val isLight = ColorUtils.isColorLight(bg)
        primaryTextColor = requireContext().getPrimaryTextColor(isLight)
        secondaryTextColor = requireContext().getSecondaryTextColor(isLight)
        accentColor = requireContext().accentColor

        binding.sheetContainer.background?.mutate()?.setTint(bg)
        binding.ivBack.setColorFilter(primaryTextColor, PorterDuff.Mode.SRC_IN)
        binding.tvPageTitle.setTextColor(primaryTextColor)
        binding.tvPageSubtitle.setTextColor(secondaryTextColor)
    }

    private inner class PresetRuleAdapter(context: Context) :
        RecyclerAdapter<HighlightRule, ItemHighlightPresetAddBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemHighlightPresetAddBinding {
            return ItemHighlightPresetAddBinding.inflate(inflater, parent, false)
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemHighlightPresetAddBinding) {
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemHighlightPresetAddBinding,
            item: HighlightRule,
            payloads: MutableList<Any>
        ) {
            binding.tvTitle.text = item.name
            binding.tvTitle.setTextColor(primaryTextColor)
            binding.tvDesc.text = item.displayPattern()
            binding.tvDesc.setTextColor(secondaryTextColor)
            binding.tvPreviewLabel.setTextColor(secondaryTextColor)
            binding.tvPreview.text = HighlightRulePreview.build(item)
            binding.tvPreview.setTextColor(primaryTextColor)
            binding.ivAdd.setColorFilter(accentColor, PorterDuff.Mode.SRC_IN)
            binding.ivAdd.setOnClickListener {
                onAddRule(item.copy(id = System.currentTimeMillis().toString()))
                dismissAllowingStateLoss()
            }
        }
    }
}
