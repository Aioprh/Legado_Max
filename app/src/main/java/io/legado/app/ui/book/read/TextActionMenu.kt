package io.legado.app.ui.book.read

import android.annotation.SuppressLint
import android.app.SearchManager
import android.content.Context
import android.content.Intent
import android.content.pm.ResolveInfo
import android.net.Uri
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.PopupWindow
import androidx.annotation.RequiresApi
import androidx.appcompat.view.SupportMenuInflater
import androidx.appcompat.view.menu.MenuBuilder
import androidx.appcompat.view.menu.MenuItemImpl
import androidx.core.view.isVisible
import io.legado.app.R
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ItemTextBinding
import io.legado.app.databinding.PopupActionMenuBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.getPrefBoolean
import io.legado.app.utils.gone
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.printOnDebug
import io.legado.app.utils.sendToClip
import io.legado.app.utils.share
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.visible

/**
 * 文本操作菜单
 *
 * 长按文本后的液态玻璃阅读菜单，保留原有菜单功能，同时根据选区和可用空间
 * 自动选择菜单显示方向，避免菜单覆盖选中的文字。
 */
@SuppressLint("RestrictedApi")
class TextActionMenu(private val context: Context, private val callBack: CallBack) :
    PopupWindow(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT) {

    private val binding = PopupActionMenuBinding.inflate(LayoutInflater.from(context))

    private val adapter = Adapter(context).apply {
        setHasStableIds(true)
    }

    private var menuItems: List<MenuItemImpl> = emptyList()
    private val visibleMenuItems = arrayListOf<MenuItemImpl>()
    private val moreMenuItems = arrayListOf<MenuItemImpl>()
    private val expandTextMenu get() = context.getPrefBoolean(PreferKey.expandTextMenu)

    private val hiddenMenuItemIds: Set<Int>
        get() = TextMenuConfig.getHiddenMenuItemIds(context)

    private val menuMarginDp = 8
    private val menuGapDp = 6

    private var lastAnchorView: View? = null
    private var lastWindowHeight = 0
    private var lastStartX = 0
    private var lastStartTopY = 0
    private var lastStartBottomY = 0

    init {
        @SuppressLint("InflateParams")
        contentView = binding.root

        isTouchable = true
        isOutsideTouchable = false
        isFocusable = false

        binding.recyclerView.adapter = adapter
        binding.recyclerViewMore.adapter = adapter

        setOnDismissListener {
            if (!context.getPrefBoolean(PreferKey.expandTextMenu)) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
        }

        binding.ivMenuMore.setOnClickListener {
            if (binding.recyclerView.isVisible) {
                binding.ivMenuMore.setImageResource(R.drawable.ic_arrow_back)
                adapter.setItems(moreMenuItems)
                binding.recyclerView.gone()
                binding.recyclerViewMore.visible()
            } else {
                binding.ivMenuMore.setImageResource(R.drawable.ic_more_vert)
                binding.recyclerViewMore.gone()
                adapter.setItems(visibleMenuItems)
                binding.recyclerView.visible()
            }
            if (isShowing) {
                binding.root.post {
                    lastAnchorView?.let { anchor ->
                        repositionMenu(anchor, lastWindowHeight, lastStartX, lastStartTopY, lastStartBottomY)
                    }
                }
            }
        }

        reloadMenuItems()

        if (expandTextMenu) {
            adapter.setItems(menuItems)
            binding.ivMenuMore.gone()
        } else {
            adapter.setItems(visibleMenuItems)
            binding.ivMenuMore.isVisible = moreMenuItems.isNotEmpty()
        }
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density + 0.5f).toInt()
    }

    private fun reloadMenuItems() {
        val myMenu = MenuBuilder(context)
        val otherMenu = MenuBuilder(context)
        SupportMenuInflater(context).inflate(R.menu.content_select_action, myMenu)
        myMenu.visibleItems.forEach { menuItem ->
            TextMenuConfig.getCustomMenuTitle(context, menuItem.itemId)?.let { customTitle ->
                menuItem.title = customTitle
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            onInitializeMenu(otherMenu)
        }

        val allMenuItems = myMenu.visibleItems + otherMenu.visibleItems
        val order = TextMenuConfig.getMenuItemOrder(context)
        val orderedMenuItems = if (order.isNotEmpty()) {
            val itemMap = allMenuItems.associateBy { it.itemId }
            val ordered = order.mapNotNull { id -> itemMap[id] }
            val remaining = allMenuItems.filter { it.itemId !in order }
            ordered + remaining
        } else {
            allMenuItems
        }

        menuItems = orderedMenuItems.filter { it.itemId !in hiddenMenuItemIds }

        visibleMenuItems.clear()
        moreMenuItems.clear()

        val visibleCount = TextMenuConfig.getTextMenuVisibleCount(context)
        if (menuItems.size > visibleCount) {
            visibleMenuItems.addAll(menuItems.subList(0, visibleCount))
            moreMenuItems.addAll(menuItems.subList(visibleCount, menuItems.size))
        } else {
            visibleMenuItems.addAll(menuItems)
        }
    }

    fun upMenu() {
        reloadMenuItems()

        if (expandTextMenu) {
            adapter.setItems(menuItems)
            binding.ivMenuMore.gone()
        } else {
            adapter.setItems(visibleMenuItems)
            binding.ivMenuMore.isVisible = moreMenuItems.isNotEmpty()
        }
    }

    /**
     * 根据真实菜单高度计算显示方向。
     * 优先放在选区上方；如果上方放不下，则比较上下空间并选择空间更大的一侧。
     * X 坐标始终限制在屏幕安全边距内。
     */
    private fun repositionMenu(
        view: View,
        windowHeight: Int,
        startX: Int,
        startTopY: Int,
        startBottomY: Int
    ) {
        val root = binding.root
        val margin = dp(menuMarginDp)
        val gap = dp(menuGapDp)
        val screenWidth = view.rootView.width.takeIf { it > 0 }
            ?: context.resources.displayMetrics.widthPixels
        val safeHeight = windowHeight.takeIf { it > 0 }
            ?: view.rootView.height.takeIf { it > 0 }
            ?: context.resources.displayMetrics.heightPixels

        val maxWidth = (screenWidth - margin * 2).coerceAtLeast(1)
        val maxHeight = (safeHeight - margin * 2).coerceAtLeast(1)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(maxWidth, View.MeasureSpec.AT_MOST),
            View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST)
        )

        val popupWidth = root.measuredWidth.coerceAtLeast(1)
        val popupHeight = root.measuredHeight.coerceAtLeast(1)
        val aboveSpace = (startTopY - margin - gap).coerceAtLeast(0)
        val belowSpace = (safeHeight - startBottomY - margin - gap).coerceAtLeast(0)

        val showAbove = aboveSpace >= popupHeight || aboveSpace >= belowSpace
        val x = startX.coerceIn(
            margin,
            (screenWidth - popupWidth - margin).coerceAtLeast(margin)
        )
        val y = if (showAbove) {
            (startTopY - popupHeight - gap).coerceAtLeast(margin)
        } else {
            (startBottomY + gap).coerceAtMost(
                (safeHeight - popupHeight - margin).coerceAtLeast(margin)
            )
        }

        if (isShowing) {
            update(x, y, -1, -1)
        } else {
            showAtLocation(view, Gravity.TOP or Gravity.START, x, y)
        }

        root.post {
            if (!isShowing) return@post
            val actualWidth = root.width.coerceAtLeast(1)
            val actualHeight = root.height.coerceAtLeast(1)
            val actualX = startX.coerceIn(
                margin,
                (screenWidth - actualWidth - margin).coerceAtLeast(margin)
            )
            val actualAbove = (startTopY - margin - gap).coerceAtLeast(0)
            val actualBelow = (safeHeight - startBottomY - margin - gap).coerceAtLeast(0)
            val actualShowAbove = actualAbove >= actualHeight || actualAbove >= actualBelow
            val actualY = if (actualShowAbove) {
                (startTopY - actualHeight - gap).coerceAtLeast(margin)
            } else {
                (startBottomY + gap).coerceAtMost(
                    (safeHeight - actualHeight - margin).coerceAtLeast(margin)
                )
            }
            if (actualX != x || actualY != y) {
                update(actualX, actualY, -1, -1)
            }
        }
    }

    fun show(
        view: View,
        windowHeight: Int,
        startX: Int,
        startTopY: Int,
        startBottomY: Int,
        endX: Int,
        endBottomY: Int
    ) {
        upMenu()
        lastAnchorView = view
        lastWindowHeight = windowHeight
        lastStartX = startX
        lastStartTopY = startTopY
        lastStartBottomY = startBottomY
        repositionMenu(view, windowHeight, startX, startTopY, startBottomY)
    }

    inner class Adapter(context: Context) :
        RecyclerAdapter<MenuItemImpl, ItemTextBinding>(context) {

        override fun getItemId(position: Int): Long {
            return position.toLong()
        }

        override fun getViewBinding(parent: ViewGroup): ItemTextBinding {
            return ItemTextBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemTextBinding,
            item: MenuItemImpl,
            payloads: MutableList<Any>
        ) {
            with(binding) {
                textView.text = item.title
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemTextBinding) {
            holder.itemView.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    if (!callBack.onMenuItemSelected(it.itemId)) {
                        onMenuItemSelected(it)
                    }
                }
                callBack.onMenuActionFinally()
            }

            holder.itemView.setOnLongClickListener {
                if (AppConfig.contentSelectSpeakMod == 0) {
                    AppConfig.contentSelectSpeakMod = 1
                    context.toastOnUi("切换为从选择的地方开始一直朗读")
                } else {
                    AppConfig.contentSelectSpeakMod = 0
                    context.toastOnUi("切换为朗读选择内容")
                }
                true
            }
        }
    }

    private fun onMenuItemSelected(item: MenuItemImpl) {
        when (item.itemId) {
            R.id.menu_copy -> context.sendToClip(callBack.selectedText)
            R.id.menu_share_str -> context.share(callBack.selectedText)
            R.id.menu_browser -> {
                kotlin.runCatching {
                    val intent = if (callBack.selectedText.isAbsUrl()) {
                        Intent(Intent.ACTION_VIEW).apply {
                            data = Uri.parse(callBack.selectedText)
                        }
                    } else {
                        Intent(Intent.ACTION_WEB_SEARCH).apply {
                            putExtra(SearchManager.QUERY, callBack.selectedText)
                        }
                    }
                    context.startActivity(intent)
                }.onFailure {
                    it.printOnDebug()
                    context.toastOnUi(it.localizedMessage ?: "ERROR")
                }
            }
            else -> item.intent?.let {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    kotlin.runCatching {
                        it.putExtra(Intent.EXTRA_PROCESS_TEXT, callBack.selectedText)
                        context.startActivity(it)
                    }.onFailure { e ->
                        AppLog.put("执行文本菜单操作出错\n$e", e, true)
                    }
                }
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntent(): Intent {
        return Intent()
            .setAction(Intent.ACTION_PROCESS_TEXT)
            .setType("text/plain")
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun getSupportedActivities(): List<ResolveInfo> {
        return context.packageManager
            .queryIntentActivities(createProcessTextIntent(), 0)
    }

    @RequiresApi(Build.VERSION_CODES.M)
    private fun createProcessTextIntentForResolveInfo(info: ResolveInfo): Intent {
        return createProcessTextIntent()
            .putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, false)
            .setClassName(info.activityInfo.packageName, info.activityInfo.name)
    }

    /** 长按文字菜单中的系统 PROCESS_TEXT 项。 */
    @RequiresApi(Build.VERSION_CODES.M)
    private fun onInitializeMenu(menu: Menu) {
        kotlin.runCatching {
            val hiddenItems = TextMenuConfig.getHiddenProcessTextItems(context)
            var menuItemOrder = 100
            for (resolveInfo in getSupportedActivities()) {
                val packageName = resolveInfo.activityInfo.packageName
                val className = resolveInfo.activityInfo.name
                val itemKey = TextMenuConfig.getProcessTextItemKey(packageName, className)
                if (itemKey !in hiddenItems) {
                    val title = TextMenuConfig.getCustomProcessTextTitle(context, itemKey)
                        ?: resolveInfo.loadLabel(context.packageManager)
                    menu.add(
                        Menu.NONE, Menu.NONE,
                        menuItemOrder++, title
                    ).intent = createProcessTextIntentForResolveInfo(resolveInfo)
                }
            }
        }.onFailure {
            context.toastOnUi("获取文字操作菜单出错:${it.localizedMessage}")
        }
    }

    interface CallBack {
        val selectedText: String
        fun onMenuItemSelected(itemId: Int): Boolean
        fun onMenuActionFinally()
    }
}
