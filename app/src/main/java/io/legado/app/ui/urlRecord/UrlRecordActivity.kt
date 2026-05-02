package io.legado.app.ui.urlRecord

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityUrlRecordBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import io.legado.app.lib.theme.primaryColor

class UrlRecordActivity : VMBaseActivity<ActivityUrlRecordBinding, UrlRecordViewModel>() {

    override val binding by viewBinding(ActivityUrlRecordBinding::inflate)
    override val viewModel by viewModels<UrlRecordViewModel>()
    
    private val adapter by lazy { UrlRecordAdapter() }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        observeData()
        updateRecordSwitch()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.url_record, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_record_switch)?.isChecked = viewModel.isRecordUrlEnabled()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_record_switch -> {
                val enabled = !item.isChecked
                item.isChecked = enabled
                viewModel.setRecordUrl(enabled)
                toastOnUi(if (enabled) "已开启URL记录" else "已关闭URL记录")
            }
            R.id.menu_clear_old_7 -> showClearConfirm(7)
            R.id.menu_clear_old_30 -> showClearConfirm(30)
            R.id.menu_clear_all -> showClearAllConfirm()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
    }

    private fun observeData() {
        lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) {
                viewModel.allRecords
            }
            adapter.setItems(records)
        }
    }

    private fun updateRecordSwitch() {
        invalidateOptionsMenu()
    }

    private fun showClearConfirm(days: Int) {
        alert(titleResource = R.string.clear_old_records) {
            setMessage("确定清除${days}天前的记录吗？")
            yesButton {
                viewModel.deleteOldRecords(days)
                toastOnUi("已清除旧记录")
                observeData()
            }
            noButton()
        }
    }

    private fun showClearAllConfirm() {
        alert(titleResource = R.string.clear_all_records) {
            setMessage(R.string.sure_del)
            yesButton {
                viewModel.clearAll()
                toastOnUi("已清除所有记录")
                observeData()
            }
            noButton()
        }
    }

    companion object {
        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, UrlRecordActivity::class.java)
            context.startActivity(intent)
        }
    }
}
