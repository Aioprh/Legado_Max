package io.legado.app.ui.config

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.databinding.DialogNavItemSortBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.ui.main.NavItemSortAdapter
import io.legado.app.utils.postEvent

/**
 * 导航栏拖拽排序对话框。
 *
 * 长按拖拽可调整底部导航栏顺序，点击 RadioButton 可选择默认主页。
 * 排序顺序与默认主页选择相互独立。
 * 确认后保存配置并通知 MainActivity 立即刷新。
 */
class NavItemSortDialogFragment : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val navOrder = AppConfig.navItemOrder
        val defaultHome = AppConfig.defaultHomePage
        val showHomepage = AppConfig.showHomepage
        val showDiscovery = AppConfig.showDiscovery
        val showRss = AppConfig.showRSS

        // 构建排序项列表，按当前排序顺序
        val items = mutableListOf<NavItemSortAdapter.NavItemConfig>()
        for (key in navOrder) {
            val config = buildNavItemConfig(context, key, showHomepage, showDiscovery, showRss)
            if (config != null) items.add(config)
        }

        // 补全可能缺失的项（兼容旧数据）
        for (key in listOf("bookshelf", "homepage", "explore", "rss", "my")) {
            if (items.none { it.key == key }) {
                val config = buildNavItemConfig(context, key, showHomepage, showDiscovery, showRss)
                if (config != null) items.add(config)
            }
        }

        val adapter = NavItemSortAdapter(context, items, defaultHome)

        val binding = DialogNavItemSortBinding.inflate(LayoutInflater.from(context))
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter
        ItemTouchHelper(adapter.itemTouchCallback)
            .attachToRecyclerView(binding.recyclerView)

        return AlertDialog.Builder(context)
            .setTitle(R.string.nav_item_sort_dialog_title)
            .setView(binding.root)
            .setPositiveButton(R.string.ok) { _, _ ->
                AppConfig.setNavItemOrder(adapter.getOrderKeys())
                AppConfig.setDefaultHomePage(adapter.getDefaultHomeKey())
                postEvent(EventBus.NOTIFY_MAIN, true)
            }
            .setNegativeButton(R.string.cancel, null)
            .create()
    }

    companion object {
        private fun buildNavItemConfig(
            context: android.content.Context,
            key: String,
            showHomepage: Boolean,
            showDiscovery: Boolean,
            showRss: Boolean
        ): NavItemSortAdapter.NavItemConfig? {
            return when (key) {
                "homepage" -> NavItemSortAdapter.NavItemConfig(
                    "homepage", context.getString(R.string.homepage),
                    R.drawable.ic_bottom_home, showHomepage
                )
                "bookshelf" -> NavItemSortAdapter.NavItemConfig(
                    "bookshelf", context.getString(R.string.bookshelf),
                    R.drawable.ic_bottom_books, true
                )
                "explore" -> NavItemSortAdapter.NavItemConfig(
                    "explore", context.getString(R.string.discovery),
                    R.drawable.ic_bottom_explore, showDiscovery
                )
                "rss" -> NavItemSortAdapter.NavItemConfig(
                    "rss", context.getString(R.string.rss),
                    R.drawable.ic_bottom_rss_feed, showRss
                )
                "my" -> NavItemSortAdapter.NavItemConfig(
                    "my", context.getString(R.string.my),
                    R.drawable.ic_bottom_person, true
                )
                else -> null
            }
        }
    }
}