package io.legado.app.ui.main.my

import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.fragment.app.viewModels
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.constant.EventBus
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.FragmentMyConfigBinding
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.dialogs.selector
import io.legado.app.service.WebService
import io.legado.app.ui.about.AboutActivity
import io.legado.app.ui.book.bookmark.AllBookmarkActivity
import io.legado.app.ui.book.readRecord.ReadRecordActivity
import io.legado.app.ui.book.source.manage.BookSourceActivity
import io.legado.app.ui.book.storage.StorageManageActivity
import io.legado.app.ui.book.toc.rule.TxtTocRuleActivity
import io.legado.app.ui.config.ConfigActivity
import io.legado.app.ui.config.ConfigTag
import io.legado.app.ui.debug.DebugToolsActivity
import io.legado.app.ui.dict.rule.DictRuleActivity
import io.legado.app.ui.download.DownloadManageActivity
import io.legado.app.ui.file.FileManageActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.replace.ReplaceRuleActivity
import io.legado.app.ui.theme.LegadoTheme
import io.legado.app.ui.urlRecord.UrlRecordActivity
import io.legado.app.utils.observeEvent
import io.legado.app.utils.observeEventSticky
import io.legado.app.utils.openUrl
import io.legado.app.utils.putPrefBoolean
import io.legado.app.utils.putPrefString
import io.legado.app.utils.sendToClip
import io.legado.app.utils.showHelp
import io.legado.app.utils.startActivity
import io.legado.app.utils.viewbindingdelegate.viewBinding

/**
 * 「我的」页（Compose 版）。
 *
 * 页面主体由 [MyScreen] 渲染，本类只负责：
 * 1. 标题栏与菜单（帮助入口）
 * 2. 入口跳转（[MyEntry] → Activity）
 * 3. 主题模式、Web 服务的系统副作用（写偏好、启停服务、应用日夜模式）
 * 4. 底栏高度回调，转成 Compose 的底部内边距
 */
class MyFragment() : BaseFragment(R.layout.fragment_my_config), MainFragmentInterface {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    private val binding by viewBinding(FragmentMyConfigBinding::bind)
    private val viewModel by viewModels<MyViewModel>()
    private var bottomPaddingPx by mutableIntStateOf(0)

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        // 让偏好里的 Web 服务状态与服务实际状态保持一致
        putPrefBoolean(PreferKey.webService, WebService.isRun)

        binding.composeView.setViewCompositionStrategy(
            ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
        )
        binding.composeView.setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val density = LocalDensity.current
            LegadoTheme {
                MyScreen(
                    state = state,
                    bottomContentPadding = with(density) { bottomPaddingPx.toDp() } + 16.dp,
                    onEntryClick = ::openEntry,
                    onThemeModeSelected = ::applyThemeMode,
                    onWebServiceToggle = ::setWebServiceRunning,
                    onWebServiceAddressClick = ::showWebServiceActions
                )
            }
        }

        viewModel.refreshPreferences()
        viewModel.observeStats()
        updateMainBottomPadding((activity as? MainActivity)?.mainContentBottomPadding() ?: 0)
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshPreferences()
    }

    override fun observeLiveBus() {
        observeEventSticky<String>(EventBus.WEB_SERVICE) {
            viewModel.refreshPreferences()
        }
        observeEvent<Boolean>(EventBus.DEBUG_MODE_CHANGED) {
            viewModel.refreshPreferences()
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        menuInflater.inflate(R.menu.main_my, menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        when (item.itemId) {
            R.id.menu_help -> showHelp("appHelp")
        }
    }

    override fun updateMainBottomPadding(bottomPadding: Int) {
        bottomPaddingPx = bottomPadding
    }

    private fun openEntry(entry: MyEntry) {
        when (entry) {
            MyEntry.BOOK_SOURCE -> startActivity<BookSourceActivity>()
            MyEntry.TXT_TOC_RULE -> startActivity<TxtTocRuleActivity>()
            MyEntry.REPLACE_RULE -> startActivity<ReplaceRuleActivity>()
            MyEntry.DICT_RULE -> startActivity<DictRuleActivity>()
            MyEntry.BOOKMARK -> startActivity<AllBookmarkActivity>()
            MyEntry.READ_RECORD -> startActivity<ReadRecordActivity>()
            MyEntry.URL_RECORD -> startActivity<UrlRecordActivity>()
            MyEntry.FILE_MANAGE -> startActivity<FileManageActivity>()
            MyEntry.STORAGE_MANAGE -> startActivity<StorageManageActivity>()
            MyEntry.DOWNLOAD_MANAGE -> startActivity<DownloadManageActivity>()
            MyEntry.PRECISE_MANAGE -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.PRECISE_MANAGE)
            }
            MyEntry.DEBUG_TOOLS -> startActivity<DebugToolsActivity>()
            MyEntry.THEME_SETTING -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.THEME_CONFIG)
            }
            MyEntry.BACKUP_RESTORE -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.BACKUP_CONFIG)
            }
            MyEntry.OTHER_SETTING -> startActivity<ConfigActivity> {
                putExtra("configTag", ConfigTag.OTHER_CONFIG)
            }
            MyEntry.ABOUT -> startActivity<AboutActivity>()
            MyEntry.EXIT -> activity?.finish()
        }
    }

    private fun applyThemeMode(mode: Int) {
        val context = context ?: return
        putPrefString(PreferKey.themeMode, mode.toString())
        ThemeConfig.applyDayNight(context)
    }

    private fun setWebServiceRunning(enabled: Boolean) {
        val context = context ?: return
        putPrefBoolean(PreferKey.webService, enabled)
        viewModel.optimisticWebServiceState(enabled)
        if (enabled) {
            WebService.start(context)
        } else {
            WebService.stop(context)
        }
    }

    private fun showWebServiceActions() {
        val context = context ?: return
        if (!WebService.isRun) return
        val address = WebService.hostAddress
        if (address.isBlank()) return
        context.selector(
            arrayListOf(
                getString(R.string.copy_url),
                getString(R.string.open_in_browser)
            )
        ) { _, index ->
            when (index) {
                0 -> context.sendToClip(address)
                1 -> context.openUrl(address)
            }
        }
    }
}
