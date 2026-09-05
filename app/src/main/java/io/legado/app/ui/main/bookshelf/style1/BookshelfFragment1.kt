@file:Suppress("DEPRECATION")

package io.legado.app.ui.main.bookshelf.style1

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListPopupWindow
import android.widget.TextView
import androidx.appcompat.widget.SearchView
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import io.legado.app.R
import io.legado.app.constant.EventBus
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookGroup
import io.legado.app.databinding.FragmentBookshelf1Binding
import io.legado.app.help.book.BookTagHelper
import io.legado.app.help.book.BookTagManagement
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.group.GroupEditDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.BookshelfTagManageActivity
import io.legado.app.ui.main.bookshelf.style1.books.BooksFragment
import io.legado.app.ui.widget.GlassTabBarView
import io.legado.app.ui.widget.RoundedTagBarView
import io.legado.app.utils.isCreated
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlin.collections.set
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 书架界面
 * 支持两种分组切换模式：
 * 1. 液态玻璃 Tab 模式：显示所有分组，可滑动点击切换
 * 2. 下拉选择模式：点击标题栏弹出下拉选择分组菜单
 */
class BookshelfFragment1() : BaseBookshelfFragment(R.layout.fragment_bookshelf1),
    SearchView.OnQueryTextListener {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    private val binding by viewBinding(FragmentBookshelf1Binding::bind)
    private val adapter by lazy { TabFragmentPageAdapter(childFragmentManager) }
    private var titleSelect: LinearLayout? = null
    private var tvGroupName: TextView? = null
    private var ivArrow: ImageView? = null
    private var glassTabBar: GlassTabBarView? = null
    private val bookGroups = mutableListOf<BookGroup>()
    private val fragmentMap = hashMapOf<Long, BooksFragment>()
    private var currentPosition = 0
    private var tagBar: RoundedTagBarView? = null
    private val tagItems = mutableListOf<RoundedTagBarView.Item>()
    private val selectedTagByGroup = hashMapOf<Long, String>()
    private var tagsJob: Job? = null
    override val groupId: Long get() = selectedGroup?.groupId ?: 0

    override val books: List<Book>
        get() = fragmentMap[groupId]?.getBooks() ?: emptyList()

    override var onlyUpdateRead = false

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        childFragmentManager.registerFragmentLifecycleCallbacks(
            object : FragmentManager.FragmentLifecycleCallbacks() {
                override fun onFragmentDestroyed(fm: FragmentManager, fragment: Fragment) {
                    fragmentMap.entries.removeIf { it.value === fragment }
                }
            }, true
        )
        setSupportToolbar(binding.titleBar.toolbar)
        initView()
        initBookGroupData()
    }

    private val selectedGroup: BookGroup?
        get() = bookGroups.getOrNull(currentPosition)

    private fun initView() {
        binding.viewPagerBookshelf.setEdgeEffectColor(primaryColor)
        binding.viewPagerBookshelf.offscreenPageLimit = 2
        binding.viewPagerBookshelf.adapter = adapter
        binding.viewPagerBookshelf.addOnPageChangeListener(object : ViewPager.OnPageChangeListener {
            override fun onPageSelected(position: Int) {
                currentPosition = position
                AppConfig.saveTabPosition = position
                tvGroupName?.text = bookGroups.getOrNull(position)?.groupName ?: ""
                glassTabBar?.getTabAt(position)?.select()
                upTagBar()
            }

            override fun onPageScrolled(position: Int, positionOffset: Float, positionOffsetPixels: Int) = Unit

            override fun onPageScrollStateChanged(state: Int) = Unit
        })

        binding.refreshLayoutBookshelf.setColorSchemeColors(accentColor)
        binding.refreshLayoutBookshelf.setOnRefreshListener {
            // SwipeRefreshLayout 触发时已自动显示刷新圈。
            // 这里不要立即置 false，否则刷新圈瞬间消失，看起来像"没实现"。
            lifecycleScope.launch {
                fragmentMap.values.forEach { it.performRefresh() }
                // 让刷新圈至少展示一小段时间，再收起，保证下拉刷新有明确视觉反馈
                delay(600)
                binding.refreshLayoutBookshelf.isRefreshing = false
            }
        }

        if (AppConfig.dropdownSelectGroup) {
            val groupSelectorView = LayoutInflater.from(requireContext())
                .inflate(R.layout.view_group_selector, binding.titleBar.toolbar, false)
            binding.titleBar.toolbar.addView(groupSelectorView)
            titleSelect = groupSelectorView.findViewById(R.id.title_select)
            tvGroupName = groupSelectorView.findViewById(R.id.tv_group_name)
            ivArrow = groupSelectorView.findViewById(R.id.iv_arrow)
            initTitleSelect()
            updateTitleColor()
        } else {
            val glass = GlassTabBarView(requireContext()).apply {
                setOnTabClickListener { position ->
                    if (position in bookGroups.indices) {
                        currentPosition = position
                        AppConfig.saveTabPosition = position
                        binding.viewPagerBookshelf.setCurrentItem(position, true)
                    }
                }
                setOnTabLongClickListener { position ->
                    if (position !in bookGroups.indices) return@setOnTabLongClickListener false
                    showDialogFragment(GroupEditDialog(bookGroups[position]))
                    true
                }
            }
            glassTabBar = glass
            binding.titleBar.toolbar.addView(glass, LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            ).apply {
                marginStart = 6
                marginEnd = 6
            })
        }
        initTagBar()
    }

    private fun initTagBar() {
        tagBar = binding.tagBar
        tagBar?.setOnTagClickListener { position ->
            val item = tagItems.getOrNull(position) ?: return@setOnTagClickListener
            val currentGroupId = groupId
            val tag = item.text.toString()
            if (tag.equals(getString(R.string.all), ignoreCase = true)) {
                selectedTagByGroup.remove(currentGroupId)
                tagBar?.setSelectedIndex(0)
                fragmentMap[currentGroupId]?.filterBooksByTag(null)
            } else {
                selectedTagByGroup[currentGroupId] = tag
                tagBar?.setSelectedIndex(position)
                fragmentMap[currentGroupId]?.filterBooksByTag(tag)
            }
        }
        tagBar?.setOnTagLongClickListener {
            startActivity<BookshelfTagManageActivity> {
                putExtra("groupId", groupId)
            }
            true
        }
    }

    private fun upTagBar() {
        val tagBar = tagBar ?: return
        val currentGroupId = groupId
        tagsJob?.cancel()
        if (!AppConfig.showBookshelfTagBar) {
            tagBar.visibility = View.GONE
            return
        }
        tagsJob = lifecycleScope.launch {
            val configured = AppConfig.bookshelfGroupTags[currentGroupId].orEmpty()
            val hidden = AppConfig.bookshelfHiddenTags[currentGroupId].orEmpty()
            val existing = withContext(Dispatchers.Default) {
                appDb.bookDao.flowShelfByGroup(currentGroupId).first()
                    .asSequence()
                    .flatMap { BookTagHelper.parse(it.customTag).asSequence() }
                    .toList()
            }
            val visible = BookTagManagement.mergeTags(configured, existing)
                .filterNot { tag -> hidden.any { it.equals(tag, ignoreCase = true) } }
            if (visible.isEmpty()) {
                tagBar.visibility = View.GONE
                return@launch
            }
            val items = buildList {
                add(RoundedTagBarView.Item(getString(R.string.all)))
                visible.forEach { add(RoundedTagBarView.Item(it)) }
            }
            tagItems.clear()
            tagItems.addAll(items)
            val savedTag = selectedTagByGroup[currentGroupId]
            val savedIndex = if (savedTag == null) {
                0
            } else {
                val idx = tagItems.indexOfFirst { it.text.toString().equals(savedTag, ignoreCase = true) }
                if (idx < 0) {
                    selectedTagByGroup.remove(currentGroupId)
                    fragmentMap[currentGroupId]?.filterBooksByTag(null)
                    0
                } else idx
            }
            tagBar.visibility = View.VISIBLE
            tagBar.submitItems(tagItems.toList(), savedIndex)
        }
    }

    private fun initTitleSelect() {
        titleSelect?.setOnClickListener {
            if (bookGroups.isEmpty()) return@setOnClickListener
            val groupNames = bookGroups.map { it.groupName }
            val popup = ListPopupWindow(requireContext())
            popup.anchorView = titleSelect
            popup.setAdapter(GroupSelectorAdapter(requireContext(), groupNames, currentPosition))
            val maxWidth = measureMaxTextWidth(groupNames)
            popup.width = maxWidth + 72
            popup.setOnItemClickListener { _, _, position, _ ->
                currentPosition = position
                AppConfig.saveTabPosition = position
                tvGroupName?.text = bookGroups[position].groupName
                binding.viewPagerBookshelf.setCurrentItem(position, false)
                popup.dismiss()
            }
            popup.show()
        }
    }

    private fun measureMaxTextWidth(items: List<String>): Int {
        val paint = tvGroupName?.paint ?: return 0
        var maxWidth = 0
        for (item in items) maxWidth = maxOf(maxWidth, paint.measureText(item).toInt())
        return maxWidth
    }

    private class GroupSelectorAdapter(
        context: android.content.Context,
        items: List<String>,
        private val selectedPosition: Int
    ) : ArrayAdapter<String>(context, android.R.layout.simple_spinner_dropdown_item, items) {
        override fun getView(position: Int, convertView: android.view.View?, parent: android.view.ViewGroup): android.view.View {
            val view = super.getView(position, convertView, parent)
            if (view is TextView) {
                view.setCompoundDrawablesWithIntrinsicBounds(
                    if (position == selectedPosition) R.drawable.ic_check else 0, 0, 0, 0
                )
            }
            return view
        }
    }

    private fun updateTitleColor() {
        val textColor = primaryTextColor
        tvGroupName?.setTextColor(textColor)
        ivArrow?.setColorFilter(textColor)
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        SearchActivity.start(requireContext(), query)
        return false
    }

    override fun onQueryTextChange(newText: String?): Boolean = false

    @Synchronized
    override fun upGroup(data: List<BookGroup>) {
        if (data.isEmpty()) {
            tagBar?.visibility = View.GONE
            appDb.bookGroupDao.enableGroup(BookGroup.IdAll)
            return
        }
        if (data == bookGroups) return
        bookGroups.clear()
        bookGroups.addAll(data)
        val lastPosition = AppConfig.saveTabPosition.coerceIn(0, bookGroups.size - 1)
        currentPosition = lastPosition
        adapter.notifyDataSetChanged()
        binding.viewPagerBookshelf.setCurrentItem(lastPosition, false)
        if (AppConfig.dropdownSelectGroup) {
            AppConfig.saveTabPosition = lastPosition
            updateTitleSelect()
        } else {
            glassTabBar?.submitTabs(bookGroups.map { it.groupName }, lastPosition)
        }
        upTagBar()
    }

    override fun upSort() {
        adapter.notifyDataSetChanged()
    }

    override fun observeLiveBus() {
        super.observeLiveBus()
        observeEvent<String>(EventBus.BOOKSHELF_REFRESH) { upTagBar() }
    }

    private fun updateTitleSelect() {
        if (bookGroups.isNotEmpty()) {
            val position = currentPosition.coerceIn(0, bookGroups.size - 1)
            tvGroupName?.text = bookGroups[position].groupName
        }
    }

    override fun gotoTop() {
        fragmentMap[groupId]?.gotoTop()
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        if (view == null) return
        fragmentMap.values.forEach {
            if (it.view != null) it.updateMainBottomPadding(bottomPadding)
        }
    }

    private fun onGroupTabReselected(position: Int) {
        selectedGroup?.let { group ->
            fragmentMap[group.groupId]?.let { toastOnUi("${group.groupName}(${it.getBooksCount()})") }
        }
    }

    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        override fun getItemPosition(any: Any): Int {
            val fragment = any as BooksFragment
            val position = fragment.position
            val group = bookGroups.getOrNull(position)
            if (fragment.groupId != group?.groupId) return POSITION_NONE
            val bookSort = group.getRealBookSort()
            fragment.setEnableRefresh(group.enableRefresh)
            if (fragment.bookSort != bookSort) fragment.upBookSort(bookSort)
            return POSITION_UNCHANGED
        }

        override fun getItem(position: Int): Fragment {
            val group = bookGroups[position]
            onlyUpdateRead = group.onlyUpdateRead
            return BooksFragment(position, group)
        }

        override fun getCount(): Int = bookGroups.size

        override fun getPageTitle(position: Int): CharSequence = bookGroups[position].groupName

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as BooksFragment
            val group = bookGroups[position]
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as BooksFragment
            }
            fragmentMap[group.groupId] = fragment
            return fragment
        }
    }
}
