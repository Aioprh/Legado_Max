package io.legado.app.ui.urlRecord

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
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
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor

/**
 * URL访问记录界面
 * 
 * 展示应用内所有网络请求记录，支持：
 * - 按域名筛选
 * - 搜索URL/域名/来源
 * - 清除旧记录
 * - 开关URL记录功能
 */
class UrlRecordActivity : VMBaseActivity<ActivityUrlRecordBinding, UrlRecordViewModel>(),
    SearchView.OnQueryTextListener {

    override val binding by viewBinding(ActivityUrlRecordBinding::inflate)
    override val viewModel by viewModels<UrlRecordViewModel>()
    
    private val adapter by lazy { UrlRecordAdapter() }
    private var isLoading = false
    
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    
    private var domainMenu: SubMenu? = null

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        observeUIState()
        observeDomains()
        updateRecordSwitch()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.url_record, menu)
        domainMenu = menu.findItem(R.id.menu_filter).subMenu
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_record_switch)?.isChecked = viewModel.isRecordUrlEnabled()
        updateDomainMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.menu_filter_all -> {
                item.isChecked = true
                viewModel.filterByDomain(null)
            }
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
        if (item.groupId == R.id.menu_domain_group) {
            item.isChecked = true
            viewModel.filterByDomain(item.title.toString())
        }
        return super.onCompatOptionsItemSelected(item)
    }

    /**
     * 初始化RecyclerView
     */
    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
    }

    /**
     * 初始化搜索框
     */
    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.queryHint = getString(R.string.search_url_record_hint)
        searchView.setOnQueryTextListener(this)
    }

    /**
     * 观察UI状态变化
     */
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

    /**
     * 观察域名列表变化，更新筛选菜单
     */
    private fun observeDomains() {
        lifecycleScope.launch {
            viewModel.domains.collectLatest { domainList ->
                updateDomainMenu(domainList)
            }
        }
    }

    /**
     * 更新域名筛选菜单
     * @param domains 域名列表
     */
    private fun updateDomainMenu(domains: List<String> = viewModel.domains.value) {
        domainMenu?.transaction { menu ->
            menu.removeGroup(R.id.menu_domain_group)
            domains.forEach { domain ->
                menu.add(R.id.menu_domain_group, Menu.NONE, Menu.NONE, domain)
            }
            val currentDomain = viewModel.currentDomain
            if (currentDomain.isNullOrEmpty()) {
                menu.findItem(R.id.menu_filter_all)?.isChecked = true
            } else {
                menu.findItem(R.id.menu_filter_all)?.isChecked = false
            }
        }
    }

    private fun updateRecordSwitch() {
        invalidateOptionsMenu()
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        viewModel.setSearchQuery(newText)
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    /**
     * 显示清除旧记录确认对话框
     * @param days 天数
     */
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

    /**
     * 显示清除所有记录确认对话框
     */
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

    /**
     * 返回键处理：有搜索内容时清空搜索，否则退出
     */
    override fun finish() {
        if (!searchView.query.isNullOrEmpty()) {
            searchView.setQuery("", true)
        } else {
            super.finish()
        }
    }

    companion object {
        /**
         * 启动URL记录界面
         * @param context 上下文
         */
        fun start(context: android.content.Context) {
            val intent = android.content.Intent(context, UrlRecordActivity::class.java)
            context.startActivity(intent)
        }
    }
}
