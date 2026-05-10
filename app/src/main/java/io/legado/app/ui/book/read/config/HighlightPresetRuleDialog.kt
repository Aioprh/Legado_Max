package io.legado.app.ui.book.read.config

import android.content.Context
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
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding

class HighlightPresetRuleDialog(
    private val onAddRule: (HighlightRule) -> Unit,
) : BaseDialogFragment(R.layout.dialog_highlight_preset_rule) {

    private val binding by viewBinding(DialogHighlightPresetRuleBinding::bind)
    private val adapter by lazy { PresetRuleAdapter(requireContext()) }
    private val presetRules by lazy { HighlightRuleStore.defaultPresetRules(requireContext()) }

    override fun onStart() {
        super.onStart()
        setLayout(ViewGroup.LayoutParams.MATCH_PARENT, 0.92f)
        dialog?.window?.setGravity(Gravity.BOTTOM)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        adapter.setItems(presetRules)

        binding.ivBack.setOnClickListener { dismissAllowingStateLoss() }
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
            binding.tvDesc.text = item.displayPattern()
            binding.tvPreview.text = HighlightRulePreview.build(item)
            binding.ivAdd.setOnClickListener {
                onAddRule(item.copy(id = System.currentTimeMillis().toString()))
                dismissAllowingStateLoss()
            }
        }
    }
}
