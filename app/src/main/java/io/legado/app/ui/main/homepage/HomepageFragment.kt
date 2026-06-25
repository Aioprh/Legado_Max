package io.legado.app.ui.main.homepage

import android.content.Intent
import android.graphics.drawable.Drawable
import android.os.Build
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.fragment.app.Fragment
import io.legado.app.help.config.ThemeConfig
import io.legado.app.data.appDb
import io.legado.app.ui.book.info.BookInfoActivity
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.ui.rss.article.RssSortActivity
import io.legado.app.ui.rss.read.ReadRssActivity
import io.legado.app.ui.theme.LegadoThemeWithBackground

/**
 * 首页 Fragment
 *
 * 作为首页在 MainActivity 中的容器，使用 ComposeView 承载 Compose 界面。
 * 通过 LegadoThemeWithBackground 包裹内容，确保主题颜色统一适配并显示背景图片。
 * 处理书籍点击（跳转 BookInfoActivity）和模块标题点击（跳转 ExploreShowActivity）的导航逻辑。
 */
class HomepageFragment() : Fragment(), MainFragmentInterface {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val backgroundDrawable = loadBackgroundDrawable()
        return ComposeView(requireContext()).apply {
            setViewCompositionStrategy(ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed)
            setContent {
                LegadoThemeWithBackground(
                    backgroundDrawable = backgroundDrawable
                ) {
                    HomepageScreen(
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

    /**
     * 加载主题设置的背景图片
     *
     * 从 ThemeConfig 获取当前主题的背景图片 Drawable，
     * 如果未设置背景图则返回 null，此时 LegadoBackgroundBox 会使用纯色背景。
     */
    private fun loadBackgroundDrawable(): Drawable? {
        return try {
            val activity = requireActivity()
            val metrics = DisplayMetrics()
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val bounds = activity.windowManager.currentWindowMetrics.bounds
                metrics.widthPixels = bounds.width()
                metrics.heightPixels = bounds.height()
            } else {
                @Suppress("DEPRECATION")
                activity.windowManager.defaultDisplay.getMetrics(metrics)
            }
            ThemeConfig.getBgImage(activity, metrics)
        } catch (_: Exception) {
            null
        }
    }
}
