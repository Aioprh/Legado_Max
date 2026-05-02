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

/**
 * URL访问记录界面
 * 
 * 展示应用内所有网络请求的记录列表，包括：
 * - 请求URL、域名、方法
 * - 响应状态码、耗时
 * - 请求来源（书源名）
 * - POST请求体内容
 * 
 * 功能菜单：
 * - 开启/关闭URL记录
 * - 清除7天前的记录
 * - 清除30天前的记录
 * - 清除所有记录
 */
class UrlRecordActivity : VMBaseActivity<ActivityUrlRecordBinding, UrlRecordViewModel>() {

    override val binding by viewBinding(ActivityUrlRecordBinding::inflate)
    override val viewModel by viewModels<UrlRecordViewModel>()
    
    // 列表适配器
    private val adapter by lazy { UrlRecordAdapter() }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        observeData()
        updateRecordSwitch()
    }

    // 创建选项菜单
    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.url_record, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    // 准备菜单，更新开关状态
    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        menu.findItem(R.id.menu_record_switch)?.isChecked = viewModel.isRecordUrlEnabled()
        return super.onPrepareOptionsMenu(menu)
    }

    // 处理菜单项点击
    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // 切换URL记录开关
            R.id.menu_record_switch -> {
                val enabled = !item.isChecked
                item.isChecked = enabled
                viewModel.setRecordUrl(enabled)
                toastOnUi(if (enabled) "已开启URL记录" else "已关闭URL记录")
            }
            // 清除7天前的记录
            R.id.menu_clear_old_7 -> showClearConfirm(7)
            // 清除30天前的记录
            R.id.menu_clear_old_30 -> showClearConfirm(30)
            // 清除所有记录
            R.id.menu_clear_all -> showClearAllConfirm()
        }
        return super.onCompatOptionsItemSelected(item)
    }

    // 初始化RecyclerView
    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.addItemDecoration(VerticalDivider(this))
        binding.recyclerView.adapter = adapter
    }

    // 观察数据变化并更新列表
    private fun observeData() {
        lifecycleScope.launch {
            val records = withContext(Dispatchers.IO) {
                viewModel.allRecords
            }
            adapter.setItems(records)
        }
    }

    // 更新菜单开关状态
    private fun updateRecordSwitch() {
        invalidateOptionsMenu()
    }

    // 显示清除旧记录确认对话框
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

    // 显示清除所有记录确认对话框
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
