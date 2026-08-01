package io.legado.app.ui.main.homepage

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import io.legado.app.data.appDb
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.main.MainActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.rss.article.RssSortActivity
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.theme.LegadoTheme

/**
 * 首页 Fragment
 *
 * 作为首页在 MainActivity 中的容器，使用 ComposeView 承载 Compose 界面。
 * 通过 LegadoTheme 提供主题颜色，背景由 BaseActivity 的 decorView 统一管理，
 * 与其他 Fragment（书架、发现等）保持一致的导航栏沉浸行为。
 * 处理书籍点击（跳转 BookInfoActivity）和模块标题点击（跳转 ExploreShowActivity）的导航逻辑。
 */
class HomepageFragment() : Fragment(), MainFragmentInterface {

    /**
     * 使用 activityViewModels 将 ViewModel 作用域绑定到 Activity，
     * 避免 FragmentStatePagerAdapter 销毁 Fragment 时 ViewModel 被一同清除，
     * 导致切回首页时所有模块数据重新加载。
     */
    private val viewModel: HomepageViewModel by activityViewModels()

    /** 底栏占用的底部高度（px），由 MainActivity 通知更新，用于 Compose 内容的底部 padding */
    private var bottomPaddingPx by mutableIntStateOf(0)

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override fun updateMainBottomPadding(bottomPadding: Int) {
        bottomPaddingPx = bottomPadding
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        // 初始化时获取当前底栏高度
        bottomPaddingPx = (activity as? MainActivity)?.mainContentBottomPadding() ?: 0
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoTheme {
                    HomepageScreen(
                        viewModel = viewModel,
                        bottomPaddingPx = bottomPaddingPx,
                        onBookClick = { name, author, bookUrl, origin, coverPath ->
                            // RSS 订阅源文章 → 直接加载文章 URL（openUrl 路径，不依赖 DB 预存）
                            if (origin != null && appDb.rssSourceDao.has(origin)) {
                                ReadRssActivity.start(
                                    context = requireContext(),
                                    singleTop = false,
                                    origin = origin,
                                    title = name,
                                    url = bookUrl
                                )
                                return@HomepageScreen
                            }
                            // 书源书籍 → 跳转详情页
                            val intent = Intent(context, BookInfoActivity::class.java).apply {
                                putExtra("name", name)
                                putExtra("author", author)
                                putExtra("bookUrl", bookUrl)
                                origin?.let { putExtra("origin", it) }
                                coverPath?.let { putExtra("coverPath", it) }
                            }
                            startActivity(intent)
                        },
                        onModuleHeaderClick = { title, sourceUrl, exploreUrl ->
                            // RSS 订阅源模块 → 跳转订阅源文章列表，自动选中对应分类
                            if (appDb.rssSourceDao.has(sourceUrl)) {
                                val intent = Intent(context, RssSortActivity::class.java).apply {
                                    putExtra("sourceUrl", sourceUrl)
                                    if (!title.isNullOrBlank()) {
                                        putExtra("sortName", title)
                                    }
                                }
                                startActivity(intent)
                                return@HomepageScreen
                            }
                            // 书源模块 → 跳转发现页
                            if (exploreUrl.isNullOrBlank()) return@HomepageScreen
                            val intent = Intent(context, ExploreShowActivity::class.java).apply {
                                putExtra("exploreName", title ?: "")
                                putExtra("sourceUrl", sourceUrl)
                                putExtra("exploreUrl", exploreUrl)
                            }
                            startActivity(intent)
                        },
                    )
                }
            }
        }
    }
}
