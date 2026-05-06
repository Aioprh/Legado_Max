package io.legado.app.ui.widget.dialog

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.databinding.DialogRecyclerViewBinding
import io.legado.app.databinding.ItemBackupCategoryBinding
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.storage.BackupInfoHelper
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 备份信息查看对话框
 * 显示当前会备份的数据统计
 */
class BackupInfoDialog : BaseDialogFragment(R.layout.dialog_recycler_view) {

    private val binding by viewBinding(DialogRecyclerViewBinding::bind)
    private val adapter by lazy { BackupInfoAdapter(requireContext()) }

    /**
     * 对话框启动时设置布局大小
     * 宽度占屏幕90%，高度占屏幕85%
     */
    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.85f)
    }

    /**
     * Fragment创建完成后的初始化
     * 设置Toolbar背景色、标题颜色，初始化RecyclerView，加载备份信息
     */
    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) = binding.run {
        toolBar.setBackgroundColor(primaryColor)
        toolBar.setTitleTextColor(primaryTextColor)
        toolBar.title = getString(R.string.view_backup_info)
        
        val lastBackup = LocalConfig.lastBackup
        if (lastBackup > 0) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            toolBar.subtitle = "上次备份: ${dateFormat.format(Date(lastBackup))}"
        }

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        loadBackupInfo()
    }

    /**
     * 加载备份信息数据
     * 从BackupInfoHelper获取备份概览，转换为列表项数据
     * 包括：头部统计、分类信息、文件详情
     */
    private fun loadBackupInfo() {
        val overview = BackupInfoHelper.getBackupOverview()

        if (overview.items.isEmpty()) {
            binding.tvMsg.visibility = View.VISIBLE
            binding.tvMsg.text = getString(R.string.no_backup_data)
            return
        }

        val items = mutableListOf<BackupInfoItem>()

        // 添加头部统计信息
        items.add(BackupInfoItem.Header(
            itemCount = overview.items.size,
            totalSize = BackupInfoHelper.formatSize(overview.totalSize)
        ))

        // 按分类添加数据
        val categories = BackupInfoHelper.categorizeItems(overview.items)
        categories.forEach { cat ->
            items.add(BackupInfoItem.Category(
                name = cat.name,
                icon = cat.icon,
                count = cat.items.size,
                totalSize = BackupInfoHelper.formatSize(cat.totalSize)
            ))
            cat.items.forEach { item ->
                items.add(BackupInfoItem.File(
                    fileName = item.fileName,
                    displayName = item.displayName,
                    size = BackupInfoHelper.formatSize(item.size)
                ))
            }
        }

        adapter.setItems(items)
    }

    companion object {
        /**
         * 创建备份信息对话框实例
         */
        fun newInstance(): BackupInfoDialog {
            return BackupInfoDialog()
        }
    }

    /**
     * 备份信息列表项密封类
     * 包含三种类型：Header(头部统计)、Category(分类)、File(文件详情)
     */
    sealed class BackupInfoItem {
        data class Header(
            val itemCount: Int,
            val totalSize: String
        ) : BackupInfoItem()

        data class Category(
            val name: String,
            val icon: String,
            val count: Int,
            val totalSize: String
        ) : BackupInfoItem()

        data class File(
            val fileName: String,
            val displayName: String,
            val size: String
        ) : BackupInfoItem()
    }

    /**
     * 备份信息列表适配器
     * 处理三种不同类型的数据项：Header、Category、File
     */
    class BackupInfoAdapter(context: Context) :
        RecyclerAdapter<BackupInfoItem, ItemBackupCategoryBinding>(context) {

        /**
         * 获取列表项类型
         * Header: 0, Category: 1, File: 2
         */
        override fun getItemViewType(item: BackupInfoItem, position: Int): Int {
            return when (item) {
                is BackupInfoItem.Header -> 0
                is BackupInfoItem.Category -> 1
                is BackupInfoItem.File -> 2
            }
        }

        override fun getViewBinding(parent: ViewGroup): ItemBackupCategoryBinding {
            return ItemBackupCategoryBinding.inflate(inflater, parent, false)
        }

        /**
         * 绑定数据到视图
         * 根据不同类型调用对应的绑定方法
         */
        override fun convert(
            holder: ItemViewHolder,
            binding: ItemBackupCategoryBinding,
            item: BackupInfoItem,
            payloads: MutableList<Any>
        ) {
            when (item) {
                is BackupInfoItem.Header -> bindHeader(binding, item)
                is BackupInfoItem.Category -> bindCategory(binding, item)
                is BackupInfoItem.File -> bindFile(binding, item)
            }
        }

        /**
         * 绑定头部统计信息
         * 显示备份数据统计、预估大小、总项数
         */
        private fun bindHeader(binding: ItemBackupCategoryBinding, item: BackupInfoItem.Header) {
            binding.root.visibility = View.VISIBLE
            binding.apply {
                tvIcon.text = "📦"
                tvTitle.text = "备份数据统计"
                tvSubtitle.text = "预估大小: ${item.totalSize}"
                tvCount.text = "${item.itemCount} 项"
                tvSize.visibility = View.GONE
            }
        }

        /**
         * 绑定分类信息
         * 显示分类图标、名称、包含项数、总大小
         */
        private fun bindCategory(binding: ItemBackupCategoryBinding, item: BackupInfoItem.Category) {
            binding.root.visibility = View.VISIBLE
            binding.apply {
                tvIcon.text = item.icon
                tvTitle.text = item.name
                tvSubtitle.visibility = View.GONE
                tvCount.text = "${item.count} 项"
                tvSize.text = item.totalSize
                tvSize.visibility = View.VISIBLE
            }
        }

        /**
         * 绑定文件详情
         * 显示文件图标、显示名称、文件名、文件大小
         */
        private fun bindFile(binding: ItemBackupCategoryBinding, item: BackupInfoItem.File) {
            binding.root.visibility = View.VISIBLE
            binding.apply {
                tvIcon.text = "  📄"
                tvTitle.text = item.displayName
                tvSubtitle.text = item.fileName
                tvSubtitle.visibility = View.VISIBLE
                tvCount.visibility = View.GONE
                tvSize.text = item.size
                tvSize.visibility = View.VISIBLE
            }
        }

        /**
         * 注册点击事件监听器
         * 当前为空实现，文件项无点击交互
         */
        override fun registerListener(holder: ItemViewHolder, binding: ItemBackupCategoryBinding) {
        }
    }
}
