@file:Suppress("DEPRECATION")

package io.legado.app.ui.main

import android.os.Bundle
import android.text.format.DateUtils
import android.graphics.Outline
import android.view.Gravity
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.activity.viewModels
import androidx.core.view.get
import androidx.core.view.postDelayed
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import androidx.lifecycle.lifecycleScope
import androidx.viewpager.widget.ViewPager
import com.google.android.material.bottomnavigation.BottomNavigationView
import io.legado.app.BuildConfig
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppConst.appInfo
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.ActivityMainBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.AppWebDav
import io.legado.app.help.book.BookHelp
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.NavigationBarConfig
import io.legado.app.help.config.LocalConfig
import io.legado.app.help.coroutine.Coroutine
import io.legado.app.help.storage.Backup
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.elevation
import io.legado.app.lib.theme.getSecondaryTextColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.service.BaseReadAloudService
import io.legado.app.ui.about.CrashLogsDialog
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.association.ImportReplaceRuleDialog
import io.legado.app.ui.association.ImportRssSourceDialog
import io.legado.app.ui.main.bookshelf.BaseBookshelfFragment
import io.legado.app.ui.main.bookshelf.style1.BookshelfFragment1
import io.legado.app.ui.main.bookshelf.style2.BookshelfFragment2
import io.legado.app.ui.main.explore.ExploreFragment
import io.legado.app.ui.main.homepage.HomepageFragment
import io.legado.app.ui.main.my.MyFragment
import io.legado.app.ui.main.rss.RssFragment
import io.legado.app.ui.widget.dialog.TextDialog
import io.legado.app.ui.widget.text.BadgeView
import io.legado.app.utils.isCreated
import io.legado.app.utils.navigationBarHeight
import io.legado.app.utils.observeEvent
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.setOnApplyWindowInsetsListenerCompat
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import io.legado.app.utils.invisible
import io.legado.app.utils.visible
import io.legado.app.utils.startActivity
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.dpToPx
import io.legado.app.utils.getCompatColor
import io.legado.app.utils.getPrefInt
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Dispatchers.Main
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import splitties.views.bottomPadding
import kotlin.coroutines.resume
import androidx.core.view.get
import androidx.core.graphics.drawable.toDrawable
import io.legado.app.help.update.AppUpdate
import io.legado.app.ui.about.UpdateDialog
import kotlin.time.Duration.Companion.hours

/**
 * 主界面
 */
