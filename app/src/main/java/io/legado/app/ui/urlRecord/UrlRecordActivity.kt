package io.legado.app.ui.urlRecord

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.lifecycle.lifecycleScope
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.databinding.ActivityUrlRecordBinding
import io.legado.app.lib.dialogs.alert
import io.legado.app.ui.widget.recycler.VerticalDivider
import io.legado.app.utils.applyTint
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor

class UrlRecordActivity : VMBaseActivity<ActivityUrlRecordBinding, UrlRecordViewModel>(),
    SearchView.OnQueryTextListener {

    override val binding by viewBinding(ActivityUrlRecordBinding::inflate)
    override val viewModel by viewModels<UrlRecordViewModel>()
    
    private val adapter by lazy { UrlRecordAdapter() }
    private var isLoading = false
    
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        observeUIState()
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

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.queryHint = getString(R.string.search_url_record_hint)
        searchView.setOnQueryTextListener(this)
    }

    private fun observeUIState() {
        lifecycleScope.launch {
            viewModel.uiState.collectLatest { state ->
                when (state) {
                    is UrlRecordUIState.Loading -> {
                        isLoading = true
                        binding.progressBar.visibility = View.VISIBLE
                        binding.recyclerView.visibility = View.GONE
                        binding.tvEmpty.visibility = View.GONE
                        binding.tvError.visibility = View.GONE
                    }
                    is UrlRecordUIState.Success -> {
                        isLoading = false
                        binding.progressBar.visibility = View.GONE
                        binding.recyclerView.visibility = View.VISIBLE
                        binding.tvEmpty.visibility = View.GONE
                        binding.tvError.visibility = View.GONE
                        adapter.setItems(state.records)
                    }
                    is UrlRecordUIState.Empty -> {
                        isLoading = false
                        binding.progressBar.visibility = View.GONE
                        binding.recyclerView.visibility = View.GONE
                        binding.tvEmpty.visibility = View.VISIBLE
                        binding.tvError.visibility = View.GONE
                        adapter.setItems(emptyList())
                    }
                    is UrlRecordUIState.Error -> {
                        isLoading = false
                        binding.progressBar.visibility = View.GONE
                        binding.recyclerView.visibility = View.GONE
                        binding.tvEmpty.visibility = View.GONE
                        binding.tvError.visibility = View.VISIBLE
                        binding.tvError.text = state.message
                    }
                }
            }
        }
    }

    private fun updateRecordSwitch() {
        invalidateOptionsMenu()
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        viewModel.observeRecords(newText)
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    private fun showClearConfirm(days: Int) {
        lifecycleScope.launch {
            val oldCount = viewModel.getOldRecordsCount(days)
            if (oldCount == 0) {
                toastOnUi("没有${days}天前的记录")
                return@launch
            }
            alert(titleResource = R.string.clear_old_records) {
                setMessage("确定清除${days}天前的记录吗？\n共 ${oldCount} 条记录")
                yesButton {
                    lifecycleScope.launch {
                        val deletedCount = viewModel.deleteOldRecords(days)
                        toastOnUi("已清除 ${deletedCount} 条记录")
                    }
                }
                noButton()
            }
        }
    }

    private fun showClearAllConfirm() {
        val totalCount = viewModel.recordCount.value
        if (totalCount == 0) {
            toastOnUi("没有记录可清除")
            return
        }
        alert(titleResource = R.string.clear_all_records) {
            setMessage("确定清除所有记录吗？\n共 ${totalCount} 条记录")
            yesButton {
                lifecycleScope.launch {
                    val deletedCount = viewModel.clearAll()
                    toastOnUi("已清除 ${deletedCount} 条记录")
                }
            }
            noButton()
        }
    }

    override fun finish() {
        if (!searchView.query.isNullOrEmpty()) {
            searchView.setQuery("", true)
        } else {
            super.finish()
        }
    }

    companion object {
        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, UrlRecordActivity::class.java)
            context.startActivity(intent)
        }
    }
}