@Suppress("PrivatePropertyName")
class MainActivity : VMBaseActivity<ActivityMainBinding, MainViewModel>(),
    BottomNavigationView.OnNavigationItemSelectedListener,
    BottomNavigationView.OnNavigationItemReselectedListener,
    MainViewModel.CallBack {

    override val binding by viewBinding(ActivityMainBinding::inflate)
    override val viewModel by viewModels<MainViewModel>()
    override fun showReadAloudMiniBar(): Boolean = true

    private val idBookshelf = 0
    private val idBookshelf1 = 11
    private val idBookshelf2 = 12
    private val idExplore = 1
    private val idRss = 2
    private val idMy = 3
    private val idHomepage = 4
    private var exitTime: Long = 0
    private var bookshelfReselected: Long = 0
    private var exploreReselected: Long = 0
    private var pagePosition = 0
    private val fragmentMap = hashMapOf<Int, Fragment>()
    private var bottomMenuCount = 5
    private val EXIT_INTERVAL = 2000L
    private val realPositions = arrayOf(idHomepage, idBookshelf, idExplore, idRss, idMy)
    private val adapter by lazy {
        TabFragmentPageAdapter(supportFragmentManager)
    }
    private var onUpBooksBadgeView: BadgeView? = null
    private var bottomNavigationConfigSignature: String? = null
    private var bottomNavigationInset = 0

    private fun bookshelfPosition(): Int = realPositions.indexOf(idBookshelf)

    private fun fragmentIdToMenuItemId(fragmentId: Int): Int = when (fragmentId) {
        idBookshelf, idBookshelf1, idBookshelf2 -> R.id.menu_bookshelf
        idHomepage -> R.id.menu_homepage
        idExplore -> R.id.menu_discovery
        idRss -> R.id.menu_rss
        idMy -> R.id.menu_my_config
        else -> R.id.menu_bookshelf
    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        upBottomMenu()
        initView()
        upHomePage()
        // setCurrentItem 到 position 0 时不会触发 onPageSelected 回调，
        // 需要显式更新底部导航栏的选中状态
        val position = binding.viewPagerMain.currentItem
        val menuItemId = fragmentIdToMenuItemId(realPositions[position])
        binding.bottomNavigationView.menu.findItem(menuItemId)?.isChecked = true
        onBackPressedDispatcher.addCallback(this) {
            val bsPos = bookshelfPosition()
            if (pagePosition != bsPos) {
                binding.viewPagerMain.currentItem = bsPos
                return@addCallback
            }
            (fragmentMap[getFragmentId(bsPos)] as? BookshelfFragment2)?.let {
                if (it.back()) {
                    return@addCallback
                }
            }
            if (System.currentTimeMillis() - exitTime > EXIT_INTERVAL) {
                toastOnUi(R.string.double_click_exit)
                exitTime = System.currentTimeMillis()
            } else {
                if (BaseReadAloudService.pause) {
                    finish()
                } else {
                    moveTaskToBack(true)
                }
            }
        }
    }

    override fun onPostCreate(savedInstanceState: Bundle?) {
        super.onPostCreate(savedInstanceState)
        lifecycleScope.launch {
            //隐私协议
            if (!privacyPolicy()) return@launch
            //版本更新
            upVersion()
            //设置本地密码
            setLocalPassword()
            notifyAppCrash()
            //备份同步
            backupSync()
            //设置回调
            viewModel.setActivityCallback(this@MainActivity)
            //自动更新书源
            binding.viewPagerMain.postDelayed(1000) {
                viewModel.ruleSubsUp()
            }
            //自动更新书籍
            val isAutoRefreshedBook = savedInstanceState?.getBoolean("isAutoRefreshedBook") ?: false
            if (AppConfig.autoRefreshBook && !isAutoRefreshedBook) {
                //每次进入书架后5秒自动更新书籍目录
                binding.viewPagerMain.postDelayed(5000) {
                    viewModel.upAllBookToc()
                }
            }
            binding.viewPagerMain.postDelayed(3000) {
                viewModel.postLoad()
            }
        }
    }

    override fun onNavigationItemSelected(item: MenuItem): Boolean = binding.run {
        when (item.itemId) {
            R.id.menu_bookshelf ->
                viewPagerMain.setCurrentItem(bookshelfPosition(), false)

            R.id.menu_homepage ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idHomepage), false)

            R.id.menu_discovery ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idExplore), false)

            R.id.menu_rss ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idRss), false)

            R.id.menu_my_config ->
                viewPagerMain.setCurrentItem(realPositions.indexOf(idMy), false)
        }
        return false
    }

    override fun onNavigationItemReselected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_bookshelf -> {
                if (System.currentTimeMillis() - bookshelfReselected > 300) {
                    bookshelfReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[getFragmentId(bookshelfPosition())] as? BaseBookshelfFragment)?.gotoTop()
                }
            }

            R.id.menu_discovery -> {
                if (System.currentTimeMillis() - exploreReselected > 300) {
                    exploreReselected = System.currentTimeMillis()
                } else {
                    (fragmentMap[idExplore] as? ExploreFragment)?.compressExplore()
                }
            }
        }
    }

    private fun initView() = binding.run {
        viewPagerMain.setEdgeEffectColor(primaryColor)
        viewPagerMain.offscreenPageLimit = 3
        viewPagerMain.adapter = adapter
        viewPagerMain.addOnPageChangeListener(PageChangeCallback())
        bottomNavigationView.setOnNavigationItemSelectedListener(this@MainActivity)
        bottomNavigationView.setOnNavigationItemReselectedListener(this@MainActivity)
        refreshBottomNavigationConfig(force = true)
        if (AppConfig.isEInkMode) {
            bottomNavigationView.setBackgroundResource(R.drawable.bg_eink_border_top)
        }
        bottomNavigationGlass.setOnApplyWindowInsetsListenerCompat { view, windowInsets ->
            bottomNavigationInset = windowInsets.navigationBarHeight
            view.bottomPadding = 0
            refreshBottomNavigationConfig(force = true)
            windowInsets
        }
    }

    /**
     * 用户隐私与协议
     */
    private suspend fun privacyPolicy(): Boolean = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.privacyPolicyOk) {
            block.resume(true)
            return@sc
        }
        val privacyPolicy = String(assets.open("privacyPolicy.md").readBytes())
        alert(getString(R.string.privacy_policy), privacyPolicy) {
            positiveButton(R.string.agree) {
                LocalConfig.privacyPolicyOk = true
                block.resume(true)
            }
            negativeButton(R.string.refuse) {
                finish()
                block.resume(false)
            }
        }
    }

    /**
     * 版本更新日志
     */
    private suspend fun upVersion() = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.versionCode == appInfo.versionCode) {
            if (AppConfig.autoUpdateVariant) {
                if (LocalConfig.lastCheckUpdate + 24.hours.inWholeMilliseconds < System.currentTimeMillis()) {
                    AppUpdate.giteeUpdate.check(lifecycleScope)
                        .onSuccess {
                            showDialogFragment(
                                UpdateDialog(it)
                            )
                        }
                    LocalConfig.lastCheckUpdate = System.currentTimeMillis()
                }
            }
            block.resume(null)
            return@sc
        }
        LocalConfig.versionCode = appInfo.versionCode
        if (LocalConfig.isFirstOpenApp) {
            val help = String(assets.open("web/help/md/appHelp.md").readBytes())
            val dialog = TextDialog(getString(R.string.help), help, TextDialog.Mode.MD)
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else if (!BuildConfig.DEBUG) {
            val log = String(assets.open("web/help/md/updateLog.md").readBytes())
            val dialog = TextDialog(getString(R.string.update_log), log, TextDialog.Mode.MD, "updateLog")
            dialog.setOnDismissListener {
                block.resume(null)
            }
            showDialogFragment(dialog)
        } else {
            block.resume(null)
        }
    }

    /**
     * 设置本地密码
     */
    private suspend fun setLocalPassword() = suspendCancellableCoroutine sc@{ block ->
        if (LocalConfig.password != null) {
            block.resume(null)
            return@sc
        }
        alert(R.string.set_local_password, R.string.set_local_password_summary) {
            val editTextBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "password"
            }
            customView {
                editTextBinding.root
            }
            onDismiss {
                block.resume(null)
            }
            okButton {
                LocalConfig.password = editTextBinding.editView.text.toString()
            }
            cancelButton {
                LocalConfig.password = ""
            }
        }
    }

    private fun notifyAppCrash() {
        if (!LocalConfig.appCrash || BuildConfig.DEBUG) {
            return
        }
        LocalConfig.appCrash = false
        alert(getString(R.string.draw), "检测到阅读发生了崩溃，是否打开崩溃日志以便报告问题？") {
            yesButton {
                showDialogFragment<CrashLogsDialog>()
            }
            noButton()
        }
    }

    /**
     * 备份同步
     */
    private fun backupSync() {
        if (!AppConfig.autoCheckNewBackup) {
            return
        }
        lifecycleScope.launch {
            val lastBackupFile =
                withContext(IO) { AppWebDav.lastBackUp().getOrNull() } ?: return@launch
            if (lastBackupFile.lastModify - LocalConfig.lastBackup > DateUtils.MINUTE_IN_MILLIS) {
                LocalConfig.lastBackup = lastBackupFile.lastModify
                alert(R.string.restore, R.string.webdav_after_local_restore_confirm) {
                    cancelButton()
                    okButton {
                        viewModel.restoreWebDav(lastBackupFile.displayName)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (AppConfig.autoRefreshBook) {
            outState.putBoolean("isAutoRefreshedBook", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Coroutine.async {
            BookHelp.clearInvalidCache()
        }
        if (!BuildConfig.DEBUG) {
            Backup.autoBack(this)
        }
    }

    override fun onResume() {
        super.onResume()
        // 用户从设置页返回时，RECREATE 事件可能未送达后台的 Activity，
        // 或 recreate() 可能被 upSort() 异常阻断。
        // 在 onResume 中直接刷新背景图片，确保主题背景变更立即生效。
        upBackgroundImage()
        refreshBottomNavigationConfig(force = true)
    }

    /**
     * 如果重启太快fragment不会重建,这里更新一下书架的排序
     */
    override fun recreate() {
        try {
            (fragmentMap[getFragmentId(bookshelfPosition())] as? BaseBookshelfFragment)?.run {
                upSort()
            }
        } catch (e: Exception) {
            // 忽略 upSort 异常，确保 super.recreate() 始终被调用
        }
        super.recreate()
    }

    override fun observeLiveBus() {
        viewModel.onUpBooksLiveData.observe(this) {
            if (onUpBooksBadgeView == null) {
                val menu = binding.bottomNavigationView.menu
                var bookshelfItemIndex = 0
                for (i in 0 until menu.size()) {
                    if (menu[i].itemId == R.id.menu_bookshelf) {
                        bookshelfItemIndex = i
                        break
                    }
                }
                onUpBooksBadgeView = binding.bottomNavigationView.addBadgeView(bookshelfItemIndex)
            }
            onUpBooksBadgeView!!.setBadgeCount(it)
        }
        observeEvent<String>(EventBus.RECREATE) {
            // 先直接刷新背景（即使 recreate 失败或被跳过也能生效）
            upBackgroundImage()
            recreate()
        }
        observeEvent<Boolean>(EventBus.NOTIFY_MAIN) {
            binding.apply {
                if (it) {
                    onUpBooksBadgeView = null
                }
                upBottomMenu()
                if (it) {
                    upHomePage()
                }
            }
        }
        observeEvent<String>(PreferKey.threadCount) {
            viewModel.upPool()
        }
        // 底栏配置变更时，重新应用底栏配置
        observeEvent<Boolean>(EventBus.NAVIGATION_BAR_CHANGED) { isNightMode ->
            if (isNightMode == AppConfig.isNightTheme) {
                refreshBottomNavigationConfig(force = true)
            }
        }
    }

    private fun upBottomMenu() {
        val showHomepage = AppConfig.showHomepage
        val showDiscovery = AppConfig.showDiscovery
        val showRss = AppConfig.showRSS
        val navOrder = AppConfig.navItemOrder

        val menu = binding.bottomNavigationView.menu
        menu.clear()

        var index = 0
        for (itemKey in navOrder) {
            when (itemKey) {
                "homepage" -> if (showHomepage) {
                    realPositions[index] = idHomepage
                    menu.add(0, R.id.menu_homepage, index, R.string.homepage)
                        .setIcon(R.drawable.ic_bottom_home)
                    index++
                }

                "bookshelf" -> {
                    realPositions[index] = idBookshelf
                    menu.add(0, R.id.menu_bookshelf, index, R.string.bookshelf)
                        .setIcon(R.drawable.ic_bottom_books)
                    index++
                }

                "explore" -> if (showDiscovery) {
                    realPositions[index] = idExplore
                    menu.add(0, R.id.menu_discovery, index, R.string.discovery)
                        .setIcon(R.drawable.ic_bottom_explore)
                    index++
                }

                "rss" -> if (showRss) {
                    realPositions[index] = idRss
                    menu.add(0, R.id.menu_rss, index, R.string.rss)
                        .setIcon(R.drawable.ic_bottom_rss_feed)
                    index++
                }

                "my" -> {
                    realPositions[index] = idMy
                    menu.add(0, R.id.menu_my_config, index, R.string.my)
                        .setIcon(R.drawable.ic_bottom_person)
                    index++
                }
            }
        }
        bottomMenuCount = index

        // 根据当前 ViewPager 位置设置底部导航栏选中状态
        val currentPosition = binding.viewPagerMain.currentItem.coerceAtMost(bottomMenuCount - 1)
        val menuItemId = fragmentIdToMenuItemId(realPositions[currentPosition])
        menu.findItem(menuItemId)?.isChecked = true

        // 应用底栏导航栏配置（布局模式、效果、背景色、透明度、自定义图标）
        applyNavigationBarPackage()

        adapter.notifyDataSetChanged()
    }

    private fun upHomePage() {
        // 根据 defaultHomePage 找到对应的 ViewPager 位置
        val defaultHome = AppConfig.defaultHomePage
        val fragmentId = when (defaultHome) {
            "homepage" -> idHomepage
            "explore" -> idExplore
            "rss" -> idRss
            "my" -> idMy
            else -> idBookshelf
        }
        val position = realPositions.indexOf(fragmentId)
        if (position >= 0) {
            binding.viewPagerMain.setCurrentItem(position, false)
        }
    }

    private fun getFragmentId(position: Int): Int {
        val id = realPositions[position]
        if (id == idBookshelf) {
            return if (AppConfig.bookGroupStyle == 1) idBookshelf2 else idBookshelf1
        }
        return id
    }

    private inner class PageChangeCallback : ViewPager.SimpleOnPageChangeListener() {

        override fun onPageSelected(position: Int) {
            pagePosition = position
            val fragmentId = realPositions[position]
            val menuItemId = fragmentIdToMenuItemId(fragmentId)
            binding.bottomNavigationView.menu.findItem(menuItemId)?.isChecked = true
        }

    }

    @Suppress("DEPRECATION")
    private inner class TabFragmentPageAdapter(fm: FragmentManager) :
        FragmentStatePagerAdapter(fm, BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT) {

        private fun getId(position: Int): Int {
            return getFragmentId(position)
        }

        override fun getItemPosition(any: Any): Int {
            val position = (any as MainFragmentInterface).position
                ?: return POSITION_NONE
            val fragmentId = getId(position)
            if ((fragmentId == idBookshelf1 && any is BookshelfFragment1)
                || (fragmentId == idBookshelf2 && any is BookshelfFragment2)
                || (fragmentId == idHomepage && any is HomepageFragment)
                || (fragmentId == idExplore && any is ExploreFragment)
                || (fragmentId == idRss && any is RssFragment)
                || (fragmentId == idMy && any is MyFragment)
            ) {
                return POSITION_UNCHANGED
            }
            return POSITION_NONE
        }

        override fun getItem(position: Int): Fragment {
            return when (getId(position)) {
                idBookshelf1 -> BookshelfFragment1(position)
                idBookshelf2 -> BookshelfFragment2(position)
                idHomepage -> HomepageFragment(position)
                idExplore -> ExploreFragment(position)
                idRss -> RssFragment(position)
                else -> MyFragment(position)
            }
        }

        override fun getCount(): Int {
            return bottomMenuCount
        }

        override fun instantiateItem(container: ViewGroup, position: Int): Any {
            var fragment = super.instantiateItem(container, position) as Fragment
            if (fragment.isCreated && getItemPosition(fragment) == POSITION_NONE) {
                destroyItem(container, position, fragment)
                fragment = super.instantiateItem(container, position) as Fragment
            }
            fragmentMap[getId(position)] = fragment
            return fragment
        }

    }

    override fun openImportUi(type:Int, source: String) {
        when (type) {
            0 -> showDialogFragment(
                ImportBookSourceDialog(source)
            )
            1 -> showDialogFragment(
                ImportRssSourceDialog(source)
            )
            2 -> showDialogFragment(
                ImportReplaceRuleDialog(source)
            )
        }
    }

    // ── 底栏导航栏配置 Shell 实现 ──

    /**
     * 计算底栏占用的总高度（导航栏高度 + 底部边距），
     * 用于设置 Fragment 内容的底部内边距，防止底栏遮挡内容。
     */
    fun mainContentBottomPadding(): Int {
        val bottomNav = binding.bottomNavigationGlass
        val layoutParams = bottomNav.layoutParams as? FrameLayout.LayoutParams
        val navHeight = bottomNav.height.takeIf { it > 0 } ?: bottomNav.minimumHeight
        val bottomMargin = layoutParams?.bottomMargin ?: 0
        return navHeight + bottomMargin
    }

    /**
     * 遍历所有 Fragment，通知它们更新底部内边距以适配底栏高度。
     */
    private fun refreshMainContentBottomPadding() {
        val bottomPadding = mainContentBottomPadding()
        fragmentMap.values.forEach { fragment ->
            if (fragment.view != null) {
                (fragment as? MainFragmentInterface)?.updateMainBottomPadding(bottomPadding)
            }
        }
    }

    /**
     * 刷新底栏配置（带签名缓存，避免重复刷新）
     */
    private fun refreshBottomNavigationConfig(force: Boolean = false) {
        val signature = NavigationBarConfig.currentSignature(this, AppConfig.isNightTheme)
        if (!force && bottomNavigationConfigSignature == signature) {
            return
        }
        bottomNavigationConfigSignature = signature
        applyNavigationBarPackage()
    }

    /**
     * 应用底栏导航栏配置包：背景色、自定义图标、布局 Shell（浮动/标准/侧栏 + 玻璃/磨砂/实色）
     */
    private fun applyNavigationBarPackage() = binding.run {
        val config = NavigationBarConfig.activeConfig(this@MainActivity, AppConfig.isNightTheme)
        val bgColor = resolveNavigationBarBackground(config)
        val hasCustomIcons = NavigationBarConfig.applyToMenu(
            bottomNavigationView.menu,
            this@MainActivity,
            AppConfig.isNightTheme,
            bgColor
        )
        if (hasCustomIcons) {
            bottomNavigationView.itemIconTintList = null
        } else {
            bottomNavigationView.restoreThemeIconTint(bgColor)
        }
        bottomNavigationView.itemBackground = Color.TRANSPARENT.toDrawable()
        applyBottomNavigationShell(config, bgColor)
        val textIsDark = ColorUtils.isColorLight(bgColor)
        bottomNavigationView.itemTextColor = io.legado.app.lib.theme.Selector.colorBuild()
            .setDefaultColor(getSecondaryTextColor(textIsDark))
            .setSelectedColor(accentColor)
            .create()
        bottomNavigationView.post {
            applyBottomNavigationSelectedIndicator()
            refreshMainContentBottomPadding()
        }
    }

    /** 解析底栏背景色：内置配置用主题色，自定义配置用配置中的颜色+透明度 */
    private fun resolveNavigationBarBackground(config: NavigationBarConfig): Int {
        if (config.isBuiltin) {
            return bottomBackground
        }
        val baseColor = if (AppConfig.isNightTheme) {
            getPrefInt(PreferKey.cNBBackground, getCompatColor(R.color.default_night_bottom_background))
        } else {
            getPrefInt(PreferKey.cBBackground, getCompatColor(R.color.default_bottom_background))
        }
        return NavigationBarConfig.resolveBottomColor(baseColor, config)
    }

    /** 解析底栏边框颜色 */
    private fun resolveBottomNavigationBorderColor(config: NavigationBarConfig): Int? {
        return config.borderColor?.let {
            if (Color.alpha(it) == 0) {
                Color.TRANSPARENT
            } else {
                ColorUtils.withAlpha(it, config.borderAlpha.coerceIn(0, 100) / 100f)
            }
        }
    }

    /**
     * 应用底栏 Shell：根据布局模式（浮动/标准/侧栏）和效果模式（实色/玻璃/磨砂）
     * 设置容器尺寸、边距、圆角、阴影和背景 Drawable
     */
    private fun applyBottomNavigationShell(config: NavigationBarConfig, bgColor: Int) = binding.run {
        val floating = config.layoutMode == NavigationBarConfig.LAYOUT_FLOATING
        val standard = config.layoutMode == NavigationBarConfig.LAYOUT_STANDARD
        val horizontalMargin = if (floating) 20.dpToPx() else 0
        val bottomMargin = if (floating) 10.dpToPx() + bottomNavigationInset else 0
        val shellHeight = if (floating) 48.dpToPx() else 50.dpToPx() + if (standard) bottomNavigationInset else 0
        bottomNavigationGlass.layoutParams = (bottomNavigationGlass.layoutParams as FrameLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = shellHeight
            gravity = Gravity.BOTTOM
            setMargins(horizontalMargin, 0, horizontalMargin, bottomMargin)
        }
        bottomNavigationView.layoutParams = (bottomNavigationView.layoutParams as FrameLayout.LayoutParams).apply {
            width = ViewGroup.LayoutParams.MATCH_PARENT
            height = ViewGroup.LayoutParams.MATCH_PARENT
            gravity = Gravity.CENTER
            setMargins(0, 0, 0, 0)
        }
        bottomNavigationGlass.minimumHeight = shellHeight
        bottomNavigationView.minimumHeight = if (floating) 48.dpToPx() else 50.dpToPx()
        bottomNavigationView.itemIconSize = if (floating) 23.dpToPx() else 22.dpToPx()
        bottomNavigationView.setPadding(
            if (floating) 6.dpToPx() else 0,
            0,
            if (floating) 6.dpToPx() else 0,
            if (standard) bottomNavigationInset else 0
        )
        bottomNavigationView.alpha = 1f
        bottomNavigationView.elevation = 0f
        bottomNavigationGlass.elevation = if (floating) {
            when (config.effectMode) {
                NavigationBarConfig.EFFECT_SOLID -> 8.dpToPx().toFloat()
                NavigationBarConfig.EFFECT_FROSTED -> 14.dpToPx().toFloat()
                else -> 12.dpToPx().toFloat()
            }
        } else {
            0f
        }
        bottomNavigationView.setBackgroundColor(Color.TRANSPARENT)
        bottomNavigationView.background = Color.TRANSPARENT.toDrawable()
        val liquid = !standard && config.effectMode != NavigationBarConfig.EFFECT_SOLID
        applyBottomNavigationGlassOutline(bottomNavigationGlass, if (floating) 24f.dpToPx() else 0f)
        if (liquid) {
            bottomNavigationShellOverlay.background = createLiquidGlassShellDrawable(
                glassLevel = config.opacity.coerceIn(0, 100) / 100f,
                cornerRadius = if (floating) 24f.dpToPx() else 0f,
                effectMode = config.effectMode,
                bgColor = bgColor,
                strokeColor = resolveBottomNavigationBorderColor(config)
            )
        } else {
            bottomNavigationShellOverlay.background = createBottomNavigationShellDrawable(config, bgColor)
        }
    }

    /** 设置底栏容器的圆角裁剪 */
    private fun applyBottomNavigationGlassOutline(view: View, cornerRadius: Float) {
        view.clipToOutline = cornerRadius > 0f
        view.outlineProvider = if (cornerRadius > 0f) {
            object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
                }
            }
        } else {
            ViewOutlineProvider.BOUNDS
        }
    }

    /** 创建玻璃/磨砂效果的 Shell 背景 Drawable */
    private fun createLiquidGlassShellDrawable(
        glassLevel: Float,
        cornerRadius: Float,
        effectMode: String,
        bgColor: Int,
        strokeColor: Int?
    ): GradientDrawable {
        val baseColor = Color.rgb(Color.red(bgColor), Color.green(bgColor), Color.blue(bgColor))
        val isLight = ColorUtils.isColorLight(baseColor)
        val neutralSurface = if (isLight) Color.WHITE else Color.rgb(22, 24, 28)
        val surfaceColor = ColorUtils.blendColors(
            baseColor,
            neutralSurface,
            if (effectMode == NavigationBarConfig.EFFECT_FROSTED) 0.26f else 0.14f
        )
        val frosted = effectMode == NavigationBarConfig.EFFECT_FROSTED
        val startAlpha = if (frosted) {
            (0.025f + glassLevel * 0.615f).coerceIn(0f, 0.76f)
        } else {
            (0.004f + glassLevel * 0.386f).coerceIn(0f, 0.48f)
        }
        val centerAlpha = if (frosted) {
            (0.018f + glassLevel * 0.462f).coerceIn(0f, 0.58f)
        } else {
            (0.003f + glassLevel * 0.267f).coerceIn(0f, 0.34f)
        }
        val endAlpha = if (frosted) {
            (0.012f + glassLevel * 0.348f).coerceIn(0f, 0.46f)
        } else {
            (0.002f + glassLevel * 0.198f).coerceIn(0f, 0.26f)
        }
        val strokeAlpha = if (frosted) {
            (0.20f + glassLevel * 0.16f).coerceIn(0f, 0.44f)
        } else {
            (0.22f + glassLevel * 0.18f).coerceIn(0f, 0.46f)
        }
        return GradientDrawable(
            GradientDrawable.Orientation.TOP_BOTTOM,
            intArrayOf(
                ColorUtils.withAlpha(surfaceColor, startAlpha),
                ColorUtils.withAlpha(surfaceColor, centerAlpha),
                ColorUtils.withAlpha(surfaceColor, endAlpha)
            )
        ).apply {
            shape = GradientDrawable.RECTANGLE
            setCornerRadius(cornerRadius)
            setStroke(1.dpToPx(), strokeColor ?: ColorUtils.withAlpha(surfaceColor, strokeAlpha))
        }
    }

    /** 创建实色/标准 Shell 背景 Drawable */
    private fun createBottomNavigationShellDrawable(config: NavigationBarConfig, bgColor: Int): Drawable {
        val standard = config.layoutMode == NavigationBarConfig.LAYOUT_STANDARD
        val radius = if (standard) 0f else 24f.dpToPx()
        val strokeColor = resolveBottomNavigationBorderColor(config)
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = radius
            setColor(bgColor)
            setStroke(
                if (!standard && strokeColor != null) 1.dpToPx() else 0,
                strokeColor ?: Color.TRANSPARENT
            )
        }
    }

    /** 清除选中项的默认背景指示器 */
    private fun applyBottomNavigationSelectedIndicator() = binding.run {
        val menuView = bottomNavigationView.getChildAt(0) as? ViewGroup ?: return@run
        val visibleItems = NavigationBarConfig.items
            .filter { bottomNavigationView.menu.findItem(it.menuId)?.isVisible == true }
        visibleItems.forEachIndexed { index, _ ->
            val child = menuView.getChildAt(index) ?: return@forEachIndexed
            child.background = Color.TRANSPARENT.toDrawable()
            child.setPadding(0, 3.dpToPx(), 0, 3.dpToPx())
        }
    }

}
